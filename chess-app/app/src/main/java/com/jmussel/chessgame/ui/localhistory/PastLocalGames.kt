package com.jmussel.chessgame.ui.localhistory

import com.jmussel.chessgame.computer.computerLabel
import com.jmussel.chessgame.computer.humanSideLabel
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.GameResult
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.local.ComputerOpponent
import com.jmussel.chessgame.local.LocalGameKind
import com.jmussel.chessgame.local.LocalGameSummary
import com.jmussel.chessgame.local.StoredLocalGame

/** The list of finished local games, and what is happening to it (`M21.3`). */
data class PastLocalGamesUiState(
    /** Finished local games, newest first, as the store returns them. */
    val games: List<LocalGameSummary> = emptyList(),
    /** Whether the list is still being read from the device. */
    val loading: Boolean = true,
)

/**
 * One finished local game being looked back at, at [ply]: the position after that many
 * moves, from `0` (the start) to every move played (the end).
 *
 * Read-only. Every position comes from what the store recorded, the `positionBefore` of
 * each move and the final state, so stepping through a game never replays a move through
 * the rules (`D029`, `D061`).
 */
data class LocalGameReview(
    val stored: StoredLocalGame,
    val ply: Int = stored.game.history.size,
) {
    init {
        require(ply in 0..plies) { "Game ${stored.id} has $plies moves; there is no ply $ply" }
    }

    /** How many moves the game has. */
    val plies: Int
        get() = stored.game.history.size

    /**
     * The game as it stood at [ply]: that position, with the moves that led to it. The last
     * ply is the finished game itself, result included.
     */
    val game: ChessGame
        get() {
            val history = stored.game.history
            return if (ply == plies) stored.game else ChessGame(history[ply].positionBefore, history.subList(0, ply))
        }

    /** A pass-and-play game is reviewed on the face-to-face board it was played on (`D087`). */
    val faceToFace: Boolean
        get() = stored.kind == LocalGameKind.PASS_AND_PLAY

    /** Which side is at the bottom: White face to face, otherwise the human's side. */
    val orientation: Side
        get() = stored.computer?.humanSide ?: Side.WHITE

    val canStepBack: Boolean
        get() = ply > 0

    val canStepForward: Boolean
        get() = ply < plies

    /** The review at [ply], kept within the game. */
    fun at(ply: Int): LocalGameReview = copy(ply = ply.coerceIn(0, plies))

    /** `"After e7e5 (2 of 24)"`, or `"Start of the game"` before the first move. */
    val position: String
        get() = if (ply == 0) "Start of the game" else "After ${stored.game.history[ply - 1].move} ($ply of $plies)"
}

/**
 * How past local games read. Pure, so the wording is tested without a screen.
 *
 * There is no series, standing or statistic for a local game (`D084`): each line is the
 * game alone.
 */
object PastLocalGames {
    /**
     * `"Pass-and-play • 1 Oct 2026 • White won by checkmate"`, or for a game against the
     * computer `"Computer (Medium) • You played Black • 1 Oct 2026 • Black won by checkmate"`.
     */
    fun summaryFor(
        game: LocalGameSummary,
        formatDate: (Long) -> String,
    ): String = listOf(opponentLabel(game.computer), formatDate(game.completedAt), resultLabel(game.result)).joinToString(SEPARATOR)

    /** Who the game was against: `"Pass-and-play"`, or `"Computer (Medium) • You played Black"`. */
    fun opponentLabel(computer: ComputerOpponent?): String =
        if (computer == null) {
            kindLabel(LocalGameKind.PASS_AND_PLAY)
        } else {
            computerLabel(computer.difficulty) + SEPARATOR + humanSideLabel(computer.humanSide)
        }

    fun kindLabel(kind: LocalGameKind): String =
        when (kind) {
            LocalGameKind.PASS_AND_PLAY -> "Pass-and-play"
            LocalGameKind.COMPUTER -> "Against the computer"
        }

    /** `"White won by checkmate"`, `"Drawn by stalemate"`. */
    fun resultLabel(result: GameResult): String {
        val reason =
            result.reason.name
                .lowercase()
                .replace('_', ' ')
        return when (result.winner) {
            null -> "Drawn by $reason"
            Side.WHITE -> "White won by $reason"
            Side.BLACK -> "Black won by $reason"
        }
    }

    private const val SEPARATOR = " • "
}
