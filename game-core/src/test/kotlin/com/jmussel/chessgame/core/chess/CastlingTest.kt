package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CastlingTest {
    private fun white(type: PieceType) = Piece(Side.WHITE, type)

    private fun black(type: PieceType) = Piece(Side.BLACK, type)

    private fun state(
        vararg placement: Pair<String, Piece>,
        sideToMove: Side = Side.WHITE,
        rights: CastlingRights = CastlingRights.ALL,
    ): GameState =
        GameState(
            board = Board.of(placement.associate { (square, piece) -> Square.parse(square) to piece }),
            sideToMove = sideToMove,
            castlingRights = rights,
        )

    /** White king and both rooks at home, Black king out of the way. */
    private fun whiteReady(
        vararg extra: Pair<String, Piece>,
        rights: CastlingRights = CastlingRights.ALL,
        sideToMove: Side = Side.WHITE,
    ): GameState =
        state(
            "e1" to white(PieceType.KING),
            "a1" to white(PieceType.ROOK),
            "h1" to white(PieceType.ROOK),
            "e8" to black(PieceType.KING),
            *extra,
            sideToMove = sideToMove,
            rights = rights,
        )

    private fun castlingMoves(state: GameState): Set<String> = Castling.availableMoves(state).map { it.toString() }.toSet()

    @Test
    fun namesTheStandardCastlingSquares() {
        assertEquals(Square.parse("e1"), Castling.kingOrigin(Side.WHITE))
        assertEquals(Square.parse("e8"), Castling.kingOrigin(Side.BLACK))
        assertEquals(Square.parse("g1"), Castling.kingDestination(Side.WHITE, CastlingSide.KING_SIDE))
        assertEquals(Square.parse("c1"), Castling.kingDestination(Side.WHITE, CastlingSide.QUEEN_SIDE))
        assertEquals(Square.parse("g8"), Castling.kingDestination(Side.BLACK, CastlingSide.KING_SIDE))
        assertEquals(Square.parse("c8"), Castling.kingDestination(Side.BLACK, CastlingSide.QUEEN_SIDE))
        assertEquals(Square.parse("h1"), Castling.rookOrigin(Side.WHITE, CastlingSide.KING_SIDE))
        assertEquals(Square.parse("a1"), Castling.rookOrigin(Side.WHITE, CastlingSide.QUEEN_SIDE))
        assertEquals(Square.parse("f1"), Castling.rookDestination(Side.WHITE, CastlingSide.KING_SIDE))
        assertEquals(Square.parse("d1"), Castling.rookDestination(Side.WHITE, CastlingSide.QUEEN_SIDE))
    }

    @Test
    fun bothCastlingsAreAvailableWhenNothingIsInTheWay() {
        assertEquals(setOf("e1g1", "e1c1"), castlingMoves(whiteReady()))
    }

    @Test
    fun blackCastlesOnItsOwnBackRank() {
        val position =
            state(
                "e8" to black(PieceType.KING),
                "a8" to black(PieceType.ROOK),
                "h8" to black(PieceType.ROOK),
                "e1" to white(PieceType.KING),
                sideToMove = Side.BLACK,
            )

        assertEquals(setOf("e8g8", "e8c8"), castlingMoves(position))
    }

    @Test
    fun castlingRequiresTheRight() {
        assertEquals(
            setOf("e1c1"),
            castlingMoves(whiteReady(rights = CastlingRights.ALL.without(Side.WHITE, CastlingSide.KING_SIDE))),
        )
        assertEquals(
            setOf("e1g1"),
            castlingMoves(whiteReady(rights = CastlingRights.ALL.without(Side.WHITE, CastlingSide.QUEEN_SIDE))),
        )
        assertTrue(castlingMoves(whiteReady(rights = CastlingRights.NONE)).isEmpty())
    }

    @Test
    fun castlingRequiresTheRookToStillBeThere() {
        val position =
            state(
                "e1" to white(PieceType.KING),
                "a1" to white(PieceType.ROOK),
                "e8" to black(PieceType.KING),
            )

        assertEquals(setOf("e1c1"), castlingMoves(position))
    }

    @Test
    fun castlingRequiresTheKingOnItsHomeSquare() {
        val position =
            state(
                "e2" to white(PieceType.KING),
                "a1" to white(PieceType.ROOK),
                "h1" to white(PieceType.ROOK),
                "e8" to black(PieceType.KING),
            )

        assertTrue(castlingMoves(position).isEmpty())
    }

    @Test
    fun everySquareBetweenKingAndRookMustBeEmpty() {
        assertFalse(castlingMoves(whiteReady("f1" to white(PieceType.BISHOP))).contains("e1g1"))
        assertFalse(castlingMoves(whiteReady("g1" to white(PieceType.KNIGHT))).contains("e1g1"))
        assertFalse(castlingMoves(whiteReady("b1" to white(PieceType.KNIGHT))).contains("e1c1"))
        assertFalse(castlingMoves(whiteReady("c1" to white(PieceType.BISHOP))).contains("e1c1"))
        assertFalse(castlingMoves(whiteReady("d1" to white(PieceType.QUEEN))).contains("e1c1"))
    }

    @Test
    fun anEnemyPieceInThePathAlsoBlocksCastling() {
        assertFalse(castlingMoves(whiteReady("g1" to black(PieceType.KNIGHT))).contains("e1g1"))
    }

    @Test
    fun aKingInCheckMayNotCastle() {
        val position = whiteReady("e5" to black(PieceType.ROOK))

        assertTrue(Attacks.isInCheck(position.board, Side.WHITE))
        assertTrue(castlingMoves(position).isEmpty())
    }

    @Test
    fun theKingMayNotCrossAnAttackedSquare() {
        assertFalse(castlingMoves(whiteReady("f5" to black(PieceType.ROOK))).contains("e1g1"))
        assertFalse(castlingMoves(whiteReady("d5" to black(PieceType.ROOK))).contains("e1c1"))
    }

    @Test
    fun theKingMayNotLandOnAnAttackedSquare() {
        assertFalse(castlingMoves(whiteReady("g5" to black(PieceType.ROOK))).contains("e1g1"))
        assertFalse(castlingMoves(whiteReady("c5" to black(PieceType.ROOK))).contains("e1c1"))
    }

    @Test
    fun anAttackedSquareBesideTheQueenSideRookDoesNotPreventCastling() {
        val position = whiteReady("b5" to black(PieceType.ROOK))

        assertTrue(
            castlingMoves(position).contains("e1c1"),
            "b1 must be empty but need not be unattacked",
        )
    }

    @Test
    fun anAttackedRookDoesNotPreventCastling() {
        assertTrue(castlingMoves(whiteReady("h5" to black(PieceType.ROOK))).contains("e1g1"))
        assertTrue(castlingMoves(whiteReady("a5" to black(PieceType.ROOK))).contains("e1c1"))
    }

    @Test
    fun castlingMovesTheRookOverTheKing() {
        val position = whiteReady()
        val kingSide = LegalMoves.boardAfter(position.board, Move.of("e1", "g1"))

        assertEquals(white(PieceType.KING), kingSide.pieceAt(Square.parse("g1")))
        assertEquals(white(PieceType.ROOK), kingSide.pieceAt(Square.parse("f1")))
        assertTrue(kingSide.isEmpty(Square.parse("e1")))
        assertTrue(kingSide.isEmpty(Square.parse("h1")))

        val queenSide = LegalMoves.boardAfter(position.board, Move.of("e1", "c1"))

        assertEquals(white(PieceType.KING), queenSide.pieceAt(Square.parse("c1")))
        assertEquals(white(PieceType.ROOK), queenSide.pieceAt(Square.parse("d1")))
        assertTrue(queenSide.isEmpty(Square.parse("e1")))
        assertTrue(queenSide.isEmpty(Square.parse("a1")))
    }

    @Test
    fun blackCastlingAlsoMovesItsRook() {
        val position =
            state(
                "e8" to black(PieceType.KING),
                "h8" to black(PieceType.ROOK),
                "e1" to white(PieceType.KING),
                sideToMove = Side.BLACK,
            )
        val after = LegalMoves.boardAfter(position.board, Move.of("e8", "g8"))

        assertEquals(black(PieceType.KING), after.pieceAt(Square.parse("g8")))
        assertEquals(black(PieceType.ROOK), after.pieceAt(Square.parse("f8")))
    }

    @Test
    fun recognisesACastlingMoveOnlyForATwoSquareKingMove() {
        val position = whiteReady().board

        assertTrue(Castling.isCastlingMove(position, Move.of("e1", "g1")))
        assertTrue(Castling.isCastlingMove(position, Move.of("e1", "c1")))
        assertFalse(Castling.isCastlingMove(position, Move.of("e1", "f1")))
        assertFalse(Castling.isCastlingMove(position, Move.of("a1", "c1")))
        assertEquals(CastlingSide.KING_SIDE, Castling.castlingSideOf(Move.of("e1", "g1")))
        assertEquals(CastlingSide.QUEEN_SIDE, Castling.castlingSideOf(Move.of("e1", "c1")))
    }

    @Test
    fun castlingAppearsInTheLegalMovesForTheSideToMove() {
        val position = whiteReady()
        val moves = LegalMoves.forSideToMove(position)

        assertTrue(moves.contains(Move.of("e1", "g1")))
        assertTrue(moves.contains(Move.of("e1", "c1")))
        assertTrue(LegalMoves.isLegal(position, Move.of("e1", "g1")))
    }

    @Test
    fun onlyTheSideToMoveGetsCastlingMoves() {
        val position =
            state(
                "e1" to white(PieceType.KING),
                "h1" to white(PieceType.ROOK),
                "e8" to black(PieceType.KING),
                "h8" to black(PieceType.ROOK),
                sideToMove = Side.WHITE,
            )

        assertEquals(setOf("e1g1"), castlingMoves(position))
        assertEquals(setOf("e8g8"), castlingMoves(position.copy(sideToMove = Side.BLACK)))
    }

    @Test
    fun theStartingPositionHasNoCastlingMoves() {
        assertTrue(Castling.availableMoves(StandardPosition.newGame()).isEmpty())
    }
}
