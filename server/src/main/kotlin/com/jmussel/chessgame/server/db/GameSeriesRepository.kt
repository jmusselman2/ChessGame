@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
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

    /**
     * Marks [seriesId] to close once its current game finishes.
     *
     * Idempotent: marking a series that is already marked, or one that is already closed,
     * changes nothing and reports `false`.
     */
    fun markCloseAfterCurrentGame(seriesId: Uuid): Boolean =
        transaction(database) {
            GameSeriesTable.update(
                {
                    (GameSeriesTable.id eq seriesId) and
                        (GameSeriesTable.status eq ACTIVE_SERIES) and
                        (GameSeriesTable.closeAfterCurrentGame eq false)
                },
            ) { row ->
                row[GameSeriesTable.closeAfterCurrentGame] = true
            } > 0
        }

    /**
     * Closes [seriesId].
     *
     * Idempotent in the way that matters: closing an already-closed series changes nothing
     * and reports `false`, so a retried or duplicated end-of-game does not move `closedAt`
     * or reopen anything (`D012`).
     */
    fun close(
        seriesId: Uuid,
        at: Instant = Instant.now(),
    ): Boolean =
        transaction(database) {
            GameSeriesTable.update(
                { (GameSeriesTable.id eq seriesId) and (GameSeriesTable.status eq ACTIVE_SERIES) },
            ) { row ->
                row[GameSeriesTable.status] = CLOSED_SERIES
                row[GameSeriesTable.closedAt] = at.atOffset(ZoneOffset.UTC)
            } > 0
        }

    /**
     * Closes [seriesId] only if it was marked to close after its current game.
     *
     * This is what a finished game asks: "am I the last one?" A series that was not marked
     * stays active and goes on to its automatic rematch (`D015`).
     */
    fun closeIfMarked(
        seriesId: Uuid,
        at: Instant = Instant.now(),
    ): Boolean {
        val series = find(seriesId) ?: return false
        if (!series.isActive || !series.closeAfterCurrentGame) return false
        return close(seriesId, at)
    }

    /**
     * Appends one audit event about a series (`ARCHITECTURE.md` §9).
     *
     * Append-only: nothing ever updates or deletes these rows.
     */
    fun recordEvent(
        seriesId: Uuid,
        gameId: Uuid?,
        type: String,
        payload: JsonObject,
    ) {
        transaction(database) {
            GameEventsTable.insert { row ->
                row[GameEventsTable.seriesId] = seriesId
                row[GameEventsTable.gameId] = gameId
                row[GameEventsTable.type] = type
                row[GameEventsTable.payload] = payload
                row[GameEventsTable.createdAt] = Instant.now().atOffset(ZoneOffset.UTC)
            }
        }
    }

    /** The audit events recorded against [seriesId], oldest first. */
    fun auditEvents(seriesId: Uuid): List<StoredGameEvent> =
        transaction(database) {
            GameEventsTable
                .selectAll()
                .where { GameEventsTable.seriesId eq seriesId }
                .orderBy(GameEventsTable.id to SortOrder.ASC)
                .map { StoredGameEvent(type = it[GameEventsTable.type], payload = it[GameEventsTable.payload]) }
        }

    /** Points [seriesId] at [gameId] as its current game. */
    fun attachCurrentGame(
        seriesId: Uuid,
        gameId: Uuid,
    ) {
        transaction(database) {
            GameSeriesTable.update({ GameSeriesTable.id eq seriesId }) { row ->
                row[GameSeriesTable.currentGameId] = gameId
            }
        }
    }

    /**
     * The series with [id], locked against other transactions until this one ends.
     *
     * Used where a decision is made from what the series says and then written back — a
     * finished game asking whether it still owes a rematch, above all. Reading and writing
     * under the lock is what makes that decision happen once even if two transactions ask
     * at the same moment; without it both could read the same series and both act.
     *
     * Must be called inside a transaction, and holds the row until it commits.
     */
    fun findForUpdate(id: Uuid): StoredSeries? =
        transaction(database) {
            GameSeriesTable
                .selectAll()
                .where { GameSeriesTable.id eq id }
                .forUpdate()
                .singleOrNull()
                ?.let(::toSeries)
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
