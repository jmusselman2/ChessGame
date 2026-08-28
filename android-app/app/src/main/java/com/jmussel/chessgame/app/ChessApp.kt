package com.jmussel.chessgame.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.navigation.AppNavigation
import com.jmussel.chessgame.navigation.Destination
import com.jmussel.chessgame.ui.board.LocalGameScreen
import com.jmussel.chessgame.ui.dashboard.DashboardActions
import com.jmussel.chessgame.ui.dashboard.DashboardScreen
import com.jmussel.chessgame.ui.dashboard.DashboardUiState
import com.jmussel.chessgame.ui.friends.FriendsActions
import com.jmussel.chessgame.ui.friends.FriendsScreen
import com.jmussel.chessgame.ui.friends.FriendsUiState
import com.jmussel.chessgame.ui.game.OnlineGameScreen
import com.jmussel.chessgame.ui.game.OnlineGameState
import com.jmussel.chessgame.ui.history.HistoryScreen
import com.jmussel.chessgame.ui.onboarding.UsernameClaim
import com.jmussel.chessgame.ui.onboarding.UsernameScreen
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
    usernameClaim: UsernameClaim = UsernameClaim.Idle,
    friends: FriendsUiState = FriendsUiState(),
    dashboard: DashboardUiState = DashboardUiState(),
    game: OnlineGameState? = null,
    onOpen: (Destination) -> Unit = {},
    onOpenFriends: () -> Unit = {},
    onOpenGame: (String) -> Unit = {},
    onBack: () -> Unit = {},
    onRetryStartup: () -> Unit = {},
    onRetryGame: () -> Unit = {},
    onSquareTapped: (Square) -> Unit = {},
    onChoosePromotion: (PieceType) -> Unit = {},
    onCancelPromotion: () -> Unit = {},
    onUndo: () -> Unit = {},
    onClaimDraw: (String) -> Unit = {},
    onClaimUsername: (String) -> Unit = {},
    friendsActions: FriendsActions = FriendsActions(),
    dashboardActions: DashboardActions = DashboardActions(),
) {
    Column(modifier = modifier.fillMaxSize()) {
        ShellChrome(navigation = navigation, onOpen = onOpen, onOpenFriends = onOpenFriends, onBack = onBack)

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
            // Live history is M14.17.
            Destination.History ->
                HistoryScreen(
                    series = emptyList(),
                    onOpenGame = { row -> onOpenGame(row.gameId) },
                )
            Destination.LocalGame -> LocalGameScreen()
            is Destination.OnlineGame ->
                OnlineGameScreen(
                    state = game ?: OnlineGameState.Loading(destination.gameId),
                    onRetry = onRetryGame,
                    onSquareTapped = onSquareTapped,
                    onChoosePromotion = onChoosePromotion,
                    onCancelPromotion = onCancelPromotion,
                    onUndo = onUndo,
                    onClaimDraw = onClaimDraw,
                )
        }
    }
}

/**
 * The way out of the current screen, and the way to the others.
 *
 * Startup and onboarding have no chrome: there is nowhere to go from either until the
 * player has an account with a username.
 */
@Composable
private fun ShellChrome(
    navigation: AppNavigation,
    onOpen: (Destination) -> Unit,
    onOpenFriends: () -> Unit,
    onBack: () -> Unit,
) {
    if (navigation.current == Destination.Startup || navigation.current == Destination.UsernameOnboarding) return

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (navigation.canGoBack) {
            TextButton(onClick = onBack) { Text(text = BACK) }
            return@Row
        }

        TextButton(onClick = onOpenFriends) { Text(text = FRIENDS) }
        TextButton(onClick = { onOpen(Destination.History) }) { Text(text = HISTORY) }
        TextButton(onClick = { onOpen(Destination.LocalGame) }) { Text(text = LOCAL_GAME) }
    }
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
private const val SIGN_IN_PROBLEM = "Cannot sign in"
private const val RETRY = "Try again"

@Preview(showBackground = true)
@Composable
private fun ChessAppPreview() {
    ChessGameTheme {
        ChessApp(navigation = AppNavigation(listOf(Destination.Dashboard)))
    }
}
