@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.Move
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Independent evaluator stress coverage for the M6 persistence boundary. */
class M6AdversarialTest {
    @Test
    fun twoWritesCompetingForOneVersionCommitExactlyOneWholeOutcome() {
        withRepository { fixture ->
            val id = fixture.repository.create(fixture.series, 1, fixture.white, fixture.black, ChessGame.newGame())
            val candidates = listOf(played("e2e4"), played("d2d4"))

            // Keep the winning UPDATE open long enough for the competing transaction to
            // read the same old version before PostgreSQL rechecks its guarded UPDATE.
            fixture.execute(
                """
                create function evaluator_slow_game_update() returns trigger language plpgsql as
                ${'$'}body${'$'}
                begin
                    perform pg_sleep(0.25);
                    return new;
                end
                ${'$'}body${'$'};
                create trigger evaluator_slow_game_update
                    before update on games
                    for each row execute function evaluator_slow_game_update()
                """.trimIndent(),
            )

            val ready = CountDownLatch(candidates.size)
            val start = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(candidates.size)
            try {
                val futures =
                    candidates.map { candidate ->
                        executor.submit<Attempt> {
                            ready.countDown()
                            check(start.await(5, TimeUnit.SECONDS)) { "the competing saves never started" }
                            try {
                                Attempt(candidate, fixture.repository.save(id, 0, candidate, "MoveMade"), null)
                            } catch (failure: Throwable) {
                                Attempt(candidate, null, failure)
                            }
                        }
                    }

                assertTrue(ready.await(5, TimeUnit.SECONDS), "both saves should be ready")
                start.countDown()
                val attempts = futures.map { it.get(10, TimeUnit.SECONDS) }
                val winner = attempts.single { it.version != null }
                val loser = attempts.single { it.failure != null }

                assertEquals(1, winner.version)
                assertIs<StaleGameVersionException>(loser.failure)

                val stored = requireNotNull(fixture.repository.load(id))
                assertEquals(1, stored.version)
                assertEquals(winner.game, stored.game)
                assertEquals(1, stored.game.history.size)
                assertEquals(listOf("MoveMade"), fixture.repository.auditTrail(id))
            } finally {
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun aFailureDuringHistoryReplacementRollsBackStateVersionHistoryAndAudit() {
        withRepository { fixture ->
            val original = played("e2e4")
            val id = fixture.repository.create(fixture.series, 1, fixture.white, fixture.black, original)

            // Fail only after save has updated the game row, deleted the old history, and
            // reinserted its first ply. This makes atomicity observable at the late edge.
            fixture.execute(
                """
                create function evaluator_reject_second_ply() returns trigger language plpgsql as
                ${'$'}body${'$'}
                begin
                    if new.ply = 2 then
                        raise exception 'evaluator rejects second ply';
                    end if;
                    return new;
                end
                ${'$'}body${'$'};
                create trigger evaluator_reject_second_ply
                    before insert on moves
                    for each row execute function evaluator_reject_second_ply()
                """.trimIndent(),
            )

            assertFails {
                fixture.repository.save(id, expectedVersion = 0, game = played("e2e4", "e7e5"), auditEvent = "MoveMade")
            }

            val stored = requireNotNull(fixture.repository.load(id))
            assertEquals(0, stored.version)
            assertEquals(original, stored.game)
            assertEquals(listOf(Move.of("e2", "e4")), stored.game.moves)
            assertTrue(fixture.repository.auditTrail(id).isEmpty())
        }
    }

    private data class Attempt(
        val game: ChessGame,
        val version: Long?,
        val failure: Throwable?,
    )

    private class Fixture(
        val dataSource: DataSource,
        val database: Database,
        val repository: GameRepository,
        val white: Uuid,
        val black: Uuid,
        val series: Uuid,
    ) {
        fun execute(sql: String) {
            dataSource.connection.use { connection ->
                connection.createStatement().use { it.execute(sql) }
                connection.commit()
            }
        }
    }

    private fun withRepository(block: (Fixture) -> Unit) {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val repository = GameRepository(database)
            val white = Uuid.random()
            val black = Uuid.random()
            val series = Uuid.random()
            val now = Instant.now().atOffset(ZoneOffset.UTC)

            transaction(database) {
                listOf(white to "evaluator-white", black to "evaluator-black").forEach { (id, username) ->
                    UsersTable.insert { row ->
                        row[UsersTable.id] = id
                        row[UsersTable.authSubject] = "auth-$id"
                        row[UsersTable.username] = username
                        row[UsersTable.usernameNormalized] = username
                        row[UsersTable.createdAt] = now
                    }
                }
                val (userA, userB) = if (white < black) white to black else black to white
                GameSeriesTable.insert { row ->
                    row[GameSeriesTable.id] = series
                    row[GameSeriesTable.userAId] = userA
                    row[GameSeriesTable.userBId] = userB
                    row[GameSeriesTable.status] = "ACTIVE"
                    row[GameSeriesTable.closeAfterCurrentGame] = false
                    row[GameSeriesTable.createdAt] = now
                }
            }

            block(Fixture(dataSource, database, repository, white, black, series))
        }
    }

    private fun played(vararg moves: String): ChessGame =
        moves.fold(ChessGame.newGame()) { game, notation ->
            ChessRules.applyMove(game, Move.of(notation.take(2), notation.drop(2)))
        }
}
