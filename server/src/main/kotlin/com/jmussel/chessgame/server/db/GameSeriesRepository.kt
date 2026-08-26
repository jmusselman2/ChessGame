@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.time.ZoneOffset
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** The status a series has once it is over. */
const val CLOSED_SERIES: String = "CLOSED"

/** A series of games between one pair of friends. */
data class StoredSeries(
    val id: Uuid,
    val userAId: Uuid,
    val userBId: Uuid,
    val status: String,
    val closeAfterCurrentGame: Boolean,
    val currentGameId: Uuid?,
    val createdAt: Instant,
    val closedAt: Instant?,
) {
    val isActive: Boolean
        get() = status == ACTIVE_SERIES

    /** The other player, given one of them. */
    fun opponentOf(userId: Uuid): Uuid = if (userId == userAId) userBId else userAId
}

/** Whether opening a series found one or made one. */
data class OpenedSeries(
    val series: StoredSeries,
    val created: Boolean,
)

/**
 * Series of games between friends.
 *
 * A pair has at most one `ACTIVE` series (`D011`), enforced by a partial unique index
 * rather than by hoping two requests do not arrive at once: "start a game with this
 * friend" opens the series that already exists instead of quietly creating a parallel one.
 * Closed series stay for history (`D012`).
 */
class GameSeriesRepository(
    private val database: Database,
) {
    /**
     * The pair's active series, opening the existing one or creating the first.
     *
     * If two requests race, the database refuses the second insert and this returns the
     * series the other one created.
     */
    fun openOrCreate(
        first: Uuid,
        second: Uuid,
    ): OpenedSeries {
        require(first != second) { "A series needs two different players" }
        val (lower, higher) = order(first, second)

        findActive(lower, higher)?.let { return OpenedSeries(it, created = false) }

        val created =
            try {
                transaction(database) { insert(lower, higher) }
            } catch (e: Exception) {
                if (!e.isUniqueViolation()) throw e
                // Another request created it a moment ago; that one is the series.
                val existing =
                    requireNotNull(findActive(lower, higher)) { "The active series vanished after a conflict" }
                return OpenedSeries(existing, created = false)
            }

        return OpenedSeries(created, created = true)
    }

    /** The pair's active series, or `null`. */
    fun findActive(
        first: Uuid,
        second: Uuid,
    ): StoredSeries? {
        if (first == second) return null
        val (lower, higher) = order(first, second)

        return transaction(database) {
            GameSeriesTable
                .selectAll()
                .where {
                    (GameSeriesTable.userAId eq lower) and
                        (GameSeriesTable.userBId eq higher) and
                        (GameSeriesTable.status eq ACTIVE_SERIES)
                }.singleOrNull()
                ?.let(::toSeries)
        }
    }

    /** The series with [id], active or closed, or `null`. */
    fun find(id: Uuid): StoredSeries? =
        transaction(database) {
            GameSeriesTable
                .selectAll()
                .where { GameSeriesTable.id eq id }
                .singleOrNull()
                ?.let(::toSeries)
        }

    /** Every series [userId] takes part in, newest first, closed ones included. */
    fun seriesFor(userId: Uuid): List<StoredSeries> =
        transaction(database) {
            GameSeriesTable
                .selectAll()
                .where { (GameSeriesTable.userAId eq userId) or (GameSeriesTable.userBId eq userId) }
                .orderBy(GameSeriesTable.createdAt to SortOrder.DESC)
                .map(::toSeries)
        }

    private fun order(
        first: Uuid,
        second: Uuid,
    ): Pair<Uuid, Uuid> = if (first < second) first to second else second to first

    private fun insert(
        lower: Uuid,
        higher: Uuid,
    ): StoredSeries {
        val id = Uuid.random()
        val now = Instant.now()

        GameSeriesTable.insert { row ->
            row[GameSeriesTable.id] = id
            row[GameSeriesTable.userAId] = lower
            row[GameSeriesTable.userBId] = higher
            row[GameSeriesTable.status] = ACTIVE_SERIES
            row[GameSeriesTable.closeAfterCurrentGame] = false
            row[GameSeriesTable.createdAt] = now.atOffset(ZoneOffset.UTC)
        }

        return StoredSeries(
            id = id,
            userAId = lower,
            userBId = higher,
            status = ACTIVE_SERIES,
            closeAfterCurrentGame = false,
            currentGameId = null,
            createdAt = now,
            closedAt = null,
        )
    }

    private fun toSeries(row: ResultRow): StoredSeries =
        StoredSeries(
            id = row[GameSeriesTable.id],
            userAId = row[GameSeriesTable.userAId],
            userBId = row[GameSeriesTable.userBId],
            status = row[GameSeriesTable.status],
            closeAfterCurrentGame = row[GameSeriesTable.closeAfterCurrentGame],
            currentGameId = row[GameSeriesTable.currentGameId],
            createdAt = row[GameSeriesTable.createdAt].toInstant(),
            closedAt = row[GameSeriesTable.closedAt]?.toInstant(),
        )
}
