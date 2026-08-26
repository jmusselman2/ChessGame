@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.series

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.GameSeriesRepository
import com.jmussel.chessgame.server.db.OpenedSeries
import com.jmussel.chessgame.server.db.StoredSeries
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

    private companion object {
        const val FIRST_GAME = 1
    }
}
