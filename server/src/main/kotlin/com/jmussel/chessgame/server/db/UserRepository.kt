@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import com.jmussel.chessgame.server.user.Username
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.ZoneOffset
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A user as the server knows them: internal id and the chosen username.
 *
 * Which installations authenticate as this user is not part of it: many Supabase subjects
 * may map to one user (`D082`, `user_auth_subjects`).
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
    val username: String?,
    val lastSeenAt: Instant?,
    val lastLoginAt: Instant? = null,
    val lastActionAt: Instant? = null,
)

/**
 * Turning an authenticated caller into an internal user.
 *
 * The Supabase subject identifies one installation, and `user_auth_subjects` says which
 * user it authenticates as. The internal `userId` is what everything else in the database
 * references, so it never changes, whichever installations reach it (`D082`).
 */
class UserRepository(
    private val database: Database,
) {
    /**
     * The user [authSubject] authenticates as, creating a nameless one the first time that
     * installation is seen.
     *
     * The nameless user stands in for the installation until it claims a name, which either
     * names it or, when the name already has a user, replaces it with that user
     * ([claimUsername]). Until then it can do nothing that another user would see (`D045`,
     * `D081`).
     *
     * Two simultaneous first requests from one installation cannot map it twice. Each makes
     * its own nameless user, the primary key on the subject lets exactly one mapping in, and
     * the loser rolls its user back and reads the winner's.
     */
    fun resolveBySubject(authSubject: String): StoredUser =
        transaction(database) { findBySubject(authSubject) ?: mapToNewUser(authSubject) }
            ?: transaction(database) {
                checkNotNull(findBySubject(authSubject)) { "A concurrent first request mapped this subject, then it vanished" }
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
     * Everyone with a username who is in [within] (or anyone, when it is `null`) and not in
     * [excluded], most recently seen first, and at most [limit] of them.
     *
     * Only the "All users" testing aid asks this (`D071`). Ordered by [StoredUser.lastSeenAt]
     * so that accounts abandoned by a reinstall, whose names stay reserved (`D008`), sink
     * below the ones still in use. The newest account comes first among equals.
     */
    fun namedUsers(
        within: Set<Uuid>?,
        excluded: Set<Uuid>,
        limit: Int,
    ): List<StoredUser> {
        if (limit <= 0 || within?.isEmpty() == true) return emptyList()

        return transaction(database) {
            UsersTable
                .selectAll()
                .where {
                    val named = UsersTable.username.isNotNull() and (UsersTable.id notInList excluded)
                    if (within == null) named else named and (UsersTable.id inList within)
                }.orderBy(UsersTable.lastSeenAt to SortOrder.DESC_NULLS_LAST, UsersTable.createdAt to SortOrder.DESC)
                .limit(limit)
                .map(::toUser)
        }
    }

    /**
     * Claims [username] for the installations that authenticate as [userId] (`D082`).
     *
     * - [userId] is nameless and nobody has the name: [userId] takes it.
     * - [userId] is nameless and someone has the name, matched as `D007` matches names:
     *   every installation of [userId] is attached to that user, with no ownership check,
     *   and the nameless [userId] is deleted. No second user is ever made for a name.
     * - [userId] already has the name: nothing changes.
     * - [userId] has another name: refused. Changing a username is outside the MVP, and so
     *   is moving a named installation to another user (`D083`).
     *
     * [userId]'s row is locked for the claim, so two claims from one installation take
     * turns. Two nameless users claiming one new name at once both reach the update; the
     * database's unique index on the normalized name lets one win (`D007`), and the loser
     * goes round again, finds the winner, and attaches to it. A name is never released
     * (`D008`).
     */
    fun claimUsername(
        userId: Uuid,
        username: Username,
    ): ClaimUsernameResult {
        repeat(CLAIM_ATTEMPTS) {
            try {
                return transaction(database) {
                    // Retried here, deliberately, rather than by Exposed behind our back.
                    maxAttempts = 1
                    claimOnce(userId, username)
                }
            } catch (e: Exception) {
                if (!e.isUniqueViolation()) throw e
            }
        }
        error("Claiming a username lost a race $CLAIM_ATTEMPTS times running")
    }

    private fun claimOnce(
        userId: Uuid,
        username: Username,
    ): ClaimUsernameResult {
        val caller =
            UsersTable
                .selectAll()
                .where { UsersTable.id eq userId }
                .forUpdate()
                .singleOrNull()
                ?.let(::toUser)
                ?: return ClaimUsernameResult.NoSuchUser

        caller.username?.let { existing ->
            return if (existing.lowercase() == username.normalized) {
                ClaimUsernameResult.Claimed(caller)
            } else {
                ClaimUsernameResult.AlreadyNamed(existing)
            }
        }

        val holder =
            UsersTable
                .selectAll()
                .where { UsersTable.usernameNormalized eq username.normalized }
                .singleOrNull()
                ?.let(::toUser)

        if (holder == null) {
            UsersTable.update({ UsersTable.id eq userId }) { row ->
                row[UsersTable.username] = username.value
                row[UsersTable.usernameNormalized] = username.normalized
            }
            return ClaimUsernameResult.Claimed(caller.copy(username = username.value))
        }

        UserAuthSubjectsTable.update({ UserAuthSubjectsTable.userId eq userId }) { row ->
            row[UserAuthSubjectsTable.userId] = holder.id
        }
        // A nameless user owns nothing another row refers to: every route that would make
        // one refuses a nameless caller. The foreign keys would refuse this delete if that
        // ever stopped being true, and the claim with it.
        UsersTable.deleteWhere { (UsersTable.id eq userId) and UsersTable.username.isNull() }

        return ClaimUsernameResult.Claimed(holder, attached = true)
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
        UserAuthSubjectsTable
            .join(UsersTable, JoinType.INNER, onColumn = UserAuthSubjectsTable.userId, otherColumn = UsersTable.id)
            .select(UsersTable.columns)
            .where { UserAuthSubjectsTable.authSubject eq authSubject }
            .singleOrNull()
            ?.let(::toUser)

    /**
     * A new nameless user with [authSubject] mapped to it, or `null` after rolling both back
     * when a concurrent first request from the same installation mapped it first.
     */
    private fun JdbcTransaction.mapToNewUser(authSubject: String): StoredUser? {
        val id = Uuid.random()
        val now = Instant.now().atOffset(ZoneOffset.UTC)

        UsersTable.insert { row ->
            row[UsersTable.id] = id
            row[UsersTable.createdAt] = now
        }

        val mapped =
            UserAuthSubjectsTable
                .insertIgnore { row ->
                    row[UserAuthSubjectsTable.authSubject] = authSubject
                    row[UserAuthSubjectsTable.userId] = id
                    row[UserAuthSubjectsTable.createdAt] = now
                }.insertedCount

        if (mapped == 0) {
            rollback()
            return null
        }

        return StoredUser(id = id, username = null, lastSeenAt = null)
    }

    private fun toUser(row: org.jetbrains.exposed.v1.core.ResultRow): StoredUser =
        StoredUser(
            id = row[UsersTable.id],
            username = row[UsersTable.username],
            lastSeenAt = row[UsersTable.lastSeenAt]?.toInstant(),
            lastLoginAt = row[UsersTable.lastLoginAt]?.toInstant(),
            lastActionAt = row[UsersTable.lastActionAt]?.toInstant(),
        )
}

/** What happened to a username claim. */
sealed interface ClaimUsernameResult {
    /**
     * The installation is now [user], who has the name: either the caller took it or, when
     * [attached], it already belonged to [user] and the caller was attached to them.
     */
    data class Claimed(
        val user: StoredUser,
        val attached: Boolean = false,
    ) : ClaimUsernameResult

    /** This user already has a different username; changes are outside the MVP. */
    data class AlreadyNamed(
        val username: String,
    ) : ClaimUsernameResult

    /** No such user. */
    data object NoSuchUser : ClaimUsernameResult
}

/**
 * How many times a claim goes round after losing a race for a new name. The second attempt
 * finds the winner and attaches, so a third is only for a winner that vanished, which a
 * named user never does.
 */
private const val CLAIM_ATTEMPTS = 3
