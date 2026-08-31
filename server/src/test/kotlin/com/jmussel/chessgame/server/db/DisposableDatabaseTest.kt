package com.jmussel.chessgame.server.db

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which databases may be destroyed (`M15.5`).
 *
 * Pure, so it runs on every machine whether or not a test database is configured — the
 * rule is worth checking exactly where a mistake would be most expensive.
 */
class DisposableDatabaseTest {
    @Test
    fun aLoopbackDatabaseIsDisposable() {
        listOf(
            // What DatabaseConfig builds and the driver reports for the compose.yaml container.
            "jdbc:postgresql://localhost:55432/chessgame_test",
            // What CI's service container gives.
            "jdbc:postgresql://localhost:5432/chessgame_test",
            "jdbc:postgresql://127.0.0.1:55432/chessgame_dev",
            "jdbc:postgresql://[::1]:5432/chessgame_test",
            // The plain form .env.example uses, before DatabaseConfig rewrites it.
            "postgresql://chessgame:chessgame@localhost:55432/chessgame_test",
            // Host casing is not a way around the rule, nor is a missing port.
            "jdbc:postgresql://LOCALHOST:55432/chessgame_test",
            "jdbc:postgresql://localhost/chessgame_test",
        ).forEach { url ->
            assertTrue(DisposableDatabase.isDisposable(url), "$url should be disposable")
        }
    }

    @Test
    fun aDatabaseAnywhereElseIsNotDisposable() {
        listOf(
            // The two ways the beta's Supabase database is reachable (D035, M15.3).
            "jdbc:postgresql://aws-0-us-east-2.pooler.supabase.com:5432/postgres",
            "jdbc:postgresql://db.rkwymrtqayyyfahfgmbm.supabase.co:5432/postgres",
            // A hosted database somewhere else, and a machine on the same network.
            "jdbc:postgresql://dpg-abc123-a.oregon-postgres.render.com:5432/chessgame",
            "jdbc:postgresql://192.168.0.42:5432/chessgame",
            // A host that merely contains the allowed name is not the allowed name.
            "jdbc:postgresql://localhost.evil.example:5432/chessgame",
            "jdbc:postgresql://notlocalhost:5432/chessgame",
        ).forEach { url ->
            assertFalse(DisposableDatabase.isDisposable(url), "$url should not be disposable")
        }
    }

    @Test
    fun aUrlThisCannotReadFailsClosed() {
        listOf("", "not a url at all", "jdbc:postgresql:///chessgame", "postgresql://").forEach { url ->
            assertFalse(DisposableDatabase.isDisposable(url), "${url.ifEmpty { "<empty>" }} should not be disposable")
        }
    }

    @Test
    fun requiringADisposableDatabasePassesForLoopback() {
        DisposableDatabase.requireDisposable("jdbc:postgresql://localhost:55432/chessgame_test", override = null)
    }

    @Test
    fun requiringADisposableDatabaseRefusesAProductionLikeUrlAndSaysWhere() {
        val refusal =
            assertFailsWith<NotADisposableDatabaseException> {
                DisposableDatabase.requireDisposable(
                    "jdbc:postgresql://aws-0-us-east-2.pooler.supabase.com:5432/postgres",
                    override = null,
                )
            }

        assertTrue(
            refusal.message.contains("aws-0-us-east-2.pooler.supabase.com"),
            "the refusal should name the host it refused: ${refusal.message}",
        )
        assertTrue(
            refusal.message.contains(DisposableDatabase.OVERRIDE_VARIABLE),
            "the refusal should say how to override it deliberately: ${refusal.message}",
        )
    }

    @Test
    fun onlyTheExactOverrideValueOpensTheDoor() {
        val remote = "jdbc:postgresql://db.example.supabase.co:5432/postgres"

        // The one value that works.
        DisposableDatabase.requireDisposable(remote, override = DisposableDatabase.OVERRIDE_VALUE)

        // Everything anyone might already have exported does not.
        listOf(null, "", "true", "1", "yes", "TRUE", DisposableDatabase.OVERRIDE_VALUE.uppercase()).forEach { value ->
            assertFailsWith<NotADisposableDatabaseException>("override=$value should not be accepted") {
                DisposableDatabase.requireDisposable(remote, override = value)
            }
        }
    }
}
