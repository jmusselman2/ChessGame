@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.ZoneOffset
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A friendship between two users, stored once with the lower id first. */
data class StoredFriendship(
    val userAId: Uuid,
    val userBId: Uuid,
    val createdAt: Instant,
    val removedAt: Instant?,
) {
    val isActive: Boolean
        get() = removedAt == null

    /** The other person, given one of them. */
    fun otherThan(userId: Uuid): Uuid = if (userId == userAId) userBId else userAId
}

/** What happened to an add-friend request. */
sealed interface AddFriendResult {
    data class Added(
        val friendship: StoredFriendship,
    ) : AddFriendResult

    /** They were already friends; nothing changed. */
    data object AlreadyFriends : AddFriendResult

    /** You cannot be your own friend. */
    data object Yourself : AddFriendResult
}

/**
 * Friendships.
 *
 * A friendship is mutual the moment it is made (`D009`), so it is one row rather than two
 * directed edges, always written with the lower user id first. The database's ordering
 * check and primary key make a self-friendship, a duplicate, and a reversed duplicate all
 * impossible rather than merely unlikely.
 *
 * Removing a friend deactivates the row instead of deleting it, preserving history
 * (`D013`); adding the same friend again revives that row.
 */
class FriendshipRepository(
    private val database: Database,
) {
    /** Makes [first] and [second] friends, in whichever order they are given. */
    fun add(
        first: Uuid,
        second: Uuid,
    ): AddFriendResult {
        if (first == second) return AddFriendResult.Yourself

        val (lower, higher) = order(first, second)

        return transaction(database) {
            val existing = findRow(lower, higher)

            when {
                existing == null -> AddFriendResult.Added(insert(lower, higher))
                existing.isActive -> AddFriendResult.AlreadyFriends
                else -> AddFriendResult.Added(reactivate(lower, higher))
            }
        }
    }

    /** The friendship between [first] and [second], removed or not, or `null`. */
    fun find(
        first: Uuid,
        second: Uuid,
    ): StoredFriendship? {
        if (first == second) return null
        val (lower, higher) = order(first, second)
        return transaction(database) { findRow(lower, higher) }
    }

    /** Whether [first] and [second] are friends right now. */
    fun areFriends(
        first: Uuid,
        second: Uuid,
    ): Boolean = find(first, second)?.isActive == true

    /** Everyone [userId] is currently friends with, oldest friendship first. */
    fun friendsOf(userId: Uuid): List<Uuid> =
        transaction(database) {
            FriendshipsTable
                .selectAll()
                .where {
                    ((FriendshipsTable.userAId eq userId) or (FriendshipsTable.userBId eq userId)) and
                        FriendshipsTable.removedAt.isNull()
                }.orderBy(FriendshipsTable.createdAt to SortOrder.ASC)
                .map { toFriendship(it).otherThan(userId) }
        }

    /** Marks the friendship between [first] and [second] removed, keeping the row. */
    fun remove(
        first: Uuid,
        second: Uuid,
        at: Instant = Instant.now(),
    ): Boolean {
        if (first == second) return false
        val (lower, higher) = order(first, second)

        return transaction(database) {
            FriendshipsTable.update(
                {
                    (FriendshipsTable.userAId eq lower) and
                        (FriendshipsTable.userBId eq higher) and
                        FriendshipsTable.removedAt.isNull()
                },
            ) { row ->
                row[FriendshipsTable.removedAt] = at.atOffset(ZoneOffset.UTC)
            } > 0
        }
    }

    private fun order(
        first: Uuid,
        second: Uuid,
    ): Pair<Uuid, Uuid> = if (first < second) first to second else second to first

    private fun findRow(
        lower: Uuid,
        higher: Uuid,
    ): StoredFriendship? =
        FriendshipsTable
            .selectAll()
            .where { (FriendshipsTable.userAId eq lower) and (FriendshipsTable.userBId eq higher) }
            .singleOrNull()
            ?.let(::toFriendship)

    private fun insert(
        lower: Uuid,
        higher: Uuid,
    ): StoredFriendship {
        val now = Instant.now()

        FriendshipsTable.insert { row ->
            row[FriendshipsTable.userAId] = lower
            row[FriendshipsTable.userBId] = higher
            row[FriendshipsTable.createdAt] = now.atOffset(ZoneOffset.UTC)
        }

        return StoredFriendship(userAId = lower, userBId = higher, createdAt = now, removedAt = null)
    }

    private fun reactivate(
        lower: Uuid,
        higher: Uuid,
    ): StoredFriendship {
        FriendshipsTable.update(
            { (FriendshipsTable.userAId eq lower) and (FriendshipsTable.userBId eq higher) },
        ) { row ->
            row[FriendshipsTable.removedAt] = null
        }

        return requireNotNull(findRow(lower, higher)) { "The friendship vanished while being restored" }
    }

    private fun toFriendship(row: ResultRow): StoredFriendship =
        StoredFriendship(
            userAId = row[FriendshipsTable.userAId],
            userBId = row[FriendshipsTable.userBId],
            createdAt = row[FriendshipsTable.createdAt].toInstant(),
            removedAt = row[FriendshipsTable.removedAt]?.toInstant(),
        )
}
