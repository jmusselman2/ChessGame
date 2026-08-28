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
import com.jmussel.chessgame.navigation.AppNavigation
import com.jmussel.chessgame.navigation.Destination
import com.jmussel.chessgame.ui.board.LocalGameScreen
import com.jmussel.chessgame.ui.dashboard.DashboardScreen
import com.jmussel.chessgame.ui.history.HistoryScreen
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
    onOpen: (Destination) -> Unit = {},
    onBack: () -> Unit = {},
    onRetryStartup: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize()) {
        ShellChrome(navigation = navigation, onOpen = onOpen, onBack = onBack)

        when (val destination = navigation.current) {
            Destination.Startup -> StartupScreen(state = startup, onRetry = onRetryStartup)
            // Claiming a username is M14.7.
            Destination.UsernameOnboarding -> PendingScreen(title = USERNAME, detail = NOT_WIRED_UP)
            // Live dashboard data is M14.9.
            Destination.Dashboard ->
                DashboardScreen(
                    entries = emptyList(),
                    friends = emptyList(),
                    onOpenGame = { row -> onOpen(Destination.OnlineGame(row.gameId)) },
                    // Opening a friend with no game yet has to ask the server for the
                    // series first, which is M14.8.
                    onPlayFriend = { row -> row.gameId?.let { onOpen(Destination.OnlineGame(it)) } },
                )
            // Adding and removing friends is M14.8.
            Destination.Friends -> PendingScreen(title = FRIENDS, detail = NOT_WIRED_UP)
            // Live history is M14.17.
            Destination.History ->
                HistoryScreen(
                    series = emptyList(),
                    onOpenGame = { row -> onOpen(Destination.OnlineGame(row.gameId)) },
                )
            Destination.LocalGame -> LocalGameScreen()
            // Loading a server-owned game is M14.10.
            is Destination.OnlineGame -> PendingScreen(title = GAME, detail = destination.gameId)
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

        TextButton(onClick = { onOpen(Destination.Friends) }) { Text(text = FRIENDS) }
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

/** A screen whose contents are a later task: what it will be, and nothing pretending to be it. */
@Composable
private fun PendingScreen(
    title: String,
    detail: String?,
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        detail?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
    }
}

private const val BACK = "Back"
private const val FRIENDS = "Friends"
private const val HISTORY = "History"
private const val LOCAL_GAME = "Local game"
private const val STARTING = "Starting…"
private const val SIGN_IN_PROBLEM = "Cannot sign in"
private const val RETRY = "Try again"
private const val USERNAME = "Username"
private const val GAME = "Game"
private const val NOT_WIRED_UP = "Not wired up yet."

@Preview(showBackground = true)
@Composable
private fun ChessAppPreview() {
    ChessGameTheme {
        ChessApp(navigation = AppNavigation(listOf(Destination.Dashboard)))
    }
}
