package com.jmussel.chessgame.ui.board

import com.jmussel.chessgame.computer.ComputerGameUiState
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.DrawClaim
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.local.ComputerOpponent
import com.jmussel.chessgame.ui.game.OnlineGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What Undo and a draw claim say they will do, where they are offered (`M21.18`): whose move
 * Undo takes back, whether anyone has to agree, and that a valid claim ends the game.
 */
class ActionExplanationTest {
    // --- Pass-and-play ------------------------------------------------------------------

    @Test
    fun passAndPlayUndoNamesWhiteAfterWhitesMove() {
        assertEquals("Undo White's move", GameControls.undoLabelFor(BoardUiState(play("e2e4"))))
    }

    @Test
    fun passAndPlayUndoNamesBlackAfterBlacksMove() {
        assertEquals("Undo Black's move", GameControls.undoLabelFor(BoardUiState(play("e2e4", "e7e5"))))
    }

    @Test
    fun passAndPlayOffersNoUndoWhenChessCoreSaysThereIsNone() {
        assertNull(GameControls.undoLabelFor(BoardUiState.newGame()))

        val mated = BoardUiState(play("f2f3", "e7e5", "g2g4", "d8h4"))
        assertNull("a game-ending move is final", GameControls.undoLabelFor(mated))
    }

    @Test
    fun theUndoLabelFollowsChessCoresUndoableSide() {
        listOf(play("e2e4"), play("e2e4", "e7e5"), play("g1f3", "g8f6", "f3g1")).forEach { game ->
            val side = ChessRules.undoableSide(game)!!
            val name = if (side == Side.WHITE) "White" else "Black"
            assertEquals("Undo $name's move", GameControls.undoLabelFor(BoardUiState(game)))
        }
    }

    @Test
    fun bothLocalClaimsNameTheirRuleAndSayAValidClaimEndsTheGame() {
        assertEquals("Claim draw (threefold repetition)", GameControls.labelFor(DrawClaim.THREEFOLD_REPETITION))
        assertEquals("Claim draw (fifty-move rule)", GameControls.labelFor(DrawClaim.FIFTY_MOVE_RULE))
        assertEquals("A valid claim ends the game at once.", GameControls.CLAIM_EXPLANATION)
    }

    @Test
    fun aDeclaredClaimSaysTheDeclaredMoveIsNotPlayed() {
        val declared = DeclaredMove(move("f6g8"), setOf(DrawClaim.THREEFOLD_REPETITION))

        assertEquals(
            "Playing f6g8 lets you claim a draw instead. Claiming ends the game now, and f6g8 is not played.",
            GameControls.declaredClaimExplanation(declared),
        )
    }

    // --- Against the computer -------------------------------------------------------------

    @Test
    fun whileTheComputerThinksUndoTakesBackThePendingMoveAlone() {
        val thinking = computerGame(play("e2e4"), thinking = true)

        assertEquals("Undo your move", thinking.takeBackLabel)
    }

    @Test
    fun afterTheReplyUndoTakesBackTheMoveAndTheReply() {
        val replied = computerGame(play("e2e4", "e7e5"))

        assertEquals("Undo your move and the computer's reply", replied.takeBackLabel)
    }

    // --- Online -------------------------------------------------------------------------

    @Test
    fun onlineUndoIsThePlayersOwnMoveWithNoApprovalAndOnlyUntilTheReply() {
        assertEquals("Undo your move", OnlineGame.UNDO_LABEL)
        assertEquals("No approval needed, until they reply.", OnlineGame.UNDO_EXPLANATION)
    }

    @Test
    fun onlineClaimsNameTheirRuleAndAreNotOffers() {
        assertEquals("Claim draw (threefold repetition)", OnlineGame.claimLabel("THREEFOLD_REPETITION"))
        assertEquals("Claim draw (fifty-move rule)", OnlineGame.claimLabel("FIFTY_MOVE_RULE"))
        assertTrue(OnlineGame.CLAIM_EXPLANATION.startsWith("Not a draw offer"))
        assertTrue(OnlineGame.CLAIM_EXPLANATION.contains("no reply is needed"))
        assertTrue(OnlineGame.CLAIM_EXPLANATION.contains("ends the game at once"))
    }

    private fun computerGame(
        game: ChessGame,
        thinking: Boolean = false,
    ) = ComputerGameUiState(
        id = 1,
        opponent = ComputerOpponent(Side.WHITE, 2),
        boardState = BoardUiState(game, orientation = Side.WHITE),
        thinking = thinking,
    )

    private fun play(vararg moves: String): ChessGame =
        moves.fold(ChessGame.newGame()) { game, text -> ChessRules.applyMove(game, move(text)) }

    private fun move(text: String) = Move(Square.parse(text.take(2)), Square.parse(text.drop(2)))
}
