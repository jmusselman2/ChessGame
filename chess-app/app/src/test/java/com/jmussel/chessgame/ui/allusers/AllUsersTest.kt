package com.jmussel.chessgame.ui.allusers

import com.jmussel.chessgame.api.ChessApiException
import com.jmussel.chessgame.api.ListedUserDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the "All users" page groups and marks everyone, and what it says when the list fails (`D071`). */
class AllUsersTest {
    private val alex = ListedUserDto(userId = "user-1", username = "Alex", friend = false)
    private val sam = ListedUserDto(userId = "user-2", username = "Sam", friend = false)
    private val robin = ListedUserDto(userId = "user-3", username = "Robin", friend = true)

    private fun refusal(explanation: String) = ChessApiException(status = 404, explanation = explanation, message = "refused")

    @Test
    fun friendsGoUnderneathEveryoneElseInTheServersOrder() {
        val sections = AllUsers.sections(AllUsersUiState(users = listOf(robin, sam, alex), loaded = true))

        assertEquals(listOf("Sam", "Alex"), sections.toAdd.map { it.user.username })
        assertEquals(listOf("Robin"), sections.friends.map { it.user.username })
    }

    @Test
    fun everyoneNotYetAFriendOffersAddAndFriendsDoNot() {
        val sections = AllUsers.sections(AllUsersUiState(users = listOf(alex, robin), loaded = true))

        assertTrue(sections.toAdd.single().canAdd)
        assertTrue(!sections.friends.single().canAdd)
    }

    @Test
    fun someoneAddedStaysWhereTheyWereAndSaysSo() {
        val sections =
            AllUsers.sections(AllUsersUiState(users = listOf(alex, sam), added = setOf("user-1"), loaded = true))

        assertEquals(listOf("Alex", "Sam"), sections.toAdd.map { it.user.username })
        assertEquals(listOf(true, false), sections.toAdd.map { it.added })
        assertEquals(listOf(false, true), sections.toAdd.map { it.canAdd })
    }

    @Test
    fun nobodyCanBeAddedWhileAnAddIsInFlight() {
        val sections = AllUsers.sections(AllUsersUiState(users = listOf(alex, sam), adding = "user-1", loaded = true))

        assertTrue(sections.toAdd.none { it.canAdd })
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
