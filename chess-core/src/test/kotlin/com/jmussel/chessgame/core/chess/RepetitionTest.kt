package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RepetitionTest {
    /** One round trip of both knights, which returns the position to what it was. */
    private val knightShuffle =
        listOf(
            Move.of("g1", "f3"),
            Move.of("g8", "f6"),
            Move.of("f3", "g1"),
            Move.of("f6", "g8"),
        )

    private fun play(
        from: GameState,
        moves: List<Move>,
    ): GameState {
        var position = from
        moves.forEach { position = ChessRules.applyMove(position, it) }
        return position
    }

    private fun shuffle(
        from: GameState,
        rounds: Int,
    ): GameState = play(from, List(rounds) { knightShuffle }.flatten())

    @Test
    fun theStartingPositionHasOccurredOnce() {
        assertEquals(1, Repetition.occurrences(StandardPosition.newGame()))
    }

    @Test
    fun returningToAPositionCountsItAgain() {
        val afterOneRound = shuffle(StandardPosition.newGame(), 1)

        assertEquals(2, Repetition.occurrences(afterOneRound))
        assertEquals(
            Repetition.keyOf(StandardPosition.newGame()),
            Repetition.keyOf(afterOneRound),
        )
    }

    @Test
    fun theThirdOccurrenceMakesADrawClaimable() {
        val position = shuffle(StandardPosition.newGame(), 2)

        assertEquals(3, Repetition.occurrences(position))
        assertTrue(Repetition.canClaimThreefold(position))
        assertFalse(position.isOver, "a threefold repetition must be claimed, not applied")
    }

    @Test
    fun fewerThanThreeOccurrencesAreNotClaimable() {
        assertFalse(Repetition.canClaimThreefold(StandardPosition.newGame()))
        assertFalse(Repetition.canClaimThreefold(shuffle(StandardPosition.newGame(), 1)))
    }

    @Test
    fun theFifthOccurrenceEndsTheGameAutomatically() {
        val position = shuffle(StandardPosition.newGame(), 4)

        assertEquals(5, Repetition.occurrences(position))
        assertTrue(Repetition.isFivefold(position))
        assertTrue(position.isOver)
        assertEquals(GameResult.draw(TerminationReason.FIVEFOLD_REPETITION), position.result)
    }

    @Test
    fun theFourthOccurrenceDoesNotEndTheGame() {
        val position = shuffle(StandardPosition.newGame(), 3)

        assertEquals(4, Repetition.occurrences(position))
        assertFalse(position.isOver)
        assertTrue(Repetition.canClaimThreefold(position))
    }

    @Test
    fun sideToMoveIsPartOfThePositionIdentity() {
        val position = StandardPosition.newGame()

        assertNotEquals(
            Repetition.keyOf(position),
            Repetition.keyOf(position.copy(sideToMove = Side.BLACK)),
        )
    }

    @Test
    fun castlingRightsArePartOfThePositionIdentity() {
        val position = StandardPosition.newGame()

        assertNotEquals(
            Repetition.keyOf(position),
            Repetition.keyOf(position.copy(castlingRights = CastlingRights.NONE)),
        )
    }

    @Test
    fun losingCastlingRightsMeansTheEarlierPositionCannotRecur() {
        val afterRookRoundTrip =
            play(
                StandardPosition.newGame(),
                listOf(
                    Move.of("a2", "a4"),
                    Move.of("a7", "a5"),
                    Move.of("a1", "a3"),
                    Move.of("a8", "a6"),
                    Move.of("a3", "a1"),
                    Move.of("a6", "a8"),
                ),
            )

        assertEquals(
            1,
            Repetition.occurrences(afterRookRoundTrip),
            "the pieces are back but neither side may castle queen side any more",
        )
        assertFalse(afterRookRoundTrip.castlingRights.has(Side.WHITE, CastlingSide.QUEEN_SIDE))
    }

    @Test
    fun aPlayableEnPassantTargetIsPartOfThePositionIdentity() {
        val afterDoubleAdvance =
            play(
                StandardPosition.newGame(),
                listOf(
                    Move.of("e2", "e4"),
                    Move.of("d7", "d5"),
                    Move.of("e4", "e5"),
                    Move.of("f7", "f5"),
                ),
            )
        val withoutTheTarget = afterDoubleAdvance.copy(enPassantTarget = null)

        assertEquals(Square.parse("f6"), afterDoubleAdvance.enPassantTarget)
        assertNotEquals(Repetition.keyOf(afterDoubleAdvance), Repetition.keyOf(withoutTheTarget))
    }

    @Test
    fun anUnusableEnPassantTargetDoesNotChangeThePosition() {
        val afterDoubleAdvance =
            play(
                StandardPosition.newGame(),
                listOf(
                    Move.of("g1", "f3"),
                    Move.of("a7", "a5"),
                ),
            )

        assertEquals(Square.parse("a6"), afterDoubleAdvance.enPassantTarget)
        assertEquals(
            Repetition.keyOf(afterDoubleAdvance.copy(enPassantTarget = null)),
            Repetition.keyOf(afterDoubleAdvance),
            "no white pawn can capture on a6, so the target does not distinguish the position",
        )
    }

    @Test
    fun anIrreversibleMoveClearsTheRepetitionHistory() {
        val afterShuffle = shuffle(StandardPosition.newGame(), 1)
        val afterPawnMove = ChessRules.applyMove(afterShuffle, Move.of("e2", "e4"))

        assertEquals(2, Repetition.occurrences(afterShuffle))
        assertEquals(1, afterPawnMove.drawRuleState.positionCounts.size)
        assertEquals(1, Repetition.occurrences(afterPawnMove))
    }

    @Test
    fun aFinishedGameHasNothingLeftToClaim() {
        val position = shuffle(StandardPosition.newGame(), 4)

        assertTrue(position.isOver)
        assertFalse(Repetition.canClaimThreefold(position))
    }
}
