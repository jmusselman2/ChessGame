@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.user

import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.testModule
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

/** Independent evaluator coverage for M7's database-backed identity state. */
class M7AdversarialTest {
    private val tokens = TestTokens()

    @Test
    fun simultaneousFirstRequestsForOneAuthSubjectAllResolveToTheWinner() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val users = UserRepository(Databases.connect(dataSource))
            execute(
                dataSource,
                """
                create function evaluator_slow_user_insert() returns trigger language plpgsql as
                ${'$'}body${'$'}
                begin
                    perform pg_sleep(0.5);
                    return new;
                end
                ${'$'}body${'$'};
                create trigger evaluator_slow_user_insert
                    before insert on users
                    for each row execute function evaluator_slow_user_insert()
                """.trimIndent(),
            )

            val contenders = 5
            val barrier = CyclicBarrier(contenders)
            val pool = Executors.newFixedThreadPool(contenders)
            val attempts =
                try {
                    pool
                        .invokeAll(
                            (1..contenders).map {
                                Callable {
                                    barrier.await(5, TimeUnit.SECONDS)
                                    users.resolveBySubject("one-supabase-subject")
                                }
                            },
                        ).map { future -> runCatching { future.get() } }
                } finally {
                    pool.shutdownNow()
                }

            assertTrue(
                attempts.all { it.isSuccess },
                "every verified request should resolve: ${attempts.mapNotNull { it.exceptionOrNull()?.cause?.message }}",
            )
            assertEquals(1, attempts.mapNotNull { it.getOrNull()?.id }.distinct().size)
            assertEquals(1, count(dataSource, "select count(*) from users where auth_subject = 'one-supabase-subject'"))
        }
    }

    @Test
    fun failedLastSeenWriteDoesNotSuppressTheNextMeaningfulActivity() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val users = UserRepository(Databases.connect(dataSource))
            val user = users.resolveBySubject("last-seen-subject")
            var now = Instant.parse("2026-09-05T12:00:00Z")
            val tracker = LastSeenTracker(users, throttle = Duration.ofMinutes(5), clock = { now })

            execute(
                dataSource,
                """
                create function evaluator_reject_last_seen() returns trigger language plpgsql as
                ${'$'}body${'$'}
                begin
                    if new.last_seen_at is distinct from old.last_seen_at then
                        raise exception 'evaluator transient write failure';
                    end if;
                    return new;
                end
                ${'$'}body${'$'};
                create trigger evaluator_reject_last_seen
                    before update on users
                    for each row execute function evaluator_reject_last_seen()
                """.trimIndent(),
            )

            assertFails { tracker.record(user.id) }
            assertEquals(null, users.find(user.id)?.lastSeenAt)

            execute(dataSource, "drop trigger evaluator_reject_last_seen on users")
            now = now.plusSeconds(1)

            assertTrue(tracker.record(user.id), "the first successfully persisted activity must not be throttled")
            assertEquals(now, users.find(user.id)?.lastSeenAt)
        }
    }

    @Test
    fun failedLaterLastSeenWriteDoesNotThrottleARetryBehindTheOlderStoredTime() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val users = UserRepository(Databases.connect(dataSource))
            val user = users.resolveBySubject("later-last-seen-subject")
            var now = Instant.parse("2026-09-05T12:00:00Z")
            val tracker = LastSeenTracker(users, throttle = Duration.ofMinutes(5), clock = { now })

            assertTrue(tracker.record(user.id))
            val firstStoredTime = now
            now = now.plus(Duration.ofMinutes(6))
            installFailingLastSeenTrigger(dataSource)

            assertFails { tracker.record(user.id) }
            assertEquals(firstStoredTime, users.find(user.id)?.lastSeenAt)

            execute(dataSource, "drop trigger evaluator_reject_last_seen on users")
            now = now.plusSeconds(1)

            assertTrue(tracker.record(user.id), "a failed later write must not start a new throttle window")
            assertEquals(now, users.find(user.id)?.lastSeenAt)
        }
    }

    @Test
    fun failedAuthenticatedActivityIsPersistedByTheImmediateRetry() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val users = UserRepository(database)
            var now = Instant.parse("2026-09-05T12:00:00Z")
            val tracker = LastSeenTracker(users, throttle = Duration.ofMinutes(5), clock = { now })
            installFailingLastSeenTrigger(dataSource)

            testApplication {
                application { testModule(tokens.verifier(), database, tracker) }

                val failed =
                    client.get("/me") {
                        header("Authorization", "Bearer ${tokens.tokenFor("route-last-seen-subject")}")
                    }
                assertEquals(HttpStatusCode.InternalServerError, failed.status)

                execute(dataSource, "drop trigger evaluator_reject_last_seen on users")
                now = now.plusSeconds(1)

                val retry =
                    client.get("/me") {
                        header("Authorization", "Bearer ${tokens.tokenFor("route-last-seen-subject")}")
                    }
                assertEquals(HttpStatusCode.OK, retry.status)
            }

            val user = users.resolveBySubject("route-last-seen-subject")
            assertEquals(now, user.lastSeenAt, "the successful authenticated retry must record its activity")
        }
    }

    @Test
    fun exactThrottleBoundaryAndConcurrentActivityPreserveOneWritePerWindow() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val users = UserRepository(Databases.connect(dataSource))
            val user = users.resolveBySubject("concurrent-last-seen-subject")
            var now = Instant.parse("2026-09-05T12:00:00Z")
            val tracker = LastSeenTracker(users, throttle = Duration.ofMinutes(5), clock = { now })
            val contenders = 8
            val barrier = CyclicBarrier(contenders)
            val pool = Executors.newFixedThreadPool(contenders)

            val firstWindow =
                try {
                    pool
                        .invokeAll(
                            (1..contenders).map {
                                Callable {
                                    barrier.await(5, TimeUnit.SECONDS)
                                    tracker.record(user.id)
                                }
                            },
                        ).map { it.get() }
                } finally {
                    pool.shutdownNow()
                }

            assertEquals(1, firstWindow.count { it }, "one concurrent caller performs the write")
            assertEquals(now, users.find(user.id)?.lastSeenAt)

            now = now.plus(Duration.ofMinutes(5))
            assertTrue(tracker.record(user.id), "the exact throttle boundary starts the next window")
            assertEquals(now, users.find(user.id)?.lastSeenAt)
        }
    }

    private fun installFailingLastSeenTrigger(dataSource: DataSource) {
        execute(
            dataSource,
            """
            create function evaluator_reject_last_seen() returns trigger language plpgsql as
            ${'$'}body${'$'}
            begin
                if new.last_seen_at is distinct from old.last_seen_at then
                    raise exception 'evaluator transient write failure';
                end if;
                return new;
            end
            ${'$'}body${'$'};
            create trigger evaluator_reject_last_seen
                before update on users
                for each row execute function evaluator_reject_last_seen()
            """.trimIndent(),
        )
    }

    private fun execute(
        dataSource: DataSource,
        sql: String,
    ) {
        dataSource.connection.use { connection ->
            connection.createStatement().use { it.execute(sql) }
            connection.commit()
        }
    }

    private fun count(
        dataSource: DataSource,
        sql: String,
    ): Int =
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rows ->
                    check(rows.next())
                    rows.getInt(1)
                }
            }
        }
}
