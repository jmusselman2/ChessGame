package com.jmussel.chessgame.ui.history

import com.jmussel.chessgame.api.FinishedGameDto
import com.jmussel.chessgame.api.SeriesHistoryDto

/** One finished game, as a line of history. */
data class HistoryGameRow(
    val gameId: String,
    val summary: String,
)

/** One opponent's finished games, newest game last. */
data class HistorySection(
    val seriesId: String,
    val heading: String,
    val games: List<HistoryGameRow>,
)

/**
 * Turning finished games into something to read.
 *
 * Every line is written from what the server stored — who won and why, and which side the
 * viewer played — so the app never re-decides an outcome it did not compute. A closed
 * series is marked as such, because "no more games with Alex" is the thing a player would
 * otherwise wonder about (`D012`, `D013`).
 *
 * Pure, so the wording is tested without a screen.
 */
object HistoryList {
    /** The series with something finished in them, in the order the server sent them. */
    fun sections(series: List<SeriesHistoryDto>): List<HistorySection> =
        series.filter { it.games.isNotEmpty() }.map { entry ->
            HistorySection(
                seriesId = entry.seriesId,
                heading = headingFor(entry),
                games = entry.games.map { game -> HistoryGameRow(gameId = game.gameId, summary = summaryFor(game)) },
            )
        }

    /** `"Alex"`, or `"Alex (closed)"` once the series is over. */
    fun headingFor(series: SeriesHistoryDto): String =
        if (series.status == CLOSED) "${series.opponent.username} $CLOSED_SUFFIX" else series.opponent.username

    /** `"Game 2 • White • Won by checkmate • 31 moves"`. */
    fun summaryFor(game: FinishedGameDto): String =
        listOfNotNull(
            "Game ${game.sequenceNumber}",
            sideLabel(game.yourSide),
            outcomeFor(game),
            moveCountFor(game),
        ).joinToString(separator = SEPARATOR)

    /** How it went for the viewer: `"Won by checkmate"`, `"Lost by resignation"`, `"Drawn …"`. */
    fun outcomeFor(game: FinishedGameDto): String? {
        val result = game.result ?: return null
        val reason = game.terminationReason?.let(::reasonLabel)
        val verdict =
            when (result) {
                DRAW -> "Drawn"
                "${game.yourSide}_WINS" -> "Won"
                else -> "Lost"
            }

        return if (reason == null) verdict else "$verdict by $reason"
    }

    private fun moveCountFor(game: FinishedGameDto): String? =
        when {
            game.moveNumber <= 0 -> null
            game.moveNumber == 1 -> "1 move"
            else -> "${game.moveNumber} moves"
        }

    /** `THREEFOLD_REPETITION_CLAIM` reads as `threefold repetition claim`. */
    private fun reasonLabel(reason: String): String = reason.lowercase().replace('_', ' ')

    private fun sideLabel(side: String): String = side.lowercase().replaceFirstChar { it.uppercase() }

    private const val CLOSED = "CLOSED"
    private const val CLOSED_SUFFIX = "(closed)"
    private const val DRAW = "DRAW"
    private const val SEPARATOR = " • "
}
