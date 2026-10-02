package com.jmussel.chessgame.ui.localhistory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jmussel.chessgame.local.LocalGameSummary
import com.jmussel.chessgame.ui.board.BoardRendering
import com.jmussel.chessgame.ui.board.ChessBoard
import com.jmussel.chessgame.ui.board.GameBackButton
import com.jmussel.chessgame.ui.board.GameControls
import com.jmussel.chessgame.ui.board.GameEndHeadline
import com.jmussel.chessgame.ui.board.GameLayout
import java.text.DateFormat
import java.util.Date

/**
 * Finished local games, newest first (`D084`, `M21.3`). Each opens read-only.
 *
 * What each line says is decided by [PastLocalGames]; the date is the device's own medium
 * date format.
 */
@Composable
fun PastLocalGamesScreen(
    state: PastLocalGamesUiState,
    modifier: Modifier = Modifier,
    onOpenGame: (LocalGameSummary) -> Unit = {},
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "PAST LOCAL GAMES", style = MaterialTheme.typography.titleSmall)

        when {
            state.loading -> Text(text = "Loading…", style = MaterialTheme.typography.bodyMedium)
            state.games.isEmpty() ->
                Text(text = "Local games you finish will appear here.", style = MaterialTheme.typography.bodyMedium)
            else -> {
                val dates = DateFormat.getDateInstance(DateFormat.MEDIUM)
                state.games.forEach { game ->
                    Text(
                        text = PastLocalGames.summaryFor(game) { millis -> dates.format(Date(millis)) },
                        modifier = Modifier.fillMaxWidth().clickable { onOpenGame(game) }.padding(vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/**
 * One finished local game, read-only: the board at the chosen ply, stepped through with
 * the buttons, and the whole move list.
 *
 * A pass-and-play game is drawn face to face, as it was played (`D087`). Nothing on the
 * board can be tapped.
 */
@Composable
fun LocalGameReviewScreen(
    review: LocalGameReview?,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    if (review == null) {
        Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            onBack?.let { GameBackButton(onClick = it) }
            Text(text = "Loading…", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val game = review.game
    GameLayout(
        onBack = onBack,
        modifier = modifier,
        board = { side ->
            ChessBoard(
                board = game.state.board,
                side = side,
                lastMove = BoardRendering.lastMoveSquares(game),
                orientation = review.orientation,
                faceToFace = review.faceToFace,
            )
        },
        controls = {
            Text(text = PastLocalGames.opponentLabel(review.stored.computer))
            review.ending?.let { GameEndHeadline(text = it) } ?: Text(text = GameControls.statusFor(game))
            Text(text = review.position, style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onStep(0) }, enabled = review.canStepBack) { Text(text = "Start") }
                Button(onClick = { onStep(review.ply - 1) }, enabled = review.canStepBack) { Text(text = "Previous") }
                Button(onClick = { onStep(review.ply + 1) }, enabled = review.canStepForward) { Text(text = "Next") }
                Button(onClick = { onStep(review.plies) }, enabled = review.canStepForward) { Text(text = "End") }
            }
        },
        moveList = {
            GameControls.moveListLines(review.stored.game).forEach { line -> Text(text = line) }
        },
    )
}
