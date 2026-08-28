package com.jmussel.chessgame.ui.friends

import com.jmussel.chessgame.api.ChessApiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the friends screen says.
 *
 * The rules belong to the server; the wording that is decided in the app is what is checked
 * here.
 */
class FriendsTest {
    private fun refusal(
        status: Int,
        explanation: String,
    ) = ChessApiException(status = status, explanation = explanation, message = "refused")

    @Test
    fun aNameIsSentWithoutTheSpacesAroundIt() {
        assertEquals("Alex", Friends.cleaned("  Alex  "))
    }

    @Test
    fun anEmptyBoxIsNotWorthARequest() {
        assertFalse(Friends.isSendable(""))
        assertFalse(Friends.isSendable("   "))
        assertTrue(Friends.isSendable("Alex"))
    }

    @Test
    fun aRefusalIsRepeatedInTheServersOwnWords() {
        assertEquals(
            "Already friends with Alex",
            Friends.messageFor(refusal(409, "Already friends with Alex")),
        )
        assertEquals("You cannot add yourself", Friends.messageFor(refusal(400, "You cannot add yourself")))
        assertEquals("No such user", Friends.messageFor(refusal(404, "No such user")))
    }

    @Test
    fun aRefusalWithNothingToSayStillLeavesSomethingToRead() {
        assertTrue(Friends.messageFor(refusal(500, "")).isNotBlank())
    }

    @Test
    fun aLostConnectionSaysWhatToDoAboutIt() {
        assertTrue(Friends.unreachableMessage().contains("connection"))
    }

    @Test
    fun theRemovalWarningSaysTheCurrentGameSurvives() {
        val warning = Friends.removalWarning("Alex")

        assertTrue("names the friend", warning.contains("Alex"))
        assertTrue("says the game finishes", warning.contains("finish"))
        assertTrue("says there is no next one", warning.contains("not be another"))
    }
}
