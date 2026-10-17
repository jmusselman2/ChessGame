@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import org.flywaydb.core.Flyway
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi

/**
 * `V16` (`M22.7`, `D101`): the SAN cutover drops every pending undo request, every last-answer
 * note and every recorded last change, and nothing else.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class SanCutoverMigrationTest {
    @Test
    fun obsoleteRequestMetadataIsClearedAndThePositionIsNot() {
        DatabaseTestSupport.withEmptyDatabase { dataSource ->
            Flyway
                .configure()
                .dataSource(dataSource)
                .locations(Migrations.LOCATION)
                .table(Migrations.HISTORY_TABLE)
                .target("15")
                .load()
                .migrate()

            fun exec(sql: String) =
                dataSource.connection.use { connection ->
                    connection.createStatement().use { it.execute(sql) }
                    connection.commit()
                }

            exec(
                "insert into users (id, username, username_normalized) values ('00000000-0000-0000-0000-00000000000a', 'Jordan', 'jordan')",
            )
            exec("insert into users (id, username, username_normalized) values ('00000000-0000-0000-0000-00000000000b', 'Alex', 'alex')")
            exec(
                "insert into tables (id, game_type, participant_set) values ('00000000-0000-0000-0000-0000000000a1', 'CHESS', " +
                    "'USER:00000000-0000-0000-0000-00000000000a,USER:00000000-0000-0000-0000-00000000000b')",
            )
            exec(
                "insert into game_series (id, table_id) values ('00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-0000000000a1')",
            )
            val state =
                """{"board":["rnbqkbnr","pppppppp","........","........","........","........","PPPPPPPP","RNBQKBNR"],""" +
                    """"sideToMove":"WHITE","castling":"KQkq"}"""
            // One game with a pending request, one with a note, each with a recorded last change.
            exec(
                "insert into games (id, series_id, sequence_number, version, play_version, state, draw_offered_by, draw_offered_version, " +
                    "undo_requested_by, undo_requested_version, last_change_kind, last_change_actor, last_change_version) values " +
                    "('00000000-0000-0000-0000-0000000000c1', '00000000-0000-0000-0000-0000000000b1', 1, 7, 5, '$state'::jsonb, " +
                    "'00000000-0000-0000-0000-00000000000b', 6, '00000000-0000-0000-0000-00000000000a', 7, 'UNDO_REQUEST', " +
                    "'00000000-0000-0000-0000-00000000000a', 6)",
            )

            Migrations.migrate(dataSource)

            assertEquals(
                0,
                DatabaseTestSupport.count(
                    dataSource,
                    "select count(*) from games where undo_requested_by is not null or undo_requested_version is not null " +
                        "or undo_answer is not null or last_change_kind is not null or last_change_actor is not null " +
                        "or last_change_version is not null or last_change_move is not null",
                ),
            )
            assertEquals(
                1,
                DatabaseTestSupport.count(
                    dataSource,
                    "select count(*) from games where version = 7 and play_version = 5 " +
                        "and draw_offered_by = '00000000-0000-0000-0000-00000000000b'",
                ),
                "the version, the play version and a draw offer are untouched",
            )
        }
    }
}
