package com.jmussel.chessgame.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.jmussel.chessgame.local.db.LocalGameDatabase
import java.util.Properties

/**
 * A local-game store in memory, for tests of the app around it: the real schema and queries,
 * through SQLDelight's JDBC driver, gone when the test is. Handing the same store to a
 * second view model is what an app restarted after process death finds.
 */
fun inMemoryLocalGameStore(): LocalGameStore = LocalGameStore(LocalGameDatabase(inMemoryLocalGameDriver()))

/** An empty local-game database in memory, with the current schema. */
fun inMemoryLocalGameDriver(): SqlDriver =
    JdbcSqliteDriver(
        url = JdbcSqliteDriver.IN_MEMORY,
        properties = Properties().apply { put("foreign_keys", "true") },
        schema = LocalGameDatabase.Schema,
    )
