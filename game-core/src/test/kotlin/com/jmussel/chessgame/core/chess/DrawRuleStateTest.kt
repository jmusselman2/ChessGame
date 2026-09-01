package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DrawRuleStateTest {
    private val position = PositionKey("start")

    @Test
    fun startsWithNoHistory() {
        val state = DrawRuleState()

        assertEquals(0, state.halfmoveClock)
        assertEquals(0, state.repetitionsOf(position))
        assertEquals(emptyMap(), state.positionCounts)
    }

    @Test
    fun countsRepeatedPositions() {
        val state = DrawRuleState().recording(position).recording(position)

        assertEquals(2, state.repetitionsOf(position))
        assertEquals(0, state.repetitionsOf(PositionKey("other")))
    }

    @Test
    fun recordingLeavesTheOriginalStateUnchanged() {
        val state = DrawRuleState().recording(position)
        state.recording(position)

        assertEquals(1, state.repetitionsOf(position))
    }

    @Test
    fun theExposedCountsRejectMutationAtEverySize() {
        // toMap() returns a different implementation for none, one, and several entries, and
        // only the several-entry LinkedHashMap was writable, so every size has to be checked.
        listOf(
            emptyMap(),
            mapOf(position to 1),
            mapOf(position to 1, PositionKey("other") to 2),
        ).forEach { counts ->
            @Suppress("UNCHECKED_CAST")
            val exposed = DrawRuleState(positionCounts = counts).positionCounts as MutableMap<PositionKey, Int>

            assertFailsWith<UnsupportedOperationException>("${counts.size} entries") { exposed[position] = 5 }
        }
    }

    @Test
    fun theExposedEntriesRejectMutation() {
        val state = DrawRuleState(positionCounts = mapOf(position to 1, PositionKey("other") to 2))

        @Suppress("UNCHECKED_CAST")
        val entry = state.positionCounts.entries.first() as MutableMap.MutableEntry<PositionKey, Int>

        assertFailsWith<UnsupportedOperationException> { entry.setValue(5) }
        assertEquals(1, state.repetitionsOf(position))
    }

    @Test
    fun tracksTheHalfmoveClock() {
        assertEquals(7, DrawRuleState().withHalfmoveClock(7).halfmoveClock)
        assertEquals(0, DrawRuleState(halfmoveClock = 7).withHalfmoveClock(0).halfmoveClock)
    }

    @Test
    fun rejectsANegativeHalfmoveClock() {
        assertFailsWith<IllegalArgumentException> { DrawRuleState(halfmoveClock = -1) }
    }

    @Test
    fun distinguishesClaimableAndAutomaticThresholds() {
        assertEquals(3, DrawRuleState.THREEFOLD_REPETITION_COUNT)
        assertEquals(5, DrawRuleState.FIVEFOLD_REPETITION_COUNT)
        assertEquals(100, DrawRuleState.FIFTY_MOVE_HALFMOVES)
        assertEquals(150, DrawRuleState.SEVENTY_FIVE_MOVE_HALFMOVES)
    }

    @Test
    fun namesTheTwoClaimableDraws() {
        assertEquals(
            listOf(DrawClaim.THREEFOLD_REPETITION, DrawClaim.FIFTY_MOVE_RULE),
            DrawClaim.entries.toList(),
        )
    }
}
