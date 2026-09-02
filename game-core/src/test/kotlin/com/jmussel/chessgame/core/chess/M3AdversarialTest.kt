package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class M3AdversarialTest {
    @Test
    fun standardPositionMatchesKnownPerftThroughDepthFour() {
        val start = StandardPosition.newGame()

        assertEquals(20, perft(start, 1))
        assertEquals(400, perft(start, 2))
        assertEquals(8_902, perft(start, 3))
        assertEquals(197_281, perft(start, 4))
    }

    @Test
    fun aFinishedGameHasNoLegalMoves() {
        val finished =
            StandardPosition.newGame().copy(
                result = GameResult.draw(TerminationReason.STALEMATE),
            )

        assertTrue(ChessRules.legalMoves(finished).isEmpty())
    }

    @Test
    fun noMoveIsLegalAfterTheGameIsFinished() {
        val finished =
            StandardPosition.newGame().copy(
                result = GameResult.draw(TerminationReason.STALEMATE),
            )

        assertFalse(ChessRules.isLegal(finished, Move.of("e2", "e4")))
    }

    @Test
    fun theChessGameQueryOverloadsAlsoStopAtAFinishedGame() {
        val finished =
            ChessGame(
                StandardPosition.newGame().copy(
                    result = GameResult.draw(TerminationReason.STALEMATE),
                ),
            )

        assertTrue(ChessRules.legalMoves(finished).isEmpty())
        assertFalse(ChessRules.isLegal(finished, Move.of("e2", "e4")))
    }

    @Test
    fun aRealCheckmateEmptiesTheLegalMoveList() {
        var mated = StandardPosition.newGame()
        listOf(
            Move.of("f2", "f3"),
            Move.of("e7", "e5"),
            Move.of("g2", "g4"),
            Move.of("d8", "h4"),
        ).forEach { mated = ChessRules.applyMove(mated, it) }

        assertTrue(mated.isOver)
        assertEquals(TerminationReason.CHECKMATE, mated.result?.reason)
        assertTrue(ChessRules.legalMoves(mated).isEmpty())
        assertFalse(ChessRules.isLegal(mated, Move.of("e1", "f2")))
    }

    @Test
    fun everyTerminalReasonRejectsEveryMoveThroughBothPublicQueryOverloads() {
        TerminationReason.entries.forEach { reason ->
            val finishedState = StandardPosition.newGame().copy(result = resultFor(reason))
            val finishedGame = ChessGame(finishedState)

            assertTrue(ChessRules.legalMoves(finishedState).isEmpty(), "$reason GameState")
            assertTrue(ChessRules.legalMoves(finishedGame).isEmpty(), "$reason ChessGame")

            allRepresentableMoves().forEach { move ->
                assertFalse(ChessRules.isLegal(finishedState, move), "$reason GameState advertised $move")
                assertFalse(ChessRules.isLegal(finishedGame, move), "$reason ChessGame advertised $move")
            }

            assertFailsWith<IllegalArgumentException>("$reason GameState accepted a move") {
                ChessRules.applyMove(finishedState, Move.of("e2", "e4"))
            }
            assertFailsWith<IllegalArgumentException>("$reason ChessGame accepted a move") {
                ChessRules.applyMove(finishedGame, Move.of("e2", "e4"))
            }
        }
    }

    @Test
    fun terminalMoveQueryGuardDoesNotChangeLiveMateOrStalemateClassification() {
        val checkmate =
            sparseState(
                "h8" to Piece(Side.BLACK, PieceType.KING),
                "g7" to Piece(Side.BLACK, PieceType.PAWN),
                "h7" to Piece(Side.BLACK, PieceType.PAWN),
                "a8" to Piece(Side.WHITE, PieceType.ROOK),
                "a1" to Piece(Side.WHITE, PieceType.KING),
                sideToMove = Side.BLACK,
            )
        val stalemate =
            sparseState(
                "h8" to Piece(Side.BLACK, PieceType.KING),
                "g6" to Piece(Side.WHITE, PieceType.QUEEN),
                "f7" to Piece(Side.WHITE, PieceType.KING),
                sideToMove = Side.BLACK,
            )
        val live = StandardPosition.newGame()

        assertNull(checkmate.result)
        assertTrue(ChessRules.hasNoLegalMoves(checkmate))
        assertTrue(ChessRules.isCheckmate(checkmate))
        assertFalse(ChessRules.isStalemate(checkmate))
        assertEquals(GameResult.checkmate(loser = Side.BLACK), ChessRules.terminalResult(checkmate))

        assertNull(stalemate.result)
        assertTrue(ChessRules.hasNoLegalMoves(stalemate))
        assertFalse(ChessRules.isCheckmate(stalemate))
        assertTrue(ChessRules.isStalemate(stalemate))
        assertEquals(GameResult.draw(TerminationReason.STALEMATE), ChessRules.terminalResult(stalemate))

        assertNull(live.result)
        assertFalse(ChessRules.hasNoLegalMoves(live))
        assertFalse(ChessRules.isCheckmate(live))
        assertFalse(ChessRules.isStalemate(live))
        assertNull(ChessRules.terminalResult(live))
    }

    @Test
    fun everyAdvertisedPublicGameActionIsAcceptedByItsTransition() {
        val freshState = StandardPosition.newGame()
        ChessRules.legalMoves(freshState).forEach { move ->
            assertTrue(ChessRules.isLegal(freshState, move), "legalMoves advertised $move but isLegal rejected it")
            ChessRules.applyMove(freshState, move)
        }

        var claimable = ChessGame.newGame()
        repeat(2) {
            listOf(
                Move.of("g1", "f3"),
                Move.of("g8", "f6"),
                Move.of("f3", "g1"),
                Move.of("f6", "g8"),
            ).forEach { move -> claimable = ChessRules.applyMove(claimable, move) }
        }
        claimable =
            claimable.copy(
                state =
                    claimable.state.copy(
                        drawRuleState = claimable.state.drawRuleState.withHalfmoveClock(100),
                    ),
            )

        ChessRules.availableDrawClaims(claimable).forEach { claim ->
            assertTrue(ChessRules.canClaimDraw(claimable.state, claim))
            assertTrue(ChessRules.claimDraw(claimable, claim).isOver)
        }

        val afterMove = ChessRules.applyMove(ChessGame.newGame(), Move.of("e2", "e4"))
        val undoableSide = ChessRules.undoableSide(afterMove)
        assertEquals(Side.WHITE, undoableSide)
        assertTrue(ChessRules.canUndo(afterMove, requireNotNull(undoableSide)))
        assertEquals(ChessGame.newGame(), ChessRules.undo(afterMove, undoableSide))
    }

    @Test
    fun aThirdOccurrenceCreatedByTheDeclaredNextMoveIsClaimable() {
        var position = StandardPosition.newGame()
        listOf(
            Move.of("g1", "f3"),
            Move.of("g8", "f6"),
            Move.of("f3", "g1"),
            Move.of("f6", "g8"),
            Move.of("g1", "f3"),
            Move.of("g8", "f6"),
            Move.of("f3", "g1"),
        ).forEach { move -> position = ChessRules.applyMove(position, move) }
        val declaredMove = Move.of("f6", "g8")

        assertEquals(2, Repetition.occurrences(position))
        assertTrue(ChessRules.isLegal(position, declaredMove))
        assertEquals(3, Repetition.occurrences(ChessRules.applyMove(position, declaredMove)))

        // PRODUCT and ARCHITECTURE require prospective legal-move claims, expressed through
        // the declared move the entitlement is bound to.
        assertTrue(ChessRules.canClaimDraw(position, DrawClaim.THREEFOLD_REPETITION, declaredMove))
        assertEquals(setOf(DrawClaim.THREEFOLD_REPETITION), ChessRules.availableDrawClaims(position, declaredMove))
        assertEquals(
            TerminationReason.THREEFOLD_REPETITION_CLAIM,
            ChessRules.claimDraw(position, DrawClaim.THREEFOLD_REPETITION, declaredMove).result?.reason,
        )

        // The entitlement is that move's alone: undeclared, declared as a quiet move
        // reaching a different position, declared as a pawn move that clears the history,
        // or declared as an illegal move, there is no claim.
        assertFalse(ChessRules.canClaimDraw(position, DrawClaim.THREEFOLD_REPETITION))
        assertFalse(ChessRules.canClaimDraw(position, DrawClaim.THREEFOLD_REPETITION, Move.of("b8", "c6")))
        assertFalse(ChessRules.canClaimDraw(position, DrawClaim.THREEFOLD_REPETITION, Move.of("e7", "e5")))
        assertFalse(ChessRules.canClaimDraw(position, DrawClaim.THREEFOLD_REPETITION, Move.of("a8", "a5")))
        assertFalse(ChessRules.canClaimDraw(position, DrawClaim.FIFTY_MOVE_RULE, declaredMove))
    }

    @Test
    fun theQuietMoveThatWouldReachOneHundredHalfmovesIsClaimable() {
        val position =
            sparseState(
                "a1" to Piece(Side.WHITE, PieceType.KING),
                "d1" to Piece(Side.WHITE, PieceType.ROOK),
                "h8" to Piece(Side.BLACK, PieceType.KING),
                "e8" to Piece(Side.BLACK, PieceType.ROOK),
                sideToMove = Side.WHITE,
                halfmoveClock = 99,
            )
        val declaredMove = Move.of("d1", "d2")

        assertTrue(ChessRules.isLegal(position, declaredMove))
        assertEquals(100, ChessRules.applyMove(position, declaredMove).halfmoveClock)

        // As above, the claim rides on the declared move rather than on an unconditional
        // early availability at ninety-nine halfmoves.
        assertTrue(ChessRules.canClaimDraw(position, DrawClaim.FIFTY_MOVE_RULE, declaredMove))
        assertEquals(setOf(DrawClaim.FIFTY_MOVE_RULE), ChessRules.availableDrawClaims(position, declaredMove))
        assertEquals(
            TerminationReason.FIFTY_MOVE_RULE_CLAIM,
            ChessRules.claimDraw(position, DrawClaim.FIFTY_MOVE_RULE, declaredMove).result?.reason,
        )

        assertFalse(ChessRules.canClaimDraw(position, DrawClaim.FIFTY_MOVE_RULE))
        assertFalse(ChessRules.canClaimDraw(position, DrawClaim.FIFTY_MOVE_RULE, Move.of("d1", "e2")))
        assertFalse(ChessRules.canClaimDraw(position, DrawClaim.THREEFOLD_REPETITION, declaredMove))

        // One halfmove earlier the same declaration reaches only ninety-nine, and claims
        // nothing.
        val tooEarly = position.copy(drawRuleState = DrawRuleState(halfmoveClock = 98))
        assertEquals(99, ChessRules.applyMove(tooEarly, declaredMove).halfmoveClock)
        assertEquals(emptySet(), ChessRules.availableDrawClaims(tooEarly, declaredMove))
    }

    @Test
    fun aCaptureLeavingOnlySameColourBishopsEndsTheGame() {
        val position =
            sparseState(
                "e1" to Piece(Side.WHITE, PieceType.KING),
                "c1" to Piece(Side.WHITE, PieceType.BISHOP),
                "e3" to Piece(Side.WHITE, PieceType.BISHOP),
                "e8" to Piece(Side.BLACK, PieceType.KING),
                "g5" to Piece(Side.BLACK, PieceType.KNIGHT),
                sideToMove = Side.WHITE,
            )
        val capture = Move.of("e3", "g5")

        assertTrue(ChessRules.isLegal(position, capture))
        val after = ChessRules.applyMove(position, capture)

        // Both surviving bishops are confined to the same square colour, so neither side
        // can ever checkmate. This is a dead position even though promotion made two bishops.
        assertTrue(InsufficientMaterial.isDraw(after))
        assertTrue(after.isOver)
        assertEquals(TerminationReason.INSUFFICIENT_MATERIAL, after.result?.reason)
    }

    @Test
    fun enPassantTargetWithoutBypassedPawnOffersNoCapture() {
        val state = enPassantState()

        assertNoEnPassant(state, Move.of("d5", "e6"))
    }

    @Test
    fun enPassantTargetBesideFriendlyPawnOffersNoCapture() {
        val state = enPassantState("e5" to Piece(Side.WHITE, PieceType.PAWN))

        assertNoEnPassant(state, Move.of("d5", "e6"))
    }

    @Test
    fun enPassantTargetBesideNonPawnOffersNoCapture() {
        val state = enPassantState("e5" to Piece(Side.BLACK, PieceType.KNIGHT))

        assertNoEnPassant(state, Move.of("d5", "e6"))
    }

    @Test
    fun enPassantTargetOnImpossibleRankOffersNoCapture() {
        val state =
            state(
                "d4" to Piece(Side.WHITE, PieceType.PAWN),
                "e4" to Piece(Side.BLACK, PieceType.PAWN),
                enPassantTarget = "e5",
            )

        assertNoEnPassant(state, Move.of("d4", "e5"))
    }

    @Test
    fun occupiedEnPassantTargetIsOnlyAnOrdinaryCapture() {
        val state =
            state(
                "d5" to Piece(Side.WHITE, PieceType.PAWN),
                "e5" to Piece(Side.BLACK, PieceType.PAWN),
                "e6" to Piece(Side.BLACK, PieceType.KNIGHT),
                enPassantTarget = "e6",
            )
        val move = Move.of("d5", "e6")

        assertFalse(EnPassant.isCapture(state, move))
        assertTrue(ChessRules.isLegal(state, move))

        val after = ChessRules.applyMove(state, move)
        assertEquals(Piece(Side.WHITE, PieceType.PAWN), after.board.pieceAt(Square.parse("e6")))
        assertEquals(Piece(Side.BLACK, PieceType.PAWN), after.board.pieceAt(Square.parse("e5")))
    }

    @Test
    fun enPassantCaptureRecognitionRequiresPawnCaptureGeometry() {
        val state =
            state(
                "a2" to Piece(Side.WHITE, PieceType.PAWN),
                "e5" to Piece(Side.BLACK, PieceType.PAWN),
                enPassantTarget = "e6",
            )

        assertFalse(EnPassant.isCapture(state, Move.of("a2", "e6")))
    }

    private fun enPassantState(vararg capturedSquare: Pair<String, Piece>): GameState =
        state(
            "d5" to Piece(Side.WHITE, PieceType.PAWN),
            *capturedSquare,
            enPassantTarget = "e6",
        )

    private fun state(
        vararg placement: Pair<String, Piece>,
        enPassantTarget: String,
    ): GameState =
        GameState(
            board =
                Board.of(
                    buildMap {
                        put(Square.parse("e1"), Piece(Side.WHITE, PieceType.KING))
                        put(Square.parse("h8"), Piece(Side.BLACK, PieceType.KING))
                        placement.forEach { (square, piece) -> put(Square.parse(square), piece) }
                    },
                ),
            sideToMove = Side.WHITE,
            castlingRights = CastlingRights.NONE,
            enPassantTarget = Square.parse(enPassantTarget),
        )

    private fun sparseState(
        vararg placement: Pair<String, Piece>,
        sideToMove: Side,
        halfmoveClock: Int = 0,
    ): GameState =
        GameState(
            board = Board.of(placement.associate { (square, piece) -> Square.parse(square) to piece }),
            sideToMove = sideToMove,
            castlingRights = CastlingRights.NONE,
            drawRuleState = DrawRuleState(halfmoveClock = halfmoveClock),
        )

    private fun resultFor(reason: TerminationReason): GameResult =
        if (reason.isDraw) GameResult.draw(reason) else GameResult.win(Side.WHITE, reason)

    private fun allRepresentableMoves(): Sequence<Move> =
        sequence {
            Square.ALL.forEach { from ->
                Square.ALL.filterNot { it == from }.forEach { to ->
                    yield(Move(from, to))
                    PieceType.PROMOTION_CHOICES.forEach { promotion -> yield(Move(from, to, promotion)) }
                }
            }
        }

    private fun assertNoEnPassant(
        state: GameState,
        move: Move,
    ) {
        assertFalse(EnPassant.isCapture(state, move))
        assertFalse(move in EnPassant.availableMoves(state))
        assertFalse(ChessRules.isLegal(state, move))
    }

    private fun perft(
        state: GameState,
        depth: Int,
    ): Int {
        if (depth == 0) return 1
        return ChessRules.legalMoves(state).sumOf { move -> perft(ChessRules.applyMove(state, move), depth - 1) }
    }
}
