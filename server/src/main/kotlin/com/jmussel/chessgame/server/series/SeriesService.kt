@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.series

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.server.db.CLOSED_SERIES
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.GameSeriesRepository
import com.jmussel.chessgame.server.db.GameTypes
import com.jmussel.chessgame.server.db.StoredGame
import com.jmussel.chessgame.server.db.StoredSeries
import com.jmussel.chessgame.server.db.TableRepository
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
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
 * Starting a series, and settling what a finished game leaves behind.
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
        val table = tables.findOrCreate(GameTypes.CHESS, listOf(caller, friend))

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
     * Settles what a finished game leaves its series: the next game, or the end of it.
     *
     * Rematches are automatic (`D015`): a normally completed game in an active series is
     * followed by the next one without either player asking. A series marked to close
     * after its current game gets no rematch and closes here instead (`D013`) — the last
     * game was allowed to finish, and now the series is over. Returns the series as it
     * stands afterwards, or `null` when there is no series to speak of.
     *
     * Either outcome happens exactly once however often this is asked. The series row is
     * locked for the transaction and the decision is made from what it says under that
     * lock: a series that is no longer active, or whose current game is no longer the
     * finished one, has already been settled and is handed back untouched. That covers a
     * retry, a duplicated command, and two transactions arriving together.
     */
    fun settleAfter(finished: StoredGame): StoredSeries? =
        transaction(database) {
            val current = series.findForUpdate(finished.seriesId) ?: return@transaction null

            when {
                !current.isActive -> current
                current.currentGameId != finished.id -> current
                current.closeAfterCurrentGame -> closeSeries(current, finished)
                else -> startRematch(current, finished)
            }
        }

    /**
     * Closes a series whose last game has just finished (`D013`).
     *
     * The game keeps its place as the series' current game: it is the last one played, and
     * a closed series stays readable as history (`D012`). Closing is guarded on the series
     * still being active, so a repeat leaves the first `closedAt` where it was.
     */
    private fun closeSeries(
        series: StoredSeries,
        finished: StoredGame,
    ): StoredSeries {
        val closedAt = Instant.now()

        if (!this.series.close(series.id, closedAt)) return series

        this.series.recordEvent(
            seriesId = series.id,
            gameId = finished.id,
            type = SERIES_CLOSED,
            payload =
                buildJsonObject {
                    put("lastGameId", finished.id.toString())
                    put("reason", CLOSE_AFTER_CURRENT_GAME)
                },
        )

        return series.copy(status = CLOSED_SERIES, closedAt = closedAt)
    }

    private fun startRematch(
        series: StoredSeries,
        finished: StoredGame,
    ): StoredSeries {
        // Colours alternate from one game to the next (`D014`): whoever had Black plays
        // White in the rematch. Taken from the game that just ended rather than counted
        // from the sequence number, so the series stays consistent even if a game is ever
        // created out of band.
        val gameId =
            games.create(
                seriesId = series.id,
                sequenceNumber = finished.sequenceNumber + 1,
                participants = listOf(finished.blackUserId, finished.whiteUserId),
                game = ChessGame.newGame(),
            )

        this.series.attachCurrentGame(series.id, gameId)
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

        return series.copy(currentGameId = gameId)
    }

    private fun startFirstGame(series: StoredSeries): StoredSeries {
        val (white, black) = randomColours(series)

        val gameId =
            games.create(
                seriesId = series.id,
                sequenceNumber = FIRST_GAME,
                participants = listOf(white, black),
                game = ChessGame.newGame(),
            )

        this.series.attachCurrentGame(series.id, gameId)

        return series.copy(currentGameId = gameId)
    }

    /**
     * Who plays White in the series' first game — a coin toss (`D014`).
     *
     * A chess table seats exactly two, in id order, so this is the same toss over the same
     * two seats that the pair columns gave before `M19.3`.
     */
    private fun randomColours(series: StoredSeries): Pair<Uuid, Uuid> {
        val (first, second) = series.participants

        return if (random.nextBoolean()) first to second else second to first
    }

    companion object {
        /** The audit event an automatic rematch records (`ARCHITECTURE.md` §9). */
        const val REMATCH_CREATED: String = "RematchCreated"

        /** The audit event a series records when it closes. */
        const val SERIES_CLOSED: String = "SeriesClosed"

        /** Why a series closed here: its last game finished after it was marked (`D013`). */
        private const val CLOSE_AFTER_CURRENT_GAME = "CloseAfterCurrentGame"

        private const val FIRST_GAME = 1
    }
}
