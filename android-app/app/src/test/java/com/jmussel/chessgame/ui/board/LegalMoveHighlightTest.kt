package com.jmussel.chessgame.ui.board

import com.jmussel.chessgame.core.chess.Board
import com.jmussel.chessgame.core.chess.CastlingRights
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.GameState
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Piece
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalMoveHighlightTest {
    private fun white(type: PieceType) = Piece(Side.WHITE, type)

    private fun black(type: PieceType) = Piece(Side.BLACK, type)

    private fun stateWith(
        vararg placement: Pair<String, Piece>,
        selected: String,
        sideToMove: Side = Side.WHITE,
    ): BoardUiState =
        BoardUiState(
            game =
                ChessGame(
                    GameState(
                        board = Board.of(placement.associate { (square, piece) -> Square.parse(square) to piece }),
                        sideToMove = sideToMove,
                        castlingRights = CastlingRights.NONE,
                    ),
                ),
            selectedSquare = Square.parse(selected),
        )

    private fun destinations(state: BoardUiState): Set<String> = BoardInteraction.legalDestinations(state).map { it.name }.toSet()

    private fun select(square: String): BoardUiState = BoardInteraction.onSquareTapped(BoardUiState.newGame(), Square.parse(square))

    @Test
    fun nothingIsHighlightedWithNoSelection() {
        assertTrue(BoardInteraction.legalDestinations(BoardUiState.newGame()).isEmpty())
    }

    @Test
    fun aSelectedPawnShowsItsTwoOpeningAdvances() {
        assertEquals(setOf("e3", "e4"), destinations(select("e2")))
    }

    @Test
    fun aSelectedKnightShowsItsTwoOpeningJumps() {
        assertEquals(setOf("a3", "c3"), destinations(select("b1")))
    }

    @Test
    fun aBlockedPieceShowsNothing() {
        assertTrue(destinations(select("c1")).isEmpty())
        assertTrue(destinations(select("e1")).isEmpty())
    }

    @Test
    fun theHighlightsMatchWhatGameCoreCallsLegal() {
        val state = select("e2")
        val fromGameCore =
            ChessRules
                .legalMoves(state.game)
                .filter { it.from == Square.parse("e2") }
                .map { it.to }
                .toSet()

        assertEquals(fromGameCore, BoardInteraction.legalDestinations(state))
    }

    @Test
    fun aPinnedPieceShowsOnlyTheMovesThatKeepTheKingSafe() {
        val state =
            stateWith(
                "e1" to white(PieceType.KING),
                "e2" to white(PieceType.ROOK),
                "e8" to black(PieceType.ROOK),
                "a8" to black(PieceType.KING),
                selected = "e2",
            )

        assertEquals(setOf("e3", "e4", "e5", "e6", "e7", "e8"), destinations(state))
        assertFalse(BoardInteraction.isLegalDestination(state, Square.parse("d2")))
    }

    @Test
    fun aCaptureIsHighlightedLikeAnyOtherDestination() {
        val state =
            stateWith(
                "e1" to white(PieceType.KING),
                "d1" to white(PieceType.ROOK),
                "d5" to black(PieceType.KNIGHT),
                "h8" to black(PieceType.KING),
                selected = "d1",
            )

        assertTrue(BoardInteraction.isLegalDestination(state, Square.parse("d5")))
        assertFalse(BoardInteraction.isLegalDestination(state, Square.parse("d6")))
    }

    @Test
    fun aPromotionSquareIsHighlightedOnceNotFourTimes() {
        val state =
            stateWith(
                "e1" to white(PieceType.KING),
                "a7" to white(PieceType.PAWN),
                "h8" to black(PieceType.KING),
                selected = "a7",
            )

        assertEquals(setOf("a8"), destinations(state))
        assertEquals(
            4,
            ChessRules.legalMoves(state.game).count { it.from == Square.parse("a7") },
        )
    }

    @Test
    fun castlingIsHighlightedAsTheKingsTwoSquareMove() {
        val state =
            BoardUiState(
                game =
                    ChessGame(
                        GameState(
                            board =
                                Board.of(
                                    mapOf(
                                        "e1" to white(PieceType.KING),
                                        "h1" to white(PieceType.ROOK),
                                        "e8" to black(PieceType.KING),
                                    ).mapKeys { (square, _) -> Square.parse(square) },
                                ),
                            sideToMove = Side.WHITE,
                            castlingRights = CastlingRights.ALL,
                        ),
                    ),
                selectedSquare = Square.parse("e1"),
            )

        assertTrue(BoardInteraction.isLegalDestination(state, Square.parse("g1")))
    }

    @Test
    fun clearingTheSelectionClearsTheHighlights() {
        val selected = select("e2")
        val cleared = BoardInteraction.onSquareTapped(selected, Square.parse("e2"))

        assertTrue(BoardInteraction.legalDestinations(cleared).isEmpty())
    }

    @Test
    fun theOpponentsPiecesAreNeverHighlighted() {
        val afterWhiteMoves =
            BoardUiState(ChessRules.applyMove(ChessGame.newGame(), Move.of("e2", "e4")))
        val tappedWhitePiece = BoardInteraction.onSquareTapped(afterWhiteMoves, Square.parse("e4"))

        assertTrue(BoardInteraction.legalDestinations(tappedWhitePiece).isEmpty())
    }
}
