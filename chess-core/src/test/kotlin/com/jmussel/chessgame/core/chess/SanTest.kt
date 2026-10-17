package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Standard algebraic notation (`M22.6`): a pure function of the position before a legal move
 * and the move.
 */
class SanTest {
    private fun white(type: PieceType) = Piece(Side.WHITE, type)

    private fun black(type: PieceType) = Piece(Side.BLACK, type)

    private fun state(
        vararg placement: Pair<String, Piece>,
        sideToMove: Side = Side.WHITE,
        castling: CastlingRights = CastlingRights.NONE,
        enPassant: String? = null,
    ): GameState =
        GameState(
            board = Board.of(placement.associate { (square, piece) -> Square.parse(square) to piece }),
            sideToMove = sideToMove,
            castlingRights = castling,
            enPassantTarget = enPassant?.let(Square::parse),
        )

    private fun played(vararg moves: String): GameState =
        moves.fold(ChessGame.newGame()) { game, move -> ChessRules.applyMove(game, move(move)) }.state

    private fun move(text: String): Move =
        Move(Square.parse(text.take(2)), Square.parse(text.substring(2, 4)), text.getOrNull(4)?.let(PieceType::fromLetter))

    private fun san(
        state: GameState,
        move: String,
    ): String = ChessRules.san(state, move(move))

    @Test
    fun piecesAreNamedByLetterAndPawnsByNone() {
        val start = ChessGame.newGame().state

        assertEquals("e4", san(start, "e2e4"))
        assertEquals("Nf3", san(start, "g1f3"))
        assertEquals("Bc4", san(played("e2e4", "e7e5"), "f1c4"))
        assertEquals("Qh5", san(played("e2e4", "e7e5"), "d1h5"))
        assertEquals("Ke2", san(played("e2e4", "e7e5"), "e1e2"))
        assertEquals("Ra3", san(played("a2a4", "a7a5"), "a1a3"))
    }

    @Test
    fun capturesUseXAndAPawnCaptureNamesItsFile() {
        val scandinavian = played("e2e4", "d7d5")

        assertEquals("exd5", san(scandinavian, "e4d5"))
        assertEquals("Bxf7+", san(played("e2e4", "e7e5", "f1c4", "a7a6"), "c4f7"))
    }

    @Test
    fun enPassantIsAnOrdinaryPawnCapture() {
        val beforeEnPassant = played("e2e4", "a7a6", "e4e5", "d7d5")

        assertEquals("exd6", san(beforeEnPassant, "e5d6"))
    }

    @Test
    fun disambiguationUsesTheFileWhenThatIsEnough() {
        val knights =
            state(
                "b1" to white(PieceType.KNIGHT),
                "f3" to white(PieceType.KNIGHT),
                "e1" to white(PieceType.KING),
                "h8" to black(PieceType.KING),
            )

        assertEquals("Nbd2", san(knights, "b1d2"))
        assertEquals("Nfd2", san(knights, "f3d2"))
    }

    @Test
    fun disambiguationUsesTheRankWhenTheFilesAgree() {
        val rooks =
            state(
                "e1" to white(PieceType.ROOK),
                "e5" to white(PieceType.ROOK),
                "a1" to white(PieceType.KING),
                "h8" to black(PieceType.KING),
            )

        assertEquals("R1e2", san(rooks, "e1e2"))
        assertEquals("R5e2", san(rooks, "e5e2"))
    }

    @Test
    fun threeQueensThatCanAllReachOneSquareNeedTheWholeSquare() {
        val queens =
            state(
                "h4" to white(PieceType.QUEEN),
                "e4" to white(PieceType.QUEEN),
                "h1" to white(PieceType.QUEEN),
                "a8" to white(PieceType.KING),
                "b6" to black(PieceType.KING),
            )

        assertEquals("Qh4e1", san(queens, "h4e1"))
        assertEquals("Qee1", san(queens, "e4e1"))
        assertEquals("Q1e1", san(queens, "h1e1"))
    }

    @Test
    fun aPieceThatCannotLegallyMoveThereDoesNotNeedTellingApart() {
        // The knight on c3 is pinned to its king, so only the one on g1 can go to e2.
        val pinned =
            state(
                "c3" to white(PieceType.KNIGHT),
                "g1" to white(PieceType.KNIGHT),
                "d4" to white(PieceType.KING),
                "b2" to black(PieceType.BISHOP),
                "h8" to black(PieceType.KING),
            )

        assertEquals("Ne2", san(pinned, "g1e2"))
    }

    @Test
    fun castlingIsWrittenWithOs() {
        val readyToCastle =
            state(
                "e1" to white(PieceType.KING),
                "h1" to white(PieceType.ROOK),
                "a1" to white(PieceType.ROOK),
                "e8" to black(PieceType.KING),
                "a7" to black(PieceType.PAWN),
                castling = CastlingRights(whiteKingSide = true, whiteQueenSide = true, blackKingSide = false, blackQueenSide = false),
            )

        assertEquals("O-O", san(readyToCastle, "e1g1"))
        assertEquals("O-O-O", san(readyToCastle, "e1c1"))
    }

    @Test
    fun castlingThatGivesCheckSaysSo() {
        val rookChecksOnF =
            state(
                "e1" to white(PieceType.KING),
                "h1" to white(PieceType.ROOK),
                "f8" to black(PieceType.KING),
                castling = CastlingRights(whiteKingSide = true, whiteQueenSide = false, blackKingSide = false, blackQueenSide = false),
            )

        assertEquals("O-O+", san(rookChecksOnF, "e1g1"))
    }

    @Test
    fun promotionNamesThePieceChosen() {
        val aboutToPromote =
            state(
                "a7" to white(PieceType.PAWN),
                "e1" to white(PieceType.KING),
                "h1" to black(PieceType.KING),
            )

        assertEquals("a8=Q+", san(aboutToPromote, "a7a8Q"))
        assertEquals("a8=R", san(aboutToPromote, "a7a8R"))
        assertEquals("a8=B+", san(aboutToPromote, "a7a8B"))
        assertEquals("a8=N", san(aboutToPromote, "a7a8N"))
    }

    @Test
    fun aPromotionThatCapturesAndGivesCheck() {
        val capturePromote =
            state(
                "g7" to white(PieceType.PAWN),
                "e1" to white(PieceType.KING),
                "h8" to black(PieceType.ROOK),
                "d8" to black(PieceType.KING),
            )

        assertEquals("gxh8=Q+", san(capturePromote, "g7h8Q"))
        assertEquals("gxh8=N", san(capturePromote, "g7h8N"))
    }

    @Test
    fun checkAndCheckmateAreMarked() {
        assertEquals("Qh4#", san(played("f2f3", "e7e5", "g2g4"), "d8h4"))
        assertEquals("Bb5+", san(played("e2e4", "d7d6"), "f1b5"))
    }

    @Test
    fun anIllegalMoveIsRefused() {
        val start = ChessGame.newGame().state

        assertFailsWith<IllegalArgumentException> { san(start, "e2e5") }
        assertFailsWith<IllegalArgumentException> { san(start, "e7e5") }
    }
}
