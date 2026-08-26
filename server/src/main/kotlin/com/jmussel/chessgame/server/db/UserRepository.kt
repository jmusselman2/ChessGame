@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import com.jmussel.chessgame.server.user.Username
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
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
    fun find(id: Uuid): StoredUser? = transaction(database) { findById(id) }

    /** The user who owns [username], matched case-insensitively, or `null`. */
    fun findByUsername(username: String): StoredUser? =
        transaction(database) {
            UsersTable
                .selectAll()
                .where { UsersTable.usernameNormalized eq username.lowercase() }
                .singleOrNull()
                ?.let(::toUser)
        }

    /**
     * Claims [username] for [userId].
     *
     * The database's unique index on the normalized username is the final authority, so
     * two users claiming the same name at the same moment cannot both win — the loser gets
     * [ClaimUsernameResult.Taken] (`D007`). A username is never released, so a lost
     * anonymous account keeps its name reserved (`D008`), and changing a username is
     * outside the MVP.
     */
    fun claimUsername(
        userId: Uuid,
        username: Username,
    ): ClaimUsernameResult {
        val user = find(userId) ?: return ClaimUsernameResult.NoSuchUser

        user.username?.let { existing ->
            return if (existing.lowercase() == username.normalized) {
                ClaimUsernameResult.Claimed(user)
            } else {
                ClaimUsernameResult.AlreadyNamed(existing)
            }
        }

        // The update is its own transaction: a unique violation aborts it, and only the
        // loser of a race sees one.
        val updated =
            try {
                transaction(database) {
                    UsersTable.update({ (UsersTable.id eq userId) and UsersTable.username.isNull() }) { row ->
                        row[UsersTable.username] = username.value
                        row[UsersTable.usernameNormalized] = username.normalized
                    }
                }
            } catch (e: Exception) {
                if (e.isUniqueViolation()) return ClaimUsernameResult.Taken else throw e
            }

        return if (updated == 0) {
            // Someone claimed a name for this user between the read and the update.
            ClaimUsernameResult.AlreadyNamed(find(userId)?.username.orEmpty())
        } else {
            ClaimUsernameResult.Claimed(user.copy(username = username.value))
        }
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

    private fun findById(id: Uuid): StoredUser? =
        UsersTable
            .selectAll()
            .where { UsersTable.id eq id }
            .singleOrNull()
            ?.let(::toUser)

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

/** What happened to a username claim. */
sealed interface ClaimUsernameResult {
    /** The name is now theirs. */
    data class Claimed(
        val user: StoredUser,
    ) : ClaimUsernameResult

    /** Someone else already has that name. */
    data object Taken : ClaimUsernameResult

    /** This user already has a username; changes are outside the MVP. */
    data class AlreadyNamed(
        val username: String,
    ) : ClaimUsernameResult

    /** No such user. */
    data object NoSuchUser : ClaimUsernameResult
}

/** PostgreSQL's SQLSTATE for a unique constraint violation. */
private const val UNIQUE_VIOLATION = "23505"

/** Whether this failure is the database refusing a duplicate, rather than anything else. */
private fun Throwable.isUniqueViolation(): Boolean =
    generateSequence(this) { it.cause.takeIf { cause -> cause !== it } }
        .filterIsInstance<java.sql.SQLException>()
        .any { it.sqlState == UNIQUE_VIOLATION }
