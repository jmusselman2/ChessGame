$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$review = Get-Content -LiteralPath (Join-Path $repositoryRoot 'docs\PLATFORM-REVIEW.md') -Raw
$backlog = Get-Content -LiteralPath (Join-Path $repositoryRoot 'docs\BACKLOG.md') -Raw
$m17Start = $backlog.IndexOf('# M17 — Friend Beta', [StringComparison]::Ordinal)
$m18Start = $backlog.IndexOf('# M18 — Post-Chess Architecture Review', [StringComparison]::Ordinal)
if ($m17Start -lt 0 -or $m18Start -le $m17Start) {
    throw 'Could not isolate the authoritative M17 evidence in docs/BACKLOG.md.'
}
$m17Evidence = $backlog.Substring($m17Start, $m18Start - $m17Start)

$claimsTwoPhysicalDevices =
    $review -match '(?is)two\s+real\s+people\s+on\s+two\s+physical\s+devices'
$recordsOwnerPhysicalDevice =
    $m17Evidence -match '(?is)project\s+owner\s+(?:used|played\s+(?:on|from)|was\s+using).{0,100}physical\s+(?:Android\s+)?device' -or
    $m17Evidence -match '(?is)(?:both|two)\s+(?:people\s+used\s+)?physical\s+(?:Android\s+)?devices'

if ($claimsTwoPhysicalDevices -and -not $recordsOwnerPhysicalDevice) {
    throw 'PLATFORM-REVIEW.md claims two physical devices, but the M17 record identifies only the tester device as physical.'
}

$reviewedAt = [regex]::Match($review, '(?m)^\*\*Reviewed at:\*\* `([0-9a-f]{7,40})`')
if (-not $reviewedAt.Success) {
    throw 'PLATFORM-REVIEW.md does not name the Git revision its measurements describe.'
}

$documentedCoreFiles = [regex]::Match(
    $review,
    '(?m)^\| `game-core` \(all chess\)\s+\|\s+(\d+)\s+\|'
)
if (-not $documentedCoreFiles.Success) {
    throw 'PLATFORM-REVIEW.md does not contain the measured game-core file count.'
}

$reviewedRevision = $reviewedAt.Groups[1].Value
$actualCoreFiles = @(
    git -C $repositoryRoot ls-tree -r --name-only $reviewedRevision -- game-core/src/main/kotlin |
        Where-Object { $_ -match '\.kt$' }
).Count
$claimedCoreFiles = [int]$documentedCoreFiles.Groups[1].Value
if ($claimedCoreFiles -ne $actualCoreFiles) {
    throw "PLATFORM-REVIEW.md claims $claimedCoreFiles game-core Kotlin files at $reviewedRevision; Git contains $actualCoreFiles."
}

Write-Output 'PASS: the platform review matches the M17 physical-device evidence and its named-snapshot file count.'
