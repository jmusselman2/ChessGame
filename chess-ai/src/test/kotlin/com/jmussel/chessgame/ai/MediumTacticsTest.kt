package com.jmussel.chessgame.ai

import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.GameState
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.PieceType
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `D089`'s Medium, and Hard with it, with material (`M21.11`): it takes what is left
 * unprotected and does not leave its own pieces to be taken for free.
 *
 * Each check runs at Medium's one-ply and two-ply searches, with a clock that stands still. A
 * slow phone, such as the Fire HD 8, has time for only the first.
 */
class MediumTacticsTest {
    private fun engines(): List<Pair<String, (GameState) -> Move?>> =
        (1L..3L).flatMap { seed ->
            listOf(1, 2).map { depth ->
                val engine =
                    AlphaBetaEngine(seed = seed, clock = { 0L }, settings = { AlphaBetaEngine.settingsFor(it).copy(maxDepth = depth) })
                "Medium at depth $depth, seed $seed" to { state: GameState -> engine.chooseMove(state, Difficulty.MEDIUM) }
            } +
                (
                    "Hard, seed $seed" to
                        { state: GameState -> AlphaBetaEngine(seed = seed, clock = { 0L }).chooseMove(state, Difficulty.HARD) }
                )
        }

    /**
     * The pieces, other than pawns, that the opponent could take after [move] with no way to
     * take back on that square.
     */
    private fun leftForFree(
        state: GameState,
        move: Move,
    ): List<String> {
        val next = ChessRules.applyMove(state, move)
        return ChessRules
            .legalMoves(next)
            .filter { capture -> next.board.pieceAt(capture.to)?.let { it.type != PieceType.PAWN } == true }
            .filter { capture ->
                val taken = ChessRules.applyMove(next, capture)
                taken.isOver || ChessRules.legalMoves(taken).none { it.to == capture.to }
            }.map { "${next.board.pieceAt(it.to)} on ${it.to}, by $it" }
    }

    @Test
    fun itTakesUnprotectedMaterial() {
        val wins =
            mapOf(
                position("4k3/p7/8/3b4/8/2N5/P7/4K3 w") to setOf(Move.of("c3", "d5")),
                position("4k3/p7/8/8/8/2r5/P7/B3K3 w") to setOf(Move.of("a1", "c3")),
                position("4k3/p7/8/3n4/4P3/8/P7/4K3 w") to setOf(Move.of("e4", "d5")),
                position("4k3/p7/8/8/8/8/1q5P/R3K3 b") to setOf(Move.of("b2", "a1")),
                // Black has just taken the e4 pawn with a knight nothing defends. Bxf7+ Kxf7
                // Nxe4 wins it back too, with Black's king pulled out.
                after("e2e4 e7e5 g1f3 b8c6 f1c4 g8f6 b1c3 f6e4") to setOf(Move.of("c3", "e4"), Move.of("c4", "f7")),
            )

        engines().forEach { (name, choose) ->
            wins.forEach { (state, win) ->
                val move = choose(state)
                assertTrue(move in win, "$name played $move, not one of $win, in\n${state.board}")
            }
        }
    }

    @Test
    fun itDoesNotLeaveAPieceToBeTakenForFree() {
        val threatened =
            listOf(
                // A bishop attacked by a pawn.
                position("4k3/p7/3p4/4B3/8/8/P7/4K3 w"),
                // A knight attacked by a bishop, with nothing defending it.
                position("4k3/p7/8/1b6/8/3N4/P7/4K3 w"),
                // The Two Knights: the bishop on c4 is attacked by the pawn on d5.
                after("e2e4 e7e5 g1f3 b8c6 f1c4 g8f6 f3g5 d7d5"),
                // The Scandinavian: Black's queen is attacked by the knight.
                after("e2e4 d7d5 e4d5 d8d5 b1c3"),
                // The Italian, where every piece is safe and should stay so.
                after("e2e4 e7e5 g1f3 b8c6 f1c4 g8f6 d2d3 f8c5 e1g1 d7d6"),
            )

        engines().forEach { (name, choose) ->
            threatened.forEach { state ->
                val move = choose(state)!!
                val free = leftForFree(state, move)
                assertTrue(free.isEmpty(), "$name played $move in\n${state.board}\nleaving $free")
            }
        }
    }
}
