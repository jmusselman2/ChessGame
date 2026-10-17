package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The current position's material balance (`D104`, `M23.1`): White's live non-king material
 * minus Black's, in exact quarter points on the owner's 9/5/3.25/3/1 scale.
 */
class MaterialBalanceTest {
    private fun white(type: PieceType) = Piece(Side.WHITE, type)

    private fun black(type: PieceType) = Piece(Side.BLACK, type)

    private fun state(
        vararg placement: Pair<String, Piece>,
        sideToMove: Side = Side.WHITE,
    ): GameState =
        GameState(
            board = Board.of(placement.associate { (square, piece) -> Square.parse(square) to piece }),
            sideToMove = sideToMove,
            castlingRights = CastlingRights.NONE,
        )

    private fun play(
        game: ChessGame,
        vararg moves: String,
    ): ChessGame =
        moves.fold(game) { current, move ->
            val promotion = move.getOrNull(4)?.let(PieceType::fromLetter)
            ChessRules.applyMove(current, Move.of(move.take(2), move.substring(2, 4), promotion))
        }

    @Test
    fun theScaleIsTheOwnersInQuarterPoints() {
        assertEquals(36, Material.quarterPoints(PieceType.QUEEN))
        assertEquals(20, Material.quarterPoints(PieceType.ROOK))
        assertEquals(13, Material.quarterPoints(PieceType.BISHOP))
        assertEquals(12, Material.quarterPoints(PieceType.KNIGHT))
        assertEquals(4, Material.quarterPoints(PieceType.PAWN))
        assertEquals(0, Material.quarterPoints(PieceType.KING))
    }

    @Test
    fun theStartingPositionIsEqual() {
        assertEquals(0, ChessRules.materialBalance(StandardPosition.newGame()))
        // Each side: eight pawns, two each of rooks, bishops and knights, and a queen.
        assertEquals(32 + 40 + 26 + 24 + 36, Material.total(StandardPosition.BOARD, Side.WHITE))
    }

    @Test
    fun aBishopIsAQuarterPointMoreThanAKnight() {
        val bishopAgainstKnight =
            state(
                "e1" to white(PieceType.KING),
                "c1" to white(PieceType.BISHOP),
                "e8" to black(PieceType.KING),
                "b8" to black(PieceType.KNIGHT),
            )

        assertEquals(1, ChessRules.materialBalance(bishopAgainstKnight))
        assertEquals(
            -1,
            ChessRules.materialBalance(
                state(
                    "e1" to white(PieceType.KING),
                    "c1" to white(PieceType.KNIGHT),
                    "e8" to black(PieceType.KING),
                    "b8" to black(PieceType.BISHOP),
                ),
            ),
        )
    }

    @Test
    fun aCaptureChangesTheBalanceBySideAndValue() {
        val game = play(ChessGame.newGame(), "e2e4", "d7d5", "e4d5")

        assertEquals(4, ChessRules.materialBalance(game.state), "White took a pawn")

        // 2... Qxd5 3. Nc3 Qxa2: Black has taken two pawns to White's one.
        val black = play(game, "d8d5", "b1c3", "d5a2")
        assertEquals(-4, ChessRules.materialBalance(black.state))

        // 4. Nb5 Qxa1: and a rook.
        assertEquals(-24, ChessRules.materialBalance(play(black, "c3b5", "a2a1").state))
    }

    @Test
    fun aPromotedPieceCountsAtItsCurrentValue() {
        val start =
            ChessGame(
                state(
                    "a7" to white(PieceType.PAWN),
                    "e1" to white(PieceType.KING),
                    "b8" to black(PieceType.ROOK),
                    "h7" to black(PieceType.KING),
                ),
            )
        assertEquals(4 - 20, ChessRules.materialBalance(start.state), "before promotion: a pawn against a rook")

        val promoted = play(start, "a7a8Q")
        assertEquals(36 - 20, ChessRules.materialBalance(promoted.state), "after promotion: a queen against a rook")

        val recaptured = play(promoted, "b8a8")
        assertEquals(-20, ChessRules.materialBalance(recaptured.state), "after the promoted queen is captured")

        // The difference between the captured-piece totals would say −36 here: wrong.
        val capturedTotals = recaptured.history.mapNotNull(ChessRules::capturedPiece).sumOf { Material.quarterPoints(it.type) }
        assertEquals(36, capturedTotals)
    }

    @Test
    fun undoAndRedoMoveTheBalanceWithThePosition() {
        val game = play(ChessGame.newGame(), "e2e4", "d7d5", "e4d5")
        val undone = ChessRules.undo(game, Side.WHITE)

        assertEquals(0, ChessRules.materialBalance(undone.state))
        assertEquals(4, ChessRules.materialBalance(ChessRules.redo(undone).state))
    }
}
