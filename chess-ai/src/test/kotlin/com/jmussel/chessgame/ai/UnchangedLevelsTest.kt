package com.jmussel.chessgame.ai

import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.StandardPosition
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Very Easy and Easy are `D088`'s Easy and Medium renamed, and play exactly as they did
 * (`D089`, `M21.11`).
 *
 * The expected moves were recorded from the engine before `M21.11` changed it, with a clock
 * that stands still so that every search runs to its full depth on any machine.
 */
class UnchangedLevelsTest {
    private fun choices(
        difficulty: Difficulty,
        seed: Long,
    ): List<String> =
        POSITIONS.map { line ->
            val state =
                line.split(" ").filter { it.isNotEmpty() }.fold(StandardPosition.newGame()) { state, text ->
                    ChessRules.applyMove(state, Move.of(text.substring(0, 2), text.substring(2, 4)))
                }
            AlphaBetaEngine(seed = seed, clock = { 0L }).chooseMove(state, difficulty).toString()
        }

    @Test
    fun veryEasyPlaysAsTheOldEasyDid() {
        assertEquals(OLD_EASY_SEED_1, choices(Difficulty.VERY_EASY, seed = 1))
        assertEquals(OLD_EASY_SEED_2, choices(Difficulty.VERY_EASY, seed = 2))
    }

    @Test
    fun easyPlaysAsTheOldMediumDid() {
        assertEquals(OLD_MEDIUM_SEED_1, choices(Difficulty.EASY, seed = 1))
        assertEquals(OLD_MEDIUM_SEED_2, choices(Difficulty.EASY, seed = 2))
    }

    private companion object {
        val POSITIONS =
            listOf(
                "",
                "e2e4",
                "e2e4 e7e5",
                "d2d4",
                "e2e4 e7e5 g1f3 b8c6",
                "d2d4 d7d5 c2c4",
                "e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3",
                "e2e4 e7e5 g1f3 b8c6 f1c4 g8f6 d2d3 f8c5 e1g1 d7d6",
                "e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5a4 g8f6 e1g1 f8e7",
                "d2d4 g8f6 c2c4 e7e6 b1c3 f8b4 e2e3 e8g8",
                "e2e4 e7e5 d1h5 b8c6 f1c4 g8f6",
                "e2e4 d7d5 e4d5 d8d5 b1c3 d5a5",
            )

        // What `D088`'s Easy and Medium chose in those positions, recorded at `1fe4dac`.
        val OLD_EASY_SEED_1 = "e2e3 h7h5 d1h5 f7f5 b1c3 b7b5 b8c6 g2g4 a4c6 g2g4 h5f7 h2h3".split(" ")
        val OLD_EASY_SEED_2 = "c2c4 b8c6 d2d3 h7h5 f3e5 e7e5 f6d5 f1e1 a4c6 g2g4 h5f7 f1b5".split(" ")
        val OLD_MEDIUM_SEED_1 = "c2c4 e7e5 d1h5 f7f6 c2c4 d5c4 g7g6 g2g4 a4c6 d4d5 h5f7 b2b4".split(" ")
        val OLD_MEDIUM_SEED_2 = "h2h4 a7a5 d1e2 e7e6 c2c4 d5c4 d8d7 d1e2 a4c6 d1e2 h5f7 b2b4".split(" ")
    }
}
