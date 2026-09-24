package com.jmussel.chessgame.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.navigation.AppNavigation
import com.jmussel.chessgame.navigation.Destination
import com.jmussel.chessgame.ui.allusers.AllUsersActions
import com.jmussel.chessgame.ui.allusers.AllUsersScreen
import com.jmussel.chessgame.ui.allusers.AllUsersUiState
import com.jmussel.chessgame.ui.board.LocalGameScreen
import com.jmussel.chessgame.ui.board.LocalGameUiState
import com.jmussel.chessgame.ui.dashboard.DashboardActions
import com.jmussel.chessgame.ui.dashboard.DashboardScreen
import com.jmussel.chessgame.ui.dashboard.DashboardUiState
import com.jmussel.chessgame.ui.friends.FriendsActions
import com.jmussel.chessgame.ui.friends.FriendsScreen
import com.jmussel.chessgame.ui.friends.FriendsUiState
import com.jmussel.chessgame.ui.game.OnlineGameScreen
import com.jmussel.chessgame.ui.game.OnlineGameState
import com.jmussel.chessgame.ui.groups.GroupActions
import com.jmussel.chessgame.ui.groups.GroupScreen
import com.jmussel.chessgame.ui.groups.GroupUiState
import com.jmussel.chessgame.ui.groups.GroupsActions
import com.jmussel.chessgame.ui.groups.GroupsScreen
import com.jmussel.chessgame.ui.groups.GroupsUiState
import com.jmussel.chessgame.ui.history.HistoryScreen
import com.jmussel.chessgame.ui.history.HistoryUiState
import com.jmussel.chessgame.ui.onboarding.UsernameClaim
import com.jmussel.chessgame.ui.onboarding.UsernameScreen
import com.jmussel.chessgame.ui.series.PlayOffer
import com.jmussel.chessgame.ui.series.PlayOfferActions
import com.jmussel.chessgame.ui.series.PlayOfferDialog
import com.jmussel.chessgame.ui.theme.ChessGameTheme

/**
 * The whole application: whichever screen [navigation] says, and the way between them.
 *
 * Which screen that is has already been decided by [AppNavigation], so this composable
 * only draws it and reports what the player asked for. Everything a screen needs is passed
 * in from [ChessAppViewModel]; nothing is constructed here.
 */
@Composable
fun ChessApp(
    navigation: AppNavigation,
    modifier: Modifier = Modifier,
    startup: StartupState = StartupState.Loading,
    /** The player's own username, once the account has one. */
    username: String? = null,
    /** The player's own user id, which marks them in a group's members. */
    userId: String? = null,
    usernameClaim: UsernameClaim = UsernameClaim.Idle,
    friends: FriendsUiState = FriendsUiState(),
    allUsers: AllUsersUiState = AllUsersUiState(),
    groups: GroupsUiState = GroupsUiState(),
    group: GroupUiState = GroupUiState(),
    dashboard: DashboardUiState = DashboardUiState(),
    history: HistoryUiState = HistoryUiState(),
    game: OnlineGameState? = null,
    /** The local game in progress, held by the view model so a rotation keeps it (`D073`). */
    localGame: LocalGameUiState = LocalGameUiState(),
    onLocalGameChange: (LocalGameUiState) -> Unit = {},
    onOpen: (Destination) -> Unit = {},
    onOpenFriends: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onRetryHistory: () -> Unit = {},
    onOpenGame: (String) -> Unit = {},
    onBack: () -> Unit = {},
    onRetryStartup: () -> Unit = {},
    onRetryGame: () -> Unit = {},
    onSquareTapped: (Square) -> Unit = {},
    onChoosePromotion: (PieceType) -> Unit = {},
    onCancelPromotion: () -> Unit = {},
    onUndo: () -> Unit = {},
    onClaimDraw: (String) -> Unit = {},
    onAskToResign: () -> Unit = {},
    onResign: () -> Unit = {},
    onCancelResignation: () -> Unit = {},
    onAskToLeaveSeries: () -> Unit = {},
    onLeaveSeries: () -> Unit = {},
    onCancelLeaveSeries: () -> Unit = {},
    onOpenNextGame: () -> Unit = {},
    onGameDone: () -> Unit = {},
    onClaimUsername: (String) -> Unit = {},
    friendsActions: FriendsActions = FriendsActions(),
    allUsersActions: AllUsersActions = AllUsersActions(),
    groupsActions: GroupsActions = GroupsActions(),
    groupActions: GroupActions = GroupActions(),
    dashboardActions: DashboardActions = DashboardActions(),
    playOffer: PlayOffer? = null,
    playOfferActions: PlayOfferActions = PlayOfferActions(),
) {
    // Play is on several screens and raises the same choice from each (`D053`).
    playOffer?.let { PlayOfferDialog(offer = it, actions = playOfferActions) }

    Column(modifier = modifier.fillMaxSize()) {
        ShellChrome(
            navigation = navigation,
            username = username,
            onOpen = onOpen,
            onOpenFriends = onOpenFriends,
            onOpenHistory = onOpenHistory,
            onBack = onBack,
        )

        when (val destination = navigation.current) {
            Destination.Startup -> StartupScreen(state = startup, onRetry = onRetryStartup)
            Destination.UsernameOnboarding -> UsernameScreen(claim = usernameClaim, onClaim = onClaimUsername)
            Destination.Dashboard ->
                DashboardScreen(
                    entries = dashboard.entries,
                    friends = friends.friends,
                    state = dashboard,
                    onOpenGame = dashboardActions.onOpenGame,
                    onPlayFriend = dashboardActions.onPlayFriend,
                    onRetry = dashboardActions.onRetry,
                )
            Destination.Friends -> FriendsScreen(state = friends, actions = friendsActions)
            Destination.AllUsers -> AllUsersScreen(state = allUsers, actions = allUsersActions)
            Destination.Groups -> GroupsScreen(state = groups, actions = groupsActions)
            is Destination.Group -> GroupScreen(state = group, ownUserId = userId, actions = groupActions)
            Destination.History ->
                HistoryScreen(
                    series = history.series,
                    state = history,
                    onOpenGame = { row -> onOpenGame(row.gameId) },
                    onRetry = onRetryHistory,
                )
            Destination.LocalGame -> LocalGameScreen(state = localGame, onStateChange = onLocalGameChange, onBack = onBack)
            is Destination.OnlineGame ->
                OnlineGameScreen(
                    state = game ?: OnlineGameState.Loading(destination.gameId),
                    onBack = onBack,
                    onRetry = onRetryGame,
                    onSquareTapped = onSquareTapped,
                    onChoosePromotion = onChoosePromotion,
                    onCancelPromotion = onCancelPromotion,
                    onUndo = onUndo,
                    onClaimDraw = onClaimDraw,
                    onAskToResign = onAskToResign,
                    onResign = onResign,
                    onCancelResignation = onCancelResignation,
                    onAskToLeaveSeries = onAskToLeaveSeries,
                    onLeaveSeries = onLeaveSeries,
                    onCancelLeaveSeries = onCancelLeaveSeries,
                    onOpenNextGame = onOpenNextGame,
                    onDone = onGameDone,
                )
        }
    }
}

/**
 * The way out of the current screen, and the way to the others.
 *
 * Startup and onboarding have no chrome: there is nowhere to go from either until the
 * player has an account with a username. The game screens have none either: they draw
 * their own Back, so the board can have the whole height (`D073`).
 */
@Composable
private fun ShellChrome(
    navigation: AppNavigation,
    username: String?,
    onOpen: (Destination) -> Unit,
    onOpenFriends: () -> Unit,
    onOpenHistory: () -> Unit,
    onBack: () -> Unit,
) {
    if (!ShellChromeContent.hasChrome(navigation)) return

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigation.canGoBack) {
            TextButton(onClick = onBack) { Text(text = BACK) }
            return@Row
        }

        TextButton(onClick = onOpenFriends) { Text(text = FRIENDS) }
        TextButton(onClick = onOpenHistory) { Text(text = HISTORY) }
        TextButton(onClick = { onOpen(Destination.LocalGame) }) { Text(text = LOCAL_GAME) }

        // At the far end, where an account conventionally sits. Plain text for now: it
        // becomes the way into user settings (`M17.4`), and until then it must not look
        // like something to tap.
        ShellChromeContent.ownUsername(navigation, username)?.let { name ->
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = name,
                modifier = Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * What the top row holds, decided without a screen so it can be tested (`M17.3`).
 */
internal object ShellChromeContent {
    /**
     * Whether there is a top row at all.
     *
     * Not on startup or onboarding, and not on a game screen, which has its own Back
     * (`D073`).
     */
    fun hasChrome(navigation: AppNavigation): Boolean =
        when (navigation.current) {
            Destination.Startup, Destination.UsernameOnboarding, Destination.LocalGame, is Destination.OnlineGame -> false
            else -> true
        }

    /**
     * The player's own name, on the home row only, or `null` when none is shown.
     *
     * A screen with Back has only Back: the name belongs to the dashboard, where it will
     * also be the way into user settings.
     */
    fun ownUsername(
        navigation: AppNavigation,
        username: String?,
    ): String? = username?.takeIf { hasChrome(navigation) && !navigation.canGoBack && it.isNotBlank() }
}

/**
 * Getting a session, and what to do when it does not arrive.
 *
 * The account is invisible (`D006`), so a working startup has nothing to say and shows
 * only that it is working. A failure says what went wrong and offers the retry when trying
 * again could help; a build with no Supabase key gets the explanation without the button,
 * because tapping it would fail identically.
 */
@Composable
private fun StartupScreen(
    state: StartupState,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (state) {
            is StartupState.Failed -> {
                Text(text = SIGN_IN_PROBLEM, style = MaterialTheme.typography.titleSmall)
                Text(text = state.message, style = MaterialTheme.typography.bodyMedium)
                if (state.canRetry) {
                    TextButton(onClick = onRetry) { Text(text = RETRY) }
                }
            }

            // Not a failure, and it must not read as one. The beta's free instance sleeps
            // after about fifteen idle minutes and takes roughly a minute to come back
            // (`M15.2`), so this is the ordinary experience of being the first to open the
            // app in a while. The retry is offered anyway — waiting is the app's job, but
            // deciding to stop waiting is the player's.
            is StartupState.Waking -> {
                Text(text = WAKING, style = MaterialTheme.typography.titleSmall)
                Text(text = WAKING_DETAIL, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRetry) { Text(text = RETRY) }
            }

            // Waiting, and the moment between a session arriving and the dashboard
            // replacing this screen, look the same: there is nothing to report.
            else -> Text(text = STARTING, style = MaterialTheme.typography.titleSmall)
        }
    }
}

private const val BACK = "Back"
private const val FRIENDS = "Friends"
private const val HISTORY = "History"
private const val LOCAL_GAME = "Local game"
private const val STARTING = "Starting…"
private const val WAKING = "Waking the server…"
private const val WAKING_DETAIL =
    "The server sleeps when nobody has played for a while. The first game of the day takes about a minute to start."
private const val SIGN_IN_PROBLEM = "Cannot sign in"
private const val RETRY = "Try again"

@Preview(showBackground = true)
@Composable
private fun ChessAppPreview() {
    ChessGameTheme {
        ChessApp(navigation = AppNavigation(listOf(Destination.Dashboard)), username = "Taylor")
    }
}
