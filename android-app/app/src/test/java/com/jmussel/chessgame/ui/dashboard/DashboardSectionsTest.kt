package com.jmussel.chessgame.ui.dashboard

import com.jmussel.chessgame.api.DashboardEntryDto
import com.jmussel.chessgame.api.UserSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the dashboard puts under YOUR TURN, and how each line reads.
 *
 * The wording is `docs/PRODUCT.md`'s recommended hierarchy: the opponent, then the colour
 * and the move number.
 */
class DashboardSectionsTest {
    private fun entry(
        opponent: String,
        yourTurn: Boolean,
        gameId: String? = "game-$opponent",
        yourSide: String? = "WHITE",
        moveNumber: Int? = 18,
    ) = DashboardEntryDto(
        seriesId = "series-$opponent",
        opponent = UserSummaryDto(userId = "user-$opponent", username = opponent),
        gameId = gameId,
        version = 1,
        yourSide = yourSide,
        sideToMove = if (yourTurn) yourSide else null,
        moveNumber = moveNumber,
        yourTurn = yourTurn,
    )

    @Test
    fun onlyTheGamesWaitingOnYouAreListed() {
        val rows =
            DashboardSections.yourTurn(
                listOf(
                    entry("Alex", yourTurn = true),
                    entry("Chris", yourTurn = false),
                    entry("Sam", yourTurn = true),
                ),
            )

        assertEquals(listOf("Alex", "Sam"), rows.map { it.opponent })
    }

    @Test
    fun theServersOrderIsKept() {
        val rows =
            DashboardSections.yourTurn(
                listOf(
                    entry("Sam", yourTurn = true),
                    entry("Alex", yourTurn = true),
                ),
            )

        assertEquals(listOf("Sam", "Alex"), rows.map { it.opponent })
    }

    @Test
    fun aRowSaysWhichColourYouAreAndWhereTheGameIs() {
        val row = DashboardSections.yourTurn(listOf(entry("Alex", yourTurn = true))).single()

        assertEquals("Alex", row.opponent)
        assertEquals("White • Move 18", row.detail)
        assertEquals("game-Alex", row.gameId)
        assertEquals("series-Alex", row.seriesId)
    }

    @Test
    fun playingBlackReadsAsBlack() {
        val row =
            DashboardSections
                .yourTurn(listOf(entry("Sam", yourTurn = true, yourSide = "BLACK", moveNumber = 7)))
                .single()

        assertEquals("Black • Move 7", row.detail)
    }

    @Test
    fun aGameWithNoMoveNumberYetShowsJustTheColour() {
        val row =
            DashboardSections
                .yourTurn(listOf(entry("Alex", yourTurn = true, moveNumber = null)))
                .single()

        assertEquals("White", row.detail)
    }

    @Test
    fun aSeriesBetweenGamesIsNotALineToTap() {
        val rows = DashboardSections.yourTurn(listOf(entry("Alex", yourTurn = true, gameId = null)))

        assertTrue("a series with no game has nothing to open", rows.isEmpty())
    }

    @Test
    fun anEmptyDashboardHasNoRows() {
        assertTrue(DashboardSections.yourTurn(emptyList()).isEmpty())
    }

    @Test
    fun nothingWaitingOnYouIsAnEmptySection() {
        val rows = DashboardSections.yourTurn(listOf(entry("Chris", yourTurn = false)))

        assertTrue(rows.isEmpty())
    }
}
