package com.jmussel.chessgame.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
 * Everything a local game screen shows: the game, and what is being asked about.
 *
 * Held by `ChessAppViewModel` in the app, so a rotation keeps a game in progress (`D073`).
 * The game itself is saved on the device as it is played (`D084`, `M21.2`); the rest of
 * this is only the screen.
 */
data class LocalGameUiState(
    val boardState: BoardUiState = BoardUiState.newGame(),
    /** The side whose resignation is being confirmed, or `null` when none is. */
    val resigning: Side? = null,
    /** Whether the player is being asked before an unfinished game is deleted for a new one. */
    val confirmingNewGame: Boolean = false,
    /** Whether the game is still being read from the device, so there is nothing to show. */
    val loading: Boolean = false,
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

    LocalGameScreen(state = state, onStateChange = { state = it }, onNewGame = { state = LocalGameUiState() }, modifier = modifier)
}

/**
 * Pass-and-play on one device: the board and whose turn it is, both read straight from
 * `chess-core`. The board is drawn face to face and never turns, like a board on a table
 * between the two players (`D087`).
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
    /** Replaces this game with a new one, once the player has been asked if they need to be. */
    onNewGame: () -> Unit = {},
    /** Opens the finished game in the past-local-game review, at its final move (`M21.16`). */
    onReview: (() -> Unit)? = null,
) {
    if (state.loading) {
        Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            onBack?.let { GameBackButton(onClick = it) }
            Text(text = "Loading…", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

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
                lastMove = BoardRendering.lastMoveSquares(game.game),
                checkedKing = BoardRendering.checkedKing(game.game),
                orientation = game.orientation,
                faceToFace = true,
                onSquareClick = { square -> play(BoardInteraction.onSquareTapped(game, square)) },
            )
        },
        controls = {
            // A finished game says how it ended above everything else (`M21.16`).
            if (game.game.isOver) {
                GameEndHeadline(text = GameControls.statusFor(game.game))
            } else {
                Text(text = GameControls.statusFor(game.game))
            }

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

            // Named for the side whose move it takes back, which `chess-core` decides (`M21.18`).
            GameControls.undoLabelFor(game)?.let { label ->
                Button(onClick = { play(GameControls.undo(game)) }) {
                    Text(text = label)
                }
            }

            val claims = GameControls.availableDrawClaims(game)
            if (claims.isNotEmpty()) {
                ExplainedActions(explanation = GameControls.CLAIM_EXPLANATION) {
                    claims.forEach { claim ->
                        Button(onClick = { play(GameControls.claimDraw(game, claim)) }) {
                            Text(text = GameControls.labelFor(claim))
                        }
                    }
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

            // A finished game's next steps (`M21.16`): another game, or a look back at this one.
            if (game.game.isOver) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onNewGame) { Text(text = "New game") }
                    onReview?.let { Button(onClick = it) { Text(text = "Review") } }
                }
            } else if (GameControls.canStartNewGame(game)) {
                // A new game deletes an unfinished one, so the player is asked first (`D084`).
                TextButton(
                    onClick = {
                        if (GameControls.newGameNeedsConfirmation(game)) {
                            onStateChange(state.copy(confirmingNewGame = true))
                        } else {
                            onNewGame()
                        }
                    },
                ) {
                    Text(text = "New game")
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

    if (state.confirmingNewGame) {
        NewGameConfirmation(
            onConfirm = onNewGame,
            onCancel = { onStateChange(state.copy(confirmingNewGame = false)) },
        )
    }
}

/** The question asked before an unfinished game is deleted for a new one (`D084`). */
@Composable
internal fun NewGameConfirmation(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(text = "Start a new game?") },
        text = { Text(text = "This game is not finished. It will be deleted, and it is not kept.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text(text = "Start a new game") } },
        dismissButton = { TextButton(onClick = onCancel) { Text(text = "Keep playing") } },
    )
}

/** The question asked before a resignation, which cannot be taken back (`D018`). */
@Composable
internal fun ResignConfirmation(
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
internal fun DeclaredMovePrompt(
    declared: DeclaredMove,
    onClaim: (DrawClaim) -> Unit,
    onPlay: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = GameControls.declaredClaimExplanation(declared))
        declared.claims.forEach { claim ->
            Button(onClick = { onClaim(claim) }) { Text(text = GameControls.labelFor(claim)) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPlay) { Text(text = "Play ${declared.move}") }
            TextButton(onClick = onCancel) { Text(text = "Cancel") }
        }
    }
}

/**
 * Controls and what they do, said right under them where the player decides (`M21.18`).
 *
 * Kept close to the controls, rather than spaced like the rest of the panel, so the words
 * read as theirs and a two-pane panel still has room for every control (`D073`).
 */
@Composable
internal fun ExplainedActions(
    explanation: String,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EXPLANATION_GAP)) {
        actions()
        Text(text = explanation, style = MaterialTheme.typography.bodySmall)
    }
}

private val EXPLANATION_GAP = 2.dp

/** The four pieces a pawn may become, offered as buttons. */
@Composable
internal fun PromotionPrompt(
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
