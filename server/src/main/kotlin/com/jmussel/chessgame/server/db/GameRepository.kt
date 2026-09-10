@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.MoveRecord
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A game as the database holds it: the rules state plus the bookkeeping that is not part
 * of chess.
 */
data class StoredGame(
    val id: Uuid,
    val seriesId: Uuid,
    val sequenceNumber: Int,
    val whiteUserId: Uuid,
    val blackUserId: Uuid,
    val version: Long,
    val game: ChessGame,
    /** When the game was finalized, and `null` while it is still running. */
    val endedAt: OffsetDateTime? = null,
) {
    val isComplete: Boolean
        get() = game.isOver

    /** The other player in this game. */
    fun opponentOf(userId: Uuid): Uuid = if (userId == whiteUserId) blackUserId else whiteUserId
}

/** One recorded audit event (`ARCHITECTURE.md` §9). */
data class StoredGameEvent(
    val type: String,
    val payload: JsonObject,
)

/** Raised when a game has moved on since the caller read it (`D021`). */
class StaleGameVersionException(
    val gameId: Uuid,
    val expectedVersion: Long,
    val actualVersion: Long,
) : RuntimeException("Game $gameId is at version $actualVersion, not $expectedVersion")

/**
 * Reading and writing canonical game state.
 *
 * Every call runs in one transaction, so a game and its move history are always saved or
 * discarded together — a half-written game is never visible to another request. The
 * version guard implements `D021`: a write only lands on the version the caller read.
 */
class GameRepository(
    private val database: Database,
) {
    /** Inserts [game] as game [sequenceNumber] of [seriesId], and returns its id. */
    fun create(
        seriesId: Uuid,
        sequenceNumber: Int,
        whiteUserId: Uuid,
        blackUserId: Uuid,
        game: ChessGame,
    ): Uuid =
        transaction(database) {
            val id = Uuid.random()
            val now = Instant.now().atOffset(java.time.ZoneOffset.UTC)

            GamesTable.insert { row ->
                row[GamesTable.id] = id
                row[GamesTable.seriesId] = seriesId
                row[GamesTable.sequenceNumber] = sequenceNumber
                row[GamesTable.whiteUserId] = whiteUserId
                row[GamesTable.blackUserId] = blackUserId
                row[GamesTable.status] = statusOf(game)
                row[GamesTable.version] = 0
                row[GamesTable.sideToMove] = game.sideToMove.name
                row[GamesTable.state] = GameStateDocument.of(game.state)
                row[GamesTable.result] = game.result?.outcome?.name
                row[GamesTable.terminationReason] = game.result?.reason?.name
                row[GamesTable.createdAt] = now
                row[GamesTable.updatedAt] = now
                row[GamesTable.endedAt] = if (game.isOver) now else null
            }

            writeHistory(id, game)
            id
        }

    /**
     * The game with [id], or `null` when there is none.
     *
     * A game lives across two tables — the row and its move history — and READ COMMITTED
     * gives every statement its own snapshot, while a competing [save] commits both
     * together. Reading them one after the other can therefore take the row from before
     * that commit and the history from after it, and return a [StoredGame] that no
     * committed state ever matched: the old version and the old position, carrying the
     * move that has just been played (`M10-01`). `GET /games/{gameId}` is this read, and
     * `M10.2` promises a stale client can refresh from it — so a hybrid is exactly what it
     * must never answer with.
     *
     * So the read checks itself instead of taking a lock. Every accepted mutation
     * increments the version (`D021`), and versions only ever go up, so re-reading the
     * version after the history settles it: unchanged means nothing committed between the
     * two statements and the pair belongs together; changed means a [save] landed in
     * between, and the read is simply taken again — against the state that save left, so a
     * refresh returns the newer coherent state rather than the older one.
     *
     * Only a read that loses that race [COHERENT_READ_ATTEMPTS] times in a row falls back
     * to [loadForUpdate], which cannot be torn because a save has to wait for it. That
     * needs a commit inside every attempt, so ordinary display reads take no lock at all
     * and the fallback is what makes the loop terminate rather than something it is
     * expected to reach.
     */
    fun load(id: Uuid): StoredGame? {
        repeat(COHERENT_READ_ATTEMPTS) {
            when (val attempt = readIfCoherent(id)) {
                is ReadAttempt.Settled -> return attempt.game
                ReadAttempt.Torn -> Unit
            }
        }

        return loadForUpdate(id)
    }

    /**
     * The game with [id], with its row locked until the surrounding transaction ends.
     *
     * What a mutating command must read from. A game is stored across two tables — the row
     * and its move history — and READ COMMITTED gives every statement its own snapshot,
     * while a competing `save` commits both together. An unlocked read interleaved with
     * that commit can therefore take the row from before it and the history from after it,
     * producing a [StoredGame] whose `version` is the old one — so it passes the version
     * check — carrying the winner's moves. The command then decides from a position that
     * never existed and refuses on a *rule* (`NothingToUndo`, `IllegalMove`) rather than on
     * its version, which is the contract `D021` actually makes (`M16.7`).
     *
     * The lock makes the loser wait rather than read something torn: it sees the winner's
     * committed version, and the version check refuses it properly. Reads that only display
     * a game keep using [load] and take no lock.
     */
    fun loadForUpdate(id: Uuid): StoredGame? =
        transaction(database) {
            GamesTable
                .selectAll()
                .where { GamesTable.id eq id }
                .forUpdate(ForUpdateOption.ForUpdate)
                .singleOrNull()
                ?.let(::storedGameOf)
        }

    /** What one unlocked [load] attempt came to. */
    private sealed interface ReadAttempt {
        /** The row and its history belong to the same committed state — including "no game". */
        data class Settled(
            val game: StoredGame?,
        ) : ReadAttempt

        /** A save committed while the two were being read, so they may not belong together. */
        data object Torn : ReadAttempt
    }

    /**
     * One unlocked read, reported as [ReadAttempt.Torn] when it cannot vouch for itself.
     *
     * The version is re-read *after* the history, which is the whole check: it is the same
     * counter the guarded write moves ([save]), so a value that has not changed since the
     * row was read means no accepted mutation committed in between.
     */
    private fun readIfCoherent(id: Uuid): ReadAttempt =
        transaction(database) {
            val row =
                GamesTable
                    .selectAll()
                    .where { GamesTable.id eq id }
                    .singleOrNull()
                    ?: return@transaction ReadAttempt.Settled(null)

            val game = storedGameOf(row)

            if (currentVersion(id) != game.version) ReadAttempt.Torn else ReadAttempt.Settled(game)
        }

    /** The row, and the history stored beside it, as one game. */
    private fun storedGameOf(row: ResultRow): StoredGame =
        StoredGame(
            id = row[GamesTable.id],
            seriesId = row[GamesTable.seriesId],
            sequenceNumber = row[GamesTable.sequenceNumber],
            whiteUserId = row[GamesTable.whiteUserId],
            blackUserId = row[GamesTable.blackUserId],
            version = row[GamesTable.version],
            game = ChessGame(state = row[GamesTable.state].toGameState(), history = readHistory(row[GamesTable.id])),
            endedAt = row[GamesTable.endedAt],
        )

    /**
     * Replaces the stored game and its history with [game], moving the version on by one.
     *
     * [expectedVersion] must be the version the caller read; anything else means another
     * command got there first and this one is stale. The whole write — game row, move
     * history, audit event — is one transaction.
     *
     * The guarded `update ... where version = expectedVersion` is what actually settles a
     * race, not the read above it: two commands can both read the same version, and only
     * the one whose update matches a row may go on to rewrite the history (`D021`).
     *
     * A write that ends the game also finalizes it, in the same transaction: the result,
     * the termination reason, `ended_at`, and one `GameEnded` audit event are committed
     * with the move that caused them, or none of them are. Finalization happens exactly
     * once because only one write can move the row off the version it was read at, and
     * only the write that finds a running game and leaves a finished one finalizes it.
     */
    fun save(
        id: Uuid,
        expectedVersion: Long,
        game: ChessGame,
        auditEvent: String? = null,
    ): Long =
        transaction(database) {
            val current =
                GamesTable
                    .selectAll()
                    .where { GamesTable.id eq id }
                    .singleOrNull()
                    ?: throw NoSuchElementException("No game $id")

            val actualVersion = current[GamesTable.version]
            if (actualVersion != expectedVersion) {
                throw StaleGameVersionException(id, expectedVersion, actualVersion)
            }

            val nextVersion = expectedVersion + 1
            val now = Instant.now().atOffset(java.time.ZoneOffset.UTC)

            // A game that was already finished keeps the moment it ended; this write is
            // what ends it only if it found the game still running.
            val previouslyEndedAt = current[GamesTable.endedAt]
            val finalizing = game.isOver && previouslyEndedAt == null

            val updated =
                GamesTable.update({ (GamesTable.id eq id) and (GamesTable.version eq expectedVersion) }) { row ->
                    row[GamesTable.status] = statusOf(game)
                    row[GamesTable.version] = nextVersion
                    row[GamesTable.sideToMove] = game.sideToMove.name
                    row[GamesTable.state] = GameStateDocument.of(game.state)
                    row[GamesTable.result] = game.result?.outcome?.name
                    row[GamesTable.terminationReason] = game.result?.reason?.name
                    row[GamesTable.updatedAt] = now
                    row[GamesTable.endedAt] = if (game.isOver) previouslyEndedAt ?: now else null
                }

            if (updated == 0) {
                // Another command committed between the read and the update, so this one
                // never held the version it claimed and must not touch the history.
                throw StaleGameVersionException(id, expectedVersion, currentVersion(id))
            }

            MovesTable.deleteWhere { MovesTable.gameId eq id }
            writeHistory(id, game)

            auditEvent?.let { type ->
                recordEvent(id, type, now, buildJsonObject { put("version", nextVersion) })
            }

            if (finalizing) {
                recordEvent(
                    gameId = id,
                    type = GAME_ENDED,
                    at = now,
                    payload =
                        buildJsonObject {
                            put("version", nextVersion)
                            put("result", game.result?.outcome?.name)
                            put("terminationReason", game.result?.reason?.name)
                        },
                )
            }

            nextVersion
        }

    /** Every game of [seriesId], in the order they were played. */
    fun inSeries(seriesId: Uuid): List<StoredGame> =
        transaction(database) {
            GamesTable
                .selectAll()
                .where { GamesTable.seriesId eq seriesId }
                .orderBy(GamesTable.sequenceNumber to SortOrder.ASC)
                .map { row -> row[GamesTable.id] }
                .mapNotNull(::load)
        }

    /** The audit event types recorded against [gameId], oldest first. */
    fun auditTrail(gameId: Uuid): List<String> = auditEvents(gameId).map { it.type }

    /** The audit events recorded against [gameId], oldest first. */
    fun auditEvents(gameId: Uuid): List<StoredGameEvent> =
        transaction(database) {
            GameEventsTable
                .selectAll()
                .where { GameEventsTable.gameId eq gameId }
                .orderBy(GameEventsTable.id to SortOrder.ASC)
                .map { StoredGameEvent(type = it[GameEventsTable.type], payload = it[GameEventsTable.payload]) }
        }

    /** Appends one audit event. Append-only: nothing ever updates or deletes these rows. */
    private fun recordEvent(
        gameId: Uuid,
        type: String,
        at: OffsetDateTime,
        payload: JsonObject,
    ) {
        GameEventsTable.insert { row ->
            row[GameEventsTable.gameId] = gameId
            row[GameEventsTable.type] = type
            row[GameEventsTable.payload] = payload
            row[GameEventsTable.createdAt] = at
        }
    }

    /** The version the row holds right now, for reporting a lost race. */
    private fun currentVersion(id: Uuid): Long =
        GamesTable
            .selectAll()
            .where { GamesTable.id eq id }
            .singleOrNull()
            ?.get(GamesTable.version)
            ?: -1L

    private fun statusOf(game: ChessGame): String = if (game.isOver) "COMPLETE" else "IN_PROGRESS"

    companion object {
        /**
         * The audit event a finalized game records, once, whatever ended it (`D020`).
         *
         * Its payload carries the result and the termination reason, so the audit trail
         * answers "how did this game end" without reading the game row it describes.
         */
        const val GAME_ENDED: String = "GameEnded"

        /**
         * How many times an unlocked [load] re-reads before it stops racing and waits.
         *
         * Each attempt is only discarded when a save commits inside it, so losing three in
         * a row means the game is being written to continuously. Waiting for the row then
         * costs less than retrying, and it is what stops the loop being unbounded.
         */
        private const val COHERENT_READ_ATTEMPTS: Int = 3
    }

    private fun writeHistory(
        gameId: Uuid,
        game: ChessGame,
    ) {
        game.history.forEachIndexed { index, record ->
            MovesTable.insert { row ->
                row[MovesTable.id] = Uuid.random()
                row[MovesTable.gameId] = gameId
                row[MovesTable.ply] = index + 1
                row[MovesTable.side] = record.positionBefore.sideToMove.name
                row[MovesTable.fromSquare] = record.move.from.name
                row[MovesTable.toSquare] = record.move.to.name
                row[MovesTable.promotion] = record.move.promotion?.name
                row[MovesTable.positionBefore] = GameStateDocument.of(record.positionBefore)
                row[MovesTable.createdAt] = Instant.now().atOffset(java.time.ZoneOffset.UTC)
            }
        }
    }

    private fun readHistory(gameId: Uuid): List<MoveRecord> =
        MovesTable
            .selectAll()
            .where { MovesTable.gameId eq gameId }
            .orderBy(MovesTable.ply to SortOrder.ASC)
            .map { row ->
                MoveRecord(
                    move =
                        Move(
                            from = Square.parse(row[MovesTable.fromSquare]),
                            to = Square.parse(row[MovesTable.toSquare]),
                            promotion = row[MovesTable.promotion]?.let { PieceType.valueOf(it) },
                        ),
                    positionBefore = row[MovesTable.positionBefore].toGameState(),
                )
            }

    /** Reads the side stored against a ply, for tests and debugging. */
    fun storedSideAt(
        gameId: Uuid,
        ply: Int,
    ): Side? =
        transaction(database) {
            MovesTable
                .selectAll()
                .where { (MovesTable.gameId eq gameId) and (MovesTable.ply eq ply) }
                .singleOrNull()
                ?.let { Side.valueOf(it[MovesTable.side]) }
        }
}
