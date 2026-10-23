package com.jmussel.chessgame.ui.review

import com.jmussel.chessgame.api.CompletedGameReviewDto
import com.jmussel.chessgame.api.GameViewDto
import com.jmussel.chessgame.api.MoveDto
import com.jmussel.chessgame.api.ReviewPlyDto
import com.jmussel.chessgame.api.ReviewPositionDto
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.ui.game.OnlineGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An online review is the server's projection stepped through on the shared read-only model
 * (`D100`, `D101`, `M22.10`): its positions and SAN are the server's, and nothing is replayed.
 */
class OnlineGameReviewTest {
    private val start = listOf("rnbqkbnr", "pppppppp", "........", "........", "........", "........", "PPPPPPPP", "RNBQKBNR")
    private val afterF3 = listOf("rnbqkbnr", "pppppppp", "........", "........", "........", ".....P..", "PPPPP.PP", "RNBQKBNR")
    private val mated = listOf("rnb.kbnr", "pppp.ppp", "........", "....p...", "......Pq", ".....P..", "PPPPP..P", "RNBQKBNR")

    private val finished =
        GameViewDto(
            gameId = "game-1",
            seriesId = "series-1",
            opponent = UserSummaryDto(userId = "user-1", username = "Alex"),
            version = 4,
            yourSide = "WHITE",
            sideToMove = "WHITE",
            result = "BLACK_WINS",
            terminationReason = "CHECKMATE",
        )

    /** A deliberately short projection: two plies, and a final position the server says is mate. */
    private val projection =
        CompletedGameReviewDto(
            gameId = "game-1",
            version = 4,
            plies =
                listOf(
                    ReviewPlyDto(MoveDto(from = "f2", to = "f3", san = "f3"), ReviewPositionDto(start, "WHITE", inCheck = false)),
                    ReviewPlyDto(MoveDto(from = "d8", to = "h4", san = "Qh4#"), ReviewPositionDto(afterF3, "BLACK", inCheck = false)),
                ),
            finalPosition = ReviewPositionDto(mated, "WHITE", inCheck = true),
            result = "BLACK_WINS",
            terminationReason = "CHECKMATE",
        )

    @Test
    fun theReviewIsTheServersPositionsAndSanStepByStep() {
        val review = GameReviews.ofOnline(finished, projection)

        assertEquals(2, review.plies)
        assertEquals("Against Alex", review.title)
        assertEquals(listOf("1. f3 Qh4#"), review.moveListLines())
        assertEquals("opens at the final position", OnlineGame.boardFrom(mated), review.position.board)
        assertEquals("Alex won by checkmate", review.status)
        assertEquals("the final checkmate is marked", Square.parse("e1"), review.checkedKing)
        assertEquals(setOf(Square.parse("d8"), Square.parse("h4")), review.lastMove)

        val first = review.at(1)
        assertEquals(OnlineGame.boardFrom(afterF3), first.position.board)
        assertEquals("Black to move", first.status)
        assertEquals("After f3 (1 of 2)", first.positionText)

        val beginning = review.at(0)
        assertEquals(OnlineGame.boardFrom(start), beginning.position.board)
        assertFalse(beginning.canStepBack)
        assertTrue(beginning.canStepForward)
    }

    @Test
    fun theBoardIsTurnedForThePlayersOwnSide() {
        assertEquals(Side.BLACK, GameReviews.ofOnline(finished.copy(yourSide = "BLACK"), projection).orientation)
        assertFalse(GameReviews.ofOnline(finished, projection).faceToFace)
    }

    @Test
    fun aZeroMoveGameHasOnlyItsFinalPosition() {
        val resigned =
            projection.copy(
                plies = emptyList(),
                finalPosition = ReviewPositionDto(start, "WHITE", false),
                terminationReason = "RESIGNATION",
            )

        val review = GameReviews.ofOnline(finished.copy(terminationReason = "RESIGNATION"), resigned)

        assertEquals(0, review.plies)
        assertEquals("Start of the game", review.positionText)
        assertFalse(review.canStepBack)
        assertFalse(review.canStepForward)
    }
}
