@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.friends

import com.jmussel.chessgame.server.api.SeriesSummary
import com.jmussel.chessgame.server.api.UserSummary
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.AddFriendResult
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.FriendshipsTable
import com.jmussel.chessgame.server.db.GameSeriesTable
import com.jmussel.chessgame.server.db.RemoveFriendResult
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.testModule
import com.jmussel.chessgame.server.user.Username
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Independent M8 probes for input, visibility, transaction, and race boundaries.
 *
 * These use the disposable PostgreSQL evaluator service because the behavior under test
 * is decided by PostgreSQL constraints, row locks, and transaction rollback.
 */
class M8AdversarialTest {
    private val tokens = TestTokens()
    private val json = Json { ignoreUnknownKeys = true }

    private class Fixture(
        val dataSource: DataSource,
        val database: Database,
        val users: UserRepository,
        val friendships: FriendshipRepository,
    ) {
        fun named(
            subject: String,
            username: String,
        ): Uuid {
            val user = users.resolveBySubject(subject)
            users.claimUsername(user.id, Username.of(username))
            return user.id
        }

        fun friendshipRows(): Int = transaction(database) { FriendshipsTable.selectAll().count().toInt() }
    }

    private fun withDatabase(block: (Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            block(Fixture(dataSource, database, UserRepository(database), FriendshipRepository(database)))
        }

    private fun withServer(block: suspend ApplicationTestBuilder.(Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val fixture = Fixture(dataSource, database, UserRepository(database), FriendshipRepository(database))

            testApplication {
                application { testModule(tokens.verifier(), database) }
                block(fixture)
            }
        }

    private fun HttpRequestBuilder.authorizedAs(subject: String) = header("Authorization", "Bearer ${tokens.tokenFor(subject)}")

    @Test
    fun aNamelessCallerCannotCreateAOneSidedInvisibleFriendship() {
        withServer { fixture ->
            val caller = fixture.users.resolveBySubject(CALLER).id
            val alex = fixture.named(FRIEND, "Alex")

            val response =
                client.post("/friends") {
                    authorizedAs(CALLER)
                    setBody("Alex")
                }

            assertEquals(emptyList(), friendsOf(FRIEND))
            assertFalse(
                fixture.friendships.areFriends(caller, alex),
                "${response.status} created an active friendship that Alex cannot list",
            )
            assertEquals(0, fixture.friendshipRows())
            assertFalse(response.status.isSuccess())
        }
    }

    @Test
    fun simultaneousInitialAddsHaveOneWinnerAndOneDuplicate() {
        withDatabase { fixture ->
            val jordan = fixture.named(CALLER, "Jordan")
            val alex = fixture.named(FRIEND, "Alex")
            waitOnFriendshipInsert(fixture.dataSource)

            val outcomes = raceAddsAfterBothPrechecks(fixture, jordan to alex, jordan to alex)
            val values = outcomes.mapNotNull { it.getOrNull() }

            assertEquals(2, outcomes.count { it.isSuccess }, outcomes.toString())
            assertEquals(1, values.count { it is AddFriendResult.Added })
            assertEquals(1, values.count { it == AddFriendResult.AlreadyFriends })
            assertEquals(1, fixture.friendshipRows())
        }
    }

    @Test
    fun simultaneousReversedAddsHaveOneWinnerAndOneDuplicate() {
        withDatabase { fixture ->
            val jordan = fixture.named(CALLER, "Jordan")
            val alex = fixture.named(FRIEND, "Alex")
            waitOnFriendshipInsert(fixture.dataSource)

            val outcomes = raceAddsAfterBothPrechecks(fixture, jordan to alex, alex to jordan)
            val values = outcomes.mapNotNull { it.getOrNull() }

            assertEquals(2, outcomes.count { it.isSuccess }, outcomes.toString())
            assertEquals(1, values.count { it is AddFriendResult.Added })
            assertEquals(1, values.count { it == AddFriendResult.AlreadyFriends })
            assertEquals(1, fixture.friendshipRows())
        }
    }

    @Test
    fun simultaneousReactivationsHaveOneWinnerAndOneDuplicate() {
        withDatabase { fixture ->
            val jordan = fixture.named(CALLER, "Jordan")
            val alex = fixture.named(FRIEND, "Alex")
            fixture.friendships.add(jordan, alex)
            fixture.friendships.remove(jordan, alex)
            waitOnFriendshipReactivation(fixture.dataSource)

            val outcomes =
                raceAddsAfterBothPrechecks(
                    fixture,
                    jordan to alex,
                    alex to jordan,
                    expectedAdvisoryWaiters = 1,
                )
            val values = outcomes.mapNotNull { it.getOrNull() }

            assertEquals(2, outcomes.count { it.isSuccess }, outcomes.toString())
            assertEquals(1, values.count { it is AddFriendResult.Added })
            assertEquals(1, values.count { it == AddFriendResult.AlreadyFriends })
            assertEquals(1, fixture.friendshipRows())
            assertTrue(fixture.friendships.areFriends(jordan, alex))
        }
    }

    @Test
    fun aSeriesMarkFailureRollsBackFriendRemovalAndRetrySucceeds() {
        withDatabase { fixture ->
            val jordan = fixture.named(CALLER, "Jordan")
            val alex = fixture.named(FRIEND, "Alex")
            fixture.friendships.add(jordan, alex)
            val seriesId = insertActiveSeries(fixture.database, jordan, alex)
            rejectSeriesMarks(fixture.dataSource)

            val failure = runCatching { fixture.friendships.remove(jordan, alex) }.exceptionOrNull()

            assertNotNull(failure)
            assertTrue(fixture.friendships.areFriends(jordan, alex), "the first mutation rolled back")
            assertFalse(seriesIsMarked(fixture.database, seriesId))

            execute(fixture.dataSource, "drop trigger evaluator_reject_series_mark on game_series")

            assertIs<RemoveFriendResult.Removed>(fixture.friendships.remove(jordan, alex))
            assertFalse(fixture.friendships.areFriends(jordan, alex))
            assertTrue(seriesIsMarked(fixture.database, seriesId))
        }
    }

    /**
     * The M8-03 scenario, kept for its orchestration and re-pointed at what the product now
     * says (`D046`).
     *
     * As written by the evaluator this asserted the opposite: that a series committing after
     * a friend removal must come out marked to close, because `POST /series` had checked the
     * friendship and that check could be raced. There is no longer a check to race — series
     * creation does not consult the friendship at all — so an unfriended pair holding an open
     * series is an accepted state rather than a violated invariant, and the assertion is
     * inverted rather than the race being closed.
     *
     * What it still proves is worth keeping: the interleaving is real and deterministic, the
     * removal commits, the series commits, and the result is the one `D046` accepts. If
     * someone later reintroduces a friendship gate or a locking protocol here, this fails and
     * sends them to the decision first.
     */
    @Test
    fun aSeriesCommittingAfterAFriendRemovalIsLeftOpenBecauseCreationNeverChecksTheFriendship() {
        withServer { fixture ->
            val jordan = fixture.named(CALLER, "Jordan")
            val alex = fixture.named(FRIEND, "Alex")
            fixture.friendships.add(jordan, alex)
            waitOnSeriesInsert(fixture.dataSource)

            val blocker = fixture.dataSource.connection
            blocker.createStatement().use { it.execute("select pg_advisory_lock($SERIES_INSERT_LOCK)") }

            try {
                coroutineScope {
                    val opening =
                        async(Dispatchers.IO) {
                            client.post("/series") {
                                authorizedAs(CALLER)
                                setBody("Alex")
                            }
                        }

                    withContext(Dispatchers.IO) {
                        awaitAdvisoryWaiters(fixture.dataSource, SERIES_INSERT_LOCK, expected = 1)
                    }

                    val removal = client.delete("/friends/Alex") { authorizedAs(CALLER) }

                    assertEquals(HttpStatusCode.OK, removal.status)
                    assertFalse(fixture.friendships.areFriends(jordan, alex))

                    blocker.createStatement().use { it.execute("select pg_advisory_unlock($SERIES_INSERT_LOCK)") }

                    val opened = opening.await()
                    assertTrue(opened.status.isSuccess())
                    json.decodeFromString<SeriesSummary>(opened.bodyAsText())

                    val pairSeries = activeSeriesFor(fixture.database, jordan, alex)
                    assertEquals(1, pairSeries.size)
                    assertFalse(
                        pairSeries.single()[GameSeriesTable.closeAfterCurrentGame],
                        "`D046`: creation does not check the friendship, so nothing marks this series",
                    )
                }
            } finally {
                blocker.createStatement().use { it.execute("select pg_advisory_unlock($SERIES_INSERT_LOCK)") }
                blocker.close()
            }
        }
    }

    private suspend fun ApplicationTestBuilder.friendsOf(subject: String): List<UserSummary> {
        val response = client.get("/friends") { authorizedAs(subject) }
        assertEquals(HttpStatusCode.OK, response.status)
        return json.decodeFromString(response.bodyAsText())
    }

    private fun raceAddsAfterBothPrechecks(
        fixture: Fixture,
        first: Pair<Uuid, Uuid>,
        second: Pair<Uuid, Uuid>,
        expectedAdvisoryWaiters: Int = 2,
    ): List<Result<AddFriendResult>> {
        val barrier = CyclicBarrier(2)
        val pool = Executors.newFixedThreadPool(2)
        val blocker = fixture.dataSource.connection
        blocker.createStatement().use { it.execute("select pg_advisory_lock($FRIENDSHIP_MUTATION_LOCK)") }

        return try {
            val attempts =
                listOf(first, second).map { pair ->
                    pool.submit(
                        Callable {
                            barrier.await(5, TimeUnit.SECONDS)
                            runCatching { fixture.friendships.add(pair.first, pair.second) }
                        },
                    )
                }

            awaitAdvisoryWaiters(
                fixture.dataSource,
                FRIENDSHIP_MUTATION_LOCK,
                expected = expectedAdvisoryWaiters,
            )
            if (expectedAdvisoryWaiters == 1) {
                awaitDatabaseLockWaiters(fixture.dataSource, expected = 2)
            }
            blocker.createStatement().use { it.execute("select pg_advisory_unlock($FRIENDSHIP_MUTATION_LOCK)") }
            attempts.map { it.get(10, TimeUnit.SECONDS) }
        } finally {
            blocker.createStatement().use { it.execute("select pg_advisory_unlock($FRIENDSHIP_MUTATION_LOCK)") }
            blocker.close()
            pool.shutdownNow()
        }
    }

    private fun waitOnFriendshipInsert(dataSource: DataSource) {
        execute(
            dataSource,
            """
            create function evaluator_wait_on_friendship_insert() returns trigger language plpgsql as
            ${'$'}${'$'}
            begin
                perform pg_advisory_xact_lock($FRIENDSHIP_MUTATION_LOCK);
                return new;
            end;
            ${'$'}${'$'};

            create trigger evaluator_wait_on_friendship_insert
            before insert on friendships
            for each row execute function evaluator_wait_on_friendship_insert();
            """.trimIndent(),
        )
    }

    private fun waitOnFriendshipReactivation(dataSource: DataSource) {
        execute(
            dataSource,
            """
            create function evaluator_wait_on_friendship_reactivation() returns trigger language plpgsql as
            ${'$'}${'$'}
            begin
                if old.removed_at is not null and new.removed_at is null then
                    perform pg_advisory_xact_lock($FRIENDSHIP_MUTATION_LOCK);
                end if;
                return new;
            end;
            ${'$'}${'$'};

            create trigger evaluator_wait_on_friendship_reactivation
            before update on friendships
            for each row execute function evaluator_wait_on_friendship_reactivation();
            """.trimIndent(),
        )
    }

    private fun rejectSeriesMarks(dataSource: DataSource) {
        execute(
            dataSource,
            """
            create function evaluator_reject_series_mark() returns trigger language plpgsql as
            ${'$'}${'$'}
            begin
                if new.close_after_current_game and not old.close_after_current_game then
                    raise exception 'evaluator rejects the series marker';
                end if;
                return new;
            end;
            ${'$'}${'$'};

            create trigger evaluator_reject_series_mark
            before update on game_series
            for each row execute function evaluator_reject_series_mark();
            """.trimIndent(),
        )
    }

    private fun waitOnSeriesInsert(dataSource: DataSource) {
        execute(
            dataSource,
            """
            create function evaluator_wait_on_series_insert() returns trigger language plpgsql as
            ${'$'}${'$'}
            begin
                perform pg_advisory_xact_lock($SERIES_INSERT_LOCK);
                return new;
            end;
            ${'$'}${'$'};

            create trigger evaluator_wait_on_series_insert
            before insert on game_series
            for each row execute function evaluator_wait_on_series_insert();
            """.trimIndent(),
        )
    }

    private fun awaitAdvisoryWaiters(
        dataSource: DataSource,
        lock: Int,
        expected: Int,
    ) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)

        while (System.nanoTime() < deadline) {
            val waiting =
                dataSource.connection.use { connection ->
                    connection.createStatement().use { statement ->
                        statement
                            .executeQuery(
                                "select count(*) from pg_locks " +
                                    "where locktype = 'advisory' and objid = $lock and not granted",
                            ).use { rows ->
                                rows.next()
                                rows.getInt(1)
                            }
                    }
                }

            if (waiting >= expected) return
            Thread.sleep(25)
        }

        error("$expected mutation(s) never reached evaluator advisory lock $lock")
    }

    private fun awaitDatabaseLockWaiters(
        dataSource: DataSource,
        expected: Int,
    ) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)

        while (System.nanoTime() < deadline) {
            val waiting =
                dataSource.connection.use { connection ->
                    connection.createStatement().use { statement ->
                        statement
                            .executeQuery(
                                "select count(*) from pg_stat_activity " +
                                    "where datname = current_database() and wait_event_type = 'Lock'",
                            ).use { rows ->
                                rows.next()
                                rows.getInt(1)
                            }
                    }
                }

            if (waiting >= expected) return
            Thread.sleep(25)
        }

        error("$expected mutations never reached their forced database lock waits")
    }

    private fun insertActiveSeries(
        database: Database,
        first: Uuid,
        second: Uuid,
    ): Uuid {
        val seriesId = Uuid.random()
        val (lower, higher) = if (first < second) first to second else second to first

        transaction(database) {
            GameSeriesTable.insert { row ->
                row[GameSeriesTable.id] = seriesId
                row[GameSeriesTable.userAId] = lower
                row[GameSeriesTable.userBId] = higher
                row[GameSeriesTable.status] = "ACTIVE"
                row[GameSeriesTable.closeAfterCurrentGame] = false
                row[GameSeriesTable.createdAt] = Instant.now().atOffset(java.time.ZoneOffset.UTC)
            }
        }
        return seriesId
    }

    private fun seriesIsMarked(
        database: Database,
        seriesId: Uuid,
    ): Boolean =
        transaction(database) {
            GameSeriesTable
                .selectAll()
                .where { GameSeriesTable.id eq seriesId }
                .single()[GameSeriesTable.closeAfterCurrentGame]
        }

    private fun activeSeriesFor(
        database: Database,
        first: Uuid,
        second: Uuid,
    ) = transaction(database) {
        val (lower, higher) = if (first < second) first to second else second to first
        GameSeriesTable
            .selectAll()
            .where {
                (GameSeriesTable.userAId eq lower) and
                    (GameSeriesTable.userBId eq higher) and
                    (GameSeriesTable.status eq "ACTIVE")
            }.toList()
    }

    private fun execute(
        dataSource: DataSource,
        sql: String,
    ) {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement -> statement.execute(sql) }
            if (!connection.autoCommit) connection.commit()
        }
    }

    private companion object {
        const val CALLER = "auth-jordan"
        const val FRIEND = "auth-alex"
        const val SERIES_INSERT_LOCK = 8004
        const val FRIENDSHIP_MUTATION_LOCK = 8005
    }
}
