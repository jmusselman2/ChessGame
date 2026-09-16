@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.series

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.GameSeriesRepository
import com.jmussel.chessgame.server.db.GameTypes
import com.jmussel.chessgame.server.db.Participant
import com.jmussel.chessgame.server.db.StoredGame
import com.jmussel.chessgame.server.db.StoredSeries
import com.jmussel.chessgame.server.db.TableRepository
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** What asking to play a friend came to (`D053`). */
sealed interface PlayOutcome {
    /** A new series was started, with its first game. */
    data class Started(
        val series: StoredSeries,
    ) : PlayOutcome

    /**
     * The pair already has active series, so nothing was started: the player is offered
     * opening one of these or starting another. Newest first.
     */
    data class Offered(
        val existing: List<StoredSeries>,
    ) : PlayOutcome
}

/**
 * Starting a series, and settling what a finished game leaves behind: the next game.
 *
 * "Play with this friend" is meant to be one tap that lands the player in a game
 * (`docs/PRODUCT.md`), so starting a series starts its first game with it. The colours for
 * that first game are a coin toss and every later game reverses them (`D014`).
 *
 * A pair may have several active series at once (`D053`), so Play never silently reuses one
 * and never silently duplicates one: when the pair already has a series it is offered back
 * ([PlayOutcome.Offered]) and a second series is started only when asked for.
 */
class SeriesService(
    private val database: Database,
    private val series: GameSeriesRepository,
    private val games: GameRepository,
    private val random: Random = Random.Default,
    private val tables: TableRepository = TableRepository(database),
) {
    /**
     * Play [friend]: start a series with its first game, or offer the series they already have.
     *
     * With [startAnother] the player has already been offered the existing ones and chosen a
     * new series, so one is started whatever exists.
     *
     * Both players tapping "Play" at the same moment is the case this has to survive. The
     * pair's table row is locked before deciding whether a series exists, so the second
     * request waits for the first, sees the series it started, and is offered it — one tap
     * each, one series, one game. The series, its first game, and the series pointing at that
     * game are one transaction, so a series is never left claiming a game that was not
     * written.
     */
    fun play(
        caller: Uuid,
        friend: Uuid,
        startAnother: Boolean = false,
    ): PlayOutcome {
        val table = tables.findOrCreate(GameTypes.CHESS, listOf(Participant.user(caller), Participant.user(friend)))

        return transaction(database) {
            tables.lockForUpdate(table.id)

            val active = series.activeAt(table.id)

            if (active.isNotEmpty() && !startAnother) {
                PlayOutcome.Offered(active)
            } else {
                PlayOutcome.Started(startFirstGame(series.create(table)))
            }
        }
    }

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
     */
    fun settleAfter(finished: StoredGame): StoredSeries? =
        transaction(database) {
            val current = series.findForUpdate(finished.seriesId) ?: return@transaction null

            when {
                !current.isActive -> current
                current.currentGameId != finished.id -> current
                else -> startRematch(current, finished)
            }
        }

    private fun startRematch(
        series: StoredSeries,
        finished: StoredGame,
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

        private const val FIRST_GAME = 1
    }
}
