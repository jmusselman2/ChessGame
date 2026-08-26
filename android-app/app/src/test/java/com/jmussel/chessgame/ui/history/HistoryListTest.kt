package com.jmussel.chessgame.ui.history

import com.jmussel.chessgame.api.FinishedGameDto
import com.jmussel.chessgame.api.SeriesHistoryDto
import com.jmussel.chessgame.api.UserSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a finished game reads once it is history.
 *
 * Every line comes from what the server stored, so these are about wording and never about
 * deciding an outcome the app did not compute.
 */
class HistoryListTest {
    private fun game(
        sequenceNumber: Int = 1,
        yourSide: String = "WHITE",
        result: String? = "WHITE_WINS",
        reason: String? = "CHECKMATE",
        moveNumber: Int = 31,
    ) = FinishedGameDto(
        gameId = "game-$sequenceNumber",
        sequenceNumber = sequenceNumber,
        yourSide = yourSide,
        result = result,
        terminationReason = reason,
        moveNumber = moveNumber,
    )

    private fun series(
        opponent: String = "Alex",
        status: String = "ACTIVE",
        games: List<FinishedGameDto> = listOf(game()),
    ) = SeriesHistoryDto(
        seriesId = "series-$opponent",
        opponent = UserSummaryDto(userId = "user-$opponent", username = opponent),
        status = status,
        closedAt = if (status == "CLOSED") "2026-08-20T18:03:00Z" else null,
        games = games,
    )

    @Test
    fun aWinReadsAsAWin() {
        assertEquals("Won by checkmate", HistoryList.outcomeFor(game()))
    }

    @Test
    fun aLossReadsAsALoss() {
        val lost = game(yourSide = "BLACK", result = "WHITE_WINS", reason = "RESIGNATION")

        assertEquals("Lost by resignation", HistoryList.outcomeFor(lost))
    }

    @Test
    fun winningAsBlackIsStillAWin() {
        val won = game(yourSide = "BLACK", result = "BLACK_WINS")

        assertEquals("Won by checkmate", HistoryList.outcomeFor(won))
    }

    @Test
    fun aDrawIsNeitherWonNorLost() {
        val drawn = game(result = "DRAW", reason = "THREEFOLD_REPETITION_CLAIM")

        assertEquals("Drawn by threefold repetition claim", HistoryList.outcomeFor(drawn))
    }

    @Test
    fun aGameWithNoStoredResultSaysNothingAboutOne() {
        assertNull(HistoryList.outcomeFor(game(result = null)))
    }

    @Test
    fun aLineSaysWhichGameItWasAndHowItWent() {
        assertEquals("Game 1 • White • Won by checkmate • 31 moves", HistoryList.summaryFor(game()))
    }

    @Test
    fun aSingleMoveIsNotPluralised() {
        assertEquals("Game 1 • White • Won by checkmate • 1 move", HistoryList.summaryFor(game(moveNumber = 1)))
    }

    @Test
    fun aGameWithNoMoveCountLeavesItOut() {
        assertEquals("Game 1 • White • Won by checkmate", HistoryList.summaryFor(game(moveNumber = 0)))
    }

    @Test
    fun aSectionIsHeadedByTheOpponent() {
        val section = HistoryList.sections(listOf(series())).single()

        assertEquals("Alex", section.heading)
        assertEquals("series-Alex", section.seriesId)
    }

    @Test
    fun aClosedSeriesSaysSo() {
        val section = HistoryList.sections(listOf(series(status = "CLOSED"))).single()

        assertEquals("Alex (closed)", section.heading)
    }

    @Test
    fun theGamesOfASeriesKeepTheirOrder() {
        val played = listOf(series(games = listOf(game(sequenceNumber = 1), game(sequenceNumber = 2))))
        val section = HistoryList.sections(played).single()

        val first = section.games.first()

        assertEquals(listOf("game-1", "game-2"), section.games.map { it.gameId })
        assertTrue(first.summary.startsWith("Game 1"))
    }

    @Test
    fun theServersOrderOfSeriesIsKept() {
        val sections = HistoryList.sections(listOf(series("Sam"), series("Alex")))

        assertEquals(listOf("Sam", "Alex"), sections.map { it.heading })
    }

    @Test
    fun aSeriesWithNothingFinishedIsNotASection() {
        assertTrue(HistoryList.sections(listOf(series(games = emptyList()))).isEmpty())
    }

    @Test
    fun anEmptyHistoryHasNoSections() {
        assertTrue(HistoryList.sections(emptyList()).isEmpty())
    }
}
