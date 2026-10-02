package com.jmussel.chessgame.ui.localhistory

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.GameResult
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.TerminationReason
import com.jmussel.chessgame.local.ComputerOpponent
import com.jmussel.chessgame.local.LocalGameSummary
import com.jmussel.chessgame.local.StoredLocalGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** How past local games read, and stepping through one ply by ply (`M21.3`). */
class PastLocalGamesTest {
    private fun move(text: String): Move =
        Move.of(text.substring(0, 2), text.substring(2, 4), text.getOrNull(4)?.let(PieceType::fromLetter))

    /** Castling, en passant and a promotion, then White resigns. */
    private val moves =
        listOf(
            "e2e4",
            "g8f6",
            "e4e5",
            "d7d5",
            "e5d6",
            "c8g4",
            "g1f3",
            "b8c6",
            "f1e2",
            "e8d7",
            "e1g1",
            "a7a6",
            "d6c7",
            "d7e8",
            "c7d8q",
        )

    private val played: ChessGame = moves.fold(ChessGame.newGame()) { game, text -> ChessRules.applyMove(game, move(text)) }

    private val finished: ChessGame = ChessRules.resign(played, Side.WHITE)

    private fun stored(
        game: ChessGame = finished,
        computer: ComputerOpponent? = null,
    ) = StoredLocalGame(id = 7, computer = computer, createdAt = 1_000, completedAt = 2_000, game = game)

    @Test
    fun theReviewRebuildsEveryRecordedPly() {
        val review = LocalGameReview(stored())
        assertEquals(moves.size, review.plies)

        // The test may replay the moves; the review itself only reads the records.
        (0..moves.size).forEach { ply ->
            val replayed = moves.take(ply).fold(ChessGame.newGame()) { game, text -> ChessRules.applyMove(game, move(text)) }
            val shown = review.at(ply).game

            assertEquals(replayed.history, shown.history)
            assertEquals(replayed.state, shown.state.copy(result = if (ply < moves.size) shown.state.result else null))
        }
    }

    @Test
    fun theReviewOpensOnTheFinishedGame() {
        val review = LocalGameReview(stored())

        assertEquals(finished, review.game)
        assertEquals(GameResult.resignation(Side.WHITE), review.game.result)
        assertFalse(review.canStepForward)
        assertTrue(review.canStepBack)
    }

    @Test
    fun earlierPliesAreGamesInProgressWithTheirOwnLastMove() {
        val review = LocalGameReview(stored()).at(2)

        assertNull(review.game.result)
        assertEquals(move("g8f6"), review.game.lastMove)
        assertEquals("After g8f6 (2 of ${moves.size})", review.position)
        assertEquals("Start of the game", review.at(0).position)
        assertFalse(review.at(0).canStepBack)
    }

    @Test
    fun steppingStaysWithinTheGame() {
        val review = LocalGameReview(stored())

        assertEquals(0, review.at(-3).ply)
        assertEquals(moves.size, review.at(moves.size + 5).ply)
        assertThrows(IllegalArgumentException::class.java) { LocalGameReview(stored(), ply = moves.size + 1) }
    }

    @Test
    fun passAndPlayIsReviewedFaceToFaceAndAComputerGameFromTheHumansSide() {
        val passAndPlay = LocalGameReview(stored())
        assertTrue(passAndPlay.faceToFace)
        assertEquals(Side.WHITE, passAndPlay.orientation)

        val computer = LocalGameReview(stored(computer = ComputerOpponent(Side.BLACK, 2)))
        assertFalse(computer.faceToFace)
        assertEquals(Side.BLACK, computer.orientation)
    }

    @Test
    fun aLineSaysTheKindTheDateAndTheResult() {
        val summary =
            LocalGameSummary(
                id = 1,
                computer = null,
                createdAt = 1_000,
                completedAt = 2_000,
                result = GameResult.checkmate(Side.BLACK),
            )

        assertEquals("Pass-and-play • day 2000 • White won by checkmate", PastLocalGames.summaryFor(summary) { "day $it" })
        assertEquals(
            "Computer (Very Easy) • You played White • day 2000 • Drawn by threefold repetition",
            PastLocalGames.summaryFor(
                summary.copy(
                    computer = ComputerOpponent(Side.WHITE, 1),
                    result = GameResult.draw(TerminationReason.THREEFOLD_REPETITION_CLAIM),
                ),
            ) { "day $it" },
        )
        assertEquals("Black won by resignation", PastLocalGames.resultLabel(GameResult.resignation(Side.WHITE)))
    }

    @Test
    fun eachLevelIsListedUnderItsName() {
        val summary =
            LocalGameSummary(
                id = 1,
                computer = null,
                createdAt = 1_000,
                completedAt = 2_000,
                result = GameResult.checkmate(Side.BLACK),
            )

        val lines =
            (1..4).map { level ->
                PastLocalGames.summaryFor(summary.copy(computer = ComputerOpponent(Side.BLACK, level))) { "day $it" }
            }

        assertEquals(
            listOf(
                "Computer (Very Easy) • You played Black • day 2000 • White won by checkmate",
                "Computer (Easy) • You played Black • day 2000 • White won by checkmate",
                "Computer (Medium) • You played Black • day 2000 • White won by checkmate",
                "Computer (Hard) • You played Black • day 2000 • White won by checkmate",
            ),
            lines,
        )
    }
}
