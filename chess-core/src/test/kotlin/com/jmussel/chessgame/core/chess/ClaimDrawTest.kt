package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClaimDrawTest {
    private fun white(type: PieceType) = Piece(Side.WHITE, type)

    private fun black(type: PieceType) = Piece(Side.BLACK, type)

    private fun quietPosition(halfmoveClock: Int): GameState =
        GameState(
            board =
                Board.of(
                    mapOf(
                        "a1" to white(PieceType.KING),
                        "d1" to white(PieceType.ROOK),
                        "h8" to black(PieceType.KING),
                        "e8" to black(PieceType.ROOK),
                    ).mapKeys { (square, _) -> Square.parse(square) },
                ),
            sideToMove = Side.WHITE,
            castlingRights = CastlingRights.NONE,
            drawRuleState = DrawRuleState(halfmoveClock = halfmoveClock),
        )

    private fun repeatedPosition(rounds: Int): GameState {
        var position = StandardPosition.newGame()
        repeat(rounds) {
            listOf(
                Move.of("g1", "f3"),
                Move.of("g8", "f6"),
                Move.of("f3", "g1"),
                Move.of("f6", "g8"),
            ).forEach { position = ChessRules.applyMove(position, it) }
        }
        return position
    }

    /** Black to move, one knight shuffle short of a third occurrence of this position. */
    private fun almostRepeatedPosition(): GameState {
        var position = repeatedPosition(rounds = 1)
        listOf(
            Move.of("g1", "f3"),
            Move.of("g8", "f6"),
            Move.of("f3", "g1"),
        ).forEach { position = ChessRules.applyMove(position, it) }
        return position
    }

    @Test
    fun aFreshGameHasNothingToClaim() {
        assertEquals(emptySet(), ChessRules.availableDrawClaims(StandardPosition.newGame()))
    }

    @Test
    fun aThreefoldRepetitionBecomesClaimable() {
        val position = repeatedPosition(rounds = 2)

        assertEquals(setOf(DrawClaim.THREEFOLD_REPETITION), ChessRules.availableDrawClaims(position))
        assertTrue(ChessRules.canClaimDraw(position, DrawClaim.THREEFOLD_REPETITION))
        assertFalse(ChessRules.canClaimDraw(position, DrawClaim.FIFTY_MOVE_RULE))
    }

    @Test
    fun claimingAThreefoldRepetitionDrawsTheGame() {
        val claimed = ChessRules.claimDraw(repeatedPosition(rounds = 2), DrawClaim.THREEFOLD_REPETITION)

        assertTrue(claimed.isOver)
        assertEquals(
            GameResult.draw(TerminationReason.THREEFOLD_REPETITION_CLAIM),
            claimed.result,
        )
        assertNull(claimed.result?.winner)
    }

    @Test
    fun theFiftyMoveRuleBecomesClaimable() {
        val position = quietPosition(halfmoveClock = 100)

        assertEquals(setOf(DrawClaim.FIFTY_MOVE_RULE), ChessRules.availableDrawClaims(position))
    }

    @Test
    fun claimingTheFiftyMoveRuleDrawsTheGame() {
        val claimed = ChessRules.claimDraw(quietPosition(halfmoveClock = 120), DrawClaim.FIFTY_MOVE_RULE)

        assertTrue(claimed.isOver)
        assertEquals(GameResult.draw(TerminationReason.FIFTY_MOVE_RULE_CLAIM), claimed.result)
    }

    @Test
    fun anInvalidClaimIsRejected() {
        val tooEarly = quietPosition(halfmoveClock = 99)

        assertFalse(ChessRules.canClaimDraw(tooEarly, DrawClaim.FIFTY_MOVE_RULE))
        assertFailsWith<IllegalArgumentException> {
            ChessRules.claimDraw(tooEarly, DrawClaim.FIFTY_MOVE_RULE)
        }
        assertFailsWith<IllegalArgumentException> {
            ChessRules.claimDraw(tooEarly, DrawClaim.THREEFOLD_REPETITION)
        }
    }

    @Test
    fun theWrongClaimForTheSituationIsRejected() {
        val repeated = repeatedPosition(rounds = 2)

        assertFailsWith<IllegalArgumentException> {
            ChessRules.claimDraw(repeated, DrawClaim.FIFTY_MOVE_RULE)
        }
    }

    @Test
    fun aClaimInAFinishedGameIsRejected() {
        val finished =
            quietPosition(halfmoveClock = 120).copy(result = GameResult.resignation(loser = Side.WHITE))

        assertFailsWith<IllegalArgumentException> {
            ChessRules.claimDraw(finished, DrawClaim.FIFTY_MOVE_RULE)
        }
    }

    @Test
    fun bothClaimsCanBeAvailableAtOnce() {
        var position = repeatedPosition(rounds = 2)
        position = position.copy(drawRuleState = position.drawRuleState.withHalfmoveClock(100))

        assertEquals(
            setOf(DrawClaim.THREEFOLD_REPETITION, DrawClaim.FIFTY_MOVE_RULE),
            ChessRules.availableDrawClaims(position),
        )
    }

    @Test
    fun aClaimableDrawDoesNotEndTheGameOnItsOwn() {
        val repeated = repeatedPosition(rounds = 2)
        val fifty = ChessRules.applyMove(quietPosition(halfmoveClock = 99), Move.of("d1", "d2"))

        assertFalse(repeated.isOver)
        assertFalse(fifty.isOver)
        assertTrue(ChessRules.availableDrawClaims(repeated).isNotEmpty())
        assertTrue(ChessRules.availableDrawClaims(fifty).isNotEmpty())
    }

    @Test
    fun anAutomaticDrawNeedsNoClaim() {
        val fivefold = repeatedPosition(rounds = 4)
        val seventyFive = ChessRules.applyMove(quietPosition(halfmoveClock = 149), Move.of("d1", "d2"))

        assertTrue(fivefold.isOver)
        assertEquals(TerminationReason.FIVEFOLD_REPETITION, fivefold.result?.reason)
        assertTrue(seventyFive.isOver)
        assertEquals(TerminationReason.SEVENTY_FIVE_MOVE_RULE, seventyFive.result?.reason)

        assertTrue(ChessRules.availableDrawClaims(fivefold).isEmpty())
        assertTrue(ChessRules.availableDrawClaims(seventyFive).isEmpty())
    }

    @Test
    fun claimingLeavesTheOriginalStateUnchanged() {
        val position = repeatedPosition(rounds = 2)
        ChessRules.claimDraw(position, DrawClaim.THREEFOLD_REPETITION)

        assertFalse(position.isOver)
    }

    @Test
    fun aDeclaredQuietMoveThatCompletesTheFiftyMoveRuleIsClaimable() {
        val position = quietPosition(halfmoveClock = 99)
        val declaredMove = Move.of("d1", "d2")

        assertEquals(setOf(DrawClaim.FIFTY_MOVE_RULE), ChessRules.availableDrawClaims(position, declaredMove))
        assertTrue(ChessRules.canClaimDraw(position, DrawClaim.FIFTY_MOVE_RULE, declaredMove))
        assertFalse(ChessRules.canClaimDraw(position, DrawClaim.FIFTY_MOVE_RULE))
    }

    @Test
    fun aDeclaredMoveOneHalfmoveShortOfTheFiftyMoveRuleClaimsNothing() {
        val position = quietPosition(halfmoveClock = 98)

        assertEquals(emptySet(), ChessRules.availableDrawClaims(position, Move.of("d1", "d2")))
    }

    @Test
    fun aDeclaredMoveCreatingTheThirdOccurrenceIsClaimable() {
        val position = almostRepeatedPosition()
        val declaredMove = Move.of("f6", "g8")

        assertEquals(2, Repetition.occurrences(position))
        assertEquals(setOf(DrawClaim.THREEFOLD_REPETITION), ChessRules.availableDrawClaims(position, declaredMove))
        assertFalse(ChessRules.canClaimDraw(position, DrawClaim.THREEFOLD_REPETITION))
    }

    @Test
    fun aDeclaredMoveReachingSomeOtherPositionClaimsNothing() {
        val position = almostRepeatedPosition()

        assertEquals(emptySet(), ChessRules.availableDrawClaims(position, Move.of("b8", "c6")))
    }

    @Test
    fun aDeclaredPawnMoveOrCaptureCanNeverCreateAClaim() {
        val position =
            GameState(
                board =
                    Board.of(
                        mapOf(
                            "a1" to white(PieceType.KING),
                            "d1" to white(PieceType.ROOK),
                            "h2" to white(PieceType.PAWN),
                            "h8" to black(PieceType.KING),
                            "d8" to black(PieceType.ROOK),
                        ).mapKeys { (square, _) -> Square.parse(square) },
                    ),
                sideToMove = Side.WHITE,
                castlingRights = CastlingRights.NONE,
                drawRuleState = DrawRuleState(halfmoveClock = 99),
            )

        // Both reset the halfmove clock and clear the repetition history, so neither can
        // ever be the move a claim is declared on.
        assertEquals(emptySet(), ChessRules.availableDrawClaims(position, Move.of("d1", "d8")))
        assertEquals(emptySet(), ChessRules.availableDrawClaims(position, Move.of("h2", "h3")))
        assertEquals(setOf(DrawClaim.FIFTY_MOVE_RULE), ChessRules.availableDrawClaims(position, Move.of("d1", "d2")))
    }

    @Test
    fun aDeclaredCheckmatingMoveOffersNoDrawClaim() {
        val position =
            GameState(
                board =
                    Board.of(
                        mapOf(
                            "a1" to white(PieceType.KING),
                            "a7" to white(PieceType.ROOK),
                            "b1" to white(PieceType.ROOK),
                            "h8" to black(PieceType.KING),
                        ).mapKeys { (square, _) -> Square.parse(square) },
                    ),
                sideToMove = Side.WHITE,
                castlingRights = CastlingRights.NONE,
                drawRuleState = DrawRuleState(halfmoveClock = 99),
            )
        val mate = Move.of("b1", "b8")

        assertEquals(TerminationReason.CHECKMATE, ChessRules.applyMove(position, mate).result?.reason)
        assertEquals(emptySet(), ChessRules.availableDrawClaims(position, mate))
        assertEquals(setOf(DrawClaim.FIFTY_MOVE_RULE), ChessRules.availableDrawClaims(position, Move.of("b1", "b2")))
    }

    @Test
    fun anIllegalDeclarationClaimsNothing() {
        val position = quietPosition(halfmoveClock = 99)

        assertEquals(emptySet(), ChessRules.availableDrawClaims(position, Move.of("d1", "e2")))
        assertFailsWith<IllegalArgumentException> {
            ChessRules.claimDraw(position, DrawClaim.FIFTY_MOVE_RULE, Move.of("d1", "e2"))
        }
    }

    @Test
    fun aDeclaredMoveInAFinishedGameClaimsNothing() {
        val finished =
            quietPosition(halfmoveClock = 99).copy(result = GameResult.resignation(loser = Side.WHITE))

        assertEquals(emptySet(), ChessRules.availableDrawClaims(finished, Move.of("d1", "d2")))
        assertFailsWith<IllegalArgumentException> {
            ChessRules.claimDraw(finished, DrawClaim.FIFTY_MOVE_RULE, Move.of("d1", "d2"))
        }
    }

    @Test
    fun aDeclarationAlsoCarriesTheClaimsThePositionAlreadyHas() {
        val repeated = repeatedPosition(rounds = 2)

        assertEquals(
            setOf(DrawClaim.THREEFOLD_REPETITION),
            ChessRules.availableDrawClaims(repeated, Move.of("e2", "e4")),
            "a claim on the current position needs no declaration and survives any",
        )
    }

    @Test
    fun claimingOnADeclaredMoveEndsTheGameWithoutPlayingIt() {
        val position = quietPosition(halfmoveClock = 99)
        val claimed = ChessRules.claimDraw(position, DrawClaim.FIFTY_MOVE_RULE, Move.of("d1", "d2"))

        assertTrue(claimed.isOver)
        assertEquals(TerminationReason.FIFTY_MOVE_RULE_CLAIM, claimed.result?.reason)
        assertEquals(GameOutcome.DRAW, claimed.result?.outcome)
        assertEquals(position.board, claimed.board, "the declared move is not played")
        assertEquals(Side.WHITE, claimed.sideToMove)
        assertEquals(99, claimed.halfmoveClock)
        assertFalse(position.isOver)
    }

    @Test
    fun theGameOverloadsTakeADeclaredMoveToo() {
        val game = ChessGame(quietPosition(halfmoveClock = 99))
        val declaredMove = Move.of("d1", "d2")

        assertEquals(setOf(DrawClaim.FIFTY_MOVE_RULE), ChessRules.availableDrawClaims(game, declaredMove))

        val claimed = ChessRules.claimDraw(game, DrawClaim.FIFTY_MOVE_RULE, declaredMove)

        assertTrue(claimed.isOver)
        assertEquals(TerminationReason.FIFTY_MOVE_RULE_CLAIM, claimed.result?.reason)
        assertTrue(claimed.history.isEmpty(), "declaring a move does not play it")
    }

    @Test
    fun aClaimReasonIsMarkedAsRequiringAClaim() {
        listOf(
            TerminationReason.THREEFOLD_REPETITION_CLAIM,
            TerminationReason.FIFTY_MOVE_RULE_CLAIM,
        ).forEach {
            assertTrue(it.requiresClaim)
            assertTrue(it.isDraw)
        }
    }
}
