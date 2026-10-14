package com.jmussel.chessgame.ui.game

import com.jmussel.chessgame.api.ChessCommandRefusedException
import com.jmussel.chessgame.api.CommandRejectionDto
import com.jmussel.chessgame.api.GameViewDto
import com.jmussel.chessgame.api.UndoNoteDto
import com.jmussel.chessgame.api.UndoRequestDto
import com.jmussel.chessgame.api.UserSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the online game says about undo requests (`D091`, `M21.19`): the request, each answer
 * and each crossing, in the decision's own words. Jordan asked; the viewer's opponent is
 * whoever the game names.
 */
class UndoRequestTextTest {
    private fun game(
        opponent: String,
        request: UndoRequestDto? = null,
        note: UndoNoteDto? = null,
        result: String? = null,
    ) = GameViewDto(
        gameId = "game-1",
        seriesId = "series-1",
        opponent = UserSummaryDto(userId = "user-$opponent", username = opponent),
        version = 3,
        yourSide = "WHITE",
        sideToMove = "WHITE",
        moves = listOf("g1f3", "b8c6"),
        undoRequest = request,
        undoNote = note,
        result = result,
    )

    private fun note(kind: String) = UndoNoteDto(kind = kind, requesterMove = "g1f3", replyMove = "b8c6")

    @Test
    fun theRequesterWaitsWithNothingToCancel() {
        val asked = game("Alex", request = UndoRequestDto(fromYou = true, requesterMove = "g1f3", replyMove = "b8c6"))

        assertEquals("Undo requested. Waiting for Alex.", OnlineGame.undoRequestText(asked))
        assertFalse("the requester answers nothing", OnlineGame.mustAnswerUndo(asked))
    }

    @Test
    fun theOpponentSeesBothMovesItWouldUndo() {
        val asking = game("Jordan", request = UndoRequestDto(fromYou = false, requesterMove = "g1f3", replyMove = "b8c6"))

        assertEquals("Jordan asks to undo g1f3 and your b8c6", OnlineGame.undoRequestText(asking))
        assertTrue(OnlineGame.mustAnswerUndo(asking))
    }

    @Test
    fun aFinishedGameAsksNothing() {
        val over = game("Jordan", request = UndoRequestDto(fromYou = false, requesterMove = "g1f3", replyMove = "b8c6"), result = "DRAW")

        assertFalse(OnlineGame.mustAnswerUndo(over))
    }

    @Test
    fun noRequestNoNoteNoWords() {
        assertNull(OnlineGame.undoRequestText(game("Alex")))
        assertNull(OnlineGame.undoNoteText(game("Alex")))
    }

    @Test
    fun eachAnswerReadsAsTheDecisionWordsIt() {
        assertEquals(
            "Alex accepted your undo request: g1f3 and b8c6 undone",
            OnlineGame.undoNoteText(game("Alex", note = note("ACCEPTED"))),
        )
        assertEquals("Alex declined your undo request", OnlineGame.undoNoteText(game("Alex", note = note("DECLINED"))))
    }

    @Test
    fun eachCrossingReadsAsTheDecisionWordsIt() {
        assertEquals(
            "Alex had undone b8c6, so g1f3 was undone too",
            OnlineGame.undoNoteText(game("Alex", note = note("UNDONE_TOO"))),
        )
        assertEquals(
            "Jordan had asked to undo g1f3, so both moves were undone",
            OnlineGame.undoNoteText(game("Jordan", note = note("BOTH_UNDONE"))),
        )
    }

    @Test
    fun theNewRefusalsAreExplained() {
        fun refusal(reason: String) = ChessCommandRefusedException(409, CommandRejectionDto(reason = reason, message = "x"))

        assertEquals("You cannot ask to undo now.", OnlineGame.messageFor(refusal("CANNOT_REQUEST_UNDO")))
        assertEquals("No undo request is waiting for you.", OnlineGame.messageFor(refusal("NO_UNDO_REQUEST")))
    }
}
