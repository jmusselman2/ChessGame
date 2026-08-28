package com.jmussel.chessgame.ui.board

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.DrawClaim
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Side

/** One numbered line of the move list: White's move and Black's reply, if it has been made. */
data class MoveListRow(
    val number: Int,
    val white: String,
    val black: String?,
)

/**
 * The controls beside the board: the move list, Undo, Claim Draw, and Resign.
 *
 * Whether a control is offered is decided by `game-core` — the UI does not keep its own
 * idea of when a move may be taken back or a draw claimed.
 */
object GameControls {
    /** The moves played so far, paired into numbered rows. */
    fun moveList(game: ChessGame): List<MoveListRow> =
        game.moves.chunked(2).mapIndexed { index, movePair ->
            MoveListRow(
                number = index + 1,
                white = format(movePair.first()),
                black = movePair.getOrNull(1)?.let(::format),
            )
        }

    /** The move list as one line per numbered move, for a compact display. */
    fun moveListLines(game: ChessGame): List<String> =
        moveList(game).map { row ->
            listOfNotNull("${row.number}.", row.white, row.black).joinToString(" ")
        }

    /**
     * The side that may take its move back right now, or `null` when nobody may.
     *
     * On one device the player at the board is whoever moved last, so Undo is offered
     * exactly when `game-core` says that move is still undoable.
     */
    fun undoableSide(state: BoardUiState): Side? = ChessRules.undoableSide(state.game)

    /** Whether the Undo control should be shown at all. */
    fun canUndo(state: BoardUiState): Boolean = undoableSide(state) != null

    /** The state after the player takes the latest move back. */
    fun undo(state: BoardUiState): BoardUiState {
        val side = requireNotNull(undoableSide(state)) { "There is no move to take back" }
        val game = ChessRules.undo(state.game, side)
        return BoardUiState(
            game = game,
            selectedSquare = null,
            pendingPromotion = null,
            orientation = game.sideToMove,
        )
    }

    /** The draws that may validly be claimed right now, which is what to offer. */
    fun availableDrawClaims(state: BoardUiState): Set<DrawClaim> = ChessRules.availableDrawClaims(state.game)

    /** Whether any Claim Draw control should be shown. */
    fun canClaimDraw(state: BoardUiState): Boolean = availableDrawClaims(state).isNotEmpty()

    /** The state after the player claims [claim]. */
    fun claimDraw(
        state: BoardUiState,
        claim: DrawClaim,
    ): BoardUiState =
        state.copy(
            game = ChessRules.claimDraw(state.game, claim),
            selectedSquare = null,
            pendingPromotion = null,
        )

    /**
     * Whether resigning is possible at all, which is only that the game is still running.
     *
     * A player may give up on their opponent's move as readily as on their own
     * (`docs/PRODUCT.md`), so whose turn it is has nothing to do with it.
     */
    fun canResign(state: BoardUiState): Boolean = !state.game.isOver

    /**
     * The state after [side] resigns.
     *
     * Final once made (`D018`), which is why the screen asks first. On one device the side
     * resigning has to be named: there are two players at one board.
     */
    fun resign(
        state: BoardUiState,
        side: Side,
    ): BoardUiState =
        state.copy(
            game = ChessRules.resign(state.game, side),
            selectedSquare = null,
            pendingPromotion = null,
        )

    /** A short label for resigning as [side]. */
    fun resignLabelFor(side: Side): String = "Resign as ${if (side == Side.WHITE) "White" else "Black"}"

    /** A short label for a draw claim. */
    fun labelFor(claim: DrawClaim): String =
        when (claim) {
            DrawClaim.THREEFOLD_REPETITION -> "Claim draw (threefold repetition)"
            DrawClaim.FIFTY_MOVE_RULE -> "Claim draw (fifty-move rule)"
        }

    private fun format(move: Move): String = move.toString()
}
