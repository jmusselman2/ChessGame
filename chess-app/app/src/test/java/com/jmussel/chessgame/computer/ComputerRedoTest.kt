package com.jmussel.chessgame.computer

import com.jmussel.chessgame.ai.ChessEngine
import com.jmussel.chessgame.ai.Difficulty
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.GameState
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.local.ComputerOpponent
import com.jmussel.chessgame.local.LocalGameKind
import com.jmussel.chessgame.local.LocalGameSession
import com.jmussel.chessgame.local.inMemoryLocalGameStore
import com.jmussel.chessgame.ui.board.BoardInteraction
import com.jmussel.chessgame.ui.board.GameControls
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Redo against the computer (`D092`, `M21.21`): a takeback is restored exactly, a recorded
 * reply is never chosen again, and a human-only takeback hands the turn back to one search.
 * Repeating the human move by hand advances the retained continuation, so the recorded reply
 * comes back without a search too (`D102`).
 *
 * The same deterministic harness as [ComputerGameTest]: one test dispatcher, a scripted
 * engine that counts its calls, and a real local-game store.
 */
class ComputerRedoTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = inMemoryLocalGameStore()
    private val session = LocalGameSession(store, dispatcher)

    /** Plays [replies] in order, then the first legal move in coordinate order; counts its calls. */
    private class ScriptedEngine(
        vararg replies: String,
    ) : ChessEngine {
        private val script = ArrayDeque(replies.map { Move.of(it.substring(0, 2), it.substring(2, 4)) })
        var calls = 0

        /** Run inside the search, before it answers. */
        var whileThinking: () -> Unit = {}

        override fun chooseMove(
            state: GameState,
            difficulty: Difficulty,
            isCancelled: () -> Boolean,
        ): Move? {
            calls++
            whileThinking()
            return script.removeFirstOrNull() ?: ChessRules.legalMoves(state).minBy { it.toString() }
        }
    }

    private fun TestScope.started(engine: ChessEngine): ComputerGame =
        ComputerGame(session, engine, this, dispatcher, chooseSide = { Side.WHITE }).also {
            it.start(ComputerOpponent(Side.WHITE, 2))
            advanceUntilIdle()
        }

    private fun ComputerGame.tap(vararg squares: String) {
        squares.forEach { square -> update(BoardInteraction.onSquareTapped(state!!.boardState, Square.parse(square))) }
    }

    private val ComputerGame.moves: List<Move>
        get() = state!!.game.moves

    private fun stored() = store.activeGame(LocalGameKind.COMPUTER)!!.game

    @Test
    fun aRecordedReplyIsRestoredExactlyWithNoEngineCall() =
        runTest(dispatcher) {
            // Asked again, the engine would now answer c7c5: Redo must not ask it.
            val engine = ScriptedEngine("e7e5", "c7c5")
            val game = started(engine)
            game.tap("e2", "e4")
            advanceUntilIdle()
            val played = game.state!!.game
            assertEquals(1, engine.calls)

            game.takeBack()
            advanceUntilIdle()
            assertTrue(game.state!!.canRedo)

            game.redo()
            advanceUntilIdle()

            assertEquals(played, game.state!!.game)
            assertEquals(listOf(Move.of("e2", "e4"), Move.of("e7", "e5")), game.moves)
            assertEquals("no new search", 1, engine.calls)
            assertFalse(game.state!!.thinking)
            assertTrue(game.state!!.humansTurn)
            assertEquals(played, stored())
        }

    @Test
    fun aHumanOnlyTakebackRedoneStartsExactlyOneSearch() =
        runTest(dispatcher) {
            val engine = ScriptedEngine("e7e5")
            val game = started(engine)
            game.tap("e2", "e4")
            game.takeBack()
            advanceUntilIdle()
            assertEquals("the search was stopped before it ran", 0, engine.calls)
            assertEquals(
                listOf(Move.of("e2", "e4")),
                game.state!!
                    .game.redoPath
                    .single()
                    .moves,
            )

            game.redo()
            assertEquals(listOf(Move.of("e2", "e4")), game.moves)
            assertTrue("the computer's turn again", game.state!!.thinking)
            advanceUntilIdle()

            assertEquals(1, engine.calls)
            assertEquals(listOf(Move.of("e2", "e4"), Move.of("e7", "e5")), game.moves)
            assertEquals(game.state!!.game, stored())
        }

    @Test
    fun aSearchInvalidatedByTakebackAndRedoCannotLandLater() =
        runTest(dispatcher) {
            val engine = ScriptedEngine("e7e5", "c7c5")
            val game = started(engine)
            var interrupted = false
            // The first search is overtaken while it runs: the human takes back and redoes, which
            // leaves exactly the position it was asked about. Only the turn says it is stale.
            engine.whileThinking = {
                if (!interrupted) {
                    interrupted = true
                    game.takeBack()
                    game.redo()
                }
            }

            game.tap("e2", "e4")
            advanceUntilIdle()

            assertEquals("the stale search and the one Redo started", 2, engine.calls)
            assertEquals(listOf(Move.of("e2", "e4"), Move.of("c7", "c5")), game.moves)
            assertEquals(game.state!!.game, stored())
        }

    @Test
    fun undoUndoRedoRedoAndUndoUndoRedoUndoAgainstTheComputer() =
        runTest(dispatcher) {
            val engine = ScriptedEngine("e7e5", "b8c6")
            val game = started(engine)
            game.tap("e2", "e4")
            advanceUntilIdle()
            game.tap("g1", "f3")
            advanceUntilIdle()
            val full = game.state!!.game

            game.takeBack()
            game.takeBack()
            val twiceTakenBack = game.state!!.game
            assertEquals(2, twiceTakenBack.redoPath.size)

            game.redo()
            game.takeBack()
            assertEquals(twiceTakenBack, game.state!!.game)

            game.redo()
            game.redo()
            advanceUntilIdle()
            assertEquals(full, game.state!!.game)
            assertEquals(2, engine.calls)
            assertEquals(full, stored())
        }

    @Test
    fun theHumanMoveReplayedByHandAfterAOnePlyTakebackIsPlayed() =
        runTest(dispatcher) {
            val engine = ScriptedEngine("e7e5")
            val game = started(engine)
            game.tap("e2", "e4")
            game.takeBack()
            advanceUntilIdle()

            // The same move by hand gives the game a Redo would: it is still played.
            game.tap("e2", "e4")
            advanceUntilIdle()

            assertEquals(listOf(Move.of("e2", "e4"), Move.of("e7", "e5")), game.moves)
            assertEquals(1, engine.calls)
            assertEquals(game.state!!.game, stored())
        }

    @Test
    fun theHumanMoveRepeatedAfterATwoPlyTakebackRestoresTheRecordedReplyWithNoSearch() =
        runTest(dispatcher) {
            // Asked again, the engine would now answer c7c5: the retained reply must not ask it.
            val engine = ScriptedEngine("e7e5", "c7c5")
            val game = started(engine)
            game.tap("e2", "e4")
            advanceUntilIdle()
            val played = game.state!!.game
            game.takeBack()
            advanceUntilIdle()

            game.tap("e2", "e4")
            advanceUntilIdle()

            assertEquals(played, game.state!!.game)
            assertEquals("the recorded reply came back with no new search (D102)", 1, engine.calls)
            assertFalse(game.state!!.thinking)
            assertTrue(game.state!!.humansTurn)
            assertEquals(played, stored())
        }

    @Test
    fun aDifferentHumanMoveAfterATakebackClearsTheReplyAndSearches() =
        runTest(dispatcher) {
            val engine = ScriptedEngine("e7e5", "c7c5")
            val game = started(engine)
            game.tap("e2", "e4")
            advanceUntilIdle()
            game.takeBack()
            advanceUntilIdle()

            game.tap("d2", "d4")
            advanceUntilIdle()

            assertEquals(listOf(Move.of("d2", "d4"), Move.of("c7", "c5")), game.moves)
            assertEquals(2, engine.calls)
            assertTrue(
                game.state!!
                    .game.redoPath
                    .isEmpty(),
            )
        }

    @Test
    fun aSearchOvertakenByATakebackIsDiscarded() =
        runTest(dispatcher) {
            val engine = ScriptedEngine("e7e5", "c7c5")
            val game = started(engine)
            engine.whileThinking = { game.takeBack() }
            game.tap("e2", "e4")
            advanceUntilIdle()

            assertEquals("the takeback during the search won", emptyList<Move>(), game.moves)
            assertEquals(
                listOf(Move.of("e2", "e4")),
                game.state!!
                    .game.redoPath
                    .single()
                    .moves,
            )
            assertEquals(game.state!!.game, stored())
        }

    @Test
    fun redoIsNotOfferedWhileTheComputerThinksOrAfterTheGameEnds() =
        runTest(dispatcher) {
            val engine = ScriptedEngine("e7e5")
            val game = started(engine)
            assertFalse("nothing taken back yet", game.state!!.canRedo)

            game.tap("e2", "e4")
            assertFalse(game.state!!.canRedo)
            advanceUntilIdle()
            game.takeBack()
            assertTrue(game.state!!.canRedo)

            game.update(GameControls.resign(game.state!!.boardState, Side.WHITE))
            advanceUntilIdle()

            assertFalse(game.state!!.canRedo)
            assertTrue(
                game.state!!
                    .game.redoPath
                    .isEmpty(),
            )
            game.redo()
            assertTrue(game.state!!.game.isOver)
        }

    @Test
    fun thePathSurvivesLeavingAndAnotherAppProcess() =
        runTest(dispatcher) {
            val first = ScriptedEngine("e7e5")
            val game = started(first)
            game.tap("e2", "e4")
            advanceUntilIdle()
            val played = game.state!!.game
            game.takeBack()
            game.leave()
            advanceUntilIdle()

            // A new game object over the same store: the app after process death.
            val later = ScriptedEngine("c7c5")
            val reopened = ComputerGame(LocalGameSession(store, dispatcher), later, this, dispatcher)
            reopened.resume()
            advanceUntilIdle()
            assertTrue(reopened.state!!.canRedo)

            reopened.redo()
            advanceUntilIdle()

            assertEquals(played, reopened.state!!.game)
            assertEquals("the recorded reply, not a new one", 0, later.calls)
        }
}
