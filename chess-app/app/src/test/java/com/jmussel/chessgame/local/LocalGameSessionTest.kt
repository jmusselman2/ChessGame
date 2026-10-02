package com.jmussel.chessgame.local

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.local.db.LocalGameDatabase
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pass-and-play game and its record in the store, kept in step (`M21.2`), and which
 * unfinished game each entry resumes (`D090`).
 */
class LocalGameSessionTest {
    private val dispatcher = StandardTestDispatcher()
    private val driver = FailingDriver(inMemoryLocalGameDriver())
    private val store = LocalGameStore(LocalGameDatabase(driver))
    private val session = LocalGameSession(store, dispatcher)

    private fun ChessGame.play(move: String): ChessGame = ChessRules.applyMove(this, Move.of(move.substring(0, 2), move.substring(2, 4)))

    @Test
    fun savesRunInTheOrderTheyWereAskedFor() =
        runTest(dispatcher) {
            val stored = session.resume()
            val start = stored.game
            val one = start.play("e2e4")
            val two = one.play("e7e5")
            val undone = ChessRules.undoLastMove(two)

            // All three are asked for before any of them has run.
            session.save(stored.id, start, one) {}
            session.save(stored.id, one, two) {}
            session.save(stored.id, two, undone) {}
            advanceUntilIdle()

            assertEquals(undone, store.activeGame(LocalGameKind.PASS_AND_PLAY)!!.game)
        }

    @Test
    fun aChangeOnlyToTheScreenWritesNothing() =
        runTest(dispatcher) {
            val stored = session.resume()
            val start = stored.game
            // Any statement at all would now fail.
            driver.failOn = ""
            var failed = false

            session.save(stored.id, start, start) { failed = true }
            advanceUntilIdle()

            assertFalse(failed)
        }

    @Test
    fun aFailedSaveLeavesTheStoredGameAndSaysSo() =
        runTest(dispatcher) {
            val stored = session.resume()
            val start = stored.game
            val one = start.play("e2e4")
            session.save(stored.id, start, one) {}
            advanceUntilIdle()

            driver.failOn = "UPDATE local_games"
            var failed = false
            session.save(stored.id, one, one.play("e7e5")) { failed = true }
            advanceUntilIdle()

            assertTrue(failed)
            driver.failOn = null
            assertEquals(one, store.activeGame(LocalGameKind.PASS_AND_PLAY)!!.game)
            // Resuming shows the stored game, which is the canonical one.
            assertEquals(one, session.resume().game)
        }

    @Test
    fun resumingWaitsForTheSavesAskedForBeforeIt() =
        runTest(dispatcher) {
            val stored = session.resume()
            val start = stored.game
            val one = start.play("e2e4")

            session.save(stored.id, start, one) {}

            assertEquals(one, session.resume().game)
        }

    // --- One unfinished game of each kind (D090) -----------------------------------------

    @Test
    fun resumeReturnsPassAndPlayWhileAComputerGameIsUnfinished() =
        runTest(dispatcher) {
            val computer = store.startGame(ComputerOpponent(Side.WHITE, 2))

            val passAndPlay = session.resume()

            assertEquals(LocalGameKind.PASS_AND_PLAY, passAndPlay.kind)
            assertEquals(computer, store.activeGame(LocalGameKind.COMPUTER))
            // Resuming again finds the same game rather than starting another.
            assertEquals(passAndPlay.id, session.resume().id)
        }

    @Test
    fun resumeComputerIgnoresPassAndPlay() =
        runTest(dispatcher) {
            store.startGame()
            assertNull(session.resumeComputer())

            val computer = store.startGame(ComputerOpponent(Side.BLACK, 4))
            assertEquals(computer, session.resumeComputer())
        }
}
