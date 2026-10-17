@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.series

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.server.db.CLOSED_SERIES
import com.jmussel.chessgame.server.db.ChangeKind
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.GameSeriesRepository
import com.jmussel.chessgame.server.db.GameTypes
import com.jmussel.chessgame.server.db.Participant
import com.jmussel.chessgame.server.db.StoredGame
import com.jmussel.chessgame.server.db.StoredSeries
import com.jmussel.chessgame.server.db.TableRepository
import com.jmussel.chessgame.server.db.userIds
import com.jmussel.chessgame.server.game.GameCommandService
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** What asking to play a friend came to (`D095`). */
data class PlayOutcome(
    /** The pair's one active series. */
    val series: StoredSeries,
    /** Whether this request started it, with its first game, rather than finding it. */
    val started: Boolean,
)

/** What asking to leave a series came to (`D052`, `D095`). */
sealed interface LeaveOutcome {
    /**
     * The caller is out of the series, which is over. [endedNow] is `false` when it had already
     * ended — a retry, or the other player leaving first — so nothing was written this time.
     *
     * [currentGame] is the series' last game as the leave left it: finished as the leaver's
     * resignation when a move had ever been played in it, or as it already was. It is `null`
     * when the series has no game, which is so when the leave deleted a game nobody had moved
     * in; [deletedGame] is that game as it was.
     */
    data class Left(
        val series: StoredSeries,
        val currentGame: StoredGame?,
        val endedNow: Boolean,
        val deletedGame: StoredGame? = null,
    ) : LeaveOutcome

    /** No series with that id has the caller in it. */
    data object NoSuchSeries : LeaveOutcome
}

/**
 * Starting a series, leaving one, and settling what a finished game leaves behind: the next
 * game.
 *
 * "Play with this friend" is meant to be one tap that lands the player in a game
 * (`docs/PRODUCT.md`), so starting a series starts its first game with it. The colours for
 * that first game are a coin toss and every later game reverses them (`D014`).
 *
 * A chess pair has at most one active series (`D095`, superseding `D053`'s parallel series),
 * so Play opens it when there is one and starts it when there is not. The database refuses a
 * second one too (`V14`).
 *
 * **Lock order.** Every transaction that may end a game and settle its series locks the series
 * row before the game row ([lockGameWithSeries]), and so does [leave]. Taking the two in one
 * order is what lets a leave and a game-ending command race without deadlocking (`D095`).
 */
class SeriesService(
    private val database: Database,
    private val series: GameSeriesRepository,
    private val games: GameRepository,
    private val random: Random = Random.Default,
    private val tables: TableRepository = TableRepository(database),
) {
    /**
     * Play [friend]: open the pair's series, or start it with its first game when they have none.
     *
     * Both players tapping "Play" at the same moment is the case this has to survive. The
     * pair's table row is locked before deciding whether a series exists, so the second
     * request waits for the first and opens the series it started — one tap each, one series,
     * one game. The series, its first game, and the series pointing at that game are one
     * transaction, so a series is never left claiming a game that was not written.
     */
    fun play(
        caller: Uuid,
        friend: Uuid,
    ): PlayOutcome {
        val table = tables.findOrCreate(GameTypes.CHESS, listOf(Participant.user(caller), Participant.user(friend)))

        return transaction(database) {
            tables.lockForUpdate(table.id)

            series.activeAt(table.id)?.let { PlayOutcome(it, started = false) }
                ?: PlayOutcome(startFirstGame(series.create(table)), started = true)
        }
    }

    /**
     * [caller] leaves [seriesId], which ends the series and its current game together (`D095`).
     *
     * In one locked transaction:
     *
     * - a current game in which a move has ever been accepted ([StoredGame.hasStarted]) ends as
     *   the leaver's resignation, which the other player wins — even when every move has since
     *   been undone;
     * - a current game no move was ever accepted in is deleted, with no result;
     * - the series closes, and no rematch follows, because [settleAfter] gives a series that is
     *   no longer active none.
     *
     * Leaving is not resigning: an ordinary resignation ends one game and the active series goes
     * on to its rematch.
     *
     * Idempotent: leaving a series that has already ended changes nothing, so a retry sees its
     * own effect. The series row is locked first and the game row second, the order every
     * game-ending command takes ([lockGameWithSeries]), so a leave and a game ending at the same
     * moment happen one after the other: whichever is second finds the game finished or the
     * series closed, and either way there is one result, a closed series and no rematch.
     *
     * A series the caller is not in is reported as not existing, as a group is (`D049`).
     */
    fun leave(
        caller: Uuid,
        seriesId: Uuid,
    ): LeaveOutcome =
        transaction(database) {
            val current = series.findForUpdate(seriesId)

            if (current == null || caller !in current.participants.userIds) {
                return@transaction LeaveOutcome.NoSuchSeries
            }

            if (!current.isActive) {
                return@transaction LeaveOutcome.Left(current, current.currentGameId?.let(games::load), endedNow = false)
            }

            val game = current.currentGameId?.let(games::loadForUpdate)
            val closedAt = Instant.now()
            series.close(seriesId, at = closedAt)
            val closed = current.copy(status = CLOSED_SERIES, closedAt = closedAt)

            when {
                game == null || game.isComplete -> {
                    recordLeft(seriesId, game?.id, caller)
                    LeaveOutcome.Left(closed, game, endedNow = true)
                }

                game.hasStarted -> {
                    resignOnLeaving(game, caller)
                    recordLeft(seriesId, game.id, caller)
                    LeaveOutcome.Left(closed, games.load(game.id), endedNow = true)
                }

                else -> {
                    series.detachCurrentGame(seriesId)
                    games.delete(game.id)
                    recordLeft(seriesId, gameId = null, caller, deletedGameId = game.id)
                    LeaveOutcome.Left(
                        series = closed.copy(currentGameId = null),
                        currentGame = null,
                        endedNow = true,
                        deletedGame = game,
                    )
                }
            }
        }

    /**
     * The game [gameId], locked for a command that may end it, with its series row locked first
     * (`D095`). Inside a transaction; `null` when there is no such game.
     *
     * The series row comes first because [leave] takes it first and then needs the game's, and
     * [settleAfter], which a finishing command calls, needs the series row too. Taking the game
     * first would let a leave and a finishing move each hold one lock and wait for the other.
     */
    fun lockGameWithSeries(gameId: Uuid): StoredGame? {
        val seriesId = games.seriesIdOf(gameId) ?: return null
        series.findForUpdate(seriesId)
        return games.loadForUpdate(gameId)
    }

    /** Ends [game] as [leaver]'s resignation, recorded as a resignation is (`D018`, `D095`). */
    private fun resignOnLeaving(
        game: StoredGame,
        leaver: Uuid,
    ) {
        val side = if (leaver == game.whiteUserId) Side.WHITE else Side.BLACK

        games.save(
            id = game.id,
            expectedVersion = game.version,
            game = ChessRules.resign(game.game, side),
            auditEvent = GameCommandService.PLAYER_RESIGNED,
            actor = leaver,
            change = ChangeKind.RESIGNATION,
            auditDetail = buildJsonObject { put("leftSeries", true) },
        )
    }

    private fun recordLeft(
        seriesId: Uuid,
        gameId: Uuid?,
        caller: Uuid,
        deletedGameId: Uuid? = null,
    ) {
        series.recordEvent(
            seriesId = seriesId,
            gameId = gameId,
            actor = caller,
            type = SERIES_LEFT,
            payload =
                buildJsonObject {
                    put("userId", caller.toString())
                    deletedGameId?.let { put("deletedGameId", it.toString()) }
                },
        )
    }

    /** Whether [seriesId] is still active, for showing a game's player whether more will follow. */
    fun isActive(seriesId: Uuid): Boolean = series.isActive(seriesId)

    /**
     * Settles what a finished game leaves its series: the next game.
     *
     * Rematches are automatic (`D015`): a normally completed game in an active series is
     * followed by the next one without either player asking. Nothing about the friend graph
     * enters into it — removing a friend no longer ends a series (`D053`, superseding
     * `D013`) — so the only series that gets no rematch is one that is no longer active.
     * Returns the series as it stands afterwards, or `null` when there is no series to speak
     * of.
     *
     * The rematch happens exactly once however often this is asked. The series row is locked
     * for the transaction and the decision is made from what it says under that lock: a
     * series that is no longer active, or whose current game is no longer the finished one,
     * has already been settled and is handed back untouched. That covers a retry, a
     * duplicated command, and two transactions arriving together.
     *
     * [actor] is the player whose command finished the game, and so caused the rematch; its
     * audit event names them (`D078`).
     */
    fun settleAfter(
        finished: StoredGame,
        actor: Uuid? = null,
    ): StoredSeries? =
        transaction(database) {
            val current = series.findForUpdate(finished.seriesId) ?: return@transaction null

            when {
                !current.isActive -> current
                current.currentGameId != finished.id -> current
                else -> startRematch(current, finished, actor)
            }
        }

    private fun startRematch(
        series: StoredSeries,
        finished: StoredGame,
        actor: Uuid?,
    ): StoredSeries {
        // The next turn order comes from the series' seat rotation (`D050`). For chess that is
        // two rotating participants, so each game reverses the last one's colours (`D014`):
        // whoever had Black plays White in the rematch. A series from before the rotation was
        // recorded picks it up from the game that just ended, which gives the same reversal.
        val played = series.seatRotation ?: SeatCycle(baseOrder = finished.participants.rotating())
        val rotation = SeatRotation.next(played, random)

        val gameId =
            games.create(
                seriesId = series.id,
                sequenceNumber = finished.sequenceNumber + 1,
                participants = SeatRotation.turnOrder(rotation, finalTurn = series.participants.finalTurn()),
                game = ChessGame.newGame(),
            )

        this.series.attachCurrentGame(series.id, gameId)
        this.series.saveSeatRotation(series.id, rotation)
        this.series.recordEvent(
            seriesId = series.id,
            gameId = gameId,
            actor = actor,
            type = REMATCH_CREATED,
            payload =
                buildJsonObject {
                    put("previousGameId", finished.id.toString())
                    put("sequenceNumber", finished.sequenceNumber + 1)
                },
        )

        return series.copy(currentGameId = gameId, seatRotation = rotation)
    }

    /**
     * The series' first game, in the first cycle of its seat rotation (`D050`).
     *
     * The cycle's base order is drawn at random, which for chess's two seats is the coin toss
     * for White (`D014`). Seat order is turn order, so whoever the draw puts first plays White.
     */
    private fun startFirstGame(series: StoredSeries): StoredSeries {
        val rotation = SeatRotation.firstCycle(series.participants.rotating(), random)

        val gameId =
            games.create(
                seriesId = series.id,
                sequenceNumber = FIRST_GAME,
                participants = SeatRotation.turnOrder(rotation, finalTurn = series.participants.finalTurn()),
                game = ChessGame.newGame(),
            )

        this.series.attachCurrentGame(series.id, gameId)
        this.series.saveSeatRotation(series.id, rotation)

        return series.copy(currentGameId = gameId, seatRotation = rotation)
    }

    companion object {
        /** The audit event an automatic rematch records (`ARCHITECTURE.md` §9). */
        const val REMATCH_CREATED: String = "RematchCreated"

        /** The audit event a participant leaving a series records (`D052`). */
        const val SERIES_LEFT: String = "SeriesLeft"

        private const val FIRST_GAME = 1
    }
}
