package com.jmussel.chessgame.ai

import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.GameResult
import com.jmussel.chessgame.core.chess.GameState
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Side
import kotlin.random.Random

/**
 * The project's own engine (`D086`): alpha-beta search over `chess-core`'s legal moves,
 * scored by [Evaluation] at the two weakest levels and by [SensibleEvaluation] above them
 * (`D089`).
 *
 * It deepens one ply at a time, starting from a legal fallback, and keeps the result of the
 * deepest search it finished. The one-ply search always finishes, however long it takes:
 * a slow phone may need more than a level's budget for it (`D089`). After that, running out
 * of [Settings.budgetMillis] or reaching [Settings.maxDepth] ends the search; either way the
 * answer is the last complete one. A cancelled search returns `null`.
 *
 * The lower levels are weaker in two ways: they look less far ahead, and they choose at
 * random among moves within [Settings.marginCentipawns] of the best. The choice is drawn from
 * a [Random] seeded by [seed] and the position, so one seed always plays the same move in the
 * same position.
 *
 * Every move goes through [ChessRules], so the engine plays only legal moves and needs no
 * rules of its own. [clock] is in nanoseconds; tests pass their own, and their own
 * [settings].
 */
class AlphaBetaEngine(
    private val seed: Long = 0,
    private val clock: () -> Long = System::nanoTime,
    private val settings: (Difficulty) -> Settings = ::settingsFor,
) : ChessEngine {
    /** How a [Difficulty] searches: `D088` and `D089` record why these. */
    data class Settings(
        val maxDepth: Int,
        val budgetMillis: Long,
        val marginCentipawns: Int,
        val evaluator: Evaluator = Evaluator.SIMPLE,
    )

    /** How a level judges a position. */
    enum class Evaluator {
        /** `D088`'s material and placement, which Very Easy and Easy keep unchanged. */
        SIMPLE,

        /** `D089`'s, which also knows the opening, pawn structure and what is en prise. */
        SENSIBLE,
    }

    override fun chooseMove(
        state: GameState,
        difficulty: Difficulty,
        isCancelled: () -> Boolean,
    ): Move? {
        require(!state.isOver) { "The game is over: ${state.result}" }
        val legal = ChessRules.legalMoves(state)
        require(legal.isNotEmpty()) { "An unfinished position has a legal move" }
        if (isCancelled()) return null

        val settings = settings(difficulty)
        val deadline = clock() + settings.budgetMillis * NANOS_PER_MILLI
        // Only a search deeper than one ply can run out of time.
        var deepening = false
        val search =
            Search(
                stop = { isCancelled() || (deepening && clock() - deadline > 0) },
                evaluator = settings.evaluator,
                engineSide = state.sideToMove,
            )

        // The fallback is settled before any search: a legal move, whatever happens next.
        var ordered = ordered(state, legal)
        var scores: List<Pair<Move, Int>>? = null

        for (depth in 1..settings.maxDepth) {
            deepening = depth > 1
            val completed = search.root(state, ordered, depth, settings.marginCentipawns) ?: break
            scores = completed
            ordered = completed.sortedByDescending { it.second }.map { it.first }
            if (completed.any { it.second >= MATE - depth }) break // A forced mate needs no deeper look.
        }

        if (isCancelled()) return null
        val finished = scores ?: return ordered.first()

        val best = finished.maxOf { it.second }
        val candidates = finished.filter { it.second >= best - settings.marginCentipawns }.map { it.first }.sortedBy { it.toString() }
        return candidates[Random(seed * 31 + positionHash(state)).nextInt(candidates.size)]
    }

    /** One search, which gives up as soon as [stop] says so. */
    private class Search(
        private val stop: () -> Boolean,
        private val evaluator: Evaluator,
        /** The side choosing the move. */
        private val engineSide: Side,
    ) {
        /**
         * Every root move's score at [depth], best first in [moves]' order of trying, or
         * `null` when the search was stopped before finishing.
         *
         * Scores are exact for moves within [margin] of the best; worse moves are only known
         * to be worse than that, which is all the choice among candidates needs.
         */
        fun root(
            state: GameState,
            moves: List<Move>,
            depth: Int,
            margin: Int,
        ): List<Pair<Move, Int>>? {
            var best = -INFINITY
            val scored = mutableListOf<Pair<Move, Int>>()
            try {
                for (move in moves) {
                    val floor = if (best == -INFINITY) -INFINITY else best - margin - 1
                    val score = -negamax(ChessRules.applyMove(state, move), depth - 1, -INFINITY, -floor, ply = 1)
                    scored += move to score
                    if (score > best) best = score
                }
            } catch (stopped: Stopped) {
                return null
            }
            return scored
        }

        private fun negamax(
            state: GameState,
            depth: Int,
            alpha: Int,
            beta: Int,
            ply: Int,
        ): Int {
            if (stop()) throw Stopped
            state.result?.let { return terminalScore(it, state, ply) }
            if (depth == 0) {
                return when (evaluator) {
                    Evaluator.SIMPLE -> Evaluation.evaluate(state)
                    // The engine's own turn is judged as it stands; its opponent's captures
                    // are played out.
                    Evaluator.SENSIBLE ->
                        if (state.sideToMove == engineSide) {
                            SensibleEvaluation.evaluate(state, engineSide)
                        } else {
                            captures(state, alpha, beta, ply, CAPTURE_PLIES)
                        }
                }
            }

            var bound = alpha
            var best = -INFINITY
            for (move in ordered(state, ChessRules.legalMoves(state))) {
                val score = -negamax(ChessRules.applyMove(state, move), depth - 1, -beta, -bound, ply + 1)
                if (score > best) best = score
                if (score > bound) bound = score
                if (bound >= beta) break
            }
            return best
        }

        /**
         * The capture search (`D089`): the side to move may let the position stand, or play one
         * of the few captures [SensibleEvaluation] says win material, and so on for [plies]
         * more. It settles what a static look cannot, such as whether taking a piece walks into
         * a fork, at the cost of only the captures worth trying.
         */
        private fun captures(
            state: GameState,
            alpha: Int,
            beta: Int,
            ply: Int,
            plies: Int,
        ): Int {
            if (stop()) throw Stopped
            state.result?.let { return terminalScore(it, state, ply) }
            val assessment = SensibleEvaluation.assess(state, engineSide, searchingCaptures = true)
            var best = assessment.score
            if (best >= beta || plies == 0) return best
            var bound = maxOf(alpha, best)
            for (capture in assessment.captures.take(CAPTURES_TRIED)) {
                if (!ChessRules.isLegal(state, capture)) continue
                val score = -captures(ChessRules.applyMove(state, capture), -beta, -bound, ply + 1, plies - 1)
                if (score > best) best = score
                if (score > bound) bound = score
                if (bound >= beta) break
            }
            return best
        }

        /** A finished game: lost for the side to move if it was mated, sooner being worse. */
        private fun terminalScore(
            result: GameResult,
            state: GameState,
            ply: Int,
        ): Int =
            when (result.winner) {
                null -> 0
                state.sideToMove -> MATE - ply
                else -> -(MATE - ply)
            }
    }

    /** Thrown to unwind a search that has been stopped. */
    private object Stopped : RuntimeException() {
        // Unwinding is all it is for, so it skips recording where it came from.
        override fun fillInStackTrace(): Throwable = this
    }

    companion object {
        private const val NANOS_PER_MILLI = 1_000_000L
        private const val INFINITY = 1_000_000

        /** How many captures deep the capture search goes, and how many it tries at each. */
        private const val CAPTURE_PLIES = 4
        private const val CAPTURES_TRIED = 3

        /** The score of mating at once; a mate found further ahead scores a little less. */
        const val MATE = 100_000

        /**
         * How each level searches. Very Easy and Easy are `D088`'s Easy and Medium, unchanged;
         * `D089` records the rest.
         */
        fun settingsFor(difficulty: Difficulty): Settings =
            when (difficulty) {
                Difficulty.VERY_EASY -> Settings(maxDepth = 1, budgetMillis = 500, marginCentipawns = 150)
                Difficulty.EASY -> Settings(maxDepth = 2, budgetMillis = 1_500, marginCentipawns = 30)
                Difficulty.MEDIUM -> Settings(maxDepth = 2, budgetMillis = 2_000, marginCentipawns = 0, evaluator = Evaluator.SENSIBLE)
                Difficulty.HARD -> Settings(maxDepth = 3, budgetMillis = 3_000, marginCentipawns = 0, evaluator = Evaluator.SENSIBLE)
            }

        /**
         * Captures and promotions first, the most valuable victim first and the cheapest
         * attacker first among equals, then quiet moves in a fixed order: alpha-beta prunes
         * far more when good moves come first, and a fixed order keeps the search repeatable.
         */
        private fun ordered(
            state: GameState,
            moves: List<Move>,
        ): List<Move> =
            moves.sortedWith(
                compareByDescending<Move> { move ->
                    val victim =
                        state.board
                            .pieceAt(move.to)
                            ?.type
                            ?.let(Evaluation::valueOf) ?: 0
                    val attacker =
                        state.board
                            .pieceAt(move.from)
                            ?.type
                            ?.let(Evaluation::valueOf) ?: 0
                    val promotion = move.promotion?.let(Evaluation::valueOf) ?: 0
                    if (victim == 0 && promotion == 0) Int.MIN_VALUE else victim * 10 - attacker / 10 + promotion
                }.thenBy { it.toString() },
            )

        private fun positionHash(state: GameState): Int =
            listOf(state.board, state.sideToMove, state.castlingRights, state.enPassantTarget).joinToString("|").hashCode()
    }
}
