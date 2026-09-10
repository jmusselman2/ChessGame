@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
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

/**
 * The status a friendship has once it has been approved.
 *
 * The only status MVP code ever writes: a friendship is usable the moment it is made and
 * there is no accept step (`D009`). `PENDING` and `DECLINED` exist in the schema for a
 * later approval flow and are never written today (`D047`).
 */
const val ACTIVE_FRIENDSHIP: String = "ACTIVE"

/** A friendship between two users, stored once with the lower id first. */
data class StoredFriendship(
    val userAId: Uuid,
    val userBId: Uuid,
    val status: String,
    val createdAt: Instant,
    val removedAt: Instant?,
) {
    /** Whether the two are friends right now: approved, and not since removed. */
    val isActive: Boolean
        get() = removedAt == null && status == ACTIVE_FRIENDSHIP

    /** The other person, given one of them. */
    fun otherThan(userId: Uuid): Uuid = if (userId == userAId) userBId else userAId
}

/** The status a series has while it is still being played. */
const val ACTIVE_SERIES: String = "ACTIVE"

/** What happened to a remove-friend request. */
sealed interface RemoveFriendResult {
    /** The friendship is over; [seriesMarkedToClose] says whether a series will close with it. */
    data class Removed(
        val seriesMarkedToClose: Boolean,
    ) : RemoveFriendResult

    /** They were not friends to begin with. */
    data object NotFriends : RemoveFriendResult
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
                // The row read as removed, but another add may revive it before this one
                // commits; only the caller whose update actually did it is told `Added`.
                existing.removedAt != null ->
                    reactivate(lower, higher)?.let(AddFriendResult::Added) ?: AddFriendResult.AlreadyFriends
                // A row that is neither active nor removed is one awaiting approval, which
                // nothing writes yet (`D047`). Failing loudly is deliberate: when that flow
                // arrives, its transition belongs here, and a quiet `AlreadyFriends` would
                // hide the omission instead of naming it.
                else -> error("Cannot add a friendship with status '${existing.status}'")
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
                        FriendshipsTable.removedAt.isNull() and
                        (FriendshipsTable.status eq ACTIVE_FRIENDSHIP)
                }.orderBy(FriendshipsTable.createdAt to SortOrder.ASC)
                .map { toFriendship(it).otherThan(userId) }
        }

    /**
     * Removes the friendship between [first] and [second] and marks their active series to
     * close after its current game.
     *
     * Both happen in one transaction, so the pair can never end up un-friended with a
     * series that will keep making rematches. Nothing is deleted and no game is touched:
     * the row stays for history, the current game plays on, and only the *next* automatic
     * rematch is disabled (`D013`). The series' own transition to `CLOSED` happens when
     * that game ends.
     */
    fun remove(
        first: Uuid,
        second: Uuid,
        at: Instant = Instant.now(),
    ): RemoveFriendResult {
        if (first == second) return RemoveFriendResult.NotFriends
        val (lower, higher) = order(first, second)

        return transaction(database) {
            val removed =
                FriendshipsTable.update(
                    {
                        (FriendshipsTable.userAId eq lower) and
                            (FriendshipsTable.userBId eq higher) and
                            FriendshipsTable.removedAt.isNull() and
                            (FriendshipsTable.status eq ACTIVE_FRIENDSHIP)
                    },
                ) { row ->
                    row[FriendshipsTable.removedAt] = at.atOffset(ZoneOffset.UTC)
                }

            if (removed == 0) {
                return@transaction RemoveFriendResult.NotFriends
            }

            val seriesClosing =
                GameSeriesTable.update(
                    {
                        (GameSeriesTable.userAId eq lower) and
                            (GameSeriesTable.userBId eq higher) and
                            (GameSeriesTable.status eq ACTIVE_SERIES)
                    },
                ) { row ->
                    row[GameSeriesTable.closeAfterCurrentGame] = true
                }

            RemoveFriendResult.Removed(seriesMarkedToClose = seriesClosing > 0)
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
            row[FriendshipsTable.status] = ACTIVE_FRIENDSHIP
            row[FriendshipsTable.createdAt] = now.atOffset(ZoneOffset.UTC)
        }

        return StoredFriendship(
            userAId = lower,
            userBId = higher,
            status = ACTIVE_FRIENDSHIP,
            createdAt = now,
            removedAt = null,
        )
    }

    /**
     * Revives the removed friendship between [lower] and [higher], or `null` when it was
     * no longer removed by the time this update committed.
     *
     * The `removed_at is not null` predicate is what makes that answer trustworthy under
     * concurrency. Two adds can both read the row as removed; PostgreSQL serialises their
     * updates, and re-evaluating the predicate on the row the winner left behind makes the
     * loser's update match nothing. Without it both updates succeed and both callers are
     * told they restored the friendship, when only one of them did.
     */
    private fun reactivate(
        lower: Uuid,
        higher: Uuid,
    ): StoredFriendship? {
        val revived =
            FriendshipsTable.update(
                {
                    (FriendshipsTable.userAId eq lower) and
                        (FriendshipsTable.userBId eq higher) and
                        FriendshipsTable.removedAt.isNotNull()
                },
            ) { row ->
                row[FriendshipsTable.removedAt] = null
                row[FriendshipsTable.status] = ACTIVE_FRIENDSHIP
            }

        if (revived == 0) return null

        return requireNotNull(findRow(lower, higher)) { "The friendship vanished while being restored" }
    }

    private fun toFriendship(row: ResultRow): StoredFriendship =
        StoredFriendship(
            userAId = row[FriendshipsTable.userAId],
            userBId = row[FriendshipsTable.userBId],
            status = row[FriendshipsTable.status],
            createdAt = row[FriendshipsTable.createdAt].toInstant(),
            removedAt = row[FriendshipsTable.removedAt]?.toInstant(),
        )
}
