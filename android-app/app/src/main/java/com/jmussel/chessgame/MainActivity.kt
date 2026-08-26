package com.jmussel.chessgame

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.ui.board.BoardInteraction
import com.jmussel.chessgame.ui.board.BoardRendering
import com.jmussel.chessgame.ui.board.BoardUiState
import com.jmussel.chessgame.ui.board.ChessBoard
import com.jmussel.chessgame.ui.board.GameControls
import com.jmussel.chessgame.ui.theme.ChessGameTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChessGameTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GameScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

/**
 * The board and whose turn it is, both read straight from `game-core`.
 *
 * Tapping a square goes through [BoardInteraction], which owns what a tap means; this
 * composable only holds the resulting state.
 */
@Composable
fun GameScreen(
    modifier: Modifier = Modifier,
    initialState: BoardUiState = BoardUiState.newGame(),
) {
    var state by remember { mutableStateOf(initialState) }

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

        Text(text = statusFor(state.game))

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

        MoveList(lines = GameControls.moveListLines(state.game))
    }
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

private fun statusFor(game: ChessGame): String {
    val result = game.result ?: return "${game.sideToMove} to move"
    val winner = result.winner
    return if (winner == null) "Draw — ${result.reason}" else "$winner wins — ${result.reason}"
}

@Preview(showBackground = true)
@Composable
private fun GameScreenPreview() {
    ChessGameTheme {
        GameScreen()
    }
}
