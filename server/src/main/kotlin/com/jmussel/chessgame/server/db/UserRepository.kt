@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.ZoneOffset
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A user as the server knows them: internal id, auth subject, and the chosen username. */
data class StoredUser(
    val id: Uuid,
    val authSubject: String,
    val username: String?,
    val lastSeenAt: Instant?,
)

/**
 * Turning an authenticated caller into an internal user.
 *
 * The Supabase subject identifies the account; the internal `userId` is what everything
 * else in the database references, so it never changes even if the auth provider does.
 */
class UserRepository(
    private val database: Database,
) {
    /**
     * The internal user for [authSubject], creating the row the first time that account is
     * seen.
     *
     * Two simultaneous first requests from the same account cannot create two users: the
     * insert relies on the unique constraint on `auth_subject` and falls back to reading
     * the row the other request won with.
     */
    fun resolveBySubject(authSubject: String): StoredUser =
        transaction(database) {
            findBySubject(authSubject)
                ?: runCatching { insert(authSubject) }.getOrElse { failure ->
                    findBySubject(authSubject) ?: throw failure
                }
        }

    /** The user with [id], or `null`. */
    fun find(id: Uuid): StoredUser? =
        transaction(database) {
            UsersTable
                .selectAll()
                .where { UsersTable.id eq id }
                .singleOrNull()
                ?.let(::toUser)
        }

    /** Records that [id] was active at [at]. */
    fun touchLastSeen(
        id: Uuid,
        at: Instant = Instant.now(),
    ) {
        transaction(database) {
            UsersTable.update({ UsersTable.id eq id }) { row ->
                row[UsersTable.lastSeenAt] = at.atOffset(ZoneOffset.UTC)
            }
        }
    }

    private fun findBySubject(authSubject: String): StoredUser? =
        UsersTable
            .selectAll()
            .where { UsersTable.authSubject eq authSubject }
            .singleOrNull()
            ?.let(::toUser)

    private fun insert(authSubject: String): StoredUser {
        val id = Uuid.random()
        val now = Instant.now()

        UsersTable.insert { row ->
            row[UsersTable.id] = id
            row[UsersTable.authSubject] = authSubject
            row[UsersTable.createdAt] = now.atOffset(ZoneOffset.UTC)
        }

        return StoredUser(id = id, authSubject = authSubject, username = null, lastSeenAt = null)
    }

    private fun toUser(row: org.jetbrains.exposed.v1.core.ResultRow): StoredUser =
        StoredUser(
            id = row[UsersTable.id],
            authSubject = row[UsersTable.authSubject],
            username = row[UsersTable.username],
            lastSeenAt = row[UsersTable.lastSeenAt]?.toInstant(),
        )
}
