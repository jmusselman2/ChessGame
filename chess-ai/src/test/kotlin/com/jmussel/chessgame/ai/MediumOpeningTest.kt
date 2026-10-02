package com.jmussel.chessgame.ai

import com.jmussel.chessgame.core.chess.Attacks
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.GameState
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.core.chess.StandardPosition
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `D089`'s Medium in the opening (`M21.11`): it develops and castles rather than wandering.
 *
 * The games are Medium against itself from ten different first moves, with a clock that
 * stands still so that its full two-ply search always runs, whatever the machine. Each side's
 * own moves are checked against the rules `D089` states, rather than against one exact move.
 * The first moves from given openings are checked at one ply too: a slow phone such as the
 * Fire HD 8 has time for no more.
 */
class MediumOpeningTest {
    /** Medium searching [depth] plies, with a clock that stands still. */
    private fun medium(
        seed: Long,
        depth: Int,
    ) = AlphaBetaEngine(seed = seed, clock = { 0L }, settings = { AlphaBetaEngine.settingsFor(it).copy(maxDepth = depth) })

    /** One game: [opening] played as given, then Medium for both sides, to Black's twelfth move. */
    private fun selfPlay(
        opening: String,
        seed: Long,
        depth: Int,
    ): Pair<List<GameState>, Int> {
        val white = medium(seed, depth)
        val black = medium(seed + 1_000, depth)
        val book = moves(opening)
        val positions = mutableListOf(StandardPosition.newGame())
        book.forEach { positions += ChessRules.applyMove(positions.last(), it) }
        while (positions.size <= 2 * PLAYED_MOVES && !positions.last().isOver) {
            val state = positions.last()
            val engine = if (state.sideToMove == Side.WHITE) white else black
            positions += ChessRules.applyMove(state, engine.chooseMove(state, Difficulty.MEDIUM)!!)
        }
        return positions to book.size
    }

    /** What [side] did in the opening that `D089` says Medium does not do. */
    private fun faults(
        positions: List<GameState>,
        bookPlies: Int,
        side: Side,
    ): List<String> {
        val faults = mutableListOf<String>()
        val movedMinors = mutableSetOf<Square>()
        var castled = false

        for (ply in positions.indices.drop(1)) {
            val before = positions[ply - 1]
            val after = positions[ply]
            if (before.sideToMove != side) continue
            val move = moveBetween(before, after)
            val piece = before.board.pieceAt(move.from)!!
            val moveNumber = before.fullmoveNumber
            val capture = !before.board.isEmpty(move.to) || (piece.type == PieceType.PAWN && move.from.file != move.to.file)
            val inCheck = ChessRules.isInCheck(before)
            val castling = piece.type == PieceType.KING && abs(move.to.file - move.from.file) == 2
            val ownMove = ply > bookPlies
            val label = "$side's move $moveNumber, $move"

            if (ownMove && moveNumber <= 8 && !capture && !inCheck) {
                if (piece.type == PieceType.KING && !castling) faults += "$label: a king move"
                if (piece.type == PieceType.ROOK && !castled) faults += "$label: a rook move before castling"
                if (piece.type == PieceType.PAWN &&
                    (move.from.file == 0 || move.from.file == Square.FILES - 1)
                ) {
                    faults += "$label: an edge pawn"
                }
            }
            if (piece.type == PieceType.KNIGHT || piece.type == PieceType.BISHOP) {
                // A capture, or a piece under attack getting out of the way, has its reason.
                val threatened = Attacks.isAttacked(before.board, move.from, side.opposite)
                if (ownMove && moveNumber <= 6 && move.from in movedMinors && !capture && !threatened) {
                    faults += "$label: the same minor piece again"
                }
                movedMinors -= move.from
                movedMinors += move.to
            }
            if (castling) castled = true
            if (moveNumber == 10 && !castled && !after.isOver) faults += "$side has not castled by move 10"
        }
        return faults
    }

    /** The move that turned [before] into [after]. */
    private fun moveBetween(
        before: GameState,
        after: GameState,
    ): Move = ChessRules.legalMoves(before).first { ChessRules.applyMove(before, it).board == after.board }

    @Test
    fun inSelfPlayItDevelopsAndCastlesWithoutAimlessMoves() {
        val faults =
            OPENINGS.flatMapIndexed { index, opening ->
                val (positions, book) = selfPlay(opening, seed = index.toLong() + 1, depth = MEDIUM_DEPTH)
                Side.entries.flatMap { side -> faults(positions, book, side).map { "after \"$opening\": $it" } }
            }

        assertEquals(emptyList(), faults, faults.joinToString("\n"))
    }

    @Test
    fun itOpensWithAReasonableDevelopingMove() {
        val reasonable =
            mapOf(
                "" to "e2e4 d2d4 g1f3 c2c4 b1c3",
                "e2e4" to "e7e5 c7c5 e7e6 c7c6 d7d5 g8f6 b8c6 d7d6",
                "d2d4" to "d7d5 g8f6 e7e6 c7c5 d7d6 b8c6",
                "e2e4 e7e5" to "g1f3 b1c3 f1c4 d2d4",
                "d2d4 d7d5" to "c2c4 g1f3 b1c3 c1f4 c1g5 e2e3",
                "e2e4 e7e5 g1f3" to "b8c6 g8f6 d7d6",
                "e2e4 c7c5" to "g1f3 b1c3 d2d4 c2c3 f1c4",
            )

        reasonable.forEach { (opening, expected) ->
            val choices = moves(expected).toSet()
            listOf(1, MEDIUM_DEPTH).forEach { depth ->
                (1L..4L).forEach { seed ->
                    val move = medium(seed, depth).chooseMove(after(opening), Difficulty.MEDIUM)
                    assertTrue(move in choices, "After \"$opening\", depth $depth, seed $seed played $move, not one of $expected")
                }
            }
        }
    }

    private companion object {
        /** Medium's full search. */
        val MEDIUM_DEPTH = AlphaBetaEngine.settingsFor(Difficulty.MEDIUM).maxDepth

        /** How many moves each side plays. */
        const val PLAYED_MOVES = 12

        /** First moves to start from, so that the games differ. */
        val OPENINGS =
            listOf(
                "",
                "e2e4",
                "d2d4",
                "c2c4",
                "g1f3",
                "e2e4 e7e5",
                "e2e4 c7c5",
                "d2d4 d7d5",
                "d2d4 g8f6",
                "e2e4 e7e6",
            )
    }
}
