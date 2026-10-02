package com.jmussel.chessgame.computer

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jmussel.chessgame.ai.AlphaBetaEngine
import com.jmussel.chessgame.ai.Difficulty
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.GameState
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.StandardPosition
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * How long each computer level takes on this device (`M21.11`, `D089`), logged under the tag
 * `EngineTiming` (`adb logcat -s EngineTiming`).
 *
 * For each level and position it logs three times: with the device's own clock, which is
 * what the player waits; with a clock that stands still, which is how long the level's full
 * search takes here; and how long its one-ply search takes, the least it must finish to
 * play a searched move. The engine always finishes that one ply, even past the budget
 * (`D089`). It checks that every answer comes within the level's budget, or its one-ply
 * time if that is longer, plus the slack a search needs to notice its time is up.
 */
@RunWith(AndroidJUnit4::class)
class EngineTimingDeviceTest {
    private fun after(line: String): GameState =
        line.split(" ").filter { it.isNotEmpty() }.fold(StandardPosition.newGame()) { state, text ->
            ChessRules.applyMove(state, Move.of(text.substring(0, 2), text.substring(2, 4)))
        }

    private fun millis(search: () -> Unit): Long {
        val started = System.nanoTime()
        search()
        return (System.nanoTime() - started) / 1_000_000
    }

    @Test
    fun everyLevelAnswersWithinItsBudget() {
        val late = mutableListOf<String>()
        Difficulty.entries.forEach { difficulty ->
            val budget = AlphaBetaEngine.settingsFor(difficulty).budgetMillis
            POSITIONS.forEach { (name, line) ->
                val state = after(line)
                val waited = millis { AlphaBetaEngine(seed = 1).chooseMove(state, difficulty) }
                val full = millis { AlphaBetaEngine(seed = 1, clock = { 0L }).chooseMove(state, difficulty) }
                val onePly =
                    millis {
                        AlphaBetaEngine(seed = 1, clock = { 0L }, settings = { AlphaBetaEngine.settingsFor(it).copy(maxDepth = 1) })
                            .chooseMove(state, difficulty)
                    }
                Log.i(TAG, "$difficulty $name: answered in $waited ms of $budget; full search $full ms; one ply $onePly ms")
                if (waited > maxOf(budget, onePly) + SLACK_MILLIS) late += "$difficulty $name took $waited ms of $budget"
            }
        }
        assertTrue(late.joinToString("\n"), late.isEmpty())
    }

    private companion object {
        const val TAG = "EngineTiming"

        /** What a search may take beyond its budget to notice it has run out. */
        const val SLACK_MILLIS = 750L

        val POSITIONS =
            listOf(
                "start" to "",
                "Italian" to "e2e4 e7e5 g1f3 b8c6 f1c4 g8f6 d2d3 f8c5 e1g1 d7d6",
                "Nimzo-Indian" to "d2d4 g8f6 c2c4 e7e6 b1c3 f8b4 e2e3 e8g8 f1d3 d7d5 g1f3 c7c5 e1g1",
                "Najdorf" to "e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 a7a6 c1e3 e7e5 d4b3 c8e6 f2f3",
                "Ruy Lopez" to "e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5a4 g8f6 e1g1 f8e7 f1e1 b7b5 a4b3 d7d6 c2c3 e8g8",
            )
    }
}
