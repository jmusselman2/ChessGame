@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.GameOutcome
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.TerminationReason
import org.flywaydb.core.Flyway
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * `V14` (`M22.1`, `D095`): a chess pair keeps only its newest active series, a closed series
 * keeps no unfinished game, every game knows whether it has started, and from then on the
 * database refuses a second active chess series.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class OneActiveChessSeriesMigrationTest {
    @Test
    fun theNewestActiveSeriesStaysAndOlderOnesGoWithEverythingInThem() {
        withV13 { db ->
            val (jordan, alex) = db.pair("a")
            val table = db.chessTable(jordan, alex)
            val oldest = db.series(table, createdAt = "2026-01-01")
            val older = db.series(table, createdAt = "2026-02-01")
            val newest = db.series(table, createdAt = "2026-03-01")
            val oldGame = db.game(oldest, jordan, alex, moves = 2)
            val olderGame = db.game(older, jordan, alex, moves = 0)
            val newestGame = db.game(newest, jordan, alex, moves = 1)
            db.event(oldGame, oldest, "MoveMade")
            db.seriesEvent(oldest, "RematchCreated")

            db.migrate()

            assertEquals(listOf(newest), db.uuids("select id from game_series where table_id = '$table'"))
            assertEquals(listOf(newestGame), db.uuids("select id from games"))
            assertEquals(0, db.count("select count(*) from moves where game_id in ('$oldGame', '$olderGame')"))
            assertEquals(0, db.count("select count(*) from game_events where series_id in ('$oldest', '$older')"))
            assertEquals(0, db.count("select count(*) from game_participants where game_id in ('$oldGame', '$olderGame')"))
            assertEquals(newestGame, db.uuids("select current_game_id from game_series where id = '$newest'").single())
        }
    }

    @Test
    fun equalCreationTimesAreBrokenByIdDescending() {
        withV13 { db ->
            val (jordan, alex) = db.pair("a")
            val table = db.chessTable(jordan, alex)
            val first = db.series(table, createdAt = "2026-01-01")
            val second = db.series(table, createdAt = "2026-01-01")

            db.migrate()

            assertEquals(listOf(maxOf(first, second)), db.uuids("select id from game_series"))
        }
    }

    @Test
    fun closedSeriesAndOtherPairsAreLeftAlone() {
        withV13 { db ->
            val (jordan, alex) = db.pair("a")
            val (sam, kim) = db.pair("b")
            val table = db.chessTable(jordan, alex)
            val closed = db.series(table, createdAt = "2025-01-01", closed = true)
            val finished = db.game(closed, jordan, alex, moves = 1, finished = true)
            val active = db.series(table, createdAt = "2026-01-01")
            val other = db.series(db.chessTable(sam, kim), createdAt = "2025-06-01")

            db.migrate()

            assertEquals(setOf(closed, active, other), db.uuids("select id from game_series").toSet())
            assertEquals(listOf(finished), db.uuids("select id from games where series_id = '$closed'"))
        }
    }

    @Test
    fun hasStartedIsBackfilledFromEvidenceOfAnAcceptedMove() {
        withV13 { db ->
            val (jordan, alex) = db.pair("a")
            val (sam, kim) = db.pair("b")
            val (lee, max) = db.pair("c")
            val withMoves = db.game(db.series(db.chessTable(jordan, alex), createdAt = "2026-01-01"), jordan, alex, moves = 1)
            val undoneSeries = db.series(db.chessTable(sam, kim), createdAt = "2026-01-01")
            val allUndone = db.game(undoneSeries, sam, kim, moves = 0)
            db.event(allUndone, undoneSeries, "MoveMade")
            db.event(allUndone, undoneSeries, "MoveUndone")
            val untouched = db.game(db.series(db.chessTable(lee, max), createdAt = "2026-01-01"), lee, max, moves = 0)

            db.migrate()

            assertTrue(db.hasStarted(withMoves))
            assertTrue(db.hasStarted(allUndone), "a game whose moves were all undone has still started")
            assertFalse(db.hasStarted(untouched))
        }
    }

    @Test
    fun aStartedGameInASeriesThatWasLeftBecomesTheLeaversResignation() {
        withV13 { db ->
            val (jordan, alex) = db.pair("a")
            val series = db.series(db.chessTable(jordan, alex), createdAt = "2026-01-01", closed = true)
            val game = db.game(series, white = jordan, black = alex, moves = 2)
            db.seriesEvent(series, "SeriesLeft", actor = jordan)

            db.migrate()

            val stored = assertNotNull(GameRepository(Databases.connect(db.dataSource)).load(game))
            val result = assertNotNull(stored.game.result)
            assertEquals(GameOutcome.BLACK_WINS, result.outcome, "White left, so Black wins")
            assertEquals(TerminationReason.RESIGNATION, result.reason)
            assertEquals(3, stored.version, "one accepted change")
            assertEquals(2, stored.game.moves.size, "what was played stays")
            assertNotNull(stored.endedAt)
            assertEquals(
                listOf("PlayerResigned", "GameEnded"),
                db.strings("select type from game_events where game_id = '$game' order by id"),
            )
            assertEquals(listOf(jordan), db.uuids("select actor_id from game_events where game_id = '$game' and type = 'PlayerResigned'"))
        }
    }

    @Test
    fun anUnstartedGameInASeriesThatWasLeftIsDeleted() {
        withV13 { db ->
            val (jordan, alex) = db.pair("a")
            val series = db.series(db.chessTable(jordan, alex), createdAt = "2026-01-01", closed = true)
            val earlier = db.game(series, jordan, alex, moves = 1, finished = true, sequence = 1)
            val rematch = db.game(series, alex, jordan, moves = 0, sequence = 2)
            db.seriesEvent(series, "SeriesLeft", actor = alex)

            db.migrate()

            assertEquals(listOf(earlier), db.uuids("select id from games"))
            assertNull(db.uuids("select current_game_id from game_series where id = '$series'").singleOrNull())
        }
    }

    @Test
    fun theDatabaseRefusesASecondActiveChessSeries() {
        withV13 { db ->
            val (jordan, alex) = db.pair("a")
            val table = db.chessTable(jordan, alex)
            val closed = db.series(table, createdAt = "2025-01-01", closed = true)
            db.series(table, createdAt = "2026-01-01")

            db.migrate()

            val error = assertFailsWith<SQLException> { db.series(table, createdAt = "2026-02-01") }
            assertTrue(error.message.orEmpty().contains("already has an active series"), error.message)
            assertFailsWith<SQLException>("nor by reopening a closed one") {
                db.execute("update game_series set status = 'ACTIVE', closed_at = null where id = '$closed'")
            }
            db.series(table, createdAt = "2026-03-01", closed = true)
        }
    }

    @Test
    fun theRuleIsForChessOnly() {
        withV13 { db ->
            val (jordan, alex) = db.pair("a")
            db.migrate()
            db.execute("insert into game_types (id, min_participants, max_participants) values ('OTHER', 2, 4)")
            val table = db.table("OTHER", jordan, alex)

            db.series(table, createdAt = "2026-01-01")
            db.series(table, createdAt = "2026-02-01")

            assertEquals(2, db.count("select count(*) from game_series where table_id = '$table' and status = 'ACTIVE'"))
        }
    }

    /** A database migrated to `V13`, and the means to fill it in that shape. */
    private class V13Database(
        val dataSource: DataSource,
    ) {
        fun migrate() = Migrations.migrate(dataSource)

        fun pair(tag: String): Pair<Uuid, Uuid> = user("$tag-1") to user("$tag-2")

        private fun user(name: String): Uuid =
            uuid("insert into users (username, username_normalized) values ('U$name', lower('U$name')) returning id")

        fun chessTable(
            first: Uuid,
            second: Uuid,
        ): Uuid = table("CHESS", first, second)

        fun table(
            type: String,
            first: Uuid,
            second: Uuid,
        ): Uuid {
            val (low, high) = listOf(first, second).sorted()
            val table =
                uuid(
                    "insert into tables (game_type, participant_set) values ('$type', 'USER:$low,USER:$high') returning id",
                )
            execute(
                "insert into table_participants (table_id, seat_index, kind, user_id) values " +
                    "('$table', 0, 'USER', '$low'), ('$table', 1, 'USER', '$high')",
            )
            return table
        }

        fun series(
            table: Uuid,
            createdAt: String,
            closed: Boolean = false,
        ): Uuid =
            uuid(
                "insert into game_series (table_id, status, created_at, closed_at) values ('$table', " +
                    (if (closed) "'CLOSED'" else "'ACTIVE'") + ", '$createdAt', " +
                    (if (closed) "'$createdAt'" else "null") + ") returning id",
            )

        /** A game at version [moves] after that many plies of `1. e4 e5`, made the series' current game. */
        fun game(
            series: Uuid,
            white: Uuid,
            black: Uuid,
            moves: Int,
            finished: Boolean = false,
            sequence: Int = 1,
        ): Uuid {
            val positions = mutableListOf(ChessGame.newGame())
            listOf(Move.of("e2", "e4"), Move.of("e7", "e5")).take(moves).forEach { positions += ChessRules.applyMove(positions.last(), it) }
            val final = if (finished) ChessRules.resign(positions.last(), positions.last().sideToMove) else positions.last()

            val game =
                uuid(
                    "insert into games (series_id, sequence_number, status, version, side_to_move, state, result, " +
                        "termination_reason, ended_at) values ('$series', $sequence, " +
                        (if (finished) "'COMPLETE'" else "'IN_PROGRESS'") + ", $moves, '${final.sideToMove}', " +
                        "'${document(final)}'::jsonb, " +
                        (if (finished) "'${final.result?.outcome}', 'RESIGNATION', now()" else "null, null, null") +
                        ") returning id",
                )
            execute(
                "insert into game_participants (game_id, seat_index, kind, user_id) values " +
                    "('$game', 0, 'USER', '$white'), ('$game', 1, 'USER', '$black')",
            )
            (0 until moves).forEach { ply ->
                val move = positions[ply + 1].history.last().move
                execute(
                    "insert into moves (game_id, ply, side, from_square, to_square, position_before) values " +
                        "('$game', ${ply + 1}, '${positions[ply].sideToMove}', '${move.from}', '${move.to}', " +
                        "'${document(positions[ply])}'::jsonb)",
                )
            }
            execute("update game_series set current_game_id = '$game' where id = '$series'")
            return game
        }

        fun event(
            game: Uuid,
            series: Uuid,
            type: String,
        ) = execute("insert into game_events (game_id, series_id, type) values ('$game', '$series', '$type')")

        fun seriesEvent(
            series: Uuid,
            type: String,
            actor: Uuid? = null,
        ) = execute(
            "insert into game_events (series_id, actor_id, type) values ('$series', " +
                (actor?.let { "'$it'" } ?: "null") + ", '$type')",
        )

        fun hasStarted(game: Uuid): Boolean = count("select count(*) from games where id = '$game' and has_started") == 1

        fun count(sql: String): Int = DatabaseTestSupport.count(dataSource, sql)

        fun uuids(sql: String): List<Uuid> = strings(sql).map(Uuid::parse)

        /** The first column of every row [sql] returns; committed, so an `insert ... returning` lands. */
        fun strings(sql: String): List<String> =
            dataSource.connection.use { connection ->
                val values =
                    connection.createStatement().use { statement ->
                        statement.executeQuery(sql).use { rows ->
                            buildList { while (rows.next()) rows.getString(1)?.let(::add) }
                        }
                    }
                connection.commit()
                values
            }

        fun execute(statement: String) {
            dataSource.connection.use { connection ->
                connection.createStatement().use { it.execute(statement) }
                connection.commit()
            }
        }

        private fun uuid(query: String): Uuid = uuids(query).single()

        private fun document(game: ChessGame): String =
            StorageJson.encodeToString(GameStateDocument.serializer(), GameStateDocument.of(game.state))
    }

    private fun withV13(block: (V13Database) -> Unit) =
        DatabaseTestSupport.withEmptyDatabase { dataSource ->
            Flyway
                .configure()
                .dataSource(dataSource)
                .locations(Migrations.LOCATION)
                .table(Migrations.HISTORY_TABLE)
                .target("13")
                .load()
                .migrate()
            block(V13Database(dataSource))
        }
}
