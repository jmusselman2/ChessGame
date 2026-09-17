$ErrorActionPreference = 'Stop'

$server = 'https://chessgame-hit7.onrender.com'
$supabase = $env:SUPABASE_URL
$anonKey = $env:SUPABASE_ANON_KEY

if ([string]::IsNullOrWhiteSpace($supabase) -or [string]::IsNullOrWhiteSpace($anonKey)) {
    throw 'SUPABASE_URL and SUPABASE_ANON_KEY are required.'
}

function Assert-True([bool]$condition, [string]$message) {
    if (-not $condition) { throw $message }
}

function New-Session {
    $response = Invoke-RestMethod -Method Post -Uri "$supabase/auth/v1/signup" `
        -Headers @{ apikey = $anonKey } -ContentType 'application/json' -Body '{}'
    Assert-True (-not [string]::IsNullOrWhiteSpace($response.access_token)) 'Supabase returned no access token.'
    return $response.access_token
}

function Invoke-Api(
    [string]$token,
    [string]$method,
    [string]$path,
    [AllowNull()]$body = $null,
    [string]$contentType = 'text/plain'
) {
    $arguments = @{
        Uri = "$server$path"
        Method = $method
        Headers = @{ Authorization = "Bearer $token" }
        SkipHttpErrorCheck = $true
    }
    if ($null -ne $body) {
        $arguments.Body = $body
        $arguments.ContentType = $contentType
    }
    return Invoke-WebRequest @arguments
}

function Json($response) {
    return $response.Content | ConvertFrom-Json
}

function Receive-WebSocketText($socket, [int]$seconds = 15) {
    $buffer = [byte[]]::new(4096)
    $cancellation = [Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds($seconds))
    try {
        $result = $socket.ReceiveAsync([ArraySegment[byte]]::new($buffer), $cancellation.Token).GetAwaiter().GetResult()
        return [Text.Encoding]::UTF8.GetString($buffer, 0, $result.Count)
    } finally {
        $cancellation.Dispose()
    }
}

$suffix = [DateTime]::UtcNow.ToString('MMddHHmmss')
$nameA = "E19A$suffix"
$nameB = "E19B$suffix"
$nameC = "E19C$suffix"
$tokenA = New-Session
$tokenB = New-Session
$tokenC = New-Session

$meA1 = Json (Invoke-Api $tokenA Get '/me')
$meA2 = Json (Invoke-Api $tokenA Get '/me')
Assert-True ($meA1.userId -eq $meA2.userId) 'The same session did not restore the same account.'
Assert-True (-not ($meA1.PSObject.Properties.Name -contains 'lastLoginAt')) 'An engagement timestamp leaked through /me.'
Write-Output 'PASS session restoration and private timestamp projection'

@(@($tokenA, $nameA), @($tokenB, $nameB), @($tokenC, $nameC)) | ForEach-Object {
    $response = Invoke-Api $_[0] Post '/username' $_[1]
    Assert-True ($response.StatusCode -eq 200) "Could not claim username $($_[1])."
}
$lookup = Json (Invoke-Api $tokenA Get "/users/$nameB")
Assert-True ($lookup.username -eq $nameB) 'Username lookup did not return the requested user.'
Write-Output 'PASS username claims and lookup'

Assert-True ((Invoke-Api $tokenA Post '/friends' $nameB).StatusCode -eq 200) 'A could not befriend B.'
Assert-True ((Invoke-Api $tokenB Post '/friends' $nameC).StatusCode -eq 200) 'B could not befriend C.'
$friendsA = Json (Invoke-Api $tokenA Get '/friends')
Assert-True ($friendsA.username -contains $nameB) 'The mutual friendship was not listed.'
Write-Output 'PASS friendships'

$group = Json (Invoke-Api $tokenA Post '/groups' "M19-$suffix")
Assert-True ($group.memberCount -eq 1) 'A new group did not immediately contain its creator.'
Assert-True ((Invoke-Api $tokenA Post "/groups/$($group.groupId)/members" $nameB).StatusCode -eq 200) 'A could not add friend B.'
Assert-True ((Invoke-Api $tokenB Post "/groups/$($group.groupId)/members" $nameC).StatusCode -eq 200) 'B could not add friend C.'
$members = Json (Invoke-Api $tokenA Get "/groups/$($group.groupId)/members")
Assert-True (($members.username -contains $nameA) -and ($members.username -contains $nameB) -and ($members.username -contains $nameC)) 'Transitive group membership was not visible.'
Assert-True ((Invoke-Api $tokenC Delete "/groups/$($group.groupId)/members/me").StatusCode -eq 200) 'C could not leave unilaterally.'
$membersAfterLeave = Json (Invoke-Api $tokenA Get "/groups/$($group.groupId)/members")
Assert-True (-not ($membersAfterLeave.username -contains $nameC)) 'Leaving did not revoke current group membership.'
Write-Output 'PASS group creation, eligible additions, transitive membership, and leave'

$first = Invoke-Api $tokenA Post '/series' $nameB
Assert-True ($first.StatusCode -eq 201) 'The first Play did not start a series.'
$series1 = Json $first
$offer = Invoke-Api $tokenA Post '/series' $nameB
Assert-True ($offer.StatusCode -eq 409) 'Play did not offer the existing series.'
$offered = Json $offer
Assert-True ($offered.existing.seriesId -contains $series1.seriesId) 'The offer did not identify the existing series.'
$parallel = Invoke-Api $tokenA Post '/series?another=true' $nameB
Assert-True ($parallel.StatusCode -eq 201) 'Start another did not create a parallel series.'
$series2 = Json $parallel
Assert-True ($series2.seriesId -ne $series1.seriesId) 'The parallel series reused the existing id.'
Write-Output 'PASS existing-series offer and parallel series'

$gameA = Json (Invoke-Api $tokenA Get "/games/$($series1.currentGameId)")
$moverToken = if ($gameA.yourTurn) { $tokenA } else { $tokenB }
$opponentToken = if ($gameA.yourTurn) { $tokenB } else { $tokenA }
$socket = [Net.WebSockets.ClientWebSocket]::new()
$socket.Options.SetRequestHeader('Authorization', "Bearer $opponentToken")
$connectCancellation = [Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds(20))
try {
    $null = $socket.ConnectAsync([Uri]'wss://chessgame-hit7.onrender.com/ws', $connectCancellation.Token).GetAwaiter().GetResult()
    $greeting = Receive-WebSocketText $socket
    Assert-True ($greeting -match 'connected') 'Realtime did not send its greeting.'

    $move = Invoke-Api $moverToken Post "/games/$($series1.currentGameId)/moves" '{"expectedVersion":0,"from":"e2","to":"e4"}' 'application/json'
    Assert-True ($move.StatusCode -eq 200) 'The legal move was not accepted.'
    $moved = Json $move
    Assert-True (($moved.version -eq 1) -and ($moved.lastMove.from -eq 'e2') -and ($moved.lastMove.to -eq 'e4')) 'The canonical moved state was wrong.'
    $update = Receive-WebSocketText $socket
    Assert-True (($update -match 'game-updated') -and ($update -match [regex]::Escape($series1.currentGameId))) 'The opponent did not receive the realtime update.'
} finally {
    $connectCancellation.Dispose()
    $socket.Dispose()
}
Write-Output 'PASS legal move and realtime opponent update'

$stale = Invoke-Api $moverToken Post "/games/$($series1.currentGameId)/moves" '{"expectedVersion":0,"from":"a2","to":"a3"}' 'application/json'
Assert-True ($stale.StatusCode -eq 409) 'The stale move was not refused as a conflict.'
$staleBody = Json $stale
Assert-True (($staleBody.reason -eq 'STALE_VERSION') -and ($staleBody.game.version -eq 1)) 'The stale refusal did not carry canonical recovery state.'
$undo = Invoke-Api $moverToken Post "/games/$($series1.currentGameId)/undo" '{"expectedVersion":1}' 'application/json'
Assert-True ($undo.StatusCode -eq 200) 'Undo was not accepted.'
$undone = Json $undo
Assert-True (($undone.version -eq 2) -and ($undone.moves.Count -eq 0)) 'Undo did not restore the prior position at a new version.'
Write-Output 'PASS undo and stale-version recovery'

$resigned = Invoke-Api $tokenA Post "/games/$($series1.currentGameId)/resignation" '{"expectedVersion":2}' 'application/json'
Assert-True ($resigned.StatusCode -eq 200) 'Resignation was not accepted.'
$resignedGame = Json $resigned
Assert-True ($resignedGame.terminationReason -eq 'RESIGNATION') 'The game did not finish by resignation.'
$dashboardA = Json (Invoke-Api $tokenA Get '/dashboard')
$rematchEntry = @($dashboardA) | Where-Object seriesId -eq $series1.seriesId
Assert-True (($null -ne $rematchEntry) -and ($rematchEntry.gameId -ne $series1.currentGameId)) 'Resignation did not create the automatic rematch.'
$rematch = Json (Invoke-Api $tokenA Get "/games/$($rematchEntry.gameId)")
Assert-True ($rematch.yourSide -ne $gameA.yourSide) 'The rematch did not rotate chess seats.'
Write-Output 'PASS resignation, automatic rematch, and seat rotation'

$parallelBefore = Json (Invoke-Api $tokenA Get "/games/$($series2.currentGameId)")
$left = Invoke-Api $tokenA Post "/series/$($series2.seriesId)/leave"
Assert-True ($left.StatusCode -eq 200) 'Explicit series exit was not accepted.'
$parallelAfter = Json (Invoke-Api $tokenA Get "/games/$($series2.currentGameId)")
Assert-True (($parallelAfter.version -eq $parallelBefore.version) -and (($parallelAfter.board -join '/') -eq ($parallelBefore.board -join '/')) -and (-not $parallelAfter.seriesActive)) 'Series exit disturbed its current game.'
Write-Output 'PASS explicit series exit without disturbing the current game'

Assert-True ((Invoke-Api $tokenA Delete "/friends/$nameB").StatusCode -eq 200) 'Unfriending failed.'
$rematchAfterUnfriend = Invoke-Api $tokenA Get "/games/$($rematchEntry.gameId)"
Assert-True ($rematchAfterUnfriend.StatusCode -eq 200) 'Unfriending made the active series game unavailable.'
$dashboardAfterUnfriend = Json (Invoke-Api $tokenA Get '/dashboard')
Assert-True ((@($dashboardAfterUnfriend) | Where-Object seriesId -eq $series1.seriesId).Count -eq 1) 'Unfriending closed or hid the active series.'
Write-Output 'PASS unfriending without closing an active series'

$history = Json (Invoke-Api $tokenA Get '/history')
$historySeries = @($history) | Where-Object seriesId -eq $series1.seriesId
Assert-True (($historySeries.games.gameId -contains $series1.currentGameId)) 'The finished pre-rematch game was not preserved in history.'
Write-Output 'PASS dashboard and history preservation across the smoke flow'

Write-Output "BETA SMOKE PASS: $nameA / $nameB / $nameC (throwaway accounts retained by D035)"
