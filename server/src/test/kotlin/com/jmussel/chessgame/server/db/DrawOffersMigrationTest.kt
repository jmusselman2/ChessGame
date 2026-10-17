@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.server.game.CommandResult
import com.jmussel.chessgame.server.game.GameCommandService
import com.jmussel.chessgame.server.series.seriesService
import org.flywaydb.core.Flyway
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * `V15` (`M22.3`, `D097`): a game in progress gains no draw offer and no block, its play version
 * is its current version, and an undo request already pending is answered at that version as
 * before.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class DrawOffersMigrationTest {
    @Test
    fun aGameWithAPendingUndoRequestIsAnsweredAsBefore() {
        DatabaseTestSupport.withEmptyDatabase { dataSource ->
            Flyway
                .configure()
                .dataSource(dataSource)
                .locations(Migrations.LOCATION)
                .table(Migrations.HISTORY_TABLE)
                .target("14")
                .load()
                .migrate()
            val (white, black, game) = aGameWithAPendingRequest(dataSource)

            // To V15 only: V16's SAN cutover clears pending requests on purpose (M22.7).
            Flyway
                .configure()
                .dataSource(dataSource)
                .locations(Migrations.LOCATION)
                .table(Migrations.HISTORY_TABLE)
                .target("15")
                .load()
                .migrate()

            val database = Databases.connect(dataSource)
            val games = GameRepository(database)
            val stored = assertNotNull(games.load(game))
            assertEquals(3, stored.playVersion)
            assertEquals(3L, stored.undo.pendingSince)
            assertNull(stored.draw.offeredBy)
            assertFalse(stored.draw.blocked)

            val commands = GameCommandService(database, games, seriesService(database))
            assertIs<CommandResult.StaleVersion>(commands.acceptUndo(black, game, 2), "from before the request")
            assertIs<CommandResult.Applied>(commands.acceptUndo(black, game, 3))
            assertEquals(emptyList(), assertNotNull(games.load(game)).game.moves)
            assertNotNull(white)
        }
    }

    /** White's g1f3 answered by Black's b8c6, and White's undo request pending at version 3. */
    private fun aGameWithAPendingRequest(dataSource: DataSource): Triple<Uuid, Uuid, Uuid> {
        val white = uuid(dataSource, "insert into users (username, username_normalized) values ('Jordan', 'jordan') returning id")
        val black = uuid(dataSource, "insert into users (username, username_normalized) values ('Alex', 'alex') returning id")
        val (low, high) = listOf(white, black).sorted()
        val table =
            uuid(dataSource, "insert into tables (game_type, participant_set) values ('CHESS', 'USER:$low,USER:$high') returning id")
        execute(
            dataSource,
            "insert into table_participants (table_id, seat_index, kind, user_id) values ('$table', 0, 'USER', '$low'), " +
                "('$table', 1, 'USER', '$high')",
        )
        val series = uuid(dataSource, "insert into game_series (table_id) values ('$table') returning id")

        val start = ChessGame.newGame()
        val afterNf3 = ChessRules.applyMove(start, Move.of("g1", "f3"))
        val afterNc6 = ChessRules.applyMove(afterNf3, Move.of("b8", "c6"))
        val game =
            uuid(
                dataSource,
                "insert into games (series_id, sequence_number, version, side_to_move, state, undo_requested_by, " +
                    "last_change_kind, last_change_actor, last_change_version, has_started) values ('$series', 1, 3, 'WHITE', " +
                    "'${document(afterNc6)}'::jsonb, '$white', 'UNDO_REQUEST', '$white', 2, true) returning id",
            )
        execute(
            dataSource,
            "insert into game_participants (game_id, seat_index, kind, user_id) values ('$game', 0, 'USER', '$white'), " +
                "('$game', 1, 'USER', '$black')",
        )
        execute(
            dataSource,
            "insert into moves (game_id, ply, side, from_square, to_square, position_before) values " +
                "('$game', 1, 'WHITE', 'g1', 'f3', '${document(start)}'::jsonb), " +
                "('$game', 2, 'BLACK', 'b8', 'c6', '${document(afterNf3)}'::jsonb)",
        )
        execute(dataSource, "update game_series set current_game_id = '$game' where id = '$series'")
        return Triple(white, black, game)
    }

    private fun document(game: ChessGame): String =
        StorageJson.encodeToString(GameStateDocument.serializer(), GameStateDocument.of(game.state))

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
    ): Uuid =
        dataSource.connection.use { connection ->
            val value =
                connection.createStatement().use { statement ->
                    statement.executeQuery(query).use { rows ->
                        check(rows.next()) { "no row returned" }
                        rows.getString(1)
                    }
                }
            connection.commit()
            Uuid.parse(value)
        }
}
