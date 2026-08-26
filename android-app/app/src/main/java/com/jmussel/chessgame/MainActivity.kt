package com.jmussel.chessgame

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.jmussel.chessgame.ui.board.BoardInteraction
import com.jmussel.chessgame.ui.board.BoardUiState
import com.jmussel.chessgame.ui.board.ChessBoard
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
            onSquareClick = { square -> state = BoardInteraction.onSquareTapped(state, square) },
        )
        Text(text = "${state.game.sideToMove} to move")
    }
}

@Preview(showBackground = true)
@Composable
private fun GameScreenPreview() {
    ChessGameTheme {
        GameScreen()
    }
}
