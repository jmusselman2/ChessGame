package com.jmussel.chessgame.ui.board

import com.jmussel.chessgame.core.chess.Board
import com.jmussel.chessgame.core.chess.CastlingRights
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.DrawRuleState
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
import org.junit.Assert.assertTrue
import org.junit.Test

/** Independent evaluator coverage retained after the M5 remediation cycle. */
class M5IndependentReevaluationTest {
    @Test
    fun everyLegalDestinationInRepresentativePositionsFollowsTheGameCoreOutcome() {
        representativeGames().forEachIndexed { positionIndex, game ->
            val movesByDestination = ChessRules.legalMoves(game).groupBy { it.from to it.to }

            movesByDestination.forEach { (squares, moves) ->
                val (from, to) = squares
                val initial = BoardUiState(game = game, orientation = game.sideToMove)
                val selected = BoardInteraction.onSquareTapped(initial, from)

                assertEquals("position $positionIndex did not select $from", from, selected.selectedSquare)
                assertTrue("position $positionIndex did not highlight $from-$to", to in BoardInteraction.legalDestinations(selected))

                val tapped = BoardInteraction.onSquareTapped(selected, to)

                if (moves.size > 1) {
                    assertTrue("only promotions may share a destination", moves.all { it.promotion != null })
                    assertEquals(PendingPromotion(from, to), tapped.pendingPromotion)
                    assertEquals(game, tapped.game)

                    moves.forEach { move ->
                        val promoted = BoardInteraction.choosePromotion(tapped, requireNotNull(move.promotion))
                        assertEquals(
                            "position $positionIndex promotion $move diverged from game-core",
                            ChessRules.applyMove(game, move),
                            promoted.game,
                        )
                        assertClearedAfterMove(promoted)
                    }
                } else {
                    val move = moves.single()
                    val prospectiveClaims =
                        ChessRules.availableDrawClaims(game, move) - ChessRules.availableDrawClaims(game)

                    if (prospectiveClaims.isEmpty()) {
                        assertEquals(
                            "position $positionIndex move $move diverged from game-core",
                            ChessRules.applyMove(game, move),
                            tapped.game,
                        )
                        assertNull(tapped.declaredMove)
                        assertClearedAfterMove(tapped)
                    } else {
                        assertEquals(game, tapped.game)
                        assertEquals(DeclaredMove(move, prospectiveClaims), tapped.declaredMove)
                        assertEquals(prospectiveClaims, GameControls.declaredDrawClaims(tapped))

                        val played = BoardInteraction.playDeclaredMove(tapped)
                        assertEquals(ChessRules.applyMove(game, move), played.game)
                        assertClearedAfterMove(played)

                        prospectiveClaims.forEach { claim ->
                            val claimed = GameControls.claimDeclaredDraw(tapped, claim)
                            assertEquals(ChessRules.claimDraw(game, claim, move), claimed.game)
                            assertEquals(game.moves, claimed.game.moves)
                            assertNull(claimed.declaredMove)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun aDifferentRealisticLiveCheckIsShownAndGoesAwayAfterAnEvasion() {
        // 1. e4 e5 2. Bc4 Nc6 3. Bxf7+ is a live bishop check, not the old queen fixture.
        val checked = play(ChessGame.newGame(), "e2e4", "e7e5", "f1c4", "b8c6", "c4f7")

        assertFalse(checked.isOver)
        assertTrue(ChessRules.isInCheck(checked.state))
        assertEquals("BLACK to move — Check", GameControls.statusFor(checked))

        val evaded = ChessRules.applyMove(checked, Move.of("e8", "f7"))

        assertFalse(evaded.isOver)
        assertFalse(ChessRules.isInCheck(evaded.state))
        assertEquals("WHITE to move", GameControls.statusFor(evaded))
    }

    @Test
    fun checkmateAtTheProspectiveFiftyMoveBoundaryIsPlayedAndLocksTheLocalUi() {
        val before =
            BoardUiState(
                ChessGame(
                    GameState(
                        board =
                            board(
                                "a1" to white(PieceType.KING),
                                "a7" to white(PieceType.ROOK),
                                "b1" to white(PieceType.ROOK),
                                "h8" to black(PieceType.KING),
                            ),
                        sideToMove = Side.WHITE,
                        castlingRights = CastlingRights.NONE,
                        drawRuleState = DrawRuleState(halfmoveClock = 99),
                    ),
                ),
            )

        val finished = tap(before, "b1", "b8")

        assertNull(finished.declaredMove)
        assertEquals(TerminationReason.CHECKMATE, finished.game.result?.reason)
        assertEquals("WHITE wins — CHECKMATE", GameControls.statusFor(finished.game))
        assertFalse(GameControls.canUndo(finished))
        assertFalse(GameControls.canClaimDraw(finished))
        assertFalse(GameControls.canResign(finished))
        assertEquals(finished.game, tap(finished, "a7", "a8").game)
    }

    private fun representativeGames(): List<ChessGame> =
        listOf(
            ChessGame.newGame(),
            // Castling and ordinary captures are both available.
            play(ChessGame.newGame(), "e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6"),
            // White may capture en passant on d6.
            play(ChessGame.newGame(), "e2e4", "a7a6", "e4e5", "d7d5"),
            // Black must answer a live check.
            play(ChessGame.newGame(), "e2e4", "f7f6", "d1h5"),
            quietPosition(halfmoveClock = 99),
            beforeTheThirdOccurrence(),
            promotionPosition(),
        )

    private fun quietPosition(halfmoveClock: Int): ChessGame =
        ChessGame(
            GameState(
                board =
                    board(
                        "a1" to white(PieceType.KING),
                        "d1" to white(PieceType.ROOK),
                        "b2" to white(PieceType.PAWN),
                        "h8" to black(PieceType.KING),
                        "d8" to black(PieceType.ROOK),
                    ),
                sideToMove = Side.WHITE,
                castlingRights = CastlingRights.NONE,
                drawRuleState = DrawRuleState(halfmoveClock = halfmoveClock),
            ),
        )

    private fun beforeTheThirdOccurrence(): ChessGame = play(ChessGame.newGame(), "g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1")

    private fun promotionPosition(): ChessGame =
        ChessGame(
            GameState(
                board =
                    board(
                        "a1" to white(PieceType.KING),
                        "b7" to white(PieceType.PAWN),
                        "h8" to black(PieceType.KING),
                        "c8" to black(PieceType.ROOK),
                    ),
                sideToMove = Side.WHITE,
                castlingRights = CastlingRights.NONE,
            ),
        )

    private fun play(
        initial: ChessGame,
        vararg moves: String,
    ): ChessGame = moves.fold(initial) { game, move -> ChessRules.applyMove(game, Move.of(move.take(2), move.drop(2))) }

    private fun tap(
        state: BoardUiState,
        vararg squares: String,
    ): BoardUiState = squares.fold(state) { current, square -> BoardInteraction.onSquareTapped(current, Square.parse(square)) }

    private fun assertClearedAfterMove(state: BoardUiState) {
        assertNull(state.selectedSquare)
        assertNull(state.pendingPromotion)
        assertNull(state.declaredMove)
        assertTrue(BoardInteraction.legalDestinations(state).isEmpty())
        assertEquals(state.game.sideToMove, state.orientation)
    }

    private fun board(vararg pieces: Pair<String, Piece>): Board =
        Board.of(pieces.associate { (square, piece) -> Square.parse(square) to piece })

    private fun white(type: PieceType): Piece = Piece(Side.WHITE, type)

    private fun black(type: PieceType): Piece = Piece(Side.BLACK, type)
}
