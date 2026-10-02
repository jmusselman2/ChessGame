package com.jmussel.chessgame.ai

import com.jmussel.chessgame.core.chess.Attacks
import com.jmussel.chessgame.core.chess.Board
import com.jmussel.chessgame.core.chess.CastlingSide
import com.jmussel.chessgame.core.chess.GameState
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Piece
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import kotlin.math.abs
import kotlin.math.min

/**
 * The evaluation Medium and Hard play by (`D089`), in centipawns from the side to move's
 * point of view. It scores what [Evaluation] does not, so that the engine plays sensibly
 * rather than aimlessly:
 *
 * - **Placement**, from piece-square tables that blend from opening to endgame as material
 *   comes off: centre pawns forward, knights and bishops out, the king behind its pawns and
 *   then, in the endgame, in the centre.
 * - **The opening:** castling, and keeping the right to castle until then; minor pieces off
 *   their first squares, and no knight raids before castling; the queen kept back until they
 *   are; the edge pawns left alone.
 * - **Structure:** doubled, isolated and passed pawns, the bishop pair, rooks on open files,
 *   and the pawns in front of a castled king.
 * - **Threats.** What a capture wins is a static exchange: capture and recapture on the
 *   square, cheapest piece first, each side free to stop. When it is the engine's own turn at
 *   the end of a line, its best capture counts half: the opponent left it standing, which may
 *   be for a reason the search did not see. A second piece of the side to move en prise
 *   costs half its worth, since only one can be saved. The attacks come from `chess-core`'s
 *   [Attacks], so the engine has no rules of its own. Pins and pieces lined up behind one
 *   another are not seen.
 *
 * When it is the engine's opponent's turn, the engine does not guess: [assess] lists the
 * captures that win material for the opponent, best first, and the engine plays them out
 * through `ChessRules` (`D089`).
 */
internal object SensibleEvaluation {
    /** The non-pawn material of both sides at the start: the opening end of the blend. */
    private const val OPENING_MATERIAL = 2 * (2 * Evaluation.KNIGHT + 2 * Evaluation.BISHOP + 2 * Evaluation.ROOK + Evaluation.QUEEN)

    /** A king's value in an exchange: so high that it takes only what nothing defends. */
    private const val KING_IN_EXCHANGE = 10_000

    /** A score, and the captures that win material for the side to move, best first. */
    class Assessment(
        val score: Int,
        val captures: List<Move>,
    )

    /** The score of [state] in a search for [engine], the side that is choosing a move. */
    fun evaluate(
        state: GameState,
        engine: Side,
    ): Int = assess(state, engine, searchingCaptures = false).score

    /**
     * [state] in a search for [engine]. When [searchingCaptures], the caller plays out the
     * side to move's winning captures itself, so the score counts nothing for them, and the
     * captures are listed.
     */
    fun assess(
        state: GameState,
        engine: Side,
        searchingCaptures: Boolean,
    ): Assessment {
        val board = state.board
        val pieces = board.occupiedSquares()

        // What each piece attacks, and, for every occupied square, each side's pieces that
        // attack it, as `value * 64 + square index`, so that sorting puts the cheapest first.
        val pawnsOnFile = Array(2) { IntArray(Square.FILES) }
        val attacksFrom = HashMap<Square, List<Square>>()
        val attackersOn = Array(2) { arrayOfNulls<MutableList<Int>>(Square.COUNT) }
        pieces.forEach { (square, piece) ->
            val code = exchangeValue(piece.type) * Square.COUNT + square.index
            val attacked = Attacks.attackedSquaresFrom(board, square)
            attacksFrom[square] = attacked
            attacked.forEach { target ->
                if (!board.isEmpty(target)) {
                    val codes =
                        attackersOn[piece.side.ordinal][target.index]
                            ?: mutableListOf<Int>().also { attackersOn[piece.side.ordinal][target.index] = it }
                    codes += code
                }
            }
            if (piece.type == PieceType.PAWN) pawnsOnFile[piece.side.ordinal][square.file]++
        }

        val nonPawnMaterial = pieces.sumOf { (_, piece) -> if (piece.type == PieceType.PAWN) 0 else Evaluation.valueOf(piece.type) }
        val phase = min(nonPawnMaterial, OPENING_MATERIAL)

        var opening = 0
        var endgame = 0
        Side.entries.forEach { side ->
            val sign = if (side == Side.WHITE) 1 else -1
            val own = pieces.filter { it.second.side == side }
            val king = own.first { it.second.type == PieceType.KING }.first
            val castled = relativeRank(king, side) == 0 && king.file in CASTLED_FILES
            val minorsAtHome = minorsAtHomeOf(own, side)
            var bishops = 0

            own.forEach { (square, piece) ->
                val index = tableIndex(square, side)
                val material = Evaluation.valueOf(piece.type)
                when (piece.type) {
                    PieceType.PAWN -> {
                        opening += sign * (material + PAWN_OPENING[index])
                        endgame += sign * (material + PAWN_ENDGAME[index])
                        val structure = pawnStructure(square, side, pawnsOnFile, pieces)
                        opening += sign * structure.first
                        endgame += sign * structure.second
                        // An edge pawn pushed before castling does nothing for the game.
                        if (!castled && (square.file == 0 || square.file == Square.FILES - 1) && relativeRank(square, side) > 1) {
                            opening -= sign * EDGE_PAWN_PUSHED
                        }
                    }
                    PieceType.KNIGHT -> {
                        opening += sign * (material + KNIGHT_TABLE[index])
                        endgame += sign * (material + KNIGHT_TABLE[index])
                        // A raid into the other half before castling wastes time the opening needs.
                        if (!castled && relativeRank(square, side) >= RAIDING_RANK) opening -= sign * EARLY_RAID
                    }
                    PieceType.BISHOP -> {
                        opening += sign * (material + BISHOP_TABLE[index])
                        endgame += sign * (material + BISHOP_TABLE[index])
                        bishops++
                    }
                    PieceType.ROOK -> {
                        val file = rookFileBonus(square, side, pawnsOnFile)
                        opening += sign * (material + ROOK_TABLE[index] + file)
                        endgame += sign * (material + ROOK_TABLE[index] + file)
                    }
                    PieceType.QUEEN -> {
                        opening += sign * (material + QUEEN_TABLE[index])
                        endgame += sign * (material + QUEEN_TABLE[index])
                        // Out early, it is chased about while the minor pieces wait.
                        if (minorsAtHome >= 1 && square != homeSquares(side, PieceType.QUEEN).single()) {
                            opening -= sign * EARLY_QUEEN
                        }
                    }
                    PieceType.KING -> {
                        opening += sign * KING_OPENING[index]
                        endgame += sign * KING_ENDGAME[index]
                    }
                }
            }

            if (bishops >= 2) {
                opening += sign * BISHOP_PAIR
                endgame += sign * BISHOP_PAIR
            }
            opening -= sign * minorsAtHome * MINOR_AT_HOME
            opening -= sign * BLOCKED_CENTRE_PAWN * blockedCentrePawns(board, side)
            opening += sign * castling(state, side, king, castled)
            // An uncastled king in the middle, with a centre file opened in front of it.
            if (!castled) opening -= sign * OPEN_CENTRE_FILE * CENTRE_FILES.count { pawnsOnFile[side.ordinal][it] == 0 }
            if (castled) opening -= sign * MISSING_SHIELD_PAWN * missingShieldPawns(king, side, pieces)
        }

        val placement = (opening * phase + endgame * (OPENING_MATERIAL - phase)) / OPENING_MATERIAL
        val mover = state.sideToMove
        val waiting = mover.opposite

        // In check, the only capture worth anything is of a piece giving check.
        val moverKing = pieces.first { (_, piece) -> piece.side == mover && piece.type == PieceType.KING }.first
        val checkers =
            pieces
                .filter { (square, piece) ->
                    piece.side == waiting && moverKing in attacksFrom.getValue(square)
                }.map { it.first }
        val winning = winnable(pieces, attackersOn, attacker = mover).filter { (target, _) -> checkers.isEmpty() || target in checkers }

        val win =
            when {
                searchingCaptures -> 0
                mover == engine -> (winning.firstOrNull()?.second ?: 0) / 2
                else -> winning.firstOrNull()?.second ?: 0
            }
        val loss = (winnable(pieces, attackersOn, attacker = waiting).map { it.second }.getOrNull(1) ?: 0) / 2
        val score = (if (mover == Side.WHITE) placement else -placement) + win - loss
        val captures =
            if (!searchingCaptures) {
                emptyList()
            } else {
                winning.map { (target, _) -> captureOf(board, attackersOn[mover.ordinal][target.index]!!.min(), target) }
            }
        return Assessment(score, captures)
    }

    /** The capture of [target] by the piece [code] names, promoting to a queen on the last rank. */
    private fun captureOf(
        board: Board,
        code: Int,
        target: Square,
    ): Move {
        val from = Square.ofIndex(code % Square.COUNT)
        val promotes = board.pieceAt(from)?.type == PieceType.PAWN && (target.rank == 0 || target.rank == Square.RANKS - 1)
        return Move(from, target, if (promotes) PieceType.QUEEN else null)
    }

    /**
     * Every piece of [attacker]'s opponent that [attacker] wins material by capturing, with
     * what it wins, best first.
     */
    private fun winnable(
        pieces: List<Pair<Square, Piece>>,
        attackersOn: Array<Array<MutableList<Int>?>>,
        attacker: Side,
    ): List<Pair<Square, Int>> =
        pieces
            .filter { (_, piece) -> piece.side != attacker && piece.type != PieceType.KING }
            .mapNotNull { (square, piece) ->
                val attackers = attackersOn[attacker.ordinal][square.index] ?: return@mapNotNull null
                val defenders = attackersOn[attacker.opposite.ordinal][square.index].orEmpty()
                val gain =
                    exchange(
                        Evaluation.valueOf(piece.type),
                        attackers.map { it / Square.COUNT }.sorted(),
                        defenders
                            .map {
                                it /
                                    Square.COUNT
                            }.sorted(),
                    )
                (square to gain).takeIf { gain > 0 }
            }.sortedByDescending { it.second }

    /**
     * What the side with [attackers] wins by starting the captures on a square holding a piece
     * worth [target], against [defenders]: both lists cheapest first, and each side may stop
     * capturing when going on would lose. Negative when starting loses.
     */
    private fun exchange(
        target: Int,
        attackers: List<Int>,
        defenders: List<Int>,
    ): Int {
        // gain[n] is what the side making the n-th capture is up if the captures stop there.
        val gain = IntArray(attackers.size + defenders.size + 1)
        gain[0] = target
        var onSquare = attackers[0]
        var nextAttacker = 1
        var nextDefender = 0
        var captures = 0
        while (true) {
            val defendersTurn = captures % 2 == 0
            val recapturer = if (defendersTurn) defenders.getOrNull(nextDefender++) else attackers.getOrNull(nextAttacker++)
            recapturer ?: break
            captures++
            gain[captures] = onSquare - gain[captures - 1]
            onSquare = recapturer
        }
        while (captures > 0) {
            gain[captures - 1] = -maxOf(-gain[captures - 1], gain[captures])
            captures--
        }
        return gain[0]
    }

    private fun exchangeValue(type: PieceType): Int = if (type == PieceType.KING) KING_IN_EXCHANGE else Evaluation.valueOf(type)

    /** Doubled, isolated and passed: an (opening, endgame) pair for the pawn on [square]. */
    private fun pawnStructure(
        square: Square,
        side: Side,
        pawnsOnFile: Array<IntArray>,
        pieces: List<Pair<Square, Piece>>,
    ): Pair<Int, Int> {
        val own = pawnsOnFile[side.ordinal]
        var opening = 0
        var endgame = 0
        if (own[square.file] > 1) {
            opening -= DOUBLED_PAWN
            endgame -= DOUBLED_PAWN
        }
        val neighbours = (square.file - 1..square.file + 1).filter { it in 0 until Square.FILES && it != square.file }
        if (neighbours.none { own[it] > 0 }) {
            opening -= ISOLATED_PAWN
            endgame -= ISOLATED_PAWN
        }
        val rank = relativeRank(square, side)
        val passed =
            pieces.none { (other, piece) ->
                piece.side != side &&
                    piece.type == PieceType.PAWN &&
                    abs(other.file - square.file) <= 1 &&
                    relativeRank(other, side) > rank
            }
        if (passed) {
            opening += PASSED_PAWN_OPENING * rank
            endgame += PASSED_PAWN_ENDGAME * rank
        }
        return opening to endgame
    }

    private fun rookFileBonus(
        square: Square,
        side: Side,
        pawnsOnFile: Array<IntArray>,
    ): Int =
        when {
            pawnsOnFile[side.ordinal][square.file] > 0 -> 0
            pawnsOnFile[side.opposite.ordinal][square.file] > 0 -> ROOK_HALF_OPEN_FILE
            else -> ROOK_OPEN_FILE
        }

    /** Castled is best; until then, the right to castle is worth keeping, and losing it costs. */
    private fun castling(
        state: GameState,
        side: Side,
        king: Square,
        castled: Boolean,
    ): Int {
        val rights = CastlingSide.entries.count { state.castlingRights.has(side, it) }
        return when {
            castled -> CASTLED
            rights > 0 -> CASTLING_RIGHT * rights
            relativeRank(king, side) == 0 && king.file == KING_FILE -> 0
            else -> -KING_STRANDED
        }
    }

    /** Unmoved d- and e-pawns with a piece of their own standing in front of them. */
    private fun blockedCentrePawns(
        board: Board,
        side: Side,
    ): Int {
        val home = if (side == Side.WHITE) 1 else Square.RANKS - 2
        val ahead = if (side == Side.WHITE) 2 else Square.RANKS - 3
        return CENTRE_FILES.count { file ->
            val pawn = board.pieceAt(Square.of(file, home))
            val blocker = board.pieceAt(Square.of(file, ahead))
            pawn?.side == side && pawn.type == PieceType.PAWN && blocker?.side == side
        }
    }

    /** Own pawns missing from the three files in front of a castled king, one or two ranks up. */
    private fun missingShieldPawns(
        king: Square,
        side: Side,
        pieces: List<Pair<Square, Piece>>,
    ): Int =
        (king.file - 1..king.file + 1).filter { it in 0 until Square.FILES }.count { file ->
            pieces.none { (square, piece) ->
                piece.side == side && piece.type == PieceType.PAWN && square.file == file && relativeRank(square, side) in 1..2
            }
        }

    private fun minorsAtHomeOf(
        own: List<Pair<Square, Piece>>,
        side: Side,
    ): Int =
        own.count { (square, piece) ->
            (piece.type == PieceType.KNIGHT || piece.type == PieceType.BISHOP) && square in homeSquares(side, piece.type)
        }

    /** Where [side]'s pieces of [type] start. */
    private fun homeSquares(
        side: Side,
        type: PieceType,
    ): Set<Square> {
        val rank = if (side == Side.WHITE) 0 else Square.RANKS - 1
        val files =
            when (type) {
                PieceType.KNIGHT -> listOf(1, 6)
                PieceType.BISHOP -> listOf(2, 5)
                PieceType.QUEEN -> listOf(3)
                else -> emptyList()
            }
        return files.mapTo(mutableSetOf()) { Square.of(it, rank) }
    }

    /** The rank counted from [side]'s own back rank, 0 to 7. */
    private fun relativeRank(
        square: Square,
        side: Side,
    ): Int = if (side == Side.WHITE) square.rank else Square.RANKS - 1 - square.rank

    /** Where [square] falls in a table written from [side]'s point of view, its eighth rank first. */
    private fun tableIndex(
        square: Square,
        side: Side,
    ): Int = (Square.RANKS - 1 - relativeRank(square, side)) * Square.FILES + square.file

    private const val KING_FILE = 4
    private val CENTRE_FILES = listOf(3, 4)
    private val CASTLED_FILES = setOf(0, 1, 2, 6, 7)

    private const val CASTLED = 50
    private const val CASTLING_RIGHT = 10
    private const val KING_STRANDED = 30
    private const val MINOR_AT_HOME = 15
    private const val EARLY_QUEEN = 25
    private const val EARLY_RAID = 15

    /** The fifth rank counted from a side's own: the far half of the board. */
    private const val RAIDING_RANK = 4
    private const val EDGE_PAWN_PUSHED = 35
    private const val MISSING_SHIELD_PAWN = 12
    private const val BLOCKED_CENTRE_PAWN = 25
    private const val OPEN_CENTRE_FILE = 15
    private const val BISHOP_PAIR = 30
    private const val DOUBLED_PAWN = 12
    private const val ISOLATED_PAWN = 12
    private const val PASSED_PAWN_OPENING = 3
    private const val PASSED_PAWN_ENDGAME = 12
    private const val ROOK_OPEN_FILE = 15
    private const val ROOK_HALF_OPEN_FILE = 8

    // The tables are written as a board is drawn, from the owner's side: its eighth rank on
    // the first line and its own back rank on the last. They are the widely used "simplified
    // evaluation function" tables, except that the king's best squares in the opening are the
    // two it castles to, so a castled king has no reason to step aside.

    private val PAWN_OPENING =
        intArrayOf(
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            50,
            50,
            50,
            50,
            50,
            50,
            50,
            50,
            10,
            10,
            20,
            30,
            30,
            20,
            10,
            10,
            5,
            5,
            10,
            25,
            25,
            10,
            5,
            5,
            0,
            0,
            0,
            20,
            20,
            0,
            0,
            0,
            5,
            -5,
            -10,
            0,
            0,
            -10,
            -5,
            5,
            5,
            10,
            10,
            -20,
            -20,
            10,
            10,
            5,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
        )

    private val PAWN_ENDGAME =
        intArrayOf(
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            80,
            80,
            80,
            80,
            80,
            80,
            80,
            80,
            50,
            50,
            50,
            50,
            50,
            50,
            50,
            50,
            30,
            30,
            30,
            30,
            30,
            30,
            30,
            30,
            20,
            20,
            20,
            20,
            20,
            20,
            20,
            20,
            10,
            10,
            10,
            10,
            10,
            10,
            10,
            10,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
        )

    private val KNIGHT_TABLE =
        intArrayOf(
            -50,
            -40,
            -30,
            -30,
            -30,
            -30,
            -40,
            -50,
            -40,
            -20,
            0,
            0,
            0,
            0,
            -20,
            -40,
            -30,
            0,
            10,
            15,
            15,
            10,
            0,
            -30,
            -30,
            5,
            15,
            20,
            20,
            15,
            5,
            -30,
            -30,
            0,
            15,
            20,
            20,
            15,
            0,
            -30,
            -30,
            5,
            10,
            15,
            15,
            10,
            5,
            -30,
            -40,
            -20,
            0,
            5,
            5,
            0,
            -20,
            -40,
            -50,
            -40,
            -30,
            -30,
            -30,
            -30,
            -40,
            -50,
        )

    private val BISHOP_TABLE =
        intArrayOf(
            -20,
            -10,
            -10,
            -10,
            -10,
            -10,
            -10,
            -20,
            -10,
            0,
            0,
            0,
            0,
            0,
            0,
            -10,
            -10,
            0,
            5,
            10,
            10,
            5,
            0,
            -10,
            -10,
            5,
            5,
            10,
            10,
            5,
            5,
            -10,
            -10,
            0,
            10,
            10,
            10,
            10,
            0,
            -10,
            -10,
            10,
            10,
            10,
            10,
            10,
            10,
            -10,
            -10,
            5,
            0,
            0,
            0,
            0,
            5,
            -10,
            -20,
            -10,
            -10,
            -10,
            -10,
            -10,
            -10,
            -20,
        )

    private val ROOK_TABLE =
        intArrayOf(
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            5,
            10,
            10,
            10,
            10,
            10,
            10,
            5,
            -5,
            0,
            0,
            0,
            0,
            0,
            0,
            -5,
            -5,
            0,
            0,
            0,
            0,
            0,
            0,
            -5,
            -5,
            0,
            0,
            0,
            0,
            0,
            0,
            -5,
            -5,
            0,
            0,
            0,
            0,
            0,
            0,
            -5,
            -5,
            0,
            0,
            0,
            0,
            0,
            0,
            -5,
            0,
            0,
            0,
            5,
            5,
            0,
            0,
            0,
        )

    private val QUEEN_TABLE =
        intArrayOf(
            -20,
            -10,
            -10,
            -5,
            -5,
            -10,
            -10,
            -20,
            -10,
            0,
            0,
            0,
            0,
            0,
            0,
            -10,
            -10,
            0,
            5,
            5,
            5,
            5,
            0,
            -10,
            -5,
            0,
            5,
            5,
            5,
            5,
            0,
            -5,
            0,
            0,
            5,
            5,
            5,
            5,
            0,
            -5,
            -10,
            5,
            5,
            5,
            5,
            5,
            0,
            -10,
            -10,
            0,
            5,
            0,
            0,
            0,
            0,
            -10,
            -20,
            -10,
            -10,
            -5,
            -5,
            -10,
            -10,
            -20,
        )

    private val KING_OPENING =
        intArrayOf(
            -30,
            -40,
            -40,
            -50,
            -50,
            -40,
            -40,
            -30,
            -30,
            -40,
            -40,
            -50,
            -50,
            -40,
            -40,
            -30,
            -30,
            -40,
            -40,
            -50,
            -50,
            -40,
            -40,
            -30,
            -30,
            -40,
            -40,
            -50,
            -50,
            -40,
            -40,
            -30,
            -20,
            -30,
            -30,
            -40,
            -40,
            -30,
            -30,
            -20,
            -10,
            -20,
            -20,
            -20,
            -20,
            -20,
            -20,
            -10,
            20,
            20,
            0,
            0,
            0,
            0,
            20,
            20,
            20,
            20,
            30,
            0,
            0,
            10,
            30,
            20,
        )

    private val KING_ENDGAME =
        intArrayOf(
            -50,
            -40,
            -30,
            -20,
            -20,
            -30,
            -40,
            -50,
            -30,
            -20,
            -10,
            0,
            0,
            -10,
            -20,
            -30,
            -30,
            -10,
            20,
            30,
            30,
            20,
            -10,
            -30,
            -30,
            -10,
            30,
            40,
            40,
            30,
            -10,
            -30,
            -30,
            -10,
            30,
            40,
            40,
            30,
            -10,
            -30,
            -30,
            -10,
            20,
            30,
            30,
            20,
            -10,
            -30,
            -30,
            -30,
            0,
            0,
            0,
            0,
            -30,
            -30,
            -50,
            -30,
            -30,
            -30,
            -30,
            -30,
            -30,
            -50,
        )
}
