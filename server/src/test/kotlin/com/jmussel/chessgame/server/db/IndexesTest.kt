package com.jmussel.chessgame.server.db

import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `V10`'s indexes (`M17.12`): the friends lookup has one for each half, and the two that
 * only repeated a unique index's leading column are gone while those unique indexes stay.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class IndexesTest {
    /** Each index on [table], by name, with its definition. */
    private fun DataSource.indexesOn(table: String): Map<String, String> =
        connection.use { connection ->
            connection.prepareStatement(INDEXES_ON_TABLE).use { query ->
                query.setString(1, table)
                query.executeQuery().use { rows ->
                    buildMap { while (rows.next()) put(rows.getString(1), rows.getString(2)) }
                }
            }
        }

    @Test
    fun bothHalvesOfTheFriendsLookupHaveAnIndex() =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val indexes = dataSource.indexesOn("friendships")

            assertTrue(indexes.getValue("friendships_pkey").endsWith("(user_a_id, user_b_id)"), "user_a_id leads the primary key")
            assertTrue(indexes.getValue("friendships_user_b_id").endsWith("(user_b_id)"), "and user_b_id has its own")
        }

    @Test
    fun theRedundantIndexesAreGoneAndTheUniqueOnesStay() =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val moves = dataSource.indexesOn("moves")
            val games = dataSource.indexesOn("games")

            assertFalse("moves_game_id" in moves)
            assertFalse("games_series_id" in games)

            assertEquals(
                "CREATE UNIQUE INDEX moves_game_ply ON public.moves USING btree (game_id, ply)",
                moves["moves_game_ply"],
            )
            assertEquals(
                "CREATE UNIQUE INDEX games_series_sequence ON public.games USING btree (series_id, sequence_number)",
                games["games_series_sequence"],
            )
        }

    private companion object {
        const val INDEXES_ON_TABLE = "select indexname, indexdef from pg_indexes where schemaname = 'public' and tablename = ?"
    }
}
