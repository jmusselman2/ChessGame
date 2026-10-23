package com.jmussel.chessgame.ui.review

import com.jmussel.chessgame.api.CompletedGameReviewDto
import com.jmussel.chessgame.api.GameViewDto
import com.jmussel.chessgame.api.ReviewPositionDto
import com.jmussel.chessgame.core.chess.Board
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.GameState
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.local.LocalGameKind
import com.jmussel.chessgame.local.StoredLocalGame
import com.jmussel.chessgame.ui.board.BoardRendering
import com.jmussel.chessgame.ui.board.GameControls
import com.jmussel.chessgame.ui.board.GameEndings
import com.jmussel.chessgame.ui.board.MoveHistory
import com.jmussel.chessgame.ui.game.OnlineGame
import com.jmussel.chessgame.ui.localhistory.PastLocalGames

/** One position a review can show: the board, whose move it is, and whether they are in check. */
data class ReviewPosition(
    val board: Board,
    val sideToMove: Side,
    val inCheck: Boolean,
)

/** One move of a review: where it went, for the highlight, and its SAN, for the words. */
data class ReviewMove(
    val from: Square,
    val to: Square,
    val san: String,
)

/**
 * A finished game being looked back at, at [ply]: the position after that many moves, from `0`
 * (the start) to every move played (the end). Read-only, and the same for a local game
 * (`M21.3`) and an online one (`D100`, `M22.10`).
 *
 * Every position is one that was recorded: [positions] holds the position each move was played
 * from and then the final one, so stepping never replays a move through the rules (`D061`,
 * `D101`). Each source maps its own recorded history into this; neither shares the other's
 * storage or wire types.
 */
data class GameReview(
    /** Who the game was against, as its list names it. */
    val title: String,
    val moves: List<ReviewMove>,
    /** `moves.size + 1` positions: before each move, then the final one. */
    val positions: List<ReviewPosition>,
    /** How the game ended, in the words of its own screen (`M21.16`), shown at the end. */
    val ending: String,
    /** Which side is at the bottom of the board. */
    val orientation: Side,
    /** Whether the board is drawn face to face, as pass-and-play was played (`D087`). */
    val faceToFace: Boolean = false,
    val ply: Int = moves.size,
) {
    init {
        require(positions.size == moves.size + 1) { "${moves.size} moves need ${moves.size + 1} positions, not ${positions.size}" }
        require(ply in 0..plies) { "The game has $plies moves; there is no ply $ply" }
    }

    val plies: Int
        get() = moves.size

    /** The position at [ply]. */
    val position: ReviewPosition
        get() = positions[ply]

    val canStepBack: Boolean
        get() = ply > 0

    val canStepForward: Boolean
        get() = ply < plies

    /** The review at [ply], kept within the game. */
    fun at(ply: Int): GameReview = copy(ply = ply.coerceIn(0, plies))

    /** The squares of the move that reached this position, for the last-move highlight. */
    val lastMove: Set<Square>
        get() = moves.getOrNull(ply - 1)?.let { setOf(it.from, it.to) } ?: emptySet()

    /** The king in check in this position, for the check marker; the final checkmate included. */
    val checkedKing: Square?
        get() = if (position.inCheck) BoardRendering.kingSquare(position.board, position.sideToMove) else null

    /** The ending at the last ply; before it, whose move it was, and a check. */
    val status: String
        get() =
            if (ply == plies) {
                ending
            } else {
                val turn = "${GameControls.sideName(position.sideToMove)} to move"
                if (position.inCheck) "$turn $SEPARATOR $CHECK" else turn
            }

    /** `"After Nf3 (2 of 24)"` in SAN, or `"Start of the game"` before the first move. */
    val positionText: String
        get() = if (ply == 0) "Start of the game" else "After ${moves[ply - 1].san} ($ply of $plies)"

    /**
     * The move list at [ply] (`D105`): only the moves up to the position on the board, the last of
     * them current, and none at the start. The moves after it are not shown.
     */
    val history: MoveHistory
        get() = MoveHistory.review(moves.map { it.san }, ply)

    /** The shown move list in SAN, as numbered pairs: `"1. e4 e5"`. */
    fun moveListLines(): List<String> = history.lines

    private companion object {
        const val SEPARATOR = "•"
        const val CHECK = "Check"
    }
}

/** How each kind of finished game becomes a [GameReview]. */
object GameReviews {
    /**
     * A finished local game, from the store's records (`M21.3`): each move's recorded position
     * and the final state. The SAN is worked out from those same recorded positions.
     */
    fun ofLocal(stored: StoredLocalGame): GameReview {
        val game = stored.game
        val result = requireNotNull(game.result) { "Local game ${stored.id} has not ended" }
        val computer = stored.computer

        return GameReview(
            title = PastLocalGames.opponentLabel(computer),
            moves =
                game.history.map { record ->
                    ReviewMove(record.move.from, record.move.to, ChessRules.san(record.positionBefore, record.move))
                },
            positions = game.history.map { positionOf(it.positionBefore) } + positionOf(game.state),
            ending = if (computer == null) GameEndings.forSides(result) else GameEndings.forComputer(result, computer.humanSide),
            orientation = computer?.humanSide ?: Side.WHITE,
            faceToFace = stored.kind == LocalGameKind.PASS_AND_PLAY,
        )
    }

    /**
     * A finished online game, from the server's review projection (`D100`, `D101`): its positions
     * and SAN are the server's, from recorded positions; nothing here replays a move. [game] is
     * the finished game as this player sees it, for their side and the opponent's name.
     */
    fun ofOnline(
        game: GameViewDto,
        review: CompletedGameReviewDto,
    ): GameReview =
        GameReview(
            title = "Against ${game.opponent.username}",
            moves =
                review.plies.map { ply ->
                    ReviewMove(Square.parse(ply.move.from), Square.parse(ply.move.to), ply.move.san)
                },
            positions = review.plies.map { positionOf(it.positionBefore) } + positionOf(review.finalPosition),
            ending = OnlineGame.statusFor(game.copy(result = review.result, terminationReason = review.terminationReason)),
            orientation = OnlineGame.sideOf(game),
        )

    private fun positionOf(state: GameState): ReviewPosition = ReviewPosition(state.board, state.sideToMove, ChessRules.isInCheck(state))

    private fun positionOf(position: ReviewPositionDto): ReviewPosition =
        ReviewPosition(
            board = OnlineGame.boardFrom(position.board),
            sideToMove = if (position.sideToMove == Side.BLACK.name) Side.BLACK else Side.WHITE,
            inCheck = position.inCheck,
        )
}
