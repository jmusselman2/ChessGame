package com.jmussel.chessgame.ui.game

import com.jmussel.chessgame.api.ChessApiException
import com.jmussel.chessgame.api.GameViewDto
import com.jmussel.chessgame.api.MoveDto
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.core.chess.StandardPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Turning the server's answer about a game into what the screen draws.
 *
 * Nothing here works anything out about the game: the position, whose move it is, the
 * check, and the result all come from the server (`D004`), and this only says how to show
 * them.
 */
class OnlineGameTest {
    private val startingRows = StandardPosition.BOARD.toString().lines()

    private fun game(
        yourSide: String = "WHITE",
        sideToMove: String = "WHITE",
        yourTurn: Boolean = true,
        inCheck: Boolean = false,
        board: List<String> = startingRows,
        moves: List<String> = emptyList(),
        lastMove: MoveDto? = null,
        moveNumber: Int = 1,
        version: Long = 1,
        result: String? = null,
        terminationReason: String? = null,
    ) = GameViewDto(
        gameId = "game-1",
        seriesId = "series-1",
        opponent = UserSummaryDto(userId = "user-1", username = "Alex"),
        version = version,
        yourSide = yourSide,
        sideToMove = sideToMove,
        yourTurn = yourTurn,
        inCheck = inCheck,
        board = board,
        moves = moves,
        lastMove = lastMove,
        moveNumber = moveNumber,
        result = result,
        terminationReason = terminationReason,
    )

    private fun refusal(status: Int) = ChessApiException(status = status, explanation = "no", message = "refused")

    @Test
    fun theStartingPositionIsReadBackAsItWasSent() {
        val board = OnlineGame.boardFrom(startingRows)

        assertEquals(StandardPosition.BOARD, board)
    }

    @Test
    fun eachPieceLandsOnTheSquareTheServerPutItOn() {
        val board =
            OnlineGame.boardFrom(
                listOf(
                    "....k...",
                    "........",
                    "........",
                    "........",
                    "........",
                    "........",
                    "....P...",
                    "....K...",
                ),
            )

        assertEquals(PieceType.KING, board.pieceAt(Square.parse("e8"))?.type)
        assertEquals(Side.BLACK, board.pieceAt(Square.parse("e8"))?.side)
        assertEquals(PieceType.PAWN, board.pieceAt(Square.parse("e2"))?.type)
        assertEquals(Side.WHITE, board.pieceAt(Square.parse("e1"))?.side)
        assertNull(board.pieceAt(Square.parse("d4")))
    }

    @Test
    fun theBoardFacesTheSideTheViewerIsPlaying() {
        assertEquals(Side.WHITE, OnlineGame.sideOf(game(yourSide = "WHITE")))
        assertEquals(Side.BLACK, OnlineGame.sideOf(game(yourSide = "BLACK")))
    }

    @Test
    fun theMoveJustPlayedIsTwoSquaresToHighlight() {
        val squares = OnlineGame.lastMoveSquares(game(lastMove = MoveDto(from = "e2", to = "e4")))

        assertEquals(setOf(Square.parse("e2"), Square.parse("e4")), squares)
    }

    @Test
    fun aGameWithNoMovesYetHighlightsNothing() {
        assertTrue(OnlineGame.lastMoveSquares(game()).isEmpty())
    }

    @Test
    fun theHeadingNamesTheOpponentAndTheSideYouPlay() {
        assertEquals("Alex • You are White", OnlineGame.headingFor(game(yourSide = "WHITE")))
        assertEquals("Alex • You are Black", OnlineGame.headingFor(game(yourSide = "BLACK")))
    }

    @Test
    fun theStatusSaysWhoseMoveItIs() {
        assertEquals("Your move", OnlineGame.statusFor(game(yourTurn = true)))
        assertEquals("Alex to move", OnlineGame.statusFor(game(yourTurn = false)))
    }

    @Test
    fun aCheckIsSaidAsWellAsWhoseMoveItIs() {
        assertEquals("Your move • Check", OnlineGame.statusFor(game(yourTurn = true, inCheck = true)))
    }

    @Test
    fun aFinishedGameSaysHowItEndedFromTheViewersSide() {
        assertEquals(
            "You won by checkmate",
            OnlineGame.statusFor(game(yourSide = "WHITE", result = "WHITE_WINS", terminationReason = "CHECKMATE")),
        )
        assertEquals(
            "Alex won by resignation",
            OnlineGame.statusFor(game(yourSide = "WHITE", result = "BLACK_WINS", terminationReason = "RESIGNATION")),
        )
        assertEquals(
            "Drawn by stalemate",
            OnlineGame.statusFor(game(result = "DRAW", terminationReason = "STALEMATE")),
        )
    }

    @Test
    fun aFinishedGameSaysNothingAboutWhoseMoveItIs() {
        val status = OnlineGame.statusFor(game(yourTurn = false, result = "DRAW", terminationReason = "STALEMATE"))

        assertFalse(status.contains("move"))
    }

    @Test
    fun theVersionIsShownBecauseACommandHasToCarryIt() {
        assertEquals("Move 18 • version 34", OnlineGame.positionFor(game(moveNumber = 18, version = 34)))
    }

    @Test
    fun theMovesAreNumberedInPairs() {
        val lines = OnlineGame.moveListLines(game(moves = listOf("e2e4", "e7e5", "g1f3")))

        assertEquals(listOf("1. e2e4 e7e5", "2. g1f3"), lines)
    }

    @Test
    fun aGameThatIsNotYoursIsNotWorthRetrying() {
        val refusal = refusal(403)

        assertFalse(OnlineGame.canRetry(refusal))
        assertTrue(OnlineGame.messageFor(refusal).contains("not yours"))
    }

    @Test
    fun aGameThatDoesNotExistIsNotWorthRetrying() {
        assertFalse(OnlineGame.canRetry(refusal(404)))
    }

    @Test
    fun anythingElseIsWorthRetrying() {
        assertTrue(OnlineGame.canRetry(refusal(500)))
        assertTrue(OnlineGame.canRetry(refusal(503)))
    }

    @Test
    fun aLostConnectionSaysWhatToDoAboutIt() {
        assertTrue(OnlineGame.unreachableMessage().contains("connection"))
    }
}
