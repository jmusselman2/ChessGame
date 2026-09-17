@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import com.jmussel.chessgame.core.chess.ChessGame
import org.flywaydb.core.Flyway
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Evaluator-only coverage for the complete supported V2 -> V9 beta upgrade. */
class M19MigrationEvaluationTest {
    @Test
    fun v3ThroughV9PreserveEveryPreM19Entity() {
        DatabaseTestSupport.withEmptyDatabase { dataSource ->
            migrateTo(dataSource, "2")

            val first =
                uuid(
                    dataSource,
                    "insert into users (auth_subject, username, username_normalized) " +
                        "values ('eval-a', 'EvalA', 'evala') returning id",
                )
            val second =
                uuid(
                    dataSource,
                    "insert into users (auth_subject, username, username_normalized) " +
                        "values ('eval-b', 'EvalB', 'evalb') returning id",
                )
            val (low, high) = listOf(first, second).sorted()
            execute(dataSource, "insert into friendships (user_a_id, user_b_id) values ('$low', '$high')")
            val series = uuid(dataSource, "insert into game_series (user_a_id, user_b_id) values ('$low', '$high') returning id")
            val state = StorageJson.encodeToString(GameStateDocument.serializer(), GameStateDocument.of(ChessGame.newGame().state))
            val game =
                uuid(
                    dataSource,
                    "insert into games (series_id, sequence_number, white_user_id, black_user_id, state) " +
                        "values ('$series', 1, '$first', '$second', '$state'::jsonb) returning id",
                )
            execute(dataSource, "update game_series set current_game_id = '$game' where id = '$series'")
            execute(
                dataSource,
                "insert into moves (game_id, ply, side, from_square, to_square, position_before) " +
                    "values ('$game', 1, 'WHITE', 'e2', 'e4', '$state'::jsonb)",
            )
            execute(
                dataSource,
                "insert into game_events (game_id, series_id, actor_id, type) " +
                    "values ('$game', '$series', '$first', 'MoveMade')",
            )

            Migrations.migrate(dataSource)

            assertEquals((1..9).map { it.toString() }, Migrations.appliedVersions(dataSource))
            assertEquals(2, count(dataSource, "select count(*) from users where id in ('$first', '$second')"))
            assertEquals(
                1,
                count(
                    dataSource,
                    "select count(*) from friendships where user_a_id = '$low' " +
                        "and user_b_id = '$high' and status = 'ACTIVE'",
                ),
            )
            assertEquals(1, count(dataSource, "select count(*) from game_series where id = '$series' and current_game_id = '$game'"))
            assertEquals(1, count(dataSource, "select count(*) from games where id = '$game' and series_id = '$series'"))
            assertEquals(1, count(dataSource, "select count(*) from moves where game_id = '$game' and ply = 1"))
            assertEquals(
                1,
                count(
                    dataSource,
                    "select count(*) from game_events where game_id = '$game' " +
                        "and series_id = '$series' and actor_id = '$first'",
                ),
            )
            assertEquals(2, count(dataSource, "select count(*) from table_participants"))
            assertEquals(2, count(dataSource, "select count(*) from game_participants"))
            assertTrue(DatabaseTestSupport.tableExists(dataSource, "groups"))
            assertTrue(DatabaseTestSupport.tableExists(dataSource, "non_user_participants"))

            val storedFirst = UserRepository(Databases.connect(dataSource)).find(first)
            assertNull(storedFirst?.lastLoginAt, "V4 must not invent a login")
            assertNull(storedFirst?.lastActionAt, "V4 must not invent an action")
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

    private fun execute(
        dataSource: DataSource,
        statement: String,
    ) {
        dataSource.connection.use { connection ->
            connection.createStatement().use { it.execute(statement) }
            connection.commit()
        }
    }

    private fun uuid(
        dataSource: DataSource,
        query: String,
    ): Uuid = Uuid.parse(single(dataSource, query))

    private fun count(
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
