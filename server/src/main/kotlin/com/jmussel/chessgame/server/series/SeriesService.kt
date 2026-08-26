@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.series

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.GameSeriesRepository
import com.jmussel.chessgame.server.db.OpenedSeries
import com.jmussel.chessgame.server.db.StoredGame
import com.jmussel.chessgame.server.db.StoredSeries
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Opening a series and making sure it has a game to play.
 *
 * "Play with this friend" is meant to be one tap that lands the player in a game
 * (`docs/PRODUCT.md`), so opening a series with no current game starts its first one. The
 * colours for that first game are random (`D014`); every later game alternates them, which
 * is the rematch rule in `M13.3`.
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
     * series is never left claiming a game that was not written.
     */
    fun openWithGame(
        caller: Uuid,
        friend: Uuid,
    ): OpenedSeries {
        val opened = series.openOrCreate(caller, friend)
        if (opened.series.currentGameId != null) return opened

        val withGame = transaction(database) { startFirstGame(opened.series) }

        return OpenedSeries(series = withGame, created = opened.created)
    }

    /**
     * Starts the rematch a finished game owes its series, if it still owes one.
     *
     * Rematches are automatic (`D015`): a normally completed game in an active series is
     * followed by the next one without either player asking. Returns the series as it
     * stands afterwards, or `null` when there is no series to speak of.
     *
     * Exactly one rematch is created however often this is asked. The series row is locked
     * for the transaction and the decision is made from what it says under that lock: a
     * series whose current game is no longer the finished one has already had its rematch,
     * and is handed back untouched. That covers a retry, a duplicated command, and two
     * transactions arriving together.
     *
     * A series marked to close after its current game gets no rematch — closing it is
     * `M13.4`.
     */
    fun startNextGameAfter(finished: StoredGame): StoredSeries? =
        transaction(database) {
            val current = series.findForUpdate(finished.seriesId) ?: return@transaction null

            when {
                !current.isActive -> current
                current.closeAfterCurrentGame -> current
                current.currentGameId != finished.id -> current
                else -> startRematch(current, finished)
            }
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

        private const val FIRST_GAME = 1
    }
}
