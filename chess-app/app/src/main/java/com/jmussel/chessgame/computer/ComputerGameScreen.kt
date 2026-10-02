package com.jmussel.chessgame.computer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jmussel.chessgame.ai.Difficulty
import com.jmussel.chessgame.ui.board.BoardInteraction
import com.jmussel.chessgame.ui.board.BoardRendering
import com.jmussel.chessgame.ui.board.BoardUiState
import com.jmussel.chessgame.ui.board.ChessBoard
import com.jmussel.chessgame.ui.board.DeclaredMovePrompt
import com.jmussel.chessgame.ui.board.GameBackButton
import com.jmussel.chessgame.ui.board.GameControls
import com.jmussel.chessgame.ui.board.GameLayout
import com.jmussel.chessgame.ui.board.NewGameConfirmation
import com.jmussel.chessgame.ui.board.PromotionPrompt
import com.jmussel.chessgame.ui.board.ReplaceUnfinishedGame
import com.jmussel.chessgame.ui.board.ResignConfirmation

/** What the computer game's screen can ask of [ComputerGame]. */
data class ComputerGameActions(
    val onUpdate: (BoardUiState) -> Unit = {},
    val onTakeBack: () -> Unit = {},
    val onConfirmReplacing: () -> Unit = {},
    val onChoose: (Difficulty) -> Unit = {},
    val onPlayAgain: () -> Unit = {},
    val onNewGame: () -> Unit = {},
    /** Back from New game's question or level choice to the game. */
    val onKeepGame: () -> Unit = {},
)

/**
 * Playing the computer (`D086`, `M21.7`): the question before a new game, then the game.
 * New game (`M21.10`) asks over the game when it is unfinished, then shows the level choice in
 * its place, with Back returning to it.
 *
 * The board is the local game's, fixed to the human's colour with every piece upright: the
 * computer has no seat across the table (`D087`). It takes taps only on the human's turn.
 */
@Composable
fun ComputerGameScreen(
    state: ComputerGameUiState?,
    setup: ComputerSetup?,
    loading: Boolean,
    actions: ComputerGameActions,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    if (state == null || loading || setup == ComputerSetup.ChooseDifficulty) {
        // From New game, the game is behind the choice and Back returns to it.
        val fromGame = state != null && !loading
        Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            (if (fromGame) actions.onKeepGame else onBack)?.let { GameBackButton(onClick = it) }
            when {
                loading || setup == null -> Text(text = "Loading…", style = MaterialTheme.typography.bodyMedium)
                setup == ComputerSetup.ConfirmReplacing ->
                    ReplaceUnfinishedGame(
                        question = "Your pass-and-play game is not finished. A game against the computer deletes it, and it is not kept.",
                        confirm = "Delete it and play the computer",
                        onConfirm = actions.onConfirmReplacing,
                        onKeep = onBack,
                    )
                else -> DifficultyChoice(onChoose = actions.onChoose, onKeepGame = actions.onKeepGame.takeIf { fromGame })
            }
        }
        return
    }

    Game(state = state, actions = actions, modifier = modifier, onBack = onBack)

    if (setup == ComputerSetup.ConfirmNewGame) NewGameConfirmation(onConfirm = actions.onConfirmReplacing, onCancel = actions.onKeepGame)
}

/** The three levels, easiest first (`D088`); [onKeepGame] goes back to the game New game left. */
@Composable
private fun DifficultyChoice(
    onChoose: (Difficulty) -> Unit,
    onKeepGame: (() -> Unit)?,
) {
    Text(text = if (onKeepGame == null) "Play the computer" else "New game", style = MaterialTheme.typography.titleSmall)
    Text(text = "Choose a level. You get a random colour.", style = MaterialTheme.typography.bodyMedium)
    Difficulty.entries.forEach { difficulty ->
        Button(onClick = { onChoose(difficulty) }) { Text(text = difficultyName(difficulty.level)) }
    }
    onKeepGame?.let { TextButton(onClick = it) { Text(text = "Back to the game") } }
}

@Composable
private fun Game(
    state: ComputerGameUiState,
    actions: ComputerGameActions,
    modifier: Modifier,
    onBack: (() -> Unit)?,
) {
    val board = state.boardState
    var resigning by rememberSaveable(state.id) { mutableStateOf(false) }

    GameLayout(
        onBack = onBack,
        modifier = modifier,
        board = { side ->
            ChessBoard(
                board = board.board,
                side = side,
                selectedSquare = board.selectedSquare.takeIf { state.humansTurn },
                legalDestinations = if (state.humansTurn) BoardInteraction.legalDestinations(board) else emptySet(),
                lastMove = BoardRendering.lastMoveSquares(board.game),
                orientation = state.humanSide,
                onSquareClick = { square -> if (state.humansTurn) actions.onUpdate(BoardInteraction.onSquareTapped(board, square)) },
            )
        },
        controls = {
            Text(text = computerLabel(state.opponent.difficulty) + " • " + humanSideLabel(state.humanSide))
            Text(text = GameControls.statusFor(board.game))
            if (state.thinking) Text(text = "The computer is thinking…", style = MaterialTheme.typography.bodySmall)

            if (state.humansTurn) {
                board.pendingPromotion?.let { pending ->
                    PromotionPrompt(choices = pending.choices, onChoose = { actions.onUpdate(BoardInteraction.choosePromotion(board, it)) })
                }
                board.declaredMove?.let { declared ->
                    DeclaredMovePrompt(
                        declared = declared,
                        onClaim = { claim -> actions.onUpdate(GameControls.claimDeclaredDraw(board, claim)) },
                        onPlay = { actions.onUpdate(BoardInteraction.playDeclaredMove(board)) },
                        onCancel = { actions.onUpdate(BoardInteraction.cancelDeclaredMove(board)) },
                    )
                }
                GameControls.availableDrawClaims(board).forEach { claim ->
                    Button(
                        onClick = { actions.onUpdate(GameControls.claimDraw(board, claim)) },
                    ) { Text(text = GameControls.labelFor(claim)) }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.canTakeBack) Button(onClick = actions.onTakeBack) { Text(text = "Undo") }
                if (!board.game.isOver) Button(onClick = { resigning = true }) { Text(text = "Resign") }
            }

            // New game is always offered; an unfinished game is asked about first (`D089`).
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (board.game.isOver) Button(onClick = actions.onPlayAgain) { Text(text = "Play again") }
                TextButton(onClick = actions.onNewGame) { Text(text = "New game") }
                if (board.game.isOver) onBack?.let { TextButton(onClick = it) { Text(text = "Leave") } }
            }
        },
        moveList = {
            GameControls.moveListLines(board.game).forEach { line -> Text(text = line) }
        },
    )

    if (resigning && !board.game.isOver) {
        ResignConfirmation(
            side = state.humanSide,
            onConfirm = {
                resigning = false
                actions.onUpdate(GameControls.resign(board, state.humanSide))
            },
            onCancel = { resigning = false },
        )
    }
}
