package com.jmussel.chessgame.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jmussel.chessgame.api.FinishedGameDto
import com.jmussel.chessgame.api.SeriesHistoryDto
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.ui.theme.ChessGameTheme

/**
 * The games that are over, kept off the home screen and available here
 * (`docs/PRODUCT.md`).
 *
 * Nothing on this screen can change anything: a finished game refuses every command
 * (`D017`) and a closed series never gets another one (`D013`), so there is nothing to
 * offer beyond opening a game to look at it. What each line says is decided by
 * [HistoryList], so this composable holds no rules of its own.
 */
@Composable
fun HistoryScreen(
    series: List<SeriesHistoryDto>,
    modifier: Modifier = Modifier,
    state: HistoryUiState = HistoryUiState(loaded = true),
    onOpenGame: (HistoryGameRow) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = HISTORY, style = MaterialTheme.typography.titleSmall)

        state.message?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }

        if (!state.loaded) {
            // Nothing has arrived yet: say which of the two that is rather than "no games".
            if (state.loading) {
                Text(text = LOADING, style = MaterialTheme.typography.bodyMedium)
            } else {
                TextButton(onClick = onRetry) { Text(text = RETRY) }
            }
            return@Column
        }

        val sections = HistoryList.sections(series)

        if (sections.isEmpty()) {
            Text(text = NOTHING_YET, style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        sections.forEach { section ->
            SeriesSection(section = section, onOpenGame = onOpenGame)
        }
    }
}

/** One opponent, and the games already played out against them. */
@Composable
private fun SeriesSection(
    section: HistorySection,
    onOpenGame: (HistoryGameRow) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = section.heading, style = MaterialTheme.typography.bodyLarge)

        section.games.forEach { game ->
            Column(
                modifier = Modifier.fillMaxWidth().clickable { onOpenGame(game) },
            ) {
                Text(text = game.summary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private const val HISTORY = "HISTORY"
private const val NOTHING_YET = "Games you finish will appear here."
private const val LOADING = "Loading…"
private const val RETRY = "Try again"

@Preview(showBackground = true)
@Composable
private fun HistoryScreenPreview() {
    ChessGameTheme {
        HistoryScreen(
            series =
                listOf(
                    SeriesHistoryDto(
                        seriesId = "series-1",
                        opponent = UserSummaryDto(userId = "user-1", username = "Alex"),
                        status = "ACTIVE",
                        games =
                            listOf(
                                FinishedGameDto(
                                    gameId = "game-1",
                                    sequenceNumber = 1,
                                    yourSide = "WHITE",
                                    result = "WHITE_WINS",
                                    terminationReason = "CHECKMATE",
                                    moveNumber = 31,
                                ),
                                FinishedGameDto(
                                    gameId = "game-2",
                                    sequenceNumber = 2,
                                    yourSide = "BLACK",
                                    result = "DRAW",
                                    terminationReason = "THREEFOLD_REPETITION_CLAIM",
                                    moveNumber = 44,
                                ),
                            ),
                    ),
                    SeriesHistoryDto(
                        seriesId = "series-2",
                        opponent = UserSummaryDto(userId = "user-2", username = "Sam"),
                        status = "CLOSED",
                        closedAt = "2026-08-20T18:03:00Z",
                        games =
                            listOf(
                                FinishedGameDto(
                                    gameId = "game-3",
                                    sequenceNumber = 1,
                                    yourSide = "WHITE",
                                    result = "BLACK_WINS",
                                    terminationReason = "RESIGNATION",
                                    moveNumber = 12,
                                ),
                            ),
                    ),
                ),
        )
    }
}
