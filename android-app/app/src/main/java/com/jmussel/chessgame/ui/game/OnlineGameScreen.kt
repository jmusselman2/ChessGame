package com.jmussel.chessgame.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.jmussel.chessgame.api.GameViewDto
import com.jmussel.chessgame.api.MoveDto
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.ui.board.ChessBoard
import com.jmussel.chessgame.ui.theme.ChessGameTheme

/**
 * One server-owned game.
 *
 * Everything on the screen comes from the server's last answer (`D004`): the position, the
 * side the viewer plays, whose move it is, the check, the moves played, and how it ended.
 * Nothing here can change any of it — playing a move is `M14.11` — so a finished game and a
 * game in progress are drawn the same way, and a game from history is read-only by
 * construction rather than by a flag.
 */
@Composable
fun OnlineGameScreen(
    state: OnlineGameState,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (state) {
            is OnlineGameState.Loading -> Text(text = LOADING, style = MaterialTheme.typography.bodyMedium)

            is OnlineGameState.Failed -> {
                Text(text = state.message, style = MaterialTheme.typography.bodyMedium)
                if (state.canRetry) {
                    TextButton(onClick = onRetry) { Text(text = RETRY) }
                }
            }

            is OnlineGameState.Ready -> Game(game = state.game)
        }
    }
}

/** The game itself, drawn from the server's answer. */
@Composable
private fun Game(game: GameViewDto) {
    Text(text = OnlineGame.headingFor(game), style = MaterialTheme.typography.titleSmall)

    ChessBoard(
        board = OnlineGame.boardFrom(game.board),
        orientation = OnlineGame.sideOf(game),
        lastMove = OnlineGame.lastMoveSquares(game),
    )

    Text(text = OnlineGame.statusFor(game), style = MaterialTheme.typography.bodyLarge)
    Text(text = OnlineGame.positionFor(game), style = MaterialTheme.typography.bodySmall)

    OnlineGame.moveListLines(game).forEach { line ->
        Text(text = line, style = MaterialTheme.typography.bodySmall)
    }
}

private const val LOADING = "Loading…"
private const val RETRY = "Try again"

@Preview(showBackground = true)
@Composable
private fun OnlineGameScreenPreview() {
    ChessGameTheme {
        OnlineGameScreen(
            state =
                OnlineGameState.Ready(
                    GameViewDto(
                        gameId = "game-1",
                        seriesId = "series-1",
                        opponent = UserSummaryDto(userId = "user-1", username = "Alex"),
                        version = 3,
                        yourSide = "WHITE",
                        sideToMove = "WHITE",
                        yourTurn = true,
                        board =
                            listOf(
                                "rnbqkbnr",
                                "pppp.ppp",
                                "........",
                                "....p...",
                                "....P...",
                                "........",
                                "PPPP.PPP",
                                "RNBQKBNR",
                            ),
                        moves = listOf("e2e4", "e7e5"),
                        lastMove = MoveDto(from = "e7", to = "e5"),
                        moveNumber = 2,
                    ),
                ),
        )
    }
}
