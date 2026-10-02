package com.jmussel.chessgame.ui.board

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.DrawClaim
import com.jmussel.chessgame.core.chess.GameResult
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.core.chess.TerminationReason
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How every board announces a finished game (`M21.16`): who won, or that it was drawn, and
 * what ended it, in a player's words, with the last move actually played still highlighted.
 */
class GameEndingsTest {
    @Test
    fun everyTerminationReasonHasAPlayersWords() {
        val expected =
            mapOf(
                TerminationReason.CHECKMATE to "checkmate",
                TerminationReason.RESIGNATION to "resignation",
                TerminationReason.STALEMATE to "stalemate",
                TerminationReason.INSUFFICIENT_MATERIAL to "insufficient material",
                TerminationReason.THREEFOLD_REPETITION_CLAIM to "threefold repetition",
                TerminationReason.FIFTY_MOVE_RULE_CLAIM to "the fifty-move rule",
                TerminationReason.FIVEFOLD_REPETITION to "fivefold repetition",
                TerminationReason.SEVENTY_FIVE_MOVE_RULE to "the seventy-five-move rule",
            )

        assertEquals("every reason is covered", TerminationReason.entries.toSet(), expected.keys)
        expected.forEach { (reason, words) ->
            assertEquals(words, GameEndings.reasonWords(reason))
            assertEquals("the server's name for $reason reads the same", words, GameEndings.reasonWords(reason.name))
        }
    }

    @Test
    fun aReasonFromANewerServerStillReadsPlainly() {
        assertEquals("time forfeit", GameEndings.reasonWords("TIME_FORFEIT"))
    }

    @Test
    fun aGameAtOneBoardNamesTheWinningColour() {
        assertEquals("White won by checkmate", GameEndings.forSides(GameResult.checkmate(Side.BLACK)))
        assertEquals("Black won by resignation", GameEndings.forSides(GameResult.resignation(Side.WHITE)))
        assertEquals("Drawn by stalemate", GameEndings.forSides(GameResult.draw(TerminationReason.STALEMATE)))
        assertEquals(
            "Drawn by the seventy-five-move rule",
            GameEndings.forSides(GameResult.draw(TerminationReason.SEVENTY_FIVE_MOVE_RULE)),
        )
    }

    @Test
    fun aGameAgainstTheComputerSaysYouOrTheComputer() {
        assertEquals("You won by checkmate", GameEndings.forComputer(GameResult.checkmate(Side.BLACK), humanSide = Side.WHITE))
        assertEquals(
            "The computer won by resignation",
            GameEndings.forComputer(GameResult.resignation(Side.BLACK), humanSide = Side.BLACK),
        )
        assertEquals(
            "Drawn by the fifty-move rule",
            GameEndings.forComputer(GameResult.draw(TerminationReason.FIFTY_MOVE_RULE_CLAIM), humanSide = Side.BLACK),
        )
    }

    @Test
    fun theLocalStatusOfAFinishedGameIsItsEnding() {
        val resigned = ChessRules.resign(play("e2e4"), Side.BLACK)

        assertEquals("White won by resignation", GameControls.statusFor(resigned))
    }

    // --- The last move stays the last move played ---------------------------------------

    @Test
    fun aMoveThatEndsTheGameStaysHighlighted() {
        val mated = play("f2f3", "e7e5", "g2g4", "d8h4")

        assertEquals(TerminationReason.CHECKMATE, mated.result?.reason)
        assertEquals(setOf(Square.parse("d8"), Square.parse("h4")), BoardRendering.lastMoveSquares(mated))
    }

    @Test
    fun aResignationInventsNoFinalMove() {
        val resigned = ChessRules.resign(play("e2e4", "e7e5"), Side.WHITE)

        assertEquals(setOf(Square.parse("e7"), Square.parse("e5")), BoardRendering.lastMoveSquares(resigned))
    }

    @Test
    fun aDrawClaimKeepsTheLastMovePlayed() {
        // The starting position for the third time, after Black's knight returns.
        val repeated = play("g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8")
        val claimed = ChessRules.claimDraw(repeated, DrawClaim.THREEFOLD_REPETITION)

        assertEquals("Drawn by threefold repetition", GameControls.statusFor(claimed))
        assertEquals(setOf(Square.parse("f6"), Square.parse("g8")), BoardRendering.lastMoveSquares(claimed))
    }

    @Test
    fun aClaimOnADeclaredMoveDoesNotHighlightTheMoveThatWasNeverPlayed() {
        // Black declares the knight's return, which would repeat the start a third time
        // (`D038`): the claim ends the game and the declared move is never played.
        val beforeDeclaration = play("g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1")
        val claimed = ChessRules.claimDraw(beforeDeclaration, DrawClaim.THREEFOLD_REPETITION, move("f6g8"))

        assertEquals("Drawn by threefold repetition", GameControls.statusFor(claimed))
        assertEquals(setOf(Square.parse("f3"), Square.parse("g1")), BoardRendering.lastMoveSquares(claimed))
    }

    private fun play(vararg moves: String): ChessGame =
        moves.fold(ChessGame.newGame()) { game, text -> ChessRules.applyMove(game, move(text)) }

    private fun move(text: String) = Move(Square.parse(text.take(2)), Square.parse(text.drop(2)))
}
