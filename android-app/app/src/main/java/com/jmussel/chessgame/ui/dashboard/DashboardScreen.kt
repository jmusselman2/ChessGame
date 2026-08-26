package com.jmussel.chessgame.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jmussel.chessgame.api.DashboardEntryDto
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.ui.theme.ChessGameTheme

/**
 * The home screen a returning player lands on (`docs/PRODUCT.md`).
 *
 * Only the games waiting on the player are shown so far; Their Turn is `M14.2` and Friends
 * is `M14.3`. What to show is decided by [DashboardSections], so this composable holds no
 * rules of its own.
 */
@Composable
fun DashboardScreen(
    entries: List<DashboardEntryDto>,
    modifier: Modifier = Modifier,
    onOpenGame: (DashboardRow) -> Unit = {},
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        YourTurnSection(
            rows = DashboardSections.yourTurn(entries),
            onOpenGame = onOpenGame,
        )
    }
}

/** The games it is the player's move in. */
@Composable
private fun YourTurnSection(
    rows: List<DashboardRow>,
    onOpenGame: (DashboardRow) -> Unit,
) {
    Text(text = YOUR_TURN, style = MaterialTheme.typography.titleSmall)

    if (rows.isEmpty()) {
        Text(text = NOTHING_WAITING, style = MaterialTheme.typography.bodyMedium)
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
private const val NOTHING_WAITING = "Nothing waiting on you."

@Preview(showBackground = true)
@Composable
private fun DashboardScreenPreview() {
    ChessGameTheme {
        DashboardScreen(
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
                ),
        )
    }
}
