@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inSubQuery
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

/** A series of games at one table (`D048`). */
data class StoredSeries(
    val id: Uuid,
    val tableId: Uuid,
    /** The table's participants, in its seat order. */
    val participants: List<Uuid>,
    val status: String,
    val closeAfterCurrentGame: Boolean,
    val currentGameId: Uuid?,
    val createdAt: Instant,
    val closedAt: Instant?,
) {
    val isActive: Boolean
        get() = status == ACTIVE_SERIES

    /** The other player at a two-participant table, given one of them. */
    fun opponentOf(userId: Uuid): Uuid = participants.single { it != userId }
}

/** Whether opening a series found one or made one. */
data class OpenedSeries(
    val series: StoredSeries,
    val created: Boolean,
    /**
     * Whether this open started the series' first game.
     *
     * Only the request that actually created the game sees this as `true`, so a second
     * request that finds the game the first made does not report starting it. It is what
     * tells the caller there is something the other player has not heard about yet.
     */
    val startedGame: Boolean = false,
)

/**
 * Series of games at a table.
 *
 * A series belongs to a table — the exact set of people playing it (`D048`) — so the same
 * people always reach the same series, and a different set reaches a different one.
 *
 * A table has at most one `ACTIVE` series (`D011`, until `M19.4`), enforced by a partial
 * unique index rather than by hoping two requests do not arrive at once: "start a game with
 * this friend" opens the series that already exists instead of quietly creating a parallel
 * one. Closed series stay for history (`D012`).
 */
class GameSeriesRepository(
    private val database: Database,
    private val tables: TableRepository = TableRepository(database),
) {
    /**
     * The active series of the [gameType] table seating exactly [participants], opening the
     * existing one or creating the table and the series as needed.
     *
     * The table checks the participant set against its game type before anything is written
     * ([TableRepository.findOrCreate]). If two requests race, the database refuses the second
     * insert and this returns the series the other one created.
     */
    fun openOrCreate(
        gameType: String,
        participants: Collection<Uuid>,
    ): OpenedSeries {
        val table = tables.findOrCreate(gameType, participants)

        findActive(table.id)?.let { return OpenedSeries(it, created = false) }

        val created =
            try {
                transaction(database) { insert(table) }
            } catch (e: Exception) {
                if (!e.isUniqueViolation()) throw e
                // Another request created it a moment ago; that one is the series.
                val existing =
                    requireNotNull(findActive(table.id)) { "The active series vanished after a conflict" }
                return OpenedSeries(existing, created = false)
            }

        return OpenedSeries(created, created = true)
    }

    /** The active series at [tableId], or `null`. */
    fun findActive(tableId: Uuid): StoredSeries? =
        transaction(database) {
            GameSeriesTable
                .selectAll()
                .where { (GameSeriesTable.tableId eq tableId) and (GameSeriesTable.status eq ACTIVE_SERIES) }
                .singleOrNull()
                ?.let(::toSeries)
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
                .where { GameSeriesTable.tableId inSubQuery tablesSeating(userId) }
                .orderBy(GameSeriesTable.createdAt to SortOrder.DESC)
                .map(::toSeries)
        }

    private fun insert(table: StoredTable): StoredSeries {
        val id = Uuid.random()
        val now = Instant.now()

        GameSeriesTable.insert { row ->
            row[GameSeriesTable.id] = id
            row[GameSeriesTable.tableId] = table.id
            row[GameSeriesTable.status] = ACTIVE_SERIES
            row[GameSeriesTable.closeAfterCurrentGame] = false
            row[GameSeriesTable.createdAt] = now.atOffset(ZoneOffset.UTC)
        }

        return StoredSeries(
            id = id,
            tableId = table.id,
            participants = table.participants,
            status = ACTIVE_SERIES,
            closeAfterCurrentGame = false,
            currentGameId = null,
            createdAt = now,
            closedAt = null,
        )
    }

    /** Must be called inside a transaction: the participants are read beside the row. */
    private fun toSeries(row: ResultRow): StoredSeries {
        val tableId = row[GameSeriesTable.tableId]

        return StoredSeries(
            id = row[GameSeriesTable.id],
            tableId = tableId,
            participants = participantsOfTables(listOf(tableId))[tableId].orEmpty(),
            status = row[GameSeriesTable.status],
            closeAfterCurrentGame = row[GameSeriesTable.closeAfterCurrentGame],
            currentGameId = row[GameSeriesTable.currentGameId],
            createdAt = row[GameSeriesTable.createdAt].toInstant(),
            closedAt = row[GameSeriesTable.closedAt]?.toInstant(),
        )
    }
}
