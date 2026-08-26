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
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.ui.board.ChessBoard
import com.jmussel.chessgame.ui.theme.ChessGameTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChessGameTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GameScreen(
                        game = ChessGame.newGame(),
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}

/** The board and whose turn it is, both read straight from `game-core`. */
@Composable
fun GameScreen(
    game: ChessGame,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChessBoard(board = game.state.board)
        Text(text = "${game.sideToMove} to move")
    }
}

@Preview(showBackground = true)
@Composable
private fun GameScreenPreview() {
    ChessGameTheme {
        GameScreen(game = ChessGame.newGame())
    }
}
