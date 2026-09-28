package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class M2DomainInvariantTest {
    @Test
    fun externalMapMutationCannotManufactureAnAutomaticDraw() {
        val initial = StandardPosition.newGame()
        val position = Repetition.keyOf(initial)
        val source = mutableMapOf(position to Repetition.occurrences(initial))
        val state = initial.copy(drawRuleState = DrawRuleState(positionCounts = source))

        source[position] = DrawRuleState.FIVEFOLD_REPETITION_COUNT

        assertFalse(Repetition.isFivefold(state))
        assertEquals(1, state.drawRuleState.repetitionsOf(position))
    }

    @Test
    fun exposedPositionCountsCannotManufactureAnAutomaticDraw() {
        val initial = StandardPosition.newGame()
        val position = Repetition.keyOf(initial)
        val state =
            initial.copy(
                drawRuleState =
                    DrawRuleState(
                        positionCounts =
                            mapOf(
                                position to Repetition.occurrences(initial),
                                PositionKey("another position") to 1,
                            ),
                    ),
            )

        @Suppress("UNCHECKED_CAST")
        val exposedCounts = state.drawRuleState.positionCounts as MutableMap<PositionKey, Int>
        runCatching { exposedCounts[position] = DrawRuleState.FIVEFOLD_REPETITION_COUNT }

        assertFalse(Repetition.isFivefold(state))
        assertEquals(1, state.drawRuleState.repetitionsOf(position))
    }

    @Test
    fun rejectsNonPositiveRepetitionCounts() {
        listOf(0, -1).forEach { count ->
            assertFailsWith<IllegalArgumentException>("count $count") {
                DrawRuleState(positionCounts = mapOf(PositionKey("position") to count))
            }
        }
    }
}
