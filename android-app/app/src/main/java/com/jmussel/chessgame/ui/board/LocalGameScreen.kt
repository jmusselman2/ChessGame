package com.jmussel.chessgame.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
 * Everything a local game screen shows: the game, and the resignation being asked about.
 *
 * Held by `ChessAppViewModel` in the app, so a rotation keeps a game in progress (`D073`).
 * It is never persisted: a local game does not survive the process.
 */
data class LocalGameUiState(
    val boardState: BoardUiState = BoardUiState.newGame(),
    /** The side whose resignation is being confirmed, or `null` when none is. */
    val resigning: Side? = null,
)

/**
 * A local game that holds its own state, starting from [initialState].
 *
 * For previews and screen tests, which have no view model; the app holds the state itself
 * and uses the other overload.
 */
@Composable
fun LocalGameScreen(
    modifier: Modifier = Modifier,
    initialState: BoardUiState = BoardUiState.newGame(),
) {
    var state by remember { mutableStateOf(LocalGameUiState(boardState = initialState)) }

    LocalGameScreen(state = state, onStateChange = { state = it }, modifier = modifier)
}

/**
 * Pass-and-play on one device: the board and whose turn it is, both read straight from
 * `game-core`.
 *
 * Nothing here is canonical and nothing here is sent anywhere — this is the local game,
 * kept separate from server-owned state (`docs/ARCHITECTURE.md`). Tapping a square goes
 * through [BoardInteraction], which owns what a tap means; this composable only shows
 * [state] and hands every change to [onStateChange].
 */
@Composable
fun LocalGameScreen(
    state: LocalGameUiState,
    onStateChange: (LocalGameUiState) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val game = state.boardState

    fun play(next: BoardUiState) = onStateChange(state.copy(boardState = next))

    GameLayout(
        onBack = onBack,
        modifier = modifier,
        board = { side ->
            ChessBoard(
                board = game.board,
                side = side,
                selectedSquare = game.selectedSquare,
                legalDestinations = BoardInteraction.legalDestinations(game),
                orientation = game.orientation,
                onSquareClick = { square -> play(BoardInteraction.onSquareTapped(game, square)) },
            )
        },
        controls = {
            Text(text = GameControls.statusFor(game.game))

            game.pendingPromotion?.let { pending ->
                PromotionPrompt(
                    choices = pending.choices,
                    onChoose = { choice -> play(BoardInteraction.choosePromotion(game, choice)) },
                )
            }

            game.declaredMove?.let { declared ->
                DeclaredMovePrompt(
                    declared = declared,
                    onClaim = { claim -> play(GameControls.claimDeclaredDraw(game, claim)) },
                    onPlay = { play(BoardInteraction.playDeclaredMove(game)) },
                    onCancel = { play(BoardInteraction.cancelDeclaredMove(game)) },
                )
            }

            if (GameControls.canUndo(game)) {
                Button(onClick = { play(GameControls.undo(game)) }) {
                    Text(text = "Undo")
                }
            }

            GameControls.availableDrawClaims(game).forEach { claim ->
                Button(onClick = { play(GameControls.claimDraw(game, claim)) }) {
                    Text(text = GameControls.labelFor(claim))
                }
            }

            // Either player may give up, on their own move or the other's, and is asked first
            // because it cannot be taken back (`D018`).
            if (GameControls.canResign(game)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Side.entries.forEach { side ->
                        Button(onClick = { onStateChange(state.copy(resigning = side)) }) {
                            Text(text = GameControls.resignLabelFor(side))
                        }
                    }
                }
            }
        },
        moveList = {
            // The moves played so far, newest last.
            GameControls.moveListLines(game.game).forEach { line -> Text(text = line) }
        },
    )

    state.resigning?.let { side ->
        ResignConfirmation(
            side = side,
            onConfirm = { onStateChange(LocalGameUiState(boardState = GameControls.resign(game, side))) },
            onCancel = { onStateChange(state.copy(resigning = null)) },
        )
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { choice ->
                PromotionChoice(type = choice, onClick = { onChoose(choice) })
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
