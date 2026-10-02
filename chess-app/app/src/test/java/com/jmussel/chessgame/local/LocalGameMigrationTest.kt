package com.jmussel.chessgame.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.jmussel.chessgame.ai.Difficulty
import com.jmussel.chessgame.computer.computerLabel
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.local.db.LocalGameDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.sql.DriverManager
import java.util.Properties

/**
 * Local games saved by the first schema version, as an installed app has them, opened by the
 * current app, which migrates the database to the current version: Hard moves from level 3
 * to 4, levels 1 and 2 keep their numbers (`D089`, `M21.12`), and an unfinished game of one
 * kind may then be kept beside one of the other (`D090`, `M21.13`).
 *
 * The version-1 database is the committed snapshot `1.db`, with games written into it in
 * SQL, as the app of that version wrote them.
 */
class LocalGameMigrationTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val drivers = mutableListOf<SqlDriver>()

    @After
    fun closeDrivers() {
        drivers.forEach { it.close() }
    }

    private val file by lazy { folder.root.resolve(LOCAL_GAME_DATABASE_NAME) }

    /** A version-1 database holding [rows], each `(kind, status, difficulty)`. */
    private fun versionOneDatabase(vararg rows: Triple<String, String, Int?>) {
        File(SNAPSHOT).copyTo(file)
        val state = LocalStateDocument.encode(ChessRules.applyMove(ChessGame.newGame(), Move.of("e2", "e4")).state)
        val final = LocalStateDocument.encode(ChessRules.resign(ChessGame.newGame(), Side.WHITE).state)
        DriverManager.getConnection("jdbc:sqlite:${file.path}").use { connection ->
            connection.createStatement().use { it.execute("PRAGMA user_version = 1") }
            rows.forEachIndexed { index, (kind, status, difficulty) ->
                val finished = status == "COMPLETED"
                connection
                    .prepareStatement(
                        "INSERT INTO local_games (kind, status, created_at, completed_at, state, outcome, termination_reason, " +
                            "human_side, difficulty) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    ).use { insert ->
                        insert.setString(1, kind)
                        insert.setString(2, status)
                        insert.setLong(3, 1_000L + index)
                        if (finished) insert.setLong(4, 2_000L + index) else insert.setNull(4, java.sql.Types.INTEGER)
                        insert.setString(5, if (finished) final else state)
                        insert.setString(6, if (finished) "BLACK_WINS" else null)
                        insert.setString(7, if (finished) "RESIGNATION" else null)
                        insert.setString(8, if (kind == "COMPUTER") "WHITE" else null)
                        if (difficulty == null) insert.setNull(9, java.sql.Types.INTEGER) else insert.setInt(9, difficulty)
                        insert.executeUpdate()
                    }
            }
        }
    }

    /** The current app's store on the file, migrating it as the app does on opening. */
    private fun openStore(): LocalGameStore =
        LocalGameStore(
            LocalGameDatabase(
                JdbcSqliteDriver(
                    url = "jdbc:sqlite:${file.path}",
                    properties = Properties().apply { put("foreign_keys", "true") },
                    schema = LocalGameDatabase.Schema,
                ).also { drivers += it },
            ),
        )

    private fun userVersion(): Long =
        DriverManager.getConnection("jdbc:sqlite:${file.path}").use { connection ->
            connection.createStatement().use { it.executeQuery("PRAGMA user_version").use { result -> result.getLong(1) } }
        }

    @Test
    fun finishedGamesAtTheThreeOldLevelsAreListedUnderTheirNewNames() {
        versionOneDatabase(
            Triple("COMPUTER", "COMPLETED", 1),
            Triple("COMPUTER", "COMPLETED", 2),
            Triple("COMPUTER", "COMPLETED", 3),
            Triple("PASS_AND_PLAY", "COMPLETED", null),
        )

        val store = openStore()

        assertEquals(LocalGameDatabase.Schema.version, userVersion())
        val listed = store.completedGames().sortedBy { it.createdAt }
        assertEquals(listOf(1, 2, 4, null), listed.map { it.computer?.difficulty })
        assertEquals(
            listOf("Computer (Very Easy)", "Computer (Easy)", "Computer (Hard)"),
            listed.mapNotNull { it.computer?.difficulty }.map(::computerLabel),
        )
    }

    @Test
    fun anUnfinishedGameResumesAtTheStrengthItWasStartedAt() {
        // The old Easy and Medium are the same engines as Very Easy and Easy; the old Hard is
        // Hard.
        val expected = mapOf(1 to Difficulty.VERY_EASY, 2 to Difficulty.EASY, 3 to Difficulty.HARD)

        expected.forEach { (old, difficulty) ->
            closeDrivers()
            drivers.clear()
            file.delete()
            versionOneDatabase(Triple("COMPUTER", "ACTIVE", old))

            val resumed = openStore().activeGame(LocalGameKind.COMPUTER)!!

            assertEquals(difficulty, Difficulty.ofLevel(resumed.computer!!.difficulty))
            assertEquals(ChessRules.applyMove(ChessGame.newGame(), Move.of("e2", "e4")).state, resumed.game.state)
        }
    }

    @Test
    fun anUnfinishedGameAndItsMoveSurviveAndAGameOfTheOtherKindCanStartBesideIt() {
        versionOneDatabase(Triple("COMPUTER", "ACTIVE", 3))
        DriverManager.getConnection("jdbc:sqlite:${file.path}").use { connection ->
            connection
                .prepareStatement("INSERT INTO local_moves (game_id, ply, move, position_before) VALUES (1, 1, 'e2e4', ?)")
                .use { insert ->
                    insert.setString(1, LocalStateDocument.encode(ChessGame.newGame().state))
                    insert.executeUpdate()
                }
        }

        val store = openStore()

        val computer = store.activeGame(LocalGameKind.COMPUTER)!!
        assertEquals(Difficulty.HARD.level, computer.computer!!.difficulty)
        assertEquals(ChessRules.applyMove(ChessGame.newGame(), Move.of("e2", "e4")), computer.game)
        val passAndPlay = store.startGame()
        assertEquals(computer, store.activeGame(LocalGameKind.COMPUTER))
        assertEquals(passAndPlay, store.activeGame(LocalGameKind.PASS_AND_PLAY))

        val queries = LocalGameDatabase(drivers.last()).localGamesQueries
        assertThrows(Exception::class.java) {
            queries.insert(
                kind = LocalGameKind.COMPUTER.name,
                createdAt = 0,
                state = LocalStateDocument.encode(ChessGame.newGame().state),
                humanSide = Side.BLACK.name,
                difficulty = 1,
            )
        }
    }

    private companion object {
        /** The released version-1 schema, relative to the module the tests run in. */
        const val SNAPSHOT = "src/main/sqldelight/databases/1.db"
    }
}
