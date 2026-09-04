package com.jmussel.chessgame.ui.board

import com.jmussel.chessgame.core.chess.Board
import com.jmussel.chessgame.core.chess.CastlingRights
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.DrawClaim
import com.jmussel.chessgame.core.chess.DrawRuleState
import com.jmussel.chessgame.core.chess.GameOutcome
import com.jmussel.chessgame.core.chess.GameState
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Piece
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.core.chess.TerminationReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Claiming a draw on the move the player is about to make, locally (`D041`).
 *
 * The entitlement itself is `game-core`'s (`D038`); what is tested here is that the local
 * screen reaches it — that the move a claim depends on can be declared before it is
 * played, that only that exact move entitles the claim, and that everything the screen
 * already did carries on unchanged.
 */
class DeclaredDrawClaimTest {
    @Test
    fun theMoveThatWouldRepeatAThirdTimeIsDeclaredRatherThanPlayed() {
        val declared = tap(beforeTheThirdOccurrence(), "f6", "g8")

        assertEquals(Move.of("f6", "g8"), declared.declaredMove?.move)
        assertEquals(setOf(DrawClaim.THREEFOLD_REPETITION), GameControls.declaredDrawClaims(declared))
        assertEquals(beforeTheThirdOccurrence().game, declared.game)
    }

    @Test
    fun theThreefoldClaimEndsTheGameWithoutPlayingTheDeclaredMove() {
        val declared = tap(beforeTheThirdOccurrence(), "f6", "g8")
        val claimed = GameControls.claimDeclaredDraw(declared, DrawClaim.THREEFOLD_REPETITION)

        assertTrue(claimed.game.isOver)
        assertEquals(GameOutcome.DRAW, claimed.game.result?.outcome)
        assertEquals(TerminationReason.THREEFOLD_REPETITION_CLAIM, claimed.game.result?.reason)
        assertEquals(beforeTheThirdOccurrence().game.moves, claimed.game.moves)
        assertEquals(GameControls.moveListLines(beforeTheThirdOccurrence().game), GameControls.moveListLines(claimed.game))
        assertNull(claimed.declaredMove)
    }

    @Test
    fun theQuietMoveReachingOneHundredHalfmovesIsDeclaredAndClaimable() {
        val declared = tap(quietPosition(halfmoveClock = 99), "d1", "d2")

        assertEquals(Move.of("d1", "d2"), declared.declaredMove?.move)
        assertEquals(setOf(DrawClaim.FIFTY_MOVE_RULE), GameControls.declaredDrawClaims(declared))

        val claimed = GameControls.claimDeclaredDraw(declared, DrawClaim.FIFTY_MOVE_RULE)

        assertEquals(TerminationReason.FIFTY_MOVE_RULE_CLAIM, claimed.game.result?.reason)
        assertTrue(claimed.game.moves.isEmpty())
        assertEquals(99, claimed.game.state.halfmoveClock)
    }

    @Test
    fun oneHalfmoveEarlyNothingIsDeclared() {
        val played = tap(quietPosition(halfmoveClock = 98), "d1", "d2")

        assertNull(played.declaredMove)
        assertEquals(Move.of("d1", "d2"), played.game.moves.last())
        assertEquals(99, played.game.state.halfmoveClock)
    }

    @Test
    fun aDifferentMoveFromTheSamePieceCarriesNoThreefoldEntitlement() {
        val played = tap(beforeTheThirdOccurrence(), "f6", "e4")

        assertNull(played.declaredMove)
        assertEquals(Move.of("f6", "e4"), played.game.moves.last())
        assertTrue(GameControls.availableDrawClaims(played).isEmpty())
    }

    @Test
    fun aPawnMoveResetsTheClockSoItDeclaresNothing() {
        val played = tap(quietPosition(halfmoveClock = 99), "b2", "b3")

        assertNull(played.declaredMove)
        assertEquals(0, played.game.state.halfmoveClock)
        assertTrue(GameControls.availableDrawClaims(played).isEmpty())
    }

    @Test
    fun aCaptureResetsTheClockSoItDeclaresNothing() {
        val played = tap(quietPosition(halfmoveClock = 99), "d1", "d8")

        assertNull(played.declaredMove)
        assertEquals(0, played.game.state.halfmoveClock)
        assertTrue(GameControls.availableDrawClaims(played).isEmpty())
    }

    @Test
    fun anIllegalDestinationDeclaresNothingAndPlaysNothing() {
        val tapped = tap(quietPosition(halfmoveClock = 99), "d1", "h4")

        assertNull(tapped.declaredMove)
        assertTrue(tapped.game.moves.isEmpty())
        assertNull(tapped.selectedSquare)
    }

    @Test
    fun claimingWithNoDeclarationIsRefused() {
        assertThrows(IllegalArgumentException::class.java) {
            GameControls.claimDeclaredDraw(quietPosition(halfmoveClock = 99), DrawClaim.FIFTY_MOVE_RULE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BoardInteraction.playDeclaredMove(quietPosition(halfmoveClock = 99))
        }
        assertTrue(GameControls.declaredDrawClaims(quietPosition(halfmoveClock = 99)).isEmpty())
    }

    @Test
    fun aClaimTheDeclarationDoesNotEntitleIsRefused() {
        val declared = tap(quietPosition(halfmoveClock = 99), "d1", "d2")

        assertThrows(IllegalArgumentException::class.java) {
            GameControls.claimDeclaredDraw(declared, DrawClaim.THREEFOLD_REPETITION)
        }
    }

    @Test
    fun aPromotionStillAsksForThePieceRatherThanForAClaim() {
        val promoting = tap(promotionPosition(halfmoveClock = 99), "b7", "b8")

        assertNull(promoting.declaredMove)
        assertEquals(Square.parse("b8"), promoting.pendingPromotion?.to)

        val promoted = BoardInteraction.choosePromotion(promoting, PieceType.QUEEN)

        assertNull(promoted.declaredMove)
        assertEquals(0, promoted.game.state.halfmoveClock)
    }

    @Test
    fun aClaimTheCurrentPositionAlreadyOffersIsNotDeclaredAgain() {
        val standing = quietPosition(halfmoveClock = 100)
        assertEquals(setOf(DrawClaim.FIFTY_MOVE_RULE), GameControls.availableDrawClaims(standing))

        val played = tap(standing, "d1", "d2")

        assertNull(played.declaredMove)
        assertEquals(Move.of("d1", "d2"), played.game.moves.last())
        assertEquals(setOf(DrawClaim.FIFTY_MOVE_RULE), GameControls.availableDrawClaims(played))
    }

    @Test
    fun cancellingLeavesTheGameAloneAndOrdinaryPlayCarriesOn() {
        val declared = tap(beforeTheThirdOccurrence(), "f6", "g8")
        val cancelled = BoardInteraction.cancelDeclaredMove(declared)

        assertNull(cancelled.declaredMove)
        assertNull(cancelled.selectedSquare)
        assertEquals(beforeTheThirdOccurrence().game, cancelled.game)

        val played = tap(cancelled, "f6", "e4")

        assertEquals(Move.of("f6", "e4"), played.game.moves.last())
        assertNull(played.declaredMove)
    }

    @Test
    fun theDeclaredMoveCanStillBeDeclaredAgainAndPlayed() {
        val cancelled = BoardInteraction.cancelDeclaredMove(tap(beforeTheThirdOccurrence(), "f6", "g8"))
        val redeclared = tap(cancelled, "f6", "g8")

        assertEquals(Move.of("f6", "g8"), redeclared.declaredMove?.move)

        val played = BoardInteraction.playDeclaredMove(redeclared)

        assertEquals(Move.of("f6", "g8"), played.game.moves.last())
        assertNull(played.declaredMove)
        assertNull(played.selectedSquare)
        assertEquals(Side.WHITE, played.orientation)
    }

    @Test
    fun tappingTheBoardBacksOutOfTheDeclarationWithoutPlayingIt() {
        val declared = tap(beforeTheThirdOccurrence(), "f6", "g8")
        val tappedElsewhere = tap(declared, "a7")

        assertNull(tappedElsewhere.declaredMove)
        assertEquals(beforeTheThirdOccurrence().game, tappedElsewhere.game)
        assertNull(tappedElsewhere.selectedSquare)
    }

    @Test
    fun theBoardStillFacesTheDeclaringPlayerWhileTheyDecide() {
        val before = beforeTheThirdOccurrence()
        val declared = tap(before, "f6", "g8")

        assertEquals(Side.BLACK, before.orientation)
        assertEquals(Side.BLACK, declared.orientation)
        assertEquals(Side.BLACK, GameControls.claimDeclaredDraw(declared, DrawClaim.THREEFOLD_REPETITION).orientation)
    }

    @Test
    fun aClaimedGameTakesNoFurtherInput() {
        val claimed =
            GameControls.claimDeclaredDraw(
                tap(beforeTheThirdOccurrence(), "f6", "g8"),
                DrawClaim.THREEFOLD_REPETITION,
            )

        val afterMoreTaps = tap(claimed, "e2", "e4", "g8", "f6")

        assertEquals(claimed.game, afterMoreTaps.game)
        assertNull(afterMoreTaps.declaredMove)
        assertFalse(GameControls.canUndo(afterMoreTaps))
        assertFalse(GameControls.canResign(afterMoreTaps))
        assertFalse(GameControls.canClaimDraw(afterMoreTaps))
    }

    @Test
    fun resigningWhileAMoveIsDeclaredEndsTheGameAndDropsTheDeclaration() {
        val declared = tap(beforeTheThirdOccurrence(), "f6", "g8")
        val resigned = GameControls.resign(declared, Side.BLACK)

        assertEquals(GameOutcome.WHITE_WINS, resigned.game.result?.outcome)
        assertEquals(TerminationReason.RESIGNATION, resigned.game.result?.reason)
        assertNull(resigned.declaredMove)
    }

    @Test
    fun takingTheLastMoveBackDropsTheDeclarationWithIt() {
        val declared = tap(beforeTheThirdOccurrence(), "f6", "g8")
        val undone = GameControls.undo(declared)

        assertNull(undone.declaredMove)
        assertEquals(6, undone.game.moves.size)
        assertEquals(Side.WHITE, undone.game.sideToMove)
    }

    /**
     * `1. Nf3 Nf6 2. Ng1 Ng8 3. Nf3 Nf6 4. Ng1`, where Black to move is one knight move
     * from the starting position for the third time.
     */
    private fun beforeTheThirdOccurrence(): BoardUiState =
        listOf("g1" to "f3", "g8" to "f6", "f3" to "g1", "f6" to "g8", "g1" to "f3", "g8" to "f6", "f3" to "g1")
            .fold(BoardUiState.newGame()) { state, (from, to) -> tap(state, from, to) }

    /** Rooks, a pawn, and two kings, so a quiet move, a capture, and a pawn move are all on offer. */
    private fun quietPosition(halfmoveClock: Int): BoardUiState =
        BoardUiState(
            ChessGame(
                GameState(
                    board =
                        board(
                            "a1" to Piece(Side.WHITE, PieceType.KING),
                            "d1" to Piece(Side.WHITE, PieceType.ROOK),
                            "b2" to Piece(Side.WHITE, PieceType.PAWN),
                            "h8" to Piece(Side.BLACK, PieceType.KING),
                            "d8" to Piece(Side.BLACK, PieceType.ROOK),
                        ),
                    sideToMove = Side.WHITE,
                    castlingRights = CastlingRights.NONE,
                    drawRuleState = DrawRuleState(halfmoveClock = halfmoveClock),
                ),
            ),
        )

    /** A white pawn one square from promoting, with the clock already at [halfmoveClock]. */
    private fun promotionPosition(halfmoveClock: Int): BoardUiState =
        BoardUiState(
            ChessGame(
                GameState(
                    board =
                        board(
                            "a1" to Piece(Side.WHITE, PieceType.KING),
                            "b7" to Piece(Side.WHITE, PieceType.PAWN),
                            "h8" to Piece(Side.BLACK, PieceType.KING),
                            "d6" to Piece(Side.BLACK, PieceType.ROOK),
                        ),
                    sideToMove = Side.WHITE,
                    castlingRights = CastlingRights.NONE,
                    drawRuleState = DrawRuleState(halfmoveClock = halfmoveClock),
                ),
            ),
        )

    private fun board(vararg pieces: Pair<String, Piece>): Board =
        Board.of(pieces.associate { (square, piece) -> Square.parse(square) to piece })

    private fun tap(
        state: BoardUiState,
        vararg squares: String,
    ): BoardUiState = squares.fold(state) { current, square -> BoardInteraction.onSquareTapped(current, Square.parse(square)) }
}
