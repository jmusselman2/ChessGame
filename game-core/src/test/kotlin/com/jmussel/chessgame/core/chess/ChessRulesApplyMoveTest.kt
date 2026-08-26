package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChessRulesApplyMoveTest {
    private fun white(type: PieceType) = Piece(Side.WHITE, type)

    private fun black(type: PieceType) = Piece(Side.BLACK, type)

    private fun state(
        vararg placement: Pair<String, Piece>,
        sideToMove: Side = Side.WHITE,
        rights: CastlingRights = CastlingRights.ALL,
        halfmoveClock: Int = 0,
        fullmoveNumber: Int = 1,
    ): GameState =
        GameState(
            board = Board.of(placement.associate { (square, piece) -> Square.parse(square) to piece }),
            sideToMove = sideToMove,
            castlingRights = rights,
            drawRuleState = DrawRuleState(halfmoveClock = halfmoveClock),
            fullmoveNumber = fullmoveNumber,
        )

    private fun kingsAnd(vararg placement: Pair<String, Piece>) =
        arrayOf("e1" to white(PieceType.KING), "e8" to black(PieceType.KING), *placement)

    @Test
    fun movesThePieceAndHandsTheTurnOver() {
        val position = state(*kingsAnd("d1" to white(PieceType.ROOK)), rights = CastlingRights.NONE)
        val after = ChessRules.applyMove(position, Move.of("d1", "d5"))

        assertEquals(white(PieceType.ROOK), after.board.pieceAt(Square.parse("d5")))
        assertTrue(after.board.isEmpty(Square.parse("d1")))
        assertEquals(Side.BLACK, after.sideToMove)
    }

    @Test
    fun countsFullmovesAfterEachBlackMove() {
        val position = state(*kingsAnd("h1" to white(PieceType.ROOK)), rights = CastlingRights.NONE)
        val afterWhite = ChessRules.applyMove(position, Move.of("e1", "d1"))
        val afterBlack = ChessRules.applyMove(afterWhite, Move.of("e8", "d8"))

        assertEquals(1, afterWhite.fullmoveNumber)
        assertEquals(2, afterBlack.fullmoveNumber)
    }

    @Test
    fun countsHalfmovesAndResetsThemOnAPawnMoveOrCapture() {
        val position =
            state(
                *kingsAnd("d1" to white(PieceType.ROOK), "a2" to white(PieceType.PAWN)),
                rights = CastlingRights.NONE,
                halfmoveClock = 5,
            )

        assertEquals(6, ChessRules.applyMove(position, Move.of("d1", "d5")).halfmoveClock)
        assertEquals(0, ChessRules.applyMove(position, Move.of("a2", "a3")).halfmoveClock)

        val withCapture =
            state(
                *kingsAnd("d1" to white(PieceType.ROOK), "d7" to black(PieceType.KNIGHT)),
                rights = CastlingRights.NONE,
                halfmoveClock = 5,
            )

        assertEquals(0, ChessRules.applyMove(withCapture, Move.of("d1", "d7")).halfmoveClock)
    }

    @Test
    fun aCapturedPieceLeavesTheBoard() {
        val position =
            state(
                *kingsAnd("d1" to white(PieceType.ROOK), "d7" to black(PieceType.KNIGHT)),
                rights = CastlingRights.NONE,
            )
        val after = ChessRules.applyMove(position, Move.of("d1", "d7"))

        assertEquals(white(PieceType.ROOK), after.board.pieceAt(Square.parse("d7")))
        assertEquals(position.board.pieceCount - 1, after.board.pieceCount)
    }

    @Test
    fun movingTheKingGivesUpBothCastlingRights() {
        val position =
            state(
                "e1" to white(PieceType.KING),
                "a1" to white(PieceType.ROOK),
                "h1" to white(PieceType.ROOK),
                "e8" to black(PieceType.KING),
            )
        val after = ChessRules.applyMove(position, Move.of("e1", "d1"))

        assertFalse(after.castlingRights.hasAny(Side.WHITE))
        assertTrue(after.castlingRights.hasAny(Side.BLACK))
    }

    @Test
    fun castlingItselfGivesUpBothRights() {
        val position =
            state(
                "e1" to white(PieceType.KING),
                "h1" to white(PieceType.ROOK),
                "e8" to black(PieceType.KING),
            )
        val after = ChessRules.applyMove(position, Move.of("e1", "g1"))

        assertFalse(after.castlingRights.hasAny(Side.WHITE))
        assertEquals(white(PieceType.ROOK), after.board.pieceAt(Square.parse("f1")))
    }

    @Test
    fun movingARookGivesUpThatSideOnly() {
        val position =
            state(
                "e1" to white(PieceType.KING),
                "a1" to white(PieceType.ROOK),
                "h1" to white(PieceType.ROOK),
                "e8" to black(PieceType.KING),
            )
        val after = ChessRules.applyMove(position, Move.of("h1", "h5"))

        assertFalse(after.castlingRights.has(Side.WHITE, CastlingSide.KING_SIDE))
        assertTrue(after.castlingRights.has(Side.WHITE, CastlingSide.QUEEN_SIDE))
    }

    @Test
    fun capturingARookOnItsHomeSquareGivesUpTheOpponentsRight() {
        val position =
            state(
                "e1" to white(PieceType.KING),
                "a2" to white(PieceType.ROOK),
                "a8" to black(PieceType.ROOK),
                "h8" to black(PieceType.ROOK),
                "e8" to black(PieceType.KING),
            )
        val after = ChessRules.applyMove(position, Move.of("a2", "a8"))

        assertFalse(after.castlingRights.has(Side.BLACK, CastlingSide.QUEEN_SIDE))
        assertTrue(after.castlingRights.has(Side.BLACK, CastlingSide.KING_SIDE))
    }

    @Test
    fun rightsThatWereAlreadyLostStayLost() {
        val position =
            state(
                "e1" to white(PieceType.KING),
                "a1" to white(PieceType.ROOK),
                "e8" to black(PieceType.KING),
                rights = CastlingRights.NONE,
            )
        val after = ChessRules.applyMove(position, Move.of("a1", "a5"))

        assertEquals(CastlingRights.NONE, after.castlingRights)
    }

    @Test
    fun leavesTheOriginalStateUnchanged() {
        val position = state(*kingsAnd("d1" to white(PieceType.ROOK)), rights = CastlingRights.NONE)
        ChessRules.applyMove(position, Move.of("d1", "d5"))

        assertEquals(white(PieceType.ROOK), position.board.pieceAt(Square.parse("d1")))
        assertEquals(Side.WHITE, position.sideToMove)
    }

    @Test
    fun leavesTheResultUnsetWhileTheGameContinues() {
        val position = state(*kingsAnd("d1" to white(PieceType.ROOK)), rights = CastlingRights.NONE)

        assertNull(ChessRules.applyMove(position, Move.of("d1", "d5")).result)
    }

    @Test
    fun rejectsAnIllegalMove() {
        val position = state(*kingsAnd("d1" to white(PieceType.ROOK)), rights = CastlingRights.NONE)

        assertFailsWith<IllegalArgumentException> { ChessRules.applyMove(position, Move.of("d1", "e2")) }
        assertFailsWith<IllegalArgumentException> { ChessRules.applyMove(position, Move.of("e8", "e7")) }
    }

    @Test
    fun rejectsAMoveInAFinishedGame() {
        val position =
            state(*kingsAnd("d1" to white(PieceType.ROOK)), rights = CastlingRights.NONE)
                .copy(result = GameResult.resignation(loser = Side.BLACK))

        assertFailsWith<IllegalArgumentException> { ChessRules.applyMove(position, Move.of("d1", "d5")) }
    }

    @Test
    fun playsTheOpeningMovesOfARealGame() {
        var position = StandardPosition.newGame()

        listOf(
            Move.of("e2", "e4"),
            Move.of("e7", "e5"),
            Move.of("g1", "f3"),
            Move.of("b8", "c6"),
            Move.of("f1", "b5"),
        ).forEach { position = ChessRules.applyMove(position, it) }

        assertEquals(white(PieceType.BISHOP), position.board.pieceAt(Square.parse("b5")))
        assertEquals(Side.BLACK, position.sideToMove)
        assertEquals(3, position.fullmoveNumber)
        assertEquals(3, position.halfmoveClock)
        assertEquals(CastlingRights.ALL, position.castlingRights)
        assertNull(position.enPassantTarget)
    }
}
