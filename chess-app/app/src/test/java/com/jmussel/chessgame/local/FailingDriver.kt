package com.jmussel.chessgame.local

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement

/** What [FailingDriver] throws. */
class SimulatedFailure : RuntimeException("simulated write failure")

/** A driver that fails any statement starting with [failOn], as a full disk or a crash would. */
class FailingDriver(
    private val delegate: SqlDriver,
) : SqlDriver by delegate {
    var failOn: String? = null

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> {
        failOn?.let { if (sql.trimStart().startsWith(it)) throw SimulatedFailure() }
        return delegate.execute(identifier, sql, parameters, binders)
    }
}
