package com.jmussel.chessgame.ui.groups

import com.jmussel.chessgame.api.ChessApiException
import com.jmussel.chessgame.api.UserSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the group screens show and say, decided without a screen (`D076`). */
class GroupsTest {
    private fun user(name: String) = UserSummaryDto(userId = "user-$name", username = name)

    private fun refusal(
        status: Int,
        explanation: String,
    ) = ChessApiException(status = status, explanation = explanation, message = "refused")

    @Test
    fun aNameIsTrimmedAndMustBeOneToFortyEightCharacters() {
        assertEquals("Tuesday", Groups.cleanedName("  Tuesday "))

        assertFalse(Groups.isCreatable(""))
        assertFalse("spaces alone are no name", Groups.isCreatable("   "))
        assertTrue(Groups.isCreatable("T"))
        assertTrue(Groups.isCreatable("x".repeat(48)))
        assertTrue("the spaces around it are not counted", Groups.isCreatable(" " + "x".repeat(48) + " "))
        assertFalse(Groups.isCreatable("x".repeat(49)))
    }

    @Test
    fun theMemberCountReadsAsEnglish() {
        assertEquals("1 member", Groups.memberCount(1))
        assertEquals("3 members", Groups.memberCount(3))
    }

    @Test
    fun thePlayerIsMarkedAsYouAndEveryoneElseCanBePlayed() {
        val state = GroupUiState(members = listOf(user("Taylor"), user("Alex"), user("Robin")))

        val rows = Groups.memberRows(state, ownUserId = "user-Taylor")

        assertEquals(listOf("Taylor", "Alex", "Robin"), rows.map { it.member.username })
        assertEquals(listOf(true, false, false), rows.map { it.isYou })
    }

    @Test
    fun onlyFriendsNotAlreadyInTheGroupCanBeAdded() {
        val state =
            GroupUiState(
                members = listOf(user("Taylor"), user("Alex")),
                friends = listOf(user("Alex"), user("Sam"), user("Robin")),
            )

        assertEquals(listOf("Sam", "Robin"), Groups.addableFriends(state).map { it.username })
        assertNull(Groups.nobodyToAdd(state))
    }

    @Test
    fun withNobodyToAddItSaysWhy() {
        val everyoneIn = GroupUiState(members = listOf(user("Taylor"), user("Alex")), friends = listOf(user("Alex")))
        val noFriends = GroupUiState(members = listOf(user("Taylor")))

        assertEquals("All your friends are in this group.", Groups.nobodyToAdd(everyoneIn))
        assertEquals("You have no friends to add yet.", Groups.nobodyToAdd(noFriends))
    }

    @Test
    fun leavingSaysThatGamesCarryOn() {
        assertEquals("Leave Tuesday? Your games with its members carry on.", Groups.leaveWarning("Tuesday"))
    }

    @Test
    fun aGroupIsGoneOnlyWhenTheServerDoesNotKnowIt() {
        assertTrue(Groups.isGone(refusal(404, "No such group")))
        assertTrue("a bare 404 is an unknown group too", Groups.isGone(refusal(404, "")))
        assertFalse("a person who does not exist is not a missing group", Groups.isGone(refusal(404, "No such user")))
        assertFalse(Groups.isGone(refusal(403, "Add Sam as a friend first")))
    }

    @Test
    fun aRefusalIsShownInTheServersWords() {
        assertEquals("Add Sam as a friend first", Groups.messageFor(refusal(403, "Add Sam as a friend first")))
        assertEquals("The server would not do that. Try again.", Groups.messageFor(refusal(500, "")))
    }
}
