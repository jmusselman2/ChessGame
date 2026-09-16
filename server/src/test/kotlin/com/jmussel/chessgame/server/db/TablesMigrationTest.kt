@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import com.jmussel.chessgame.core.chess.ChessGame
import org.flywaydb.core.Flyway
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * `V5__tables_and_participants.sql` carries the pair model over without losing anything
 * (`M19.3`: "forward-only and preserves all existing games, series, and history").
 *
 * The database is migrated only as far as `V4`, filled the way the pair-keyed server filled
 * it, and then migrated the rest of the way — exactly what the beta database will go through.
 * The checks read the result both as rows and through the server's own repositories, since
 * the point is that a migrated pair and a pair the new code creates are the same table.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class TablesMigrationTest {
    @Test
    fun everyPairSeriesGameAndEventSurvivesTheMoveToTables() {
        DatabaseTestSupport.withEmptyDatabase { dataSource ->
            migrateTo(dataSource, version = "4")

            val jordan =
                Sql.uuid(
                    dataSource,
                    "insert into users (auth_subject, username, username_normalized) values ('a-j', 'Jordan', 'jordan') returning id",
                )
            val alex =
                Sql.uuid(
                    dataSource,
                    "insert into users (auth_subject, username, username_normalized) values ('a-a', 'Alex', 'alex') returning id",
                )
            val sam =
                Sql.uuid(
                    dataSource,
                    "insert into users (auth_subject, username, username_normalized) values ('a-s', 'Sam', 'sam') returning id",
                )
            val (jaLow, jaHigh) = listOf(jordan, alex).sorted()
            val (jsLow, jsHigh) = listOf(jordan, sam).sorted()

            val closedSeries =
                Sql.uuid(
                    dataSource,
                    "insert into game_series (user_a_id, user_b_id, status, closed_at) " +
                        "values ('$jaLow', '$jaHigh', 'CLOSED', now()) returning id",
                )
            val activeSeries =
                Sql.uuid(dataSource, "insert into game_series (user_a_id, user_b_id) values ('$jaLow', '$jaHigh') returning id")
            val otherSeries =
                Sql.uuid(dataSource, "insert into game_series (user_a_id, user_b_id) values ('$jsLow', '$jsHigh') returning id")

            val newGame = StorageJson.encodeToString(GameStateDocument.serializer(), GameStateDocument.of(ChessGame.newGame().state))
            val finished =
                Sql.uuid(
                    dataSource,
                    "insert into games (series_id, sequence_number, white_user_id, black_user_id, state, version, " +
                        "status, result, termination_reason, ended_at) values ('$closedSeries', 1, '$alex', '$jordan', " +
                        "'$newGame'::jsonb, 7, 'COMPLETE', 'BLACK_WINS', 'RESIGNATION', now()) returning id",
                )
            val running =
                Sql.uuid(
                    dataSource,
                    "insert into games (series_id, sequence_number, white_user_id, black_user_id, state, version) " +
                        "values ('$activeSeries', 1, '$jordan', '$alex', '$newGame'::jsonb, 3) returning id",
                )
            val withSam =
                Sql.uuid(
                    dataSource,
                    "insert into games (series_id, sequence_number, white_user_id, black_user_id, state) " +
                        "values ('$otherSeries', 1, '$sam', '$jordan', '$newGame'::jsonb) returning id",
                )
            Sql.execute(dataSource, "update game_series set current_game_id = '$running' where id = '$activeSeries'")
            Sql.execute(dataSource, "update game_series set current_game_id = '$withSam' where id = '$otherSeries'")
            Sql.execute(
                dataSource,
                "insert into moves (game_id, ply, side, from_square, to_square, position_before) " +
                    "values ('$finished', 1, 'WHITE', 'e2', 'e4', '$newGame'::jsonb)",
            )
            Sql.execute(dataSource, "insert into game_events (game_id, series_id, type) values ('$finished', '$closedSeries', 'GameEnded')")
            Sql.execute(dataSource, "insert into game_events (series_id, type) values ('$activeSeries', 'RematchCreated')")

            Migrations.migrate(dataSource)

            // Rows: one table per pair, and every series and game kept its identity and content.
            assertEquals(2, Sql.count(dataSource, "select count(*) from tables where game_type = 'CHESS'"))
            assertEquals(3, Sql.count(dataSource, "select count(*) from game_series"))
            assertEquals(3, Sql.count(dataSource, "select count(*) from games"))
            assertEquals(1, Sql.count(dataSource, "select count(*) from moves"))
            assertEquals(2, Sql.count(dataSource, "select count(*) from game_events"))
            assertEquals(6, Sql.count(dataSource, "select count(*) from game_participants"))
            assertEquals(4, Sql.count(dataSource, "select count(*) from table_participants"))
            assertEquals(
                1,
                Sql.count(
                    dataSource,
                    "select count(*) from games where id = '$finished' and version = 7 and result = 'BLACK_WINS' " +
                        "and termination_reason = 'RESIGNATION' and ended_at is not null",
                ),
            )

            val database = Databases.connect(dataSource)
            val series = GameSeriesRepository(database)
            val games = GameRepository(database)

            val closed = requireNotNull(series.find(closedSeries))
            val active = requireNotNull(series.find(activeSeries))
            val other = requireNotNull(series.find(otherSeries))
            assertEquals(closed.tableId, active.tableId, "one pair, one table")
            assertNotEquals(active.tableId, other.tableId)
            assertEquals(listOf(jaLow, jaHigh), active.participants.userIds, "the pair's stored order is the seat order")
            assertEquals(CLOSED_SERIES, closed.status)
            assertEquals(running, active.currentGameId)

            // White is seat 0 and Black is seat 1, whichever of the pair was stored first.
            assertEquals(alex, games.load(finished)?.whiteUserId)
            assertEquals(jordan, games.load(finished)?.blackUserId)
            assertEquals(jordan, games.load(running)?.whiteUserId)
            assertEquals(3L, games.load(running)?.version)
            assertEquals(sam, games.load(withSam)?.whiteUserId)

            // The server computes the same key for the same people, so it finds the migrated
            // table and series rather than making new ones.
            val reopened = series.openOrCreate(GameTypes.CHESS, listOf(alex, jordan))
            assertFalse(reopened.created)
            assertEquals(activeSeries, reopened.series.id)
            assertEquals(2, Sql.count(dataSource, "select count(*) from tables"))

            val dashboard = DashboardQueries(database).activeSeriesFor(jordan)
            assertEquals(setOf(activeSeries, otherSeries), dashboard.map { it.seriesId }.toSet())
            assertEquals("WHITE", dashboard.single { it.seriesId == activeSeries }.yourSide)
            assertEquals("BLACK", dashboard.single { it.seriesId == otherSeries }.yourSide)

            val history = HistoryQueries(database).historyFor(jordan)
            assertEquals(listOf(closedSeries), history.map { it.seriesId })
            assertEquals(
                "BLACK",
                history
                    .single()
                    .games
                    .single()
                    .yourSide,
            )
            assertEquals(alex, history.single().opponent.id)

            assertEquals(
                0,
                Sql.count(
                    dataSource,
                    "select count(*) from information_schema.columns where table_name in ('games', 'game_series') " +
                        "and column_name in ('white_user_id', 'black_user_id', 'user_a_id', 'user_b_id')",
                ),
                "the pair columns are gone",
            )
        }
    }

    @Test
    fun anEmptyDatabaseMigratesToExactlyTheChessRegistration() {
        DatabaseTestSupport.withEmptyDatabase { dataSource ->
            migrateTo(dataSource, version = "4")
            Migrations.migrate(dataSource)

            assertEquals(0, Sql.count(dataSource, "select count(*) from tables"))
            assertEquals(
                1,
                Sql.count(
                    dataSource,
                    "select count(*) from game_types where id = 'CHESS' and min_participants = 2 and max_participants = 2",
                ),
            )
            assertEquals(1, Sql.count(dataSource, "select count(*) from game_types"))
        }
    }

    private fun migrateTo(
        dataSource: DataSource,
        version: String,
    ) {
        Flyway
            .configure()
            .dataSource(dataSource)
            .locations(Migrations.LOCATION)
            .table(Migrations.HISTORY_TABLE)
            .target(version)
            .load()
            .migrate()
    }

    /** Plain JDBC, because the columns being migrated away have no Exposed mapping any more. */
    private object Sql {
        fun execute(
            dataSource: DataSource,
            statement: String,
        ) {
            dataSource.connection.use { connection ->
                connection.createStatement().use { it.execute(statement) }
                connection.commit()
            }
        }

        fun uuid(
            dataSource: DataSource,
            query: String,
        ): Uuid = Uuid.parse(single(dataSource, query))

        fun count(
            dataSource: DataSource,
            query: String,
        ): Int = single(dataSource, query).toInt()

        private fun single(
            dataSource: DataSource,
            query: String,
        ): String =
            dataSource.connection.use { connection ->
                connection
                    .createStatement()
                    .use { statement ->
                        statement.executeQuery(query).use { rows ->
                            check(rows.next()) { "no row returned" }
                            rows.getString(1)
                        }
                    }.also { connection.commit() }
            }
    }
}
