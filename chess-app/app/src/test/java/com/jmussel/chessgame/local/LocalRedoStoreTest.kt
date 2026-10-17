package com.jmussel.chessgame.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.DrawClaim
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.local.db.LocalGameDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.sql.DriverManager
import java.util.Properties

/**
 * Redo in the local-game store (`D092`, `M21.21`): a takeback keeps what it removed as one
 * operation, Redo restores it exactly, the path survives closing and reopening the database,
 * and every write that touches it is one transaction.
 *
 * A real SQLite file through SQLDelight's JDBC driver, as in [LocalGameStoreTest]; a second
 * [openStore] is the app after process death or a restart.
 */
class LocalRedoStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val drivers = mutableListOf<SqlDriver>()
    private var clock = 1_000L

    private val file by lazy { folder.root.resolve(LOCAL_GAME_DATABASE_NAME) }

    @After
    fun closeDrivers() {
        drivers.forEach { it.close() }
    }

    private fun openDriver(): SqlDriver =
        JdbcSqliteDriver(
            url = "jdbc:sqlite:${file.path}",
            properties = Properties().apply { put("foreign_keys", "true") },
            schema = LocalGameDatabase.Schema,
        ).also { drivers += it }

    private fun openStore(driver: SqlDriver = openDriver()): LocalGameStore = LocalGameStore(LocalGameDatabase(driver), now = { clock++ })

    private fun ChessGame.play(vararg moves: String): ChessGame =
        moves.fold(this) { game, move ->
            ChessRules.applyMove(game, Move.of(move.substring(0, 2), move.substring(2, 4), move.getOrNull(4)?.let(PieceType::fromLetter)))
        }

    private fun LocalGameStore.playAndRecord(
        id: Long,
        start: ChessGame,
        vararg moves: String,
    ): ChessGame = moves.fold(start) { game, move -> game.play(move).also { recordMove(id, it) } }

    private fun reopened(kind: LocalGameKind = LocalGameKind.PASS_AND_PLAY): ChessGame = openStore().activeGame(kind)!!.game

    @Test
    fun aTakebackKeepsWhatItRemovedAndRedoRestoresItExactlyAfterReopening() {
        val store = openStore()
        val id = store.startGame().id
        val played = store.playAndRecord(id, ChessGame.newGame(), "e2e4", "e7e5", "g1f3")

        val undone = store.takeBack(id)
        assertEquals(ChessRules.undoLastMove(played), undone)
        assertEquals("the path is stored, not held in memory", undone, reopened())

        assertEquals(played, openStore().redo(id))
        assertEquals(played, reopened())
    }

    @Test
    fun undoUndoRedoRedoAndUndoUndoRedoUndoSurviveEveryReopen() {
        val id = openStore().let { store -> store.startGame().id.also { store.playAndRecord(it, ChessGame.newGame(), "e2e4", "e7e5") } }
        val full = reopened()

        openStore().takeBack(id)
        openStore().takeBack(id)
        val twiceUndone = reopened()
        assertEquals(2, twiceUndone.redoPath.size)

        openStore().redo(id)
        openStore().takeBack(id)
        assertEquals("Undo, Undo, Redo, Undo keeps the whole path", twiceUndone, reopened())

        openStore().redo(id)
        openStore().redo(id)
        assertEquals("Undo, Undo, Redo, Redo restores both in order", full, reopened())
        assertThrows(IllegalArgumentException::class.java) { openStore().redo(id) }
    }

    @Test
    fun aComputerTakebackIsOneTwoPlyOperationRestoredWhole() {
        val store = openStore()
        val id = store.startGame(ComputerOpponent(Side.WHITE, 2)).id
        val played = store.playAndRecord(id, ChessGame.newGame(), "e2e4", "e7e5", "g1f3", "b8c6")

        val takenBack = store.takeBack(id, plies = 2)
        assertEquals(ChessRules.undoLastMoves(played, 2), takenBack)
        assertEquals(listOf(Move.of("g1", "f3"), Move.of("b8", "c6")), reopened(LocalGameKind.COMPUTER).redoPath.single().moves)

        assertEquals(played, openStore().redo(id))
        assertEquals(played, reopened(LocalGameKind.COMPUTER))
    }

    @Test
    fun theNextRetainedMoveByHandKeepsTheRestOfThePathAcrossReopening() {
        // `D102`, superseding `D092`'s "the same coordinates still clear the path".
        val store = openStore()
        val id = store.startGame().id
        val played = store.playAndRecord(id, ChessGame.newGame(), "e2e4", "e7e5")
        store.takeBack(id)
        val undone = store.takeBack(id)

        store.recordMove(id, ChessRules.applyMove(undone, Move.of("e2", "e4")))

        val partly = reopened()
        assertEquals(played.history.take(1), partly.history)
        assertEquals("Black's move is still ahead", listOf(Move.of("e7", "e5")), partly.redoPath.single().moves)
        assertEquals(Side.BLACK, ChessRules.redoableSide(partly))
        assertEquals(played, openStore().redo(id))
        assertEquals(played, reopened())
    }

    @Test
    fun bothSidesAdvanceTheirRetainedMovesByHand() {
        val store = openStore()
        val id = store.startGame().id
        val played = store.playAndRecord(id, ChessGame.newGame(), "e2e4", "e7e5", "g1f3")
        repeat(3) { store.takeBack(id) }

        store.playAndRecord(id, reopened(), "e2e4", "e7e5")

        val partly = reopened()
        assertEquals("White's Nf3 is still retained for White", Side.WHITE, ChessRules.redoableSide(partly))
        store.playAndRecord(id, partly, "g1f3")
        assertEquals(played, reopened())
    }

    @Test
    fun aDifferentMoveClearsThePath() {
        val store = openStore()
        val id = store.startGame().id
        store.playAndRecord(id, ChessGame.newGame(), "e2e4", "e7e5")
        store.takeBack(id)
        val undone = store.takeBack(id)

        store.recordMove(id, ChessRules.applyMove(undone, Move.of("d2", "d4")))

        assertTrue(reopened().redoPath.isEmpty())
        assertThrows(IllegalArgumentException::class.java) { openStore().redo(id) }
    }

    @Test
    fun aComputerTakebacksFirstMoveByHandLeavesTheReplyRetained() {
        val store = openStore()
        val id = store.startGame(ComputerOpponent(Side.WHITE, 2)).id
        val played = store.playAndRecord(id, ChessGame.newGame(), "e2e4", "e7e5")
        val takenBack = store.takeBack(id, plies = 2)

        store.recordMove(id, ChessRules.applyMove(takenBack, Move.of("e2", "e4")))

        val partly = reopened(LocalGameKind.COMPUTER)
        assertEquals(listOf(Move.of("e7", "e5")), partly.redoPath.single().moves)
        assertEquals(Move.of("e7", "e5"), ChessRules.retainedMove(partly))
        store.recordMove(id, ChessRules.applyMove(partly, Move.of("e7", "e5")))
        assertEquals(played, reopened(LocalGameKind.COMPUTER))
    }

    @Test
    fun aMoveThatKeepsAPathTheRulesWouldClearIsRefused() {
        val store = openStore()
        val id = store.startGame().id
        store.playAndRecord(id, ChessGame.newGame(), "e2e4", "e7e5")
        val undone = store.takeBack(id)
        // A game that played on while keeping the path would not be what the rules produce.
        val inconsistent = ChessRules.applyMove(undone, Move.of("d7", "d5")).copy(redoPath = undone.redoPath)

        assertThrows(IllegalArgumentException::class.java) { store.recordMove(id, inconsistent) }
        assertEquals(undone, reopened())
    }

    @Test
    fun resignationAndADrawClaimClearThePathAndTheFinishedGameHasNone() {
        val store = openStore()
        val resignedId = store.startGame().id
        store.playAndRecord(resignedId, ChessGame.newGame(), "e2e4")
        val withPath = store.takeBack(resignedId)
        assertEquals(1, withPath.redoPath.size)

        store.recordResult(resignedId, ChessRules.resign(withPath, Side.WHITE))

        val finished = openStore().game(resignedId)!!
        assertTrue(finished.game.redoPath.isEmpty())
        assertEquals(null, ChessRules.redoableSide(finished.game))
        assertThrows(IllegalArgumentException::class.java) { openStore().redo(resignedId) }

        val drawnId = openStore().startGame().id
        val knights =
            openStore().playAndRecord(
                drawnId,
                ChessGame.newGame(),
                "g1f3",
                "g8f6",
                "f3g1",
                "f6g8",
                "g1f3",
                "g8f6",
                "f3g1",
                "f6g8",
                "e2e4",
            )
        val back = openStore().takeBack(drawnId)
        assertEquals(knights.history.dropLast(1), back.history)
        openStore().recordResult(drawnId, ChessRules.claimDraw(back, DrawClaim.THREEFOLD_REPETITION))
        assertTrue(
            openStore()
                .game(drawnId)!!
                .game.redoPath
                .isEmpty(),
        )
    }

    @Test
    fun aRedosAppendAndStateAreOneTransaction() {
        val driver = FailingDriver(openDriver())
        val store = openStore(driver)
        val id = store.startGame(ComputerOpponent(Side.WHITE, 1)).id
        store.playAndRecord(id, ChessGame.newGame(), "e2e4", "e7e5")
        val takenBack = store.takeBack(id, plies = 2)

        // The history rows are appended first and the state and path written second; failing
        // the second must take the rows with it.
        driver.failOn = "UPDATE local_games"
        assertThrows(SimulatedFailure::class.java) { store.redo(id) }
        assertEquals(takenBack, reopened(LocalGameKind.COMPUTER))

        driver.failOn = null
        store.redo(id)
        assertEquals(2, reopened(LocalGameKind.COMPUTER).history.size)
    }

    @Test
    fun aTakebacksTruncationStateAndPathAreOneTransaction() {
        val driver = FailingDriver(openDriver())
        val store = openStore(driver)
        val id = store.startGame().id
        val played = store.playAndRecord(id, ChessGame.newGame(), "e2e4", "e7e5")

        driver.failOn = "UPDATE local_games"
        assertThrows(SimulatedFailure::class.java) { store.takeBack(id) }

        val restored = reopened()
        assertEquals(played, restored)
        assertTrue("a failed takeback keeps no operation", restored.redoPath.isEmpty())
    }

    @Test
    fun startingANewGameReplacesOnlyItsOwnKindsPath() {
        val store = openStore()
        val passId = store.startGame().id
        store.playAndRecord(passId, ChessGame.newGame(), "e2e4")
        store.takeBack(passId)
        val computerId = store.startGame(ComputerOpponent(Side.WHITE, 1)).id
        store.playAndRecord(computerId, ChessGame.newGame(), "d2d4", "d7d5")
        store.takeBack(computerId, plies = 2)

        openStore().startGame(ComputerOpponent(Side.BLACK, 3))

        assertEquals("pass-and-play keeps its path (D090)", 1, reopened(LocalGameKind.PASS_AND_PLAY).redoPath.size)
        assertTrue(reopened(LocalGameKind.COMPUTER).redoPath.isEmpty())
        assertEquals(null, openStore().game(computerId))
    }

    @Test
    fun aVersionThreeDatabaseMigratesWithNoPathAndThenKeepsOne() {
        File(SNAPSHOT_V3).copyTo(file)
        DriverManager.getConnection("jdbc:sqlite:${file.path}").use { connection ->
            connection.createStatement().use { it.execute("PRAGMA user_version = 3") }
            connection
                .prepareStatement(
                    "INSERT INTO local_games (kind, status, created_at, state) VALUES ('PASS_AND_PLAY', 'ACTIVE', 1, ?)",
                ).use { insert ->
                    insert.setString(1, LocalStateDocument.encode(ChessGame.newGame().play("e2e4").state))
                    insert.executeUpdate()
                }
            connection
                .prepareStatement("INSERT INTO local_moves (game_id, ply, move, position_before) VALUES (1, 1, 'e2e4', ?)")
                .use { insert ->
                    insert.setString(1, LocalStateDocument.encode(ChessGame.newGame().state))
                    insert.executeUpdate()
                }
        }

        val store = openStore()
        val migrated = store.activeGame(LocalGameKind.PASS_AND_PLAY)!!

        assertEquals(ChessGame.newGame().play("e2e4"), migrated.game)
        assertTrue("an existing game starts with no Redo path", migrated.game.redoPath.isEmpty())
        store.takeBack(migrated.id)
        assertEquals(ChessGame.newGame().play("e2e4"), openStore().redo(migrated.id))
    }

    private companion object {
        /** The released version-3 schema, before Redo. */
        const val SNAPSHOT_V3 = "src/main/sqldelight/databases/3.db"
    }
}
