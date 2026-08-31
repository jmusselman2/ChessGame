package com.jmussel.chessgame.server.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class DatabaseConfigTest {
    @Test
    fun readsAPostgresUrl() {
        val config = DatabaseConfig.fromUrl("postgresql://chessgame:secret@localhost:55432/chessgame_dev")

        assertEquals("jdbc:postgresql://localhost:55432/chessgame_dev", config.jdbcUrl)
        assertEquals("chessgame", config.username)
        assertEquals("secret", config.password)
    }

    @Test
    fun acceptsBothPostgresSchemes() {
        assertEquals(
            DatabaseConfig.fromUrl("postgresql://u:p@host:5432/db").jdbcUrl,
            DatabaseConfig.fromUrl("postgres://u:p@host:5432/db").jdbcUrl,
        )
    }

    @Test
    fun fallsBackToTheDefaultPort() {
        assertEquals(
            "jdbc:postgresql://db.example.com:5432/chessgame",
            DatabaseConfig.fromUrl("postgresql://u:p@db.example.com/chessgame").jdbcUrl,
        )
    }

    @Test
    fun rejectsAUrlForAnotherDatabase() {
        assertFailsWith<IllegalArgumentException> { DatabaseConfig.fromUrl("mysql://u:p@host/db") }
    }

    @Test
    fun neverPrintsThePassword() {
        val rendered = DatabaseConfig.fromUrl("postgresql://chessgame:hunter2@localhost:55432/db").toString()

        assertFalse(rendered.contains("hunter2"), "the password must not appear in logs")
    }

    /**
     * The beta reaches Supabase through the Supavisor session pooler over TLS (`M15.3`),
     * and `sslmode` travels in the query string. Dropping it would connect anyway, in the
     * clear, which is the failure worth a test.
     */
    @Test
    fun keepsConnectionPropertiesFromTheQueryString() {
        val config =
            DatabaseConfig.fromUrl(
                "postgresql://postgres.abcdefghijklmnop:secret" +
                    "@aws-0-us-east-2.pooler.supabase.com:5432/postgres?sslmode=require",
            )

        assertEquals(
            "jdbc:postgresql://aws-0-us-east-2.pooler.supabase.com:5432/postgres?sslmode=require",
            config.jdbcUrl,
        )
        assertEquals("postgres.abcdefghijklmnop", config.username)
        assertEquals("secret", config.password)
    }

    @Test
    fun keepsEveryConnectionPropertyNotJustTheFirst() {
        val config =
            DatabaseConfig.fromUrl(
                "postgresql://u:p@db.example.com:5432/postgres?sslmode=verify-full&ApplicationName=chessgame",
            )

        assertEquals(
            "jdbc:postgresql://db.example.com:5432/postgres?sslmode=verify-full&ApplicationName=chessgame",
            config.jdbcUrl,
        )
    }

    @Test
    fun aUrlWithoutPropertiesGainsNoQuestionMark() {
        assertEquals(
            "jdbc:postgresql://localhost:55432/chessgame_dev",
            DatabaseConfig.fromUrl("postgresql://chessgame:secret@localhost:55432/chessgame_dev").jdbcUrl,
        )
    }

    /** A generated database password routinely contains characters a URL has to escape. */
    @Test
    fun decodesEscapedCharactersInTheCredentials() {
        val config = DatabaseConfig.fromUrl("postgresql://u%40name:p%40ss%3Aword%2F1@db.example.com:5432/postgres")

        assertEquals("u@name", config.username)
        assertEquals("p@ss:word/1", config.password)
    }

    @Test
    fun neverPrintsThePasswordWhenTheUrlIsRejected() {
        val refusal =
            assertFailsWith<IllegalArgumentException> {
                DatabaseConfig.fromUrl("mysql://chessgame:hunter2@db.example.com:5432/db")
            }

        assertFalse(
            refusal.message.orEmpty().contains("hunter2"),
            "a rejected URL must not echo the password: ${refusal.message}",
        )
    }

    @Test
    fun readsNothingWhenTheVariableIsUnset() {
        assertEquals(null, DatabaseConfig.fromEnvironmentOrNull("CHESSGAME_DEFINITELY_UNSET_URL"))
        assertFailsWith<IllegalArgumentException> {
            DatabaseConfig.fromEnvironment("CHESSGAME_DEFINITELY_UNSET_URL")
        }
    }
}
