@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.series

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.server.db.CLOSED_SERIES
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.GameSeriesRepository
import com.jmussel.chessgame.server.db.OpenedSeries
import com.jmussel.chessgame.server.db.StoredGame
import com.jmussel.chessgame.server.db.StoredSeries
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Opening a series and making sure it has a game to play.
 *
 * "Play with this friend" is meant to be one tap that lands the player in a game
 * (`docs/PRODUCT.md`), so opening a series with no current game starts its first one. The
 * colours for that first game are a coin toss and every later game reverses them (`D014`).
 *
 * It also settles what a finished game leaves behind: the next game, or the end of the
 * series.
 */
class SeriesService(
    private val database: Database,
    private val series: GameSeriesRepository,
    private val games: GameRepository,
    private val random: Random = Random.Default,
) {
    /**
     * The pair's active series, with a current game, creating either if they do not exist.
     *
     * Creating the game and pointing the series at it happen in one transaction, so a
     * series is never left claiming a game that was not written. The result says whether
     * this call is the one that started it, because a game nobody asked for is the one
     * thing the other player cannot find out for themselves (`SeriesRoutes`).
     *
     * Both players tapping "Play" at the same moment is the case this has to survive. The
     * series row is locked before the first game is started and re-read under that lock,
     * so the second request waits for the first, finds the game it created, and hands that
     * back — the same mechanism [settleAfter] uses for the same class of race. Without the
     * lock both would find no current game and both would try to be game one of the
     * series, and the second would be refused by the database.
     */
    fun openWithGame(
        caller: Uuid,
        friend: Uuid,
    ): OpenedSeries {
        val opened = series.openOrCreate(caller, friend)
        if (opened.series.currentGameId != null) return opened

        val (withGame, startedGame) =
            transaction(database) {
                val current = series.findForUpdate(opened.series.id) ?: opened.series

                if (current.currentGameId != null) current to false else startFirstGame(current) to true
            }

        return OpenedSeries(series = withGame, created = opened.created, startedGame = startedGame)
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
                whiteUserId = finished.blackUserId,
                blackUserId = finished.whiteUserId,
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
                whiteUserId = white,
                blackUserId = black,
                game = ChessGame.newGame(),
            )

        this.series.attachCurrentGame(series.id, gameId)

        return series.copy(currentGameId = gameId)
    }

    /** Who plays White in the series' first game — a coin toss (`D014`). */
    private fun randomColours(series: StoredSeries): Pair<Uuid, Uuid> =
        if (random.nextBoolean()) {
            series.userAId to series.userBId
        } else {
            series.userBId to series.userAId
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
