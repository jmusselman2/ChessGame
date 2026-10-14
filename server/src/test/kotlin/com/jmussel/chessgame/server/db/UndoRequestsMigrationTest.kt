@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.server.game.CommandResult
import com.jmussel.chessgame.server.game.GameCommandService
import com.jmussel.chessgame.server.series.seriesService
import org.flywaydb.core.Flyway
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * `V12` (`M21.19`, `D091`): undo requests are added to the game row without touching a game
 * in progress, which then reads as having no request, no answer and no recorded last change,
 * and can be asked about at once.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class UndoRequestsMigrationTest {
    @Test
    fun aGameInProgressBeforeV12CarriesOnWithUndoRequests() {
        DatabaseTestSupport.withEmptyDatabase { dataSource ->
            migrateTo(dataSource, "2")
            val (white, black, game) = aGameWithTwoMovesBeforeV5(dataSource)

            Migrations.migrate(dataSource)

            val database = Databases.connect(dataSource)
            val games = GameRepository(database)
            val stored = assertNotNull(games.load(game))
            assertEquals(2, stored.version, "the version is untouched")
            assertEquals(listOf(Move.of("e2", "e4"), Move.of("e7", "e5")), stored.game.moves)
            assertEquals(UndoRecord(), stored.undo, "no request and no answer")
            assertNull(stored.lastChange, "no change is recorded from before V12")

            val commands = GameCommandService(database, games, seriesService(database))

            assertIs<CommandResult.StaleVersion>(
                commands.requestUndo(white, game, 1),
                "a crossing needs a recorded last change, so an old version is just stale",
            )
            assertIs<CommandResult.Applied>(commands.requestUndo(white, game, 2))
            assertIs<CommandResult.Applied>(commands.acceptUndo(black, game, 3))
            assertEquals(ChessGame.newGame(), assertNotNull(games.load(game)).game)
        }
    }

    @Test
    fun aFinishedGameCannotHoldARequest() {
        DatabaseTestSupport.withEmptyDatabase { dataSource ->
            migrateTo(dataSource, "2")
            val (white, _, game) = aGameWithTwoMovesBeforeV5(dataSource)
            Migrations.migrate(dataSource)

            execute(
                dataSource,
                "update games set status = 'COMPLETE', result = 'DRAW', termination_reason = 'STALEMATE', " +
                    "ended_at = now() where id = '$game'",
            )

            assertFailsWith<SQLException> {
                execute(dataSource, "update games set undo_requested_by = '$white' where id = '$game'")
            }
        }
    }

    /** Two players, a series and a game at version 2 after `1. e4 e5`, in `V2`'s shape. */
    private fun aGameWithTwoMovesBeforeV5(dataSource: DataSource): Triple<Uuid, Uuid, Uuid> {
        val first =
            uuid(
                dataSource,
                "insert into users (auth_subject, username, username_normalized) values ('v12-a', 'Jordan', 'jordan') returning id",
            )
        val second =
            uuid(
                dataSource,
                "insert into users (auth_subject, username, username_normalized) values ('v12-b', 'Alex', 'alex') returning id",
            )
        val (low, high) = listOf(first, second).sorted()
        execute(dataSource, "insert into friendships (user_a_id, user_b_id) values ('$low', '$high')")
        val series = uuid(dataSource, "insert into game_series (user_a_id, user_b_id) values ('$low', '$high') returning id")

        val start = ChessGame.newGame()
        val afterE4 = ChessRules.applyMove(start, Move.of("e2", "e4"))
        val afterE5 = ChessRules.applyMove(afterE4, Move.of("e7", "e5"))

        val game =
            uuid(
                dataSource,
                "insert into games (series_id, sequence_number, white_user_id, black_user_id, state, version) " +
                    "values ('$series', 1, '$first', '$second', '${document(afterE5)}'::jsonb, 2) returning id",
            )
        execute(dataSource, "update game_series set current_game_id = '$game' where id = '$series'")
        execute(
            dataSource,
            "insert into moves (game_id, ply, side, from_square, to_square, position_before) values " +
                "('$game', 1, 'WHITE', 'e2', 'e4', '${document(start)}'::jsonb), " +
                "('$game', 2, 'BLACK', 'e7', 'e5', '${document(afterE4)}'::jsonb)",
        )

        return Triple(first, second, game)
    }

    private fun document(game: ChessGame): String =
        StorageJson.encodeToString(GameStateDocument.serializer(), GameStateDocument.of(game.state))

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
