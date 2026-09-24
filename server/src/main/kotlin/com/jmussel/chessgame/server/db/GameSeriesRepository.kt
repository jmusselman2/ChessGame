@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import com.jmussel.chessgame.server.series.SeatCycle
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
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
    val participants: List<Participant>,
    val status: String,
    val currentGameId: Uuid?,
    val createdAt: Instant,
    val closedAt: Instant?,
    /** Where the series is in its seat rotation (`D050`), or `null` before it has one. */
    val seatRotation: SeatCycle<Participant>? = null,
) {
    val isActive: Boolean
        get() = status == ACTIVE_SERIES

    /** The other player at a two-participant table, given one of them. */
    fun opponentOf(userId: Uuid): Uuid = participants.userIds.single { it != userId }
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
 * people always reach their own series, and a different set reaches different ones.
 *
 * A table may have several `ACTIVE` series at once (`D053`). Nothing in the schema limits it,
 * so the decisions that depend on how many there are take the table's row lock first
 * ([TableRepository.lockForUpdate]). Closed series stay for history (`D012`).
 */
class GameSeriesRepository(
    private val database: Database,
    private val tables: TableRepository = TableRepository(database),
) {
    /**
     * The newest active series of the [gameType] table seating exactly [participants], or a
     * new one when it has none.
     *
     * A storage primitive: whether a player should be *offered* an existing series instead is
     * the product's question, and `SeriesService.play` asks it. The table is locked while
     * deciding, so simultaneous calls agree on one series.
     */
    fun openOrCreate(
        gameType: String,
        participants: Collection<Participant>,
    ): OpenedSeries {
        val table = tables.findOrCreate(gameType, participants)

        return transaction(database) {
            tables.lockForUpdate(table.id)

            activeAt(table.id).firstOrNull()?.let { OpenedSeries(it, created = false) }
                ?: OpenedSeries(create(table), created = true)
        }
    }

    /** The newest active series of the table seating exactly these users, or a new one. */
    @JvmName("openOrCreateForUsers")
    fun openOrCreate(
        gameType: String,
        users: Collection<Uuid>,
    ): OpenedSeries = openOrCreate(gameType, users.map(Participant::user))

    /** Every active series at [tableId], newest first. */
    fun activeAt(tableId: Uuid): List<StoredSeries> =
        transaction(database) {
            GameSeriesTable
                .selectAll()
                .where { (GameSeriesTable.tableId eq tableId) and (GameSeriesTable.status eq ACTIVE_SERIES) }
                .orderBy(GameSeriesTable.createdAt to SortOrder.DESC)
                .map(::toSeries)
        }

    /**
     * A new active series at [table], with no game yet.
     *
     * Always a new one: a table may have several (`D053`). Callers deciding whether to make
     * one should hold the table's lock while they decide.
     */
    fun create(table: StoredTable): StoredSeries =
        transaction(database) {
            val id = Uuid.random()
            val now = Instant.now()

            GameSeriesTable.insert { row ->
                row[GameSeriesTable.id] = id
                row[GameSeriesTable.tableId] = table.id
                row[GameSeriesTable.status] = ACTIVE_SERIES
                row[GameSeriesTable.createdAt] = now.atOffset(ZoneOffset.UTC)
            }

            StoredSeries(
                id = id,
                tableId = table.id,
                participants = table.participants,
                status = ACTIVE_SERIES,
                currentGameId = null,
                createdAt = now,
                closedAt = null,
            )
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
     * Appends one audit event about a series (`ARCHITECTURE.md` §9).
     *
     * Append-only: nothing ever updates or deletes these rows.
     */
    fun recordEvent(
        seriesId: Uuid,
        gameId: Uuid?,
        actor: Uuid?,
        type: String,
        payload: JsonObject,
    ) {
        transaction(database) {
            GameEventsTable.insert { row ->
                row[GameEventsTable.seriesId] = seriesId
                row[GameEventsTable.gameId] = gameId
                row[GameEventsTable.actorId] = actor
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
                .map(::toStoredGameEvent)
        }

    /** Records where [seriesId] is in its seat rotation, beside the game that position produced. */
    fun saveSeatRotation(
        seriesId: Uuid,
        cycle: SeatCycle<Participant>,
    ) {
        transaction(database) {
            GameSeriesTable.update({ GameSeriesTable.id eq seriesId }) { row ->
                row[GameSeriesTable.seatRotation] = SeatRotationDocument.of(cycle)
            }
        }
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

    /** Whether the series with [id] is still active; `false` when it is closed or does not exist. */
    fun isActive(id: Uuid): Boolean =
        transaction(database) {
            GameSeriesTable
                .select(GameSeriesTable.status)
                .where { GameSeriesTable.id eq id }
                .singleOrNull()
                ?.get(GameSeriesTable.status) == ACTIVE_SERIES
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

    /** Must be called inside a transaction: the participants are read beside the row. */
    private fun toSeries(row: ResultRow): StoredSeries {
        val tableId = row[GameSeriesTable.tableId]

        return StoredSeries(
            id = row[GameSeriesTable.id],
            tableId = tableId,
            participants = participantsOfTables(listOf(tableId))[tableId].orEmpty(),
            status = row[GameSeriesTable.status],
            currentGameId = row[GameSeriesTable.currentGameId],
            createdAt = row[GameSeriesTable.createdAt].toInstant(),
            closedAt = row[GameSeriesTable.closedAt]?.toInstant(),
            seatRotation = row[GameSeriesTable.seatRotation]?.toCycle(),
        )
    }
}
