package com.jmussel.chessgame.server.db

import java.sql.SQLException
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The initial schema: the tables exist and the constraints that protect the product rules
 * actually reject what they are meant to.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class InitialSchemaTest {
    private val tables =
        listOf("users", "friendships", "game_series", "games", "moves", "game_events")

    @Test
    fun everyTableExists() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            tables.forEach { table ->
                assertTrue(DatabaseTestSupport.tableExists(dataSource, table), "missing table $table")
            }
        }
    }

    @Test
    fun aUsernameIsUniqueOnceNormalized() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            insertUser(dataSource, subject = "auth-1", username = "Jordan")

            assertFailsWith<SQLException> {
                insertUser(dataSource, subject = "auth-2", username = "jordan")
            }
        }
    }

    @Test
    fun aUserCanExistBeforeChoosingAUsername() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val first = insertUser(dataSource, subject = "auth-1", username = null)
            val second = insertUser(dataSource, subject = "auth-2", username = null)

            assertTrue(first != second, "two unnamed users are still distinct")
        }
    }

    @Test
    fun theAuthSubjectIsUnique() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            insertUser(dataSource, subject = "auth-1", username = "alex")

            assertFailsWith<SQLException> {
                insertUser(dataSource, subject = "auth-1", username = "sam")
            }
        }
    }

    @Test
    fun aUsernameMustBeThreeToTwentyFourAllowedCharacters() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            assertFailsWith<SQLException> { insertUser(dataSource, "auth-short", "ab") }
            assertFailsWith<SQLException> { insertUser(dataSource, "auth-long", "a".repeat(25)) }
            assertFailsWith<SQLException> { insertUser(dataSource, "auth-space", "has space") }
        }
    }

    @Test
    fun aFriendshipCannotBeWithYourself() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val user = insertUser(dataSource, "auth-1", "jordan")

            assertFailsWith<SQLException> { insertFriendship(dataSource, user, user) }
        }
    }

    @Test
    fun aFriendshipCannotBeDuplicatedInEitherDirection() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val (first, second) = twoUsers(dataSource)
            val (lower, higher) = orderedPair(first, second)

            insertFriendship(dataSource, lower, higher)

            assertFailsWith<SQLException> { insertFriendship(dataSource, lower, higher) }
            assertFailsWith<SQLException> {
                // The reversed pair is rejected by the ordering constraint, so it can
                // never become a second row for the same two people.
                insertFriendship(dataSource, higher, lower)
            }
        }
    }

    @Test
    fun onlyOneActiveSeriesExistsPerPair() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val (first, second) = twoUsers(dataSource)
            val (lower, higher) = orderedPair(first, second)

            insertSeries(dataSource, lower, higher)

            assertFailsWith<SQLException> { insertSeries(dataSource, lower, higher) }
        }
    }

    @Test
    fun aClosedSeriesLeavesRoomForANewActiveOne() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val (first, second) = twoUsers(dataSource)
            val (lower, higher) = orderedPair(first, second)
            val closed = insertSeries(dataSource, lower, higher)

            execute(
                dataSource,
                "update game_series set status = 'CLOSED', closed_at = now() where id = ?::uuid",
                closed,
            )

            insertSeries(dataSource, lower, higher)

            assertEquals(2, count(dataSource, "select count(*) from game_series"))
        }
    }

    @Test
    fun gamesAreNumberedOncePerSeries() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val (white, black) = twoUsers(dataSource)
            val (lower, higher) = orderedPair(white, black)
            val series = insertSeries(dataSource, lower, higher)

            insertGame(dataSource, series, white, black, sequence = 1)
            insertGame(dataSource, series, black, white, sequence = 2)

            assertFailsWith<SQLException> {
                insertGame(dataSource, series, white, black, sequence = 2)
            }
        }
    }

    @Test
    fun aGameNeedsTwoDifferentPlayers() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val (white, black) = twoUsers(dataSource)
            val (lower, higher) = orderedPair(white, black)
            val series = insertSeries(dataSource, lower, higher)

            assertFailsWith<SQLException> {
                insertGame(dataSource, series, white, white, sequence = 1)
            }
        }
    }

    @Test
    fun aNewGameStartsAtVersionZeroAndInProgress() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val (white, black) = twoUsers(dataSource)
            val (lower, higher) = orderedPair(white, black)
            val series = insertSeries(dataSource, lower, higher)
            insertGame(dataSource, series, white, black, sequence = 1)

            assertEquals(
                1,
                count(dataSource, "select count(*) from games where version = 0 and status = 'IN_PROGRESS'"),
            )
        }
    }

    @Test
    fun aFinishedGameMustCarryItsResult() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val (white, black) = twoUsers(dataSource)
            val (lower, higher) = orderedPair(white, black)
            val series = insertSeries(dataSource, lower, higher)
            val game = insertGame(dataSource, series, white, black, sequence = 1)

            assertFailsWith<SQLException> {
                execute(dataSource, "update games set status = 'COMPLETE' where id = ?::uuid", game)
            }

            execute(
                dataSource,
                """
                update games
                   set status = 'COMPLETE',
                       result = 'WHITE_WINS',
                       termination_reason = 'CHECKMATE',
                       ended_at = now()
                 where id = ?::uuid
                """.trimIndent(),
                game,
            )

            assertEquals(1, count(dataSource, "select count(*) from games where status = 'COMPLETE'"))
        }
    }

    @Test
    fun aSeriesPointsAtItsCurrentGame() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val (white, black) = twoUsers(dataSource)
            val (lower, higher) = orderedPair(white, black)
            val series = insertSeries(dataSource, lower, higher)
            val game = insertGame(dataSource, series, white, black, sequence = 1)

            execute(
                dataSource,
                "update game_series set current_game_id = ?::uuid where id = ?::uuid",
                game,
                series,
            )

            assertEquals(
                1,
                count(dataSource, "select count(*) from game_series where current_game_id is not null"),
            )
            assertFailsWith<SQLException> {
                execute(
                    dataSource,
                    "update game_series set current_game_id = gen_random_uuid() where id = ?::uuid",
                    series,
                )
            }
        }
    }

    @Test
    fun eachPlyIsRecordedOncePerGame() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val game = aGame(dataSource)

            insertMove(dataSource, game, ply = 1, side = "WHITE", from = "e2", to = "e4")
            insertMove(dataSource, game, ply = 2, side = "BLACK", from = "e7", to = "e5")

            assertFailsWith<SQLException> {
                insertMove(dataSource, game, ply = 2, side = "BLACK", from = "c7", to = "c5")
            }
        }
    }

    @Test
    fun aMoveMustNameRealSquares() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val game = aGame(dataSource)

            assertFailsWith<SQLException> {
                insertMove(dataSource, game, ply = 1, side = "WHITE", from = "e2", to = "e9")
            }
            assertFailsWith<SQLException> {
                insertMove(dataSource, game, ply = 1, side = "WHITE", from = "e2", to = "e2")
            }
        }
    }

    @Test
    fun onlyTheFourPromotionChoicesAreAccepted() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val game = aGame(dataSource)

            insertMove(dataSource, game, ply = 1, side = "WHITE", from = "a7", to = "a8", promotion = "KNIGHT")

            assertFailsWith<SQLException> {
                insertMove(dataSource, game, ply = 2, side = "BLACK", from = "b2", to = "b1", promotion = "KING")
            }
        }
    }

    @Test
    fun deletingAGameTakesItsMovesWithIt() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val game = aGame(dataSource)
            insertMove(dataSource, game, ply = 1, side = "WHITE", from = "e2", to = "e4")

            execute(dataSource, "delete from games where id = ?::uuid", game)

            assertEquals(0, count(dataSource, "select count(*) from moves"))
        }
    }

    @Test
    fun auditEventsAreAppendedWithOrWithoutAGame() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val game = aGame(dataSource)

            execute(
                dataSource,
                "insert into game_events (game_id, type, payload) values (?::uuid, 'MoveMade', '{\"ply\": 1}'::jsonb)",
                game,
            )
            execute(dataSource, "insert into game_events (type) values ('FriendAdded')")

            assertEquals(2, count(dataSource, "select count(*) from game_events"))
            assertEquals(
                2,
                count(dataSource, "select count(distinct id) from game_events"),
            )
        }
    }

    // Helpers ---------------------------------------------------------------

    private fun insertUser(
        dataSource: DataSource,
        subject: String,
        username: String?,
    ): String =
        queryForString(
            dataSource,
            """
            insert into users (auth_subject, username, username_normalized)
            values (?, ?, lower(?))
            returning id::text
            """.trimIndent(),
            subject,
            username,
            username,
        )

    private fun twoUsers(dataSource: DataSource): Pair<String, String> =
        insertUser(dataSource, "auth-1", "jordan") to insertUser(dataSource, "auth-2", "alex")

    /** Friendships and series always store the lower id first. */
    private fun orderedPair(
        first: String,
        second: String,
    ): Pair<String, String> = if (first < second) first to second else second to first

    private fun insertFriendship(
        dataSource: DataSource,
        userA: String,
        userB: String,
    ) = execute(
        dataSource,
        "insert into friendships (user_a_id, user_b_id) values (?::uuid, ?::uuid)",
        userA,
        userB,
    )

    private fun insertSeries(
        dataSource: DataSource,
        userA: String,
        userB: String,
    ): String =
        queryForString(
            dataSource,
            "insert into game_series (user_a_id, user_b_id) values (?::uuid, ?::uuid) returning id::text",
            userA,
            userB,
        )

    private fun insertGame(
        dataSource: DataSource,
        series: String,
        white: String,
        black: String,
        sequence: Int,
    ): String =
        queryForString(
            dataSource,
            """
            insert into games (series_id, sequence_number, white_user_id, black_user_id, state)
            values (?::uuid, $sequence, ?::uuid, ?::uuid, '{}'::jsonb)
            returning id::text
            """.trimIndent(),
            series,
            white,
            black,
        )

    private fun aGame(dataSource: DataSource): String {
        val (white, black) = twoUsers(dataSource)
        val (lower, higher) = orderedPair(white, black)
        return insertGame(dataSource, insertSeries(dataSource, lower, higher), white, black, sequence = 1)
    }

    private fun insertMove(
        dataSource: DataSource,
        game: String,
        ply: Int,
        side: String,
        from: String,
        to: String,
        promotion: String? = null,
    ) = execute(
        dataSource,
        """
        insert into moves (game_id, ply, side, from_square, to_square, promotion, position_before)
        values (?::uuid, $ply, ?, ?, ?, ?, '{}'::jsonb)
        """.trimIndent(),
        game,
        side,
        from,
        to,
        promotion,
    )

    private fun execute(
        dataSource: DataSource,
        sql: String,
        vararg parameters: String?,
    ) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                parameters.forEachIndexed { index, value -> statement.setString(index + 1, value) }
                statement.execute()
            }
            connection.commit()
        }
    }

    private fun queryForString(
        dataSource: DataSource,
        sql: String,
        vararg parameters: String?,
    ): String =
        dataSource.connection.use { connection ->
            connection
                .prepareStatement(sql)
                .use { statement ->
                    parameters.forEachIndexed { index, value -> statement.setString(index + 1, value) }
                    statement.executeQuery().use { rows ->
                        check(rows.next()) { "no row returned" }
                        rows.getString(1)
                    }
                }.also { connection.commit() }
        }

    private fun count(
        dataSource: DataSource,
        sql: String,
    ): Int =
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rows ->
                    check(rows.next()) { "no row returned" }
                    rows.getInt(1)
                }
            }
        }
}
