package com.jmussel.chessgame.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jmussel.chessgame.ui.board.ChessBoard
import com.jmussel.chessgame.ui.board.GameBackButton
import com.jmussel.chessgame.ui.board.GameEndHeadline
import com.jmussel.chessgame.ui.board.GameLayout

/**
 * A finished game, read-only: the board at the chosen ply, stepped through with Start,
 * Previous, Next and End, and the move list in SAN up to that ply, its last move current
 * (`D105`). The same screen for a local game
 * (`M21.3`) and an online one (`M22.10`). Nothing on the board can be tapped.
 *
 * [unavailable] is what to say instead when there is no review to show: the server could not
 * project one, or it could not be read.
 */
@Composable
fun GameReviewScreen(
    review: GameReview?,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    unavailable: String? = null,
) {
    if (review == null) {
        Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            onBack?.let { GameBackButton(onClick = it) }
            Text(text = unavailable ?: LOADING, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    GameLayout(
        onBack = onBack,
        modifier = modifier,
        board = { side ->
            ChessBoard(
                board = review.position.board,
                side = side,
                lastMove = review.lastMove,
                checkedKing = review.checkedKing,
                orientation = review.orientation,
                faceToFace = review.faceToFace,
            )
        },
        controls = {
            Text(text = review.title)
            // The steps come before anything whose height changes with the ply — the ending's
            // larger headline at the last ply, a turn line elsewhere — so they stay exactly where
            // the player's thumb is while stepping, in every window and text size (`M23.14`).
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onStep(0) }, enabled = review.canStepBack) { Text(text = START) }
                Button(onClick = { onStep(review.ply - 1) }, enabled = review.canStepBack) { Text(text = PREVIOUS) }
                Button(onClick = { onStep(review.ply + 1) }, enabled = review.canStepForward) { Text(text = NEXT) }
                Button(onClick = { onStep(review.plies) }, enabled = review.canStepForward) { Text(text = END) }
            }
            if (review.ply == review.plies) GameEndHeadline(text = review.status) else Text(text = review.status)
            Text(text = review.positionText, style = MaterialTheme.typography.bodySmall)
        },
        // Only the moves up to the position on the board, the last of them current (`D105`).
        moveHistory = remember(review.moves, review.ply) { review.history },
    )
}

private const val LOADING = "Loading…"
private const val START = "Start"
private const val PREVIOUS = "Previous"
private const val NEXT = "Next"
private const val END = "End"
