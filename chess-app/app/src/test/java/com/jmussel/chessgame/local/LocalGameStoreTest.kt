package com.jmussel.chessgame.local

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.DrawClaim
import com.jmussel.chessgame.core.chess.GameResult
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Piece
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.core.chess.TerminationReason
import com.jmussel.chessgame.local.db.LocalGameDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Properties

/**
 * The local-game store (`M21.1`, `D084`): what it saves, what it restores, and that every
 * write is one transaction that appends or truncates.
 *
 * The database is a real SQLite file through SQLDelight's JDBC driver, so the generated
 * schema, its constraints and the queries all run as they do on a device. Reopening the
 * file with a new driver and a new store is what process death looks like to the store.
 */
class LocalGameStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val drivers = mutableListOf<SqlDriver>()
    private var clock = 1_000L

    private val databaseUrl by lazy { "jdbc:sqlite:${folder.root.resolve(LOCAL_GAME_DATABASE_NAME).path}" }

    @After
    fun closeDrivers() {
        drivers.forEach { it.close() }
    }

    /** A driver on the test's database file, creating the schema the first time. */
    private fun openDriver(): SqlDriver =
        JdbcSqliteDriver(
            url = databaseUrl,
            properties = Properties().apply { put("foreign_keys", "true") },
            schema = LocalGameDatabase.Schema,
        ).also { drivers += it }

    /** A store on the test's database file; a second call is the app after process death. */
    private fun openStore(driver: SqlDriver = openDriver()): LocalGameStore = LocalGameStore(LocalGameDatabase(driver), now = { clock++ })

    private fun ChessGame.play(vararg moves: String): ChessGame =
        moves.fold(this) { game, move ->
            ChessRules.applyMove(game, Move.of(move.substring(0, 2), move.substring(2, 4), move.getOrNull(4)?.let(PieceType::fromLetter)))
        }

    /** Plays [moves] one at a time, saving each, and returns the game as played in memory. */
    private fun LocalGameStore.playAndRecord(
        id: Long,
        start: ChessGame,
        vararg moves: String,
    ): ChessGame =
        moves.fold(start) { game, move ->
            game.play(move).also { recordMove(id, it) }
        }

    // --- Saving and loading -----------------------------------------------------------

    @Test
    fun aNewGameIsSavedAndLoaded() {
        val store = openStore()

        val started = store.startGame()

        assertEquals(started, store.activeGame(LocalGameKind.PASS_AND_PLAY))
        assertEquals(ChessGame.newGame(), store.activeGame(LocalGameKind.PASS_AND_PLAY)!!.game)
        assertEquals(LocalGameKind.PASS_AND_PLAY, started.kind)
        assertTrue(started.isActive)
        assertNull(started.computer)
    }

    @Test
    fun aGameAfterOrdinaryMovesIsRestoredExactly() {
        val store = openStore()
        val id = store.startGame().id

        val played = store.playAndRecord(id, ChessGame.newGame(), "e2e4", "e7e5", "g1f3", "b8c6", "f1b5")

        val restored = store.activeGame(LocalGameKind.PASS_AND_PLAY)!!.game
        assertEquals(played, restored)
        assertEquals(played.history, restored.history)
        assertEquals(5, restored.history.size)
    }

    @Test
    fun castlingRoundTrips() {
        val store = openStore()
        val id = store.startGame().id

        val played = store.playAndRecord(id, ChessGame.newGame(), "e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "f8c5", "e1g1")

        val restored = openStore().activeGame(LocalGameKind.PASS_AND_PLAY)!!.game
        assertEquals(played, restored)
        assertEquals(Piece(Side.WHITE, PieceType.KING), restored.state.board.pieceAt(Square.parse("g1")))
        assertEquals(Piece(Side.WHITE, PieceType.ROOK), restored.state.board.pieceAt(Square.parse("f1")))
        assertFalse(restored.state.castlingRights.hasAny(Side.WHITE))
        assertTrue(restored.state.castlingRights.hasAny(Side.BLACK))
    }

    @Test
    fun enPassantRoundTrips() {
        val store = openStore()
        val id = store.startGame().id

        // The en passant target is part of the stored position before the capture...
        val beforeCapture = store.playAndRecord(id, ChessGame.newGame(), "e2e4", "a7a6", "e4e5", "d7d5")
        val restoredBeforeCapture = openStore().activeGame(LocalGameKind.PASS_AND_PLAY)!!.game
        assertEquals(Square.parse("d6"), restoredBeforeCapture.state.enPassantTarget)

        // ...so the capture is legal in the restored game, and the capture itself round trips.
        assertTrue(ChessRules.isLegal(restoredBeforeCapture, Move.of("e5", "d6")))
        val played = store.playAndRecord(id, beforeCapture, "e5d6")

        val restored = openStore().activeGame(LocalGameKind.PASS_AND_PLAY)!!.game
        assertEquals(played, restored)
        assertNull(restored.state.board.pieceAt(Square.parse("d5")))
        assertEquals(Move.of("e5", "d6"), restored.lastMove)
    }

    @Test
    fun promotionRoundTrips() {
        val store = openStore()
        val id = store.startGame().id

        val played =
            store.playAndRecord(id, ChessGame.newGame(), "a2a4", "b7b5", "a4b5", "a7a6", "b5a6", "c8b7", "a6b7", "b8c6", "b7a8q")

        val restored = openStore().activeGame(LocalGameKind.PASS_AND_PLAY)!!.game
        assertEquals(played, restored)
        assertEquals(Move.of("b7", "a8", PieceType.QUEEN), restored.lastMove)
        assertEquals(Piece(Side.WHITE, PieceType.QUEEN), restored.state.board.pieceAt(Square.parse("a8")))
    }

    @Test
    fun repetitionCountsAndDrawClaimsSurviveRestoring() {
        val store = openStore()
        val id = store.startGame().id
        val shuffle = arrayOf("g1f3", "g8f6", "f3g1", "f6g8")

        val played = store.playAndRecord(id, ChessGame.newGame(), *shuffle, *shuffle)
        assertEquals(setOf(DrawClaim.THREEFOLD_REPETITION), ChessRules.availableDrawClaims(played))

        val restored = openStore().activeGame(LocalGameKind.PASS_AND_PLAY)!!.game
        assertEquals(played.state.drawRuleState, restored.state.drawRuleState)
        assertEquals(setOf(DrawClaim.THREEFOLD_REPETITION), ChessRules.availableDrawClaims(restored))
        // Each recorded prior position keeps its own counts too, so an undo restores them.
        assertEquals(played.history.map { it.positionBefore }, restored.history.map { it.positionBefore })
        val undone = ChessRules.undoLastMove(restored)
        assertEquals(emptySet<DrawClaim>(), ChessRules.availableDrawClaims(undone))
    }

    @Test
    fun aComputerGameKeepsTheHumansColourAndTheDifficulty() {
        val store = openStore()

        val started = store.startGame(ComputerOpponent(humanSide = Side.BLACK, difficulty = 2))

        val restored = openStore().activeGame(LocalGameKind.COMPUTER)!!
        assertEquals(LocalGameKind.COMPUTER, restored.kind)
        assertEquals(ComputerOpponent(Side.BLACK, 2), restored.computer)
        assertEquals(started, restored)
    }

    // --- Undo and takeback ------------------------------------------------------------

    @Test
    fun undoAfterReloadingRestoresTheRecordedPriorPosition() {
        val id = openStore().let { store -> store.startGame().id.also { store.playAndRecord(it, ChessGame.newGame(), "e2e4", "e7e5") } }

        val reloaded = openStore()
        val before = reloaded.activeGame(LocalGameKind.PASS_AND_PLAY)!!.game
        val undone = reloaded.takeBack(id)

        assertEquals(ChessRules.undoLastMove(before), undone)
        assertEquals(undone, openStore().activeGame(LocalGameKind.PASS_AND_PLAY)!!.game)
        assertEquals(listOf(Move.of("e2", "e4")), openStore().activeGame(LocalGameKind.PASS_AND_PLAY)!!.game.moves)
    }

    @Test
    fun aTwoPlyTakebackRestoresThePositionBeforeTheEarlierMove() {
        val store = openStore()
        val id = store.startGame(ComputerOpponent(Side.WHITE, 1)).id
        val played = store.playAndRecord(id, ChessGame.newGame(), "e2e4", "e7e5", "g1f3", "b8c6")

        val takenBack = store.takeBack(id, plies = 2)

        assertEquals(ChessRules.undoLastMove(ChessRules.undoLastMove(played)), takenBack)
        assertEquals(takenBack, openStore().activeGame(LocalGameKind.COMPUTER)!!.game)
        assertEquals(Side.WHITE, takenBack.sideToMove)
    }

    @Test
    fun takebackCanBeRepeatedToTheFirstMoveAndNoFurther() {
        val store = openStore()
        val id = store.startGame().id
        store.playAndRecord(id, ChessGame.newGame(), "e2e4", "e7e5")

        store.takeBack(id)
        assertEquals(ChessGame.newGame(), store.takeBack(id))
        assertThrows(IllegalArgumentException::class.java) { store.takeBack(id) }
        assertEquals(ChessGame.newGame(), store.activeGame(LocalGameKind.PASS_AND_PLAY)!!.game)
    }

    @Test
    fun aMoveIsTheStoredGamePlusOneMoveOrIsRefused() {
        val store = openStore()
        val id = store.startGame().id
        val oneMove = store.playAndRecord(id, ChessGame.newGame(), "e2e4")

        // Two moves at once would skip a record.
        assertThrows(IllegalArgumentException::class.java) { store.recordMove(id, oneMove.play("e7e5", "g1f3")) }
        // A move from some other position would leave the history disagreeing with the board.
        assertThrows(IllegalArgumentException::class.java) { store.recordMove(id, ChessGame.newGame().play("d2d4", "d7d5")) }

        assertEquals(oneMove, store.activeGame(LocalGameKind.PASS_AND_PLAY)!!.game)
    }

    // --- Atomicity --------------------------------------------------------------------

    @Test
    fun aMoveAndItsHistoryAppendAreOneTransaction() {
        val driver = FailingDriver(openDriver())
        val store = openStore(driver)
        val id = store.startGame().id
        val played = store.playAndRecord(id, ChessGame.newGame(), "e2e4")

        // The history row is written first and the state second; failing the state write
        // must take the appended row with it.
        driver.failOn = "UPDATE local_games"
        assertThrows(SimulatedFailure::class.java) { store.recordMove(id, played.play("e7e5")) }

        val restored = openStore().activeGame(LocalGameKind.PASS_AND_PLAY)!!.game
        assertEquals(played, restored)
        assertEquals(1, restored.history.size)

        // And with the failure gone, the same move saves normally.
        driver.failOn = null
        store.recordMove(id, played.play("e7e5"))
        assertEquals(played.play("e7e5"), openStore().activeGame(LocalGameKind.PASS_AND_PLAY)!!.game)
    }

    @Test
    fun anUndosStateRestoreAndHistoryTruncationAreOneTransaction() {
        val driver = FailingDriver(openDriver())
        val store = openStore(driver)
        val id = store.startGame().id
        val played = store.playAndRecord(id, ChessGame.newGame(), "e2e4", "e7e5")

        // The truncation runs first and the state restore second.
        driver.failOn = "UPDATE local_games"
        assertThrows(SimulatedFailure::class.java) { store.takeBack(id) }

        assertEquals(played, openStore().activeGame(LocalGameKind.PASS_AND_PLAY)!!.game)
    }

    @Test
    fun replacingTheUnfinishedGameIsOneTransaction() {
        val driver = FailingDriver(openDriver())
        val store = openStore(driver)
        val first = store.startGame()
        val played = store.playAndRecord(first.id, ChessGame.newGame(), "e2e4")

        driver.failOn = "INSERT INTO local_games"
        assertThrows(SimulatedFailure::class.java) { store.startGame() }

        val restored = openStore().activeGame(LocalGameKind.PASS_AND_PLAY)!!
        assertEquals(first.id, restored.id)
        assertEquals(played, restored.game)
    }

    // --- One unfinished game of each kind (D090) -----------------------------------------

    @Test
    fun oneUnfinishedGameOfEachKindStandSideBySide() {
        val store = openStore()
        val passAndPlay = store.startGame()
        val computer = store.startGame(ComputerOpponent(Side.WHITE, 3))

        assertEquals(passAndPlay, store.activeGame(LocalGameKind.PASS_AND_PLAY))
        assertEquals(computer, store.activeGame(LocalGameKind.COMPUTER))
        assertEquals(2L, countRows("SELECT count(*) FROM local_games WHERE status = 'ACTIVE'"))
    }

    @Test
    fun startingAComputerGameLeavesPassAndPlayAndItsMovesUntouched() {
        val store = openStore()
        val passAndPlay = store.startGame()
        val played = store.playAndRecord(passAndPlay.id, ChessGame.newGame(), "e2e4", "e7e5")

        store.startGame(ComputerOpponent(Side.BLACK, 1))
        store.startGame(ComputerOpponent(Side.WHITE, 2))

        val kept = openStore().activeGame(LocalGameKind.PASS_AND_PLAY)!!
        assertEquals(passAndPlay.id, kept.id)
        assertEquals(played, kept.game)
        assertEquals(2L, countRows("SELECT count(*) FROM local_moves WHERE game_id = ${passAndPlay.id}"))
    }

    @Test
    fun startingPassAndPlayLeavesTheComputerGameAndItsMovesUntouched() {
        val store = openStore()
        val computer = store.startGame(ComputerOpponent(Side.WHITE, 4))
        val played = store.playAndRecord(computer.id, ChessGame.newGame(), "d2d4", "d7d5")

        store.startGame()
        store.startGame()

        val kept = openStore().activeGame(LocalGameKind.COMPUTER)!!
        assertEquals(computer.id, kept.id)
        assertEquals(played, kept.game)
        assertEquals(2L, countRows("SELECT count(*) FROM local_moves WHERE game_id = ${computer.id}"))
    }

    @Test
    fun startingAGameReplacesOnlyItsOwnKindsUnfinishedGameWithItsMoves() {
        val store = openStore()
        val oldComputer = store.startGame(ComputerOpponent(Side.WHITE, 1))
        store.playAndRecord(oldComputer.id, ChessGame.newGame(), "e2e4")
        val passAndPlay = store.startGame()
        store.playAndRecord(passAndPlay.id, ChessGame.newGame(), "c2c4")

        val newComputer = store.startGame(ComputerOpponent(Side.BLACK, 2))

        assertNull(store.game(oldComputer.id))
        assertEquals(0L, countRows("SELECT count(*) FROM local_moves WHERE game_id = ${oldComputer.id}"))
        assertEquals(newComputer, store.activeGame(LocalGameKind.COMPUTER))
        assertEquals(passAndPlay.id, store.activeGame(LocalGameKind.PASS_AND_PLAY)!!.id)
        assertEquals(1L, countRows("SELECT count(*) FROM local_moves WHERE game_id = ${passAndPlay.id}"))
    }

    @Test
    fun theDatabaseRefusesASecondUnfinishedGameOfOneKindAndAcceptsOneOfEach() {
        val driver = openDriver()
        openStore(driver).startGame()
        val queries = LocalGameDatabase(driver).localGamesQueries

        fun insert(computer: ComputerOpponent?) =
            queries.insert(
                kind = (if (computer == null) LocalGameKind.PASS_AND_PLAY else LocalGameKind.COMPUTER).name,
                createdAt = 0,
                state = LocalStateDocument.encode(ChessGame.newGame().state),
                humanSide = computer?.humanSide?.name,
                difficulty = computer?.difficulty?.toLong(),
            )

        assertThrows(Exception::class.java) { insert(null) }
        insert(ComputerOpponent(Side.WHITE, 1))
        assertThrows(Exception::class.java) { insert(ComputerOpponent(Side.BLACK, 2)) }
        assertEquals(2L, countRows("SELECT count(*) FROM local_games"))
    }

    @Test
    fun finishingOneKindLeavesTheOtherUnfinished() {
        val store = openStore()
        val passAndPlay = store.startGame()
        val computer = store.startGame(ComputerOpponent(Side.WHITE, 3))

        store.recordResult(computer.id, ChessRules.resign(ChessGame.newGame(), Side.WHITE))

        assertNull(store.activeGame(LocalGameKind.COMPUTER))
        assertEquals(passAndPlay, store.activeGame(LocalGameKind.PASS_AND_PLAY))
        assertEquals(listOf(computer.id), store.completedGames().map { it.id })
    }

    @Test
    fun aReplacedUnfinishedGameIsDeletedNotKept() {
        val store = openStore()
        val replaced = store.startGame()
        store.playAndRecord(replaced.id, ChessGame.newGame(), "e2e4", "e7e5")

        val replacement = store.startGame()

        assertNotEquals(replaced.id, replacement.id)
        assertNull(store.game(replaced.id))
        assertEquals(emptyList<LocalGameSummary>(), store.completedGames())
        assertEquals(0L, countRows("SELECT count(*) FROM local_moves"))
        assertEquals(ChessGame.newGame(), store.activeGame(LocalGameKind.PASS_AND_PLAY)!!.game)
    }

    // --- Completion -------------------------------------------------------------------

    @Test
    fun aGameEndingMoveCompletesTheGameAndKeepsIt() {
        val store = openStore()
        val id = store.startGame().id

        val mated = store.playAndRecord(id, ChessGame.newGame(), "f2f3", "e7e5", "g2g4", "d8h4")

        assertNull(store.activeGame(LocalGameKind.PASS_AND_PLAY))
        val kept = openStore().game(id)!!
        assertFalse(kept.isActive)
        assertEquals(mated, kept.game)
        assertEquals(GameResult.checkmate(Side.WHITE), kept.game.result)
        assertEquals(listOf(id), store.completedGames().map { it.id })
        assertEquals(GameResult.checkmate(Side.WHITE), store.completedGames().single().result)
    }

    @Test
    fun resignationAndDrawClaimsCompleteTheGame() {
        val store = openStore()
        val resigned = store.startGame().id
        val played = store.playAndRecord(resigned, ChessGame.newGame(), "e2e4")
        store.recordResult(resigned, ChessRules.resign(played, Side.BLACK))

        val claimed = store.startGame().id
        val shuffle = arrayOf("g1f3", "g8f6", "f3g1", "f6g8")
        val repeated = store.playAndRecord(claimed, ChessGame.newGame(), *shuffle, *shuffle)
        store.recordResult(claimed, ChessRules.claimDraw(repeated, DrawClaim.THREEFOLD_REPETITION))

        assertNull(store.activeGame(LocalGameKind.PASS_AND_PLAY))
        assertEquals(GameResult.resignation(Side.BLACK), store.game(resigned)!!.game.result)
        val drawn = store.game(claimed)!!.game
        assertEquals(TerminationReason.THREEFOLD_REPETITION_CLAIM, drawn.result!!.reason)
        assertEquals(8, drawn.history.size)
    }

    @Test
    fun aFinishedGamesMovesAreFinal() {
        val store = openStore()
        val id = store.startGame().id
        val mated = store.playAndRecord(id, ChessGame.newGame(), "f2f3", "e7e5", "g2g4", "d8h4")

        assertThrows(IllegalArgumentException::class.java) { store.takeBack(id) }
        assertThrows(IllegalArgumentException::class.java) { store.recordResult(id, mated) }
        assertEquals(mated, store.game(id)!!.game)
    }

    @Test
    fun severalFinishedGamesCoexistAndComeBackNewestFirst() {
        val store = openStore()
        val finished =
            (1..3).map { round ->
                val id = store.startGame(if (round == 2) ComputerOpponent(Side.BLACK, 1) else null).id
                store.recordResult(id, ChessRules.resign(ChessGame.newGame(), Side.WHITE))
                id
            }
        // Starting a game while none is unfinished deletes nothing.
        val unfinished = store.startGame().id

        val listed = openStore().completedGames()
        assertEquals(finished.reversed(), listed.map { it.id })
        assertEquals(listOf(LocalGameKind.PASS_AND_PLAY, LocalGameKind.COMPUTER, LocalGameKind.PASS_AND_PLAY), listed.map { it.kind })
        assertTrue(listed.zipWithNext().all { (newer, older) -> newer.completedAt > older.completedAt })
        assertEquals(unfinished, store.activeGame(LocalGameKind.PASS_AND_PLAY)!!.id)
    }

    // --- Support ----------------------------------------------------------------------

    private fun countRows(sql: String): Long {
        val driver = openDriver()
        return driver
            .executeQuery(null, sql, { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0)!! else 0L) }, 0)
            .value
    }
}
