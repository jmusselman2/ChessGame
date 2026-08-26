package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoveCountDrawsTest {
    private fun white(type: PieceType) = Piece(Side.WHITE, type)

    private fun black(type: PieceType) = Piece(Side.BLACK, type)

    /** Kings and rooks far apart, so quiet moves are always available. */
    private fun position(
        halfmoveClock: Int,
        sideToMove: Side = Side.WHITE,
        extra: Map<String, Piece> = emptyMap(),
    ): GameState =
        GameState(
            board =
                Board.of(
                    (
                        mapOf(
                            "a1" to white(PieceType.KING),
                            "d1" to white(PieceType.ROOK),
                            "h8" to black(PieceType.KING),
                            "e8" to black(PieceType.ROOK),
                        ) + extra
                    ).mapKeys { (square, _) -> Square.parse(square) },
                ),
            sideToMove = sideToMove,
            castlingRights = CastlingRights.NONE,
            drawRuleState = DrawRuleState(halfmoveClock = halfmoveClock),
        )

    @Test
    fun theClaimNeedsAHundredHalfmoves() {
        assertFalse(MoveCountDraws.canClaimFiftyMove(position(halfmoveClock = 99)))
        assertTrue(MoveCountDraws.canClaimFiftyMove(position(halfmoveClock = 100)))
        assertTrue(MoveCountDraws.canClaimFiftyMove(position(halfmoveClock = 101)))
    }

    @Test
    fun theFiftyMoveDrawIsClaimableRatherThanAutomatic() {
        val reached = ChessRules.applyMove(position(halfmoveClock = 99), Move.of("d1", "d2"))

        assertEquals(100, reached.halfmoveClock)
        assertFalse(reached.isOver, "the fifty-move rule must be claimed")
        assertTrue(MoveCountDraws.canClaimFiftyMove(reached))
        assertNull(reached.result)
    }

    @Test
    fun theSeventyFiveMoveDrawEndsTheGameAutomatically() {
        val reached = ChessRules.applyMove(position(halfmoveClock = 149), Move.of("d1", "d2"))

        assertEquals(150, reached.halfmoveClock)
        assertTrue(reached.isOver)
        assertEquals(GameResult.draw(TerminationReason.SEVENTY_FIVE_MOVE_RULE), reached.result)
    }

    @Test
    fun oneHalfmoveShortOfSeventyFiveMovesTheGameContinues() {
        val reached = ChessRules.applyMove(position(halfmoveClock = 148), Move.of("d1", "d2"))

        assertEquals(149, reached.halfmoveClock)
        assertFalse(reached.isOver)
        assertFalse(MoveCountDraws.isSeventyFiveMoveDraw(reached))
    }

    @Test
    fun checkmateOnTheSeventyFifthMoveIsStillCheckmate() {
        val mateInOne =
            GameState(
                board =
                    Board.of(
                        mapOf(
                            "a1" to white(PieceType.KING),
                            "b7" to white(PieceType.ROOK),
                            "c6" to white(PieceType.ROOK),
                            "h8" to black(PieceType.KING),
                        ).mapKeys { (square, _) -> Square.parse(square) },
                    ),
                sideToMove = Side.WHITE,
                castlingRights = CastlingRights.NONE,
                drawRuleState = DrawRuleState(halfmoveClock = 149),
            )
        val reached = ChessRules.applyMove(mateInOne, Move.of("c6", "c8"))

        assertEquals(150, reached.halfmoveClock)
        assertEquals(GameResult.checkmate(loser = Side.BLACK), reached.result)
    }

    @Test
    fun aPawnMoveResetsTheCounter() {
        val withPawn = position(halfmoveClock = 120, extra = mapOf("a2" to white(PieceType.PAWN)))
        val reached = ChessRules.applyMove(withPawn, Move.of("a2", "a3"))

        assertEquals(0, reached.halfmoveClock)
        assertFalse(MoveCountDraws.canClaimFiftyMove(reached))
    }

    @Test
    fun aCaptureResetsTheCounter() {
        val withTarget = position(halfmoveClock = 120, extra = mapOf("d7" to black(PieceType.KNIGHT)))
        val reached = ChessRules.applyMove(withTarget, Move.of("d1", "d7"))

        assertEquals(0, reached.halfmoveClock)
    }

    @Test
    fun aQuietMoveAdvancesTheCounter() {
        val reached = ChessRules.applyMove(position(halfmoveClock = 7), Move.of("d1", "d2"))

        assertEquals(8, reached.halfmoveClock)
    }

    @Test
    fun theCounterRunsAcrossBothSides() {
        var state = position(halfmoveClock = 0)

        listOf(
            Move.of("d1", "d2"),
            Move.of("e8", "e7"),
            Move.of("d2", "d3"),
            Move.of("e7", "e6"),
        ).forEach { state = ChessRules.applyMove(state, it) }

        assertEquals(4, state.halfmoveClock)
    }

    @Test
    fun aFinishedGameHasNothingLeftToClaim() {
        val finished =
            position(halfmoveClock = 120).copy(result = GameResult.resignation(loser = Side.WHITE))

        assertFalse(MoveCountDraws.canClaimFiftyMove(finished))
    }

    @Test
    fun aNewGameStartsAtZero() {
        val newGame = StandardPosition.newGame()

        assertEquals(0, newGame.halfmoveClock)
        assertFalse(MoveCountDraws.canClaimFiftyMove(newGame))
        assertFalse(MoveCountDraws.isSeventyFiveMoveDraw(newGame))
    }
}
