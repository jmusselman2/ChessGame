package com.jmussel.chessgame.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.Move
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The local-game store on a real device: SQLDelight's Android driver, the platform's own
 * SQLite, and the database file the backup rules exclude (`M21.1`).
 *
 * `LocalGameStoreTest` covers the store's behaviour on the JVM. This checks only what the
 * JVM cannot: that the schema, its partial unique index and the foreign keys work on the
 * device's SQLite, and that a reopened store finds the same game. It deletes the database
 * before and after, so it leaves no local games behind.
 */
@RunWith(AndroidJUnit4::class)
class LocalGameStoreDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    @After
    fun deleteDatabase() {
        context.deleteDatabase(LOCAL_GAME_DATABASE_NAME)
    }

    @Test
    fun aGameIsSavedRestoredTakenBackAndReplacedOnTheDevice() {
        val store = openLocalGameStore(context)
        val id = store.startGame().id
        var game = ChessGame.newGame()
        listOf(Move.of("e2", "e4"), Move.of("e7", "e5")).forEach { move ->
            game = ChessRules.applyMove(game, move)
            store.recordMove(id, game)
        }

        val reopened = openLocalGameStore(context)
        assertEquals(game, reopened.activeGame(LocalGameKind.PASS_AND_PLAY)!!.game)
        assertEquals(ChessRules.undoLastMove(game), reopened.takeBack(id))

        reopened.startGame()
        assertNull(reopened.game(id))
        assertEquals(ChessGame.newGame(), openLocalGameStore(context).activeGame(LocalGameKind.PASS_AND_PLAY)!!.game)
    }
}
