@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import com.jmussel.chessgame.core.chess.Side
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.time.ZoneOffset
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** The game types this server knows. Chess is the only one registered (`D063`). */
object GameTypes {
    const val CHESS: String = "CHESS"
}

/**
 * How chess maps its two sides onto a game's seats.
 *
 * Seat order is turn order, and White moves first, so White is seat 0. This is the only place
 * that knows it; the participants relation itself says nothing about colours.
 */
object ChessSeats {
    const val WHITE: Int = 0
    const val BLACK: Int = 1

    fun sideOf(seat: Int): Side = if (seat == WHITE) Side.WHITE else Side.BLACK
}

/** A table as the database holds it: a game type and its exact participant set, in seat order. */
data class StoredTable(
    val id: Uuid,
    val gameType: String,
    val participants: List<Participant>,
)

/** Raised when a table is requested for a game type that is not registered. */
class UnknownGameTypeException(
    val gameType: String,
) : IllegalArgumentException("No game type $gameType is registered")

/** Raised when a table's size is outside what its game type seats (`D048`). */
class TableSizeOutOfRangeException(
    val gameType: String,
    val size: Int,
    val allowed: IntRange,
) : IllegalArgumentException("A $gameType table seats ${allowed.first}-${allowed.last} participants, not $size")

/**
 * Tables: the exact participant sets that series belong to (`D048`).
 *
 * A table is found by its participants rather than created per request, so the same people
 * always sit at the same table for a game type and a different set is a different table —
 * which is what makes series identity an exact-set match.
 *
 * How many participants a table seats is read from its game type's registration every time
 * ([participantRange]). There is no platform-wide count here and no assumption that it is 2:
 * chess's 2 is a row in `game_types`, and a game type that seats 2–4 would be another row
 * (`D063`).
 *
 * A participant is a user or a non-user participant, by kind (`D051`), and it is identified by
 * both: a user and a non-user participant that happen to share an id are two participants.
 * Seats are assigned in [canonicalOrder] — ref, then kind — which is the canonical order of the
 * set and carries no meaning of its own. Who moves first is a property of each game
 * ([GameParticipantsTable]), not of the table.
 */
class TableRepository(
    private val database: Database,
) {
    /**
     * The table of [gameType] seating exactly [participants], creating it if it does not exist.
     *
     * Refused before anything is written when a participant is named twice, when the game
     * type is not registered, or when the set's size is outside the game type's range. If two
     * requests race to create the same table, the database refuses the second insert and this
     * returns the table the other one created.
     *
     * Not to be called inside a transaction: a refused insert aborts the transaction it runs
     * in, and the recovery needs a fresh one.
     */
    fun findOrCreate(
        gameType: String,
        participants: Collection<Participant>,
    ): StoredTable {
        // Kind and ref together are the identity (`D051`): comparing refs alone would refuse a
        // valid table whose user and non-user participant share an id (`M19-01`).
        require(participants.toSet().size == participants.size) { "A table seats each participant once" }

        val allowed = participantRange(gameType) ?: throw UnknownGameTypeException(gameType)
        if (participants.size !in allowed) {
            throw TableSizeOutOfRangeException(gameType, participants.size, allowed)
        }

        val seats = participants.sortedWith(canonicalOrder)
        val key = participantSetOf(seats)

        find(gameType, key)?.let { return it }

        return try {
            transaction(database) { insert(gameType, key, seats) }
        } catch (e: Exception) {
            if (!e.isUniqueViolation()) throw e
            requireNotNull(find(gameType, key)) { "The table vanished after a conflict" }
        }
    }

    /** The table of [gameType] seating exactly these users: the case every table so far is. */
    @JvmName("findOrCreateForUsers")
    fun findOrCreate(
        gameType: String,
        users: Collection<Uuid>,
    ): StoredTable = findOrCreate(gameType, users.map(Participant::user))

    /** How many participants a table of [gameType] seats, or `null` if it is not registered. */
    fun participantRange(gameType: String): IntRange? =
        transaction(database) {
            GameTypesTable
                .selectAll()
                .where { GameTypesTable.id eq gameType }
                .singleOrNull()
                ?.let { it[GameTypesTable.minParticipants]..it[GameTypesTable.maxParticipants] }
        }

    /**
     * Locks the table with [id] until the surrounding transaction ends.
     *
     * What serialises decisions about a table's series now that nothing in the schema limits
     * how many it may have (`D053`): two requests that both ask "does this table have a series
     * yet?" take turns, so the second sees what the first created. Must be called inside a
     * transaction.
     */
    fun lockForUpdate(id: Uuid) {
        transaction(database) {
            TablesTable
                .select(TablesTable.id)
                .where { TablesTable.id eq id }
                .forUpdate()
                .toList()
        }
    }

    /** The table with [id], or `null`. */
    fun find(id: Uuid): StoredTable? =
        transaction(database) {
            TablesTable
                .selectAll()
                .where { TablesTable.id eq id }
                .singleOrNull()
                ?.let(::tableOf)
        }

    private fun find(
        gameType: String,
        key: String,
    ): StoredTable? =
        transaction(database) {
            TablesTable
                .selectAll()
                .where { (TablesTable.gameType eq gameType) and (TablesTable.participantSet eq key) }
                .singleOrNull()
                ?.let(::tableOf)
        }

    private fun tableOf(row: ResultRow): StoredTable {
        val id = row[TablesTable.id]
        return StoredTable(
            id = id,
            gameType = row[TablesTable.gameType],
            participants = participantsOfTables(listOf(id))[id].orEmpty(),
        )
    }

    /**
     * Inserts the table and its seats together, so a table whose rows disagree with its
     * [TablesTable.participantSet] is never visible. Both of the rules the schema cannot
     * check — the key matches the seats, and the size is in range — hold because this is
     * the only writer.
     */
    private fun insert(
        gameType: String,
        key: String,
        seats: List<Participant>,
    ): StoredTable {
        val id = Uuid.random()

        TablesTable.insert { row ->
            row[TablesTable.id] = id
            row[TablesTable.gameType] = gameType
            row[TablesTable.participantSet] = key
            row[TablesTable.createdAt] = Instant.now().atOffset(ZoneOffset.UTC)
        }

        seats.forEachIndexed { seat, participant ->
            TableParticipantsTable.insert { row ->
                row[TableParticipantsTable.tableId] = id
                row[TableParticipantsTable.seatIndex] = seat
                row[TableParticipantsTable.kind] = participant.kind.name
                row[TableParticipantsTable.userId] = participant.userId
                row[TableParticipantsTable.nonUserParticipantId] = participant.ref.takeIf { participant.userId == null }
            }
        }

        return StoredTable(id = id, gameType = gameType, participants = seats)
    }

    companion object {
        /**
         * The canonical order of a participant set: by ref, then by kind.
         *
         * The kind breaks the tie a ref alone leaves when a user and a non-user participant share
         * an id, so one set has one order whatever order it was named in (`M19-01`). Refs are
         * distinct in every set without such a tie, so their order — and every key already
         * stored, all of them sets of users — is unchanged.
         */
        val canonicalOrder: Comparator<Participant> = compareBy<Participant>({ it.ref }, { it.kind.name })

        /**
         * The canonical form of a participant set: each participant as `KIND:ref`, in
         * [canonicalOrder], comma-joined.
         *
         * `V5__tables_and_participants.sql` computes the same string for the pairs it carries
         * over, so a table made by the migration and one made here for the same people are the
         * same table.
         */
        fun participantSetOf(participants: Collection<Participant>): String =
            participants.sortedWith(canonicalOrder).joinToString(",") { it.toString() }
    }
}

/**
 * The participants of each of [tableIds], in seat order.
 *
 * One query however many tables are asked about. Must be called inside a transaction.
 */
fun participantsOfTables(tableIds: Collection<Uuid>): Map<Uuid, List<Participant>> {
    if (tableIds.isEmpty()) return emptyMap()

    return TableParticipantsTable
        .selectAll()
        .where { TableParticipantsTable.tableId inList tableIds }
        .orderBy(TableParticipantsTable.seatIndex to SortOrder.ASC)
        .groupBy({ it[TableParticipantsTable.tableId] }) { row ->
            participantOf(
                kind = row[TableParticipantsTable.kind],
                userId = row[TableParticipantsTable.userId],
                nonUserId = row[TableParticipantsTable.nonUserParticipantId],
            )
        }
}

/** The ids of every table [userId] sits at, as a subquery. */
fun tablesSeating(userId: Uuid): Query =
    TableParticipantsTable
        .select(TableParticipantsTable.tableId)
        .where { TableParticipantsTable.userId eq userId }

/**
 * The participants of each of [gameIds], in seat order.
 *
 * One query however many games are asked about. Must be called inside a transaction.
 */
fun participantsOfGames(gameIds: Collection<Uuid>): Map<Uuid, List<Participant>> {
    if (gameIds.isEmpty()) return emptyMap()

    return GameParticipantsTable
        .selectAll()
        .where { GameParticipantsTable.gameId inList gameIds }
        .orderBy(GameParticipantsTable.seatIndex to SortOrder.ASC)
        .groupBy({ it[GameParticipantsTable.gameId] }) { row ->
            participantOf(
                kind = row[GameParticipantsTable.kind],
                userId = row[GameParticipantsTable.userId],
                nonUserId = row[GameParticipantsTable.nonUserParticipantId],
            )
        }
}

/** A stored seat as a [Participant]; the schema guarantees exactly the right reference is set. */
private fun participantOf(
    kind: String,
    userId: Uuid?,
    nonUserId: Uuid?,
): Participant {
    val participantKind = ParticipantKind.valueOf(kind)
    val ref = if (participantKind == ParticipantKind.USER) userId else nonUserId

    return Participant(participantKind, requireNotNull(ref) { "A $kind seat names nobody" })
}
