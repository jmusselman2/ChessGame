package com.jmussel.chessgame.ui.allusers

import com.jmussel.chessgame.api.ChessApiException
import com.jmussel.chessgame.api.UserSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What each row of the "All users" page offers, and what the page says when the list fails (`D071`). */
class AllUsersTest {
    private val alex = UserSummaryDto(userId = "user-1", username = "Alex")
    private val sam = UserSummaryDto(userId = "user-2", username = "Sam")

    private fun refusal(explanation: String) = ChessApiException(status = 404, explanation = explanation, message = "refused")

    @Test
    fun everyoneNotYetAddedOffersAdd() {
        val rows = AllUsers.rows(AllUsersUiState(users = listOf(alex, sam), loaded = true))

        assertEquals(listOf("Alex", "Sam"), rows.map { it.user.username })
        assertTrue(rows.all { !it.added && it.canAdd })
    }

    @Test
    fun someoneAddedSaysSoInsteadOfOfferingAddAgain() {
        val rows = AllUsers.rows(AllUsersUiState(users = listOf(alex, sam), added = setOf("user-1"), loaded = true))

        assertEquals(listOf(true, false), rows.map { it.added })
        assertEquals(listOf(false, true), rows.map { it.canAdd })
    }

    @Test
    fun nobodyCanBeAddedWhileAnAddIsInFlight() {
        val rows = AllUsers.rows(AllUsersUiState(users = listOf(alex, sam), adding = "user-1", loaded = true))

        assertTrue(rows.none { it.canAdd })
    }

    @Test
    fun aRefusalIsRepeatedInTheServersWords() {
        assertEquals("Not allowed", AllUsers.messageFor(refusal("Not allowed")))
    }

    @Test
    fun aRefusalWithNothingToSaySaysTheListIsNotThere() {
        // What an older app sees once the route has been removed.
        assertEquals("The list of users is not available.", AllUsers.messageFor(refusal("")))
    }

    @Test
    fun aLostConnectionSaysSo() {
        assertTrue(AllUsers.unreachableMessage().startsWith("Could not reach the server"))
    }
}
