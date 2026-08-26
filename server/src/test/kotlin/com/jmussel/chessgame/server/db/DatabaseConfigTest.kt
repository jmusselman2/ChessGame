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

    @Test
    fun readsNothingWhenTheVariableIsUnset() {
        assertEquals(null, DatabaseConfig.fromEnvironmentOrNull("CHESSGAME_DEFINITELY_UNSET_URL"))
        assertFailsWith<IllegalArgumentException> {
            DatabaseConfig.fromEnvironment("CHESSGAME_DEFINITELY_UNSET_URL")
        }
    }
}
