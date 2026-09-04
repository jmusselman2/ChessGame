package com.jmussel.chessgame.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jmussel.chessgame.core.chess.DrawClaim
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.ui.theme.ChessGameTheme

/**
 * Pass-and-play on one device: the board and whose turn it is, both read straight from
 * `game-core`.
 *
 * Nothing here is canonical and nothing here is sent anywhere — this is the local game,
 * kept separate from server-owned state (`docs/ARCHITECTURE.md`). Tapping a square goes
 * through [BoardInteraction], which owns what a tap means; this composable only holds the
 * resulting state.
 */
@Composable
fun LocalGameScreen(
    modifier: Modifier = Modifier,
    initialState: BoardUiState = BoardUiState.newGame(),
) {
    var state by remember { mutableStateOf(initialState) }
    var resigning by remember { mutableStateOf<Side?>(null) }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChessBoard(
            board = state.board,
            selectedSquare = state.selectedSquare,
            legalDestinations = BoardInteraction.legalDestinations(state),
            orientation = state.orientation,
            onSquareClick = { square -> state = BoardInteraction.onSquareTapped(state, square) },
        )

        state.pendingPromotion?.let { pending ->
            PromotionPrompt(
                choices = pending.choices,
                onChoose = { choice -> state = BoardInteraction.choosePromotion(state, choice) },
            )
        }

        state.declaredMove?.let { declared ->
            DeclaredMovePrompt(
                declared = declared,
                onClaim = { claim -> state = GameControls.claimDeclaredDraw(state, claim) },
                onPlay = { state = BoardInteraction.playDeclaredMove(state) },
                onCancel = { state = BoardInteraction.cancelDeclaredMove(state) },
            )
        }

        Text(text = GameControls.statusFor(state.game))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (GameControls.canUndo(state)) {
                Button(onClick = { state = GameControls.undo(state) }) {
                    Text(text = "Undo")
                }
            }
        }

        GameControls.availableDrawClaims(state).forEach { claim ->
            Button(onClick = { state = GameControls.claimDraw(state, claim) }) {
                Text(text = GameControls.labelFor(claim))
            }
        }

        // Either player may give up, on their own move or the other's, and is asked first
        // because it cannot be taken back (`D018`).
        if (GameControls.canResign(state)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Side.entries.forEach { side ->
                    Button(onClick = { resigning = side }) { Text(text = GameControls.resignLabelFor(side)) }
                }
            }
        }

        resigning?.let { side ->
            ResignConfirmation(
                side = side,
                onConfirm = {
                    state = GameControls.resign(state, side)
                    resigning = null
                },
                onCancel = { resigning = null },
            )
        }

        MoveList(lines = GameControls.moveListLines(state.game))
    }
}

/** The question asked before a resignation, which cannot be taken back (`D018`). */
@Composable
private fun ResignConfirmation(
    side: Side,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(text = "Resign?") },
        text = { Text(text = "${if (side == Side.WHITE) "White" else "Black"} loses this game. This cannot be undone.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text(text = "Resign") } },
        dismissButton = { TextButton(onClick = onCancel) { Text(text = "Keep playing") } },
    )
}

/** The moves played so far, newest last. */
@Composable
private fun MoveList(lines: List<String>) {
    if (lines.isEmpty()) return

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        lines.forEach { line -> Text(text = line) }
    }
}

/**
 * The choice a move that would entitle a draw raises: claim that draw, or play the move
 * and give it up.
 *
 * Standard chess lets the player to move claim on the position their declared move is
 * about to make, and the tap that plays it hands the position to the other player, so the
 * screen has to ask before playing. The declaration binds — only this exact move entitles
 * these claims (`D038`, `D041`).
 */
@Composable
private fun DeclaredMovePrompt(
    declared: DeclaredMove,
    onClaim: (DrawClaim) -> Unit,
    onPlay: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Playing ${declared.move} lets you claim a draw first.")
        declared.claims.forEach { claim ->
            Button(onClick = { onClaim(claim) }) { Text(text = GameControls.labelFor(claim)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPlay) { Text(text = "Play ${declared.move}") }
            TextButton(onClick = onCancel) { Text(text = "Cancel") }
        }
    }
}

/** The four pieces a pawn may become, offered as buttons. */
@Composable
private fun PromotionPrompt(
    choices: List<PieceType>,
    onChoose: (PieceType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Promote to")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { choice ->
                Button(onClick = { onChoose(choice) }) {
                    Text(text = BoardRendering.glyphFor(choice).toString())
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LocalGameScreenPreview() {
    ChessGameTheme {
        LocalGameScreen()
    }
}
