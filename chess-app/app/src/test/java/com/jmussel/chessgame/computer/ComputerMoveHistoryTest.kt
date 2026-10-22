package com.jmussel.chessgame.computer

import com.jmussel.chessgame.ai.ChessEngine
import com.jmussel.chessgame.ai.Difficulty
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.GameState
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.local.ComputerOpponent
import com.jmussel.chessgame.local.LocalGameSession
import com.jmussel.chessgame.local.inMemoryLocalGameStore
import com.jmussel.chessgame.ui.board.BoardInteraction
import com.jmussel.chessgame.ui.board.GameControls
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The move list of a game against the computer (`D105`, `M23.2`), through the real
 * [ComputerGame]: the human's move, then the computer's reply, is current; a takeback and Redo
 * move the current move with them. Same harness as [ComputerRedoTest].
 */
class ComputerMoveHistoryTest {
    private val dispatcher = StandardTestDispatcher()
    private val session = LocalGameSession(inMemoryLocalGameStore(), dispatcher)

    private class ScriptedEngine(
        vararg replies: String,
    ) : ChessEngine {
        private val script = ArrayDeque(replies.map { Move.of(it.substring(0, 2), it.substring(2, 4)) })

        override fun chooseMove(
            state: GameState,
            difficulty: Difficulty,
            isCancelled: () -> Boolean,
        ): Move? = script.removeFirstOrNull() ?: ChessRules.legalMoves(state).minBy { it.toString() }
    }

    private fun TestScope.started(): ComputerGame =
        ComputerGame(session, ScriptedEngine("e7e5", "b8c6"), this, dispatcher, chooseSide = { Side.WHITE }).also {
            it.start(ComputerOpponent(Side.WHITE, 2))
            advanceUntilIdle()
        }

    private fun ComputerGame.tap(vararg squares: String) {
        squares.forEach { square -> update(BoardInteraction.onSquareTapped(state!!.boardState, Square.parse(square))) }
    }

    private val ComputerGame.current: String?
        get() = GameControls.moveHistory(state!!.game).current?.san

    @Test
    fun theHumansMoveThenTheComputersReplyIsCurrentAndATakebackAndRedoFollow() =
        runTest(dispatcher) {
            val game = started()
            assertNull(game.current)

            game.tap("e2", "e4")
            assertEquals("the human's move, while the computer thinks", "e4", game.current)
            advanceUntilIdle()
            assertEquals("the computer's reply", "e5", game.current)

            game.takeBack()
            advanceUntilIdle()
            assertNull("the move and its reply are taken back", game.current)

            game.redo()
            advanceUntilIdle()
            assertEquals("e5", game.current)
            assertEquals(listOf("1. e4 e5"), GameControls.moveListLines(game.state!!.game))
        }
}
