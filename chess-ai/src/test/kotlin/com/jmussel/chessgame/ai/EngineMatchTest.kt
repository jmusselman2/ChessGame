package com.jmussel.chessgame.ai

import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.GameState
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.StandardPosition
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Seeded matches between the levels (`D089`, `M21.11`), run only by `:chess-ai:match`, never
 * by `build`: they take minutes.
 *
 * Every game starts from the opening position, the levels taking turns with White, each
 * engine seeded with the game's number. The clock stands still, so each level searches to its
 * full depth whatever the machine: the result is the levels' strength as designed, and is
 * the same on every run. A game still going at [PLY_LIMIT] is adjudicated on material: a
 * side ahead by [DECISIVE_MATERIAL] or more wins, otherwise it is drawn.
 */
class EngineMatchTest {
    private class Outcome(
        val score: Double,
        val summary: String,
    )

    /** One game between [first] (White when [firstIsWhite]) and [second], scored for [first]. */
    private fun game(
        first: Difficulty,
        second: Difficulty,
        seed: Long,
        firstIsWhite: Boolean,
    ): Outcome {
        val engine = AlphaBetaEngine(seed = seed, clock = { 0L })
        val firstSide = if (firstIsWhite) Side.WHITE else Side.BLACK
        var state = StandardPosition.newGame()
        var plies = 0
        while (!state.isOver && plies < PLY_LIMIT) {
            val level = if (state.sideToMove == firstSide) first else second
            state = ChessRules.applyMove(state, engine.chooseMove(state, level)!!)
            plies++
        }

        val result = state.result
        val margin = material(state, firstSide) - material(state, firstSide.opposite)
        val score =
            when {
                result != null ->
                    if (result.winner == null) {
                        0.5
                    } else if (result.winner == firstSide) {
                        1.0
                    } else {
                        0.0
                    }
                margin >= DECISIVE_MATERIAL -> 1.0
                margin <= -DECISIVE_MATERIAL -> 0.0
                else -> 0.5
            }
        val how = result?.reason?.toString() ?: "adjudicated at $plies plies, material ${if (margin >= 0) "+" else ""}$margin"
        return Outcome(score, "seed $seed, $first ${if (firstIsWhite) "White" else "Black"}: $score ($how)")
    }

    private fun material(
        state: GameState,
        side: Side,
    ): Int =
        state.board
            .occupiedSquares()
            .filter {
                it.second.side == side && it.second.type != PieceType.KING
            }.sumOf { Evaluation.valueOf(it.second.type) }

    /** [games] games of [first] against [second], in parallel; [first]'s share of the points. */
    private fun match(
        first: Difficulty,
        second: Difficulty,
        games: Int,
    ): Double {
        val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
        val outcomes =
            (1..games)
                .map { number -> pool.submit<Outcome> { game(first, second, seed = number.toLong(), firstIsWhite = number % 2 == 1) } }
                .map { it.get() }
        pool.shutdown()
        pool.awaitTermination(1, TimeUnit.MINUTES)

        outcomes.forEach { println("MATCH ${it.summary}") }
        val share = outcomes.sumOf { it.score } / games
        println("MATCH $first against $second: ${outcomes.sumOf { it.score }} of $games, ${(share * 100).toInt()}%")
        return share
    }

    @Test
    fun mediumBeatsEasyClearly() {
        val share = match(Difficulty.MEDIUM, Difficulty.EASY, games = GAMES)
        assertTrue(share >= 0.7, "Medium scored ${(share * 100).toInt()}% against Easy")
    }

    @Test
    fun hardIsAtLeastAsStrongAsMedium() {
        val share = match(Difficulty.HARD, Difficulty.MEDIUM, games = GAMES)
        assertTrue(share >= 0.5, "Hard scored ${(share * 100).toInt()}% against Medium")
    }

    private companion object {
        const val GAMES = 12
        const val PLY_LIMIT = 160

        /** Two pawns. */
        const val DECISIVE_MATERIAL = 200
    }
}
