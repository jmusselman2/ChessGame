package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class M2CollectionImmutabilityTest {
    @Test
    fun boardSnapshotsConstructorPlacement() {
        val square = Square.parse("e1")
        val piece = Piece(Side.WHITE, PieceType.KING)
        val source = mutableMapOf(square to piece)
        val board = Board.of(source)

        source.clear()

        assertEquals(piece, board.pieceAt(square))
    }

    @Test
    fun boardQueryResultsDoNotExposePlacement() {
        val board = StandardPosition.BOARD

        @Suppress("UNCHECKED_CAST")
        val occupied = board.occupiedSquares() as MutableList<Pair<Square, Piece>>

        @Suppress("UNCHECKED_CAST")
        val whiteSquares = board.squaresOf(Side.WHITE) as MutableList<Square>
        occupied.clear()
        whiteSquares.clear()

        assertEquals(32, board.pieceCount)
        assertEquals(16, board.squaresOf(Side.WHITE).size)
    }

    @Test
    fun chessGameSnapshotsConstructorHistory() {
        val source = mutableListOf<MoveRecord>()
        val game = ChessGame(StandardPosition.newGame(), source)

        source += moveRecord("e2", "e4")

        assertTrue(game.history.isEmpty())
    }

    @Test
    fun publishedChessGameHistoryCannotBeMutated() {
        val game =
            listOf(Move.of("e2", "e4"), Move.of("e7", "e5")).fold(ChessGame.newGame()) { current, move ->
                ChessRules.applyMove(current, move)
            }
        val expected = game.history.toList()

        @Suppress("UNCHECKED_CAST")
        val exposed = game.history as MutableList<MoveRecord>
        runCatching { exposed.clear() }

        assertEquals(expected, game.history)
    }

    @Test
    fun derivedMoveListDoesNotExposeHistory() {
        val game =
            listOf(Move.of("e2", "e4"), Move.of("e7", "e5")).fold(ChessGame.newGame()) { current, move ->
                ChessRules.applyMove(current, move)
            }

        @Suppress("UNCHECKED_CAST")
        val moves = game.moves as MutableList<Move>
        moves.clear()

        assertEquals(2, game.history.size)
        assertEquals(listOf(Move.of("e2", "e4"), Move.of("e7", "e5")), game.moves)
    }

    @Test
    fun squareRegistryCannotBeMutated() {
        assertSharedListCannotBeChanged({ Square.ALL }, 0, Square.parse("h8"))
        assertEquals(Square.parse("a1"), Square.ofIndex(0))
    }

    @Test
    fun promotionChoicesCannotBeMutated() {
        assertSharedListCannotBeChanged({ PieceType.PROMOTION_CHOICES }, 0, PieceType.PAWN)
        assertEquals(
            listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT),
            PieceType.PROMOTION_CHOICES,
        )
    }

    @Test
    fun standardBackRankCannotBeMutated() {
        assertSharedListCannotBeChanged({ StandardPosition.BACK_RANK }, 0, PieceType.QUEEN)
        assertEquals(PieceType.ROOK, StandardPosition.BACK_RANK.first())
    }

    @Test
    fun orthogonalDirectionsCannotBeMutated() {
        assertSharedListCannotBeChanged({ Direction.ORTHOGONAL }, 0, Direction.NORTH_EAST)
        assertEquals(Direction.NORTH, Direction.ORTHOGONAL.first())
    }

    @Test
    fun diagonalDirectionsCannotBeMutated() {
        assertSharedListCannotBeChanged({ Direction.DIAGONAL }, 0, Direction.NORTH)
        assertEquals(Direction.NORTH_EAST, Direction.DIAGONAL.first())
    }

    @Test
    fun allDirectionsCannotBeMutated() {
        assertSharedListCannotBeChanged({ Direction.ALL }, 0, Direction.NORTH_EAST)
        assertEquals(Direction.NORTH, Direction.ALL.first())
    }

    @Test
    fun knightStepsCannotBeMutated() {
        assertSharedListCannotBeChanged({ PseudoLegalMoves.KNIGHT_STEPS }, 0, Direction(0, 0))
        assertEquals(Direction(1, 2), PseudoLegalMoves.KNIGHT_STEPS.first())
    }

    private fun moveRecord(
        from: String,
        to: String,
    ): MoveRecord = MoveRecord(Move.of(from, to), StandardPosition.newGame())

    private fun <T> assertSharedListCannotBeChanged(
        list: () -> List<T>,
        index: Int,
        replacement: T,
    ) {
        val exposed = list()
        val original = exposed[index]
        try {
            @Suppress("UNCHECKED_CAST")
            val mutable = exposed as MutableList<T>
            runCatching { mutable[index] = replacement }

            assertEquals(original, list()[index], "shared collection accepted replacement with $replacement")
        } finally {
            if (list()[index] != original) {
                @Suppress("UNCHECKED_CAST")
                val mutable = exposed as MutableList<T>
                mutable[index] = original
            }
        }
    }
}
