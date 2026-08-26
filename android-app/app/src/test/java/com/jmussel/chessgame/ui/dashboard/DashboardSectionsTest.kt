package com.jmussel.chessgame.ui.dashboard

import com.jmussel.chessgame.api.DashboardEntryDto
import com.jmussel.chessgame.api.UserSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun person(username: String) = UserSummaryDto(userId = "user-$username", username = username)

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

    @Test
    fun theGamesWaitingOnTheOpponentAreTheirTurn() {
        val rows =
            DashboardSections.theirTurn(
                listOf(
                    entry("Alex", yourTurn = true),
                    entry("Chris", yourTurn = false),
                ),
            )

        assertEquals(listOf("Chris"), rows.map { it.opponent })
    }

    @Test
    fun aTheirTurnRowReadsTheSameWay() {
        val row =
            DashboardSections
                .theirTurn(listOf(entry("Chris", yourTurn = false, moveNumber = 24)))
                .single()

        assertEquals("Chris", row.opponent)
        assertEquals("White • Move 24", row.detail)
        assertEquals("game-Chris", row.gameId)
    }

    @Test
    fun everyActiveSeriesIsInExactlyOneSection() {
        val entries =
            listOf(
                entry("Alex", yourTurn = true),
                entry("Chris", yourTurn = false),
                entry("Sam", yourTurn = true),
            )

        val yours = DashboardSections.yourTurn(entries)
        val theirs = DashboardSections.theirTurn(entries)

        assertEquals(entries.size, yours.size + theirs.size)
        assertTrue(
            "no game appears in both sections",
            yours.map { it.gameId }.intersect(theirs.map { it.gameId }.toSet()).isEmpty(),
        )
    }

    @Test
    fun aSeriesBetweenGamesIsInNeitherSection() {
        val entries = listOf(entry("Alex", yourTurn = false, gameId = null))

        assertTrue(DashboardSections.yourTurn(entries).isEmpty())
        assertTrue(DashboardSections.theirTurn(entries).isEmpty())
    }

    @Test
    fun everyFriendIsListed() {
        val rows =
            DashboardSections.friends(
                friends = listOf(person("Sam"), person("Alex"), person("Chris")),
                entries = emptyList(),
            )

        assertEquals(listOf("Alex", "Chris", "Sam"), rows.map { it.username })
    }

    @Test
    fun aFriendWithNoGameIsSomeoneToPlay() {
        val row = DashboardSections.friends(listOf(person("Alex")), emptyList()).single()

        assertEquals("Play", row.action)
        assertNull(row.gameId)
    }

    @Test
    fun aFriendWithAGameIsSomeoneToOpen() {
        val row =
            DashboardSections
                .friends(
                    friends = listOf(person("Alex")),
                    entries = listOf(entry("Alex", yourTurn = true)),
                ).single()

        assertEquals("Open", row.action)
        assertEquals("game-Alex", row.gameId)
    }

    @Test
    fun aFriendItIsNotYourTurnAgainstIsStillSomeoneToOpen() {
        val row =
            DashboardSections
                .friends(
                    friends = listOf(person("Chris")),
                    entries = listOf(entry("Chris", yourTurn = false)),
                ).single()

        assertEquals("Open", row.action)
    }

    @Test
    fun aSeriesBetweenGamesLeavesTheFriendSomeoneToPlay() {
        val row =
            DashboardSections
                .friends(
                    friends = listOf(person("Alex")),
                    entries = listOf(entry("Alex", yourTurn = false, gameId = null)),
                ).single()

        assertEquals("Play", row.action)
        assertNull(row.gameId)
    }

    @Test
    fun friendsAlreadyShownAboveAreListedAgain() {
        val entries = listOf(entry("Alex", yourTurn = true))

        val rows = DashboardSections.friends(listOf(person("Alex"), person("Robin")), entries)

        // The section is the way to reach a friend, not a leftovers pile.
        assertEquals(listOf("Alex", "Robin"), rows.map { it.username })
    }

    @Test
    fun anAccountWithNoFriendsHasNoRows() {
        assertTrue(DashboardSections.friends(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun aGameAgainstSomeoneWhoIsNoLongerAFriendIsNotAFriendRow() {
        // Removing a friend leaves the game in progress alone (`D013`), so the series can
        // outlive the friendship; the Friends section still only lists friends.
        val rows = DashboardSections.friends(listOf(person("Alex")), listOf(entry("Chris", yourTurn = true)))

        assertEquals(listOf("Alex"), rows.map { it.username })
        assertEquals("Play", rows.single().action)
    }

    @Test
    fun theirTurnKeepsTheServersOrder() {
        val rows =
            DashboardSections.theirTurn(
                listOf(
                    entry("Sam", yourTurn = false),
                    entry("Chris", yourTurn = false),
                ),
            )

        assertEquals(listOf("Sam", "Chris"), rows.map { it.opponent })
    }
}
