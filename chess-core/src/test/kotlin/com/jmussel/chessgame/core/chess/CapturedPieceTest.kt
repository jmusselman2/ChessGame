package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The captured piece read from a recorded move (`D104`, `M23.1`): from the position the move
 * was played from, never by replaying it, through Undo, Redo and a matching continuation.
 */
class CapturedPieceTest {
    private fun played(
        vararg moves: String,
        from: ChessGame = ChessGame.newGame(),
    ): ChessGame =
        moves.fold(from) { game, move ->
            val promotion = move.getOrNull(4)?.let(PieceType::fromLetter)
            ChessRules.applyMove(game, Move.of(move.take(2), move.substring(2, 4), promotion))
        }

    private fun capturesIn(game: ChessGame): List<Piece> = game.history.mapNotNull(ChessRules::capturedPiece)

    private fun white(type: PieceType) = Piece(Side.WHITE, type)

    private fun black(type: PieceType) = Piece(Side.BLACK, type)

    /** White's pawn about to promote on a8, under Black's rook on b8. */
    private val promotion =
        ChessGame(
            GameState(
                board =
                    Board.of(
                        mapOf(
                            Square.parse("a7") to white(PieceType.PAWN),
                            Square.parse("e1") to white(PieceType.KING),
                            Square.parse("b8") to black(PieceType.ROOK),
                            Square.parse("h7") to black(PieceType.KING),
                        ),
                    ),
                sideToMove = Side.WHITE,
                castlingRights = CastlingRights.NONE,
            ),
        )

    @Test
    fun anOrdinaryCaptureIsThePieceThatStoodOnTheDestination() {
        val game = played("e2e4", "d7d5", "e4d5")

        assertEquals(black(PieceType.PAWN), ChessRules.capturedPiece(game.history.last()))
        assertEquals(listOf(black(PieceType.PAWN)), capturesIn(game))
    }

    @Test
    fun aPieceCaptureKeepsItsSideAndType() {
        // 1. e4 d5 2. Bb5+ c6 3. Bxc6+ Nxc6: each side has taken one piece.
        val game = played("e2e4", "d7d5", "f1b5", "c7c6", "b5c6", "b8c6")

        assertEquals(listOf(black(PieceType.PAWN), white(PieceType.BISHOP)), capturesIn(game))
    }

    @Test
    fun enPassantCapturesThePawnBesideTheDestination() {
        val game = played("e2e4", "a7a6", "e4e5", "d7d5", "e5d6")

        assertEquals(black(PieceType.PAWN), ChessRules.capturedPiece(game.history.last()))
    }

    @Test
    fun aPromotedPieceIsCapturedAsWhatItBecame() {
        val game = played("a7a8Q", "b8a8", from = promotion)

        assertNull(ChessRules.capturedPiece(game.history.first()), "promoting by advancing captures nothing")
        assertEquals(white(PieceType.QUEEN), ChessRules.capturedPiece(game.history.last()))
    }

    @Test
    fun aPromotionThatCapturesTakesThePieceOnTheDestination() {
        val game = played("a7b8N", from = promotion)

        assertEquals(black(PieceType.ROOK), ChessRules.capturedPiece(game.history.single()))
    }

    @Test
    fun aMoveThatCapturesNothingHasNoCapturedPiece() {
        val game = played("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6", "e1g1")

        game.history.forEach { record -> assertNull(ChessRules.capturedPiece(record), "${record.move}") }
    }

    @Test
    fun theQueryNeitherReplaysNorChangesTheRecord() {
        val game = played("e2e4", "d7d5", "e4d5")
        val record = game.history.last()
        val before = record.positionBefore

        ChessRules.capturedPiece(record)

        assertEquals(before, record.positionBefore)
        assertEquals(played("e2e4", "d7d5", "e4d5"), game)
    }

    @Test
    fun undoRemovesACaptureAndRedoRestoresIt() {
        val game = played("e2e4", "d7d5", "e4d5")
        val undone = ChessRules.undo(game, Side.WHITE)

        assertEquals(emptyList(), capturesIn(undone))
        assertEquals(listOf(black(PieceType.PAWN)), capturesIn(ChessRules.redo(undone)))
    }

    @Test
    fun aTwoPlyUndoRemovesBothCapturesAndRedoRestoresBoth() {
        val game = played("e2e4", "d7d5", "f1b5", "c7c6", "b5c6", "b8c6")
        val undone = ChessRules.undoLastMoves(game, plies = 2)

        assertEquals(emptyList(), capturesIn(undone))
        assertEquals(capturesIn(game), capturesIn(ChessRules.redo(undone)))
    }

    @Test
    fun aMatchingContinuationRestoresTheRecordedCapture() {
        val undone = ChessRules.undo(played("e2e4", "d7d5", "e4d5"), Side.WHITE)
        val continued = ChessRules.applyMove(undone, Move.of("e4", "d5"))

        assertEquals(listOf(black(PieceType.PAWN)), capturesIn(continued))
    }
}
