package com.jmussel.chessgame.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jmussel.chessgame.api.DashboardEntryDto
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.ui.theme.ChessGameTheme

/**
 * The home screen a returning player lands on (`docs/PRODUCT.md`).
 *
 * The games waiting on the player come first, the games waiting on the opponent follow,
 * and every friend is reachable at the bottom. What to show is decided by
 * [DashboardSections], so this composable holds no rules of its own.
 */
@Composable
fun DashboardScreen(
    entries: List<DashboardEntryDto>,
    modifier: Modifier = Modifier,
    friends: List<UserSummaryDto> = emptyList(),
    state: DashboardUiState = DashboardUiState(loaded = true),
    onOpenGame: (DashboardRow) -> Unit = {},
    onPlayFriend: (FriendRow) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.message?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }

        if (!state.loaded) {
            // Nothing has arrived yet, so there is nothing to show but what is happening.
            if (state.loading) {
                Text(text = LOADING, style = MaterialTheme.typography.bodyMedium)
            } else {
                TextButton(onClick = onRetry) { Text(text = RETRY) }
            }
            return@Column
        }

        Section(
            heading = YOUR_TURN,
            emptyMessage = NOTHING_WAITING,
            rows = DashboardSections.yourTurn(entries),
            onOpenGame = onOpenGame,
        )

        // Nothing can be done in these, but a player still wants to look.
        Section(
            heading = THEIR_TURN,
            emptyMessage = null,
            rows = DashboardSections.theirTurn(entries),
            onOpenGame = onOpenGame,
        )

        FriendsSection(
            rows = DashboardSections.friends(friends, entries),
            onPlayFriend = onPlayFriend,
        )
    }
}

/**
 * Everyone the player can play, each with the one thing to do about them.
 *
 * This is how a player reaches a friend they have no game with, so it is shown even when
 * the list is empty — an account with no friends yet needs to be told that adding one is
 * how anything starts.
 */
@Composable
private fun FriendsSection(
    rows: List<FriendRow>,
    onPlayFriend: (FriendRow) -> Unit,
) {
    Text(text = FRIENDS, style = MaterialTheme.typography.titleSmall)

    if (rows.isEmpty()) {
        Text(text = NO_FRIENDS_YET, style = MaterialTheme.typography.bodyMedium)
        return
    }

    rows.forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = row.username, style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { onPlayFriend(row) }) {
                Text(text = row.action)
            }
        }
    }
}

/**
 * One heading and its games.
 *
 * A section with nothing in it shows [emptyMessage] if it has one, and otherwise says
 * nothing at all — an empty THEIR TURN is not worth a line.
 */
@Composable
private fun Section(
    heading: String,
    emptyMessage: String?,
    rows: List<DashboardRow>,
    onOpenGame: (DashboardRow) -> Unit,
) {
    if (rows.isEmpty() && emptyMessage == null) return

    Text(text = heading, style = MaterialTheme.typography.titleSmall)

    if (rows.isEmpty()) {
        emptyMessage?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
        return
    }

    rows.forEach { row ->
        DashboardRowItem(row = row, onClick = { onOpenGame(row) })
    }
}

/** One opponent, and where that game is. */
@Composable
private fun DashboardRowItem(
    row: DashboardRow,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = row.opponent, style = MaterialTheme.typography.bodyLarge)
        Text(text = row.detail, style = MaterialTheme.typography.bodySmall)
    }
}

private const val YOUR_TURN = "YOUR TURN"
private const val THEIR_TURN = "THEIR TURN"
private const val FRIENDS = "FRIENDS"
private const val NOTHING_WAITING = "Nothing waiting on you."
private const val NO_FRIENDS_YET = "Add a friend by username to start playing."
private const val LOADING = "Loading…"
private const val RETRY = "Try again"

@Preview(showBackground = true)
@Composable
private fun DashboardScreenPreview() {
    ChessGameTheme {
        DashboardScreen(
            friends =
                listOf(
                    UserSummaryDto(userId = "user-1", username = "Alex"),
                    UserSummaryDto(userId = "user-3", username = "Chris"),
                    UserSummaryDto(userId = "user-4", username = "Robin"),
                ),
            entries =
                listOf(
                    DashboardEntryDto(
                        seriesId = "series-1",
                        opponent = UserSummaryDto(userId = "user-1", username = "Alex"),
                        gameId = "game-1",
                        version = 34,
                        yourSide = "WHITE",
                        sideToMove = "WHITE",
                        moveNumber = 18,
                        yourTurn = true,
                    ),
                    DashboardEntryDto(
                        seriesId = "series-2",
                        opponent = UserSummaryDto(userId = "user-2", username = "Sam"),
                        gameId = "game-2",
                        version = 12,
                        yourSide = "BLACK",
                        sideToMove = "BLACK",
                        moveNumber = 7,
                        yourTurn = true,
                    ),
                    DashboardEntryDto(
                        seriesId = "series-3",
                        opponent = UserSummaryDto(userId = "user-3", username = "Chris"),
                        gameId = "game-3",
                        version = 47,
                        yourSide = "WHITE",
                        sideToMove = "BLACK",
                        moveNumber = 24,
                        yourTurn = false,
                    ),
                ),
        )
    }
}
