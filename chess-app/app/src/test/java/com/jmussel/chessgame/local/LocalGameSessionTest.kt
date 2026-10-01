package com.jmussel.chessgame.local

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.local.db.LocalGameDatabase
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pass-and-play game and its record in the store, kept in step (`M21.2`). */
class LocalGameSessionTest {
    private val dispatcher = StandardTestDispatcher()
    private val driver = FailingDriver(inMemoryLocalGameDriver())
    private val store = LocalGameStore(LocalGameDatabase(driver))
    private val session = LocalGameSession(store, dispatcher)

    private fun ChessGame.play(move: String): ChessGame = ChessRules.applyMove(this, Move.of(move.substring(0, 2), move.substring(2, 4)))

    @Test
    fun savesRunInTheOrderTheyWereAskedFor() =
        runTest(dispatcher) {
            val start = session.resume()
            val one = start.play("e2e4")
            val two = one.play("e7e5")
            val undone = ChessRules.undoLastMove(two)

            // All three are asked for before any of them has run.
            session.save(start, one) {}
            session.save(one, two) {}
            session.save(two, undone) {}
            advanceUntilIdle()

            assertEquals(undone, store.activeGame()!!.game)
        }

    @Test
    fun aChangeOnlyToTheScreenWritesNothing() =
        runTest(dispatcher) {
            val start = session.resume()
            // Any statement at all would now fail.
            driver.failOn = ""
            var failed = false

            session.save(start, start) { failed = true }
            advanceUntilIdle()

            assertFalse(failed)
        }

    @Test
    fun aFailedSaveLeavesTheStoredGameAndSaysSo() =
        runTest(dispatcher) {
            val start = session.resume()
            val one = start.play("e2e4")
            session.save(start, one) {}
            advanceUntilIdle()

            driver.failOn = "UPDATE local_games"
            var failed = false
            session.save(one, one.play("e7e5")) { failed = true }
            advanceUntilIdle()

            assertTrue(failed)
            driver.failOn = null
            assertEquals(one, store.activeGame()!!.game)
            // Resuming shows the stored game, which is the canonical one.
            assertEquals(one, session.resume())
        }

    @Test
    fun resumingWaitsForTheSavesAskedForBeforeIt() =
        runTest(dispatcher) {
            val start = session.resume()
            val one = start.play("e2e4")

            session.save(start, one) {}

            assertEquals(one, session.resume())
        }
}
