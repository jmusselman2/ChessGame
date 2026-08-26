package com.jmussel.chessgame.server.db

import java.sql.SQLException

/** PostgreSQL's SQLSTATE for a unique constraint violation. */
const val UNIQUE_VIOLATION: String = "23505"

/**
 * Whether this failure is the database refusing a duplicate, rather than anything else.
 *
 * Several places rely on a unique constraint to settle a race — a username claim, a first
 * request from a new account, an active series — and each needs to tell "someone else won"
 * apart from "something is broken".
 */
fun Throwable.isUniqueViolation(): Boolean =
    generateSequence(this) { it.cause.takeIf { cause -> cause !== it } }
        .filterIsInstance<SQLException>()
        .any { it.sqlState == UNIQUE_VIOLATION }
