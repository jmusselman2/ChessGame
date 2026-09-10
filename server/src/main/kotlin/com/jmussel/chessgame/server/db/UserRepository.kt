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

/**
 * A user as the server knows them: internal id, auth subject, and the chosen username.
 *
 * The three activity timestamps answer three different questions and none substitutes for
 * another (`M19.11`, `database/migrations/V4__engagement_timestamps.sql`):
 * [lastSeenAt] is the throttled "recently around" marker (`D010`), accurate to five
 * minutes and written from any authenticated request; [lastLoginAt] is exact and records a
 * session starting; [lastActionAt] is exact and records a command being accepted.
 *
 * None of them reaches the API. `toCurrentUser` and `toSummaryOrNull` name the fields they
 * expose, and these are not among them (`ARCHITECTURE.md` §14).
 */
data class StoredUser(
    val id: Uuid,
    val authSubject: String,
    val username: String?,
    val lastSeenAt: Instant?,
    val lastLoginAt: Instant? = null,
    val lastActionAt: Instant? = null,
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

    /**
     * Records that [id] started a session at [at] (`M19.11`).
     *
     * Unthrottled, unlike [touchLastSeen]: a session start is a discrete, infrequent event
     * and an approximate one would answer nothing. `GET /me` is the only route that is a
     * session starting rather than a session being used — if it ever became a route a
     * client polled, this would need `LastSeenTracker`'s throttle for the reason `D010`
     * gives.
     */
    fun touchLastLogin(
        id: Uuid,
        at: Instant = Instant.now(),
    ) {
        transaction(database) {
            UsersTable.update({ UsersTable.id eq id }) { row ->
                row[UsersTable.lastLoginAt] = at.atOffset(ZoneOffset.UTC)
            }
        }
    }

    /**
     * Records that a command from [id] was accepted at [at] (`M19.11`).
     *
     * Called from inside the command's own transaction, so it commits with the mutation it
     * describes or not at all — a refused or lost command can never leave a timestamp
     * claiming an action the database did not take. It therefore does **not** open a
     * transaction of its own; `transaction(database)` here would join the caller's, and
     * being explicit about that is the point.
     */
    fun touchLastActionInTransaction(
        id: Uuid,
        at: Instant = Instant.now(),
    ) {
        UsersTable.update({ UsersTable.id eq id }) { row ->
            row[UsersTable.lastActionAt] = at.atOffset(ZoneOffset.UTC)
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
            lastLoginAt = row[UsersTable.lastLoginAt]?.toInstant(),
            lastActionAt = row[UsersTable.lastActionAt]?.toInstant(),
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
