@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.user

import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.UserRepository
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
