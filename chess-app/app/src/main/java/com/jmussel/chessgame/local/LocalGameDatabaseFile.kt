package com.jmussel.chessgame.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.jmussel.chessgame.local.db.LocalGameDatabase

/**
 * The file the local-game database lives in, in the app's database directory.
 *
 * `backup_rules.xml` and `data_extraction_rules.xml` name it, with SQLite's companion
 * files, to keep it out of cloud backup and device transfer: a local game stays on this
 * installation (`D084`). Renaming it means renaming it there too.
 */
const val LOCAL_GAME_DATABASE_NAME: String = "local_games.db"

/**
 * Opens the local-game store on this device, creating the database, or migrating it to the
 * current schema version, as needed.
 *
 * Foreign keys are switched on for every connection, because SQLite leaves them off and
 * `local_moves` relies on its reference to `local_games`.
 */
fun openLocalGameStore(context: Context): LocalGameStore {
    val schema = LocalGameDatabase.Schema
    val driver =
        AndroidSqliteDriver(
            schema = schema,
            context = context.applicationContext,
            name = LOCAL_GAME_DATABASE_NAME,
            callback =
                object : AndroidSqliteDriver.Callback(schema) {
                    override fun onConfigure(db: SupportSQLiteDatabase) {
                        db.setForeignKeyConstraintsEnabled(true)
                    }
                },
        )
    return LocalGameStore(LocalGameDatabase(driver))
}
