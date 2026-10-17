package com.jmussel.chessgame.ui.localhistory

import com.jmussel.chessgame.computer.computerLabel
import com.jmussel.chessgame.computer.humanSideLabel
import com.jmussel.chessgame.core.chess.GameResult
import com.jmussel.chessgame.local.ComputerOpponent
import com.jmussel.chessgame.local.LocalGameKind
import com.jmussel.chessgame.local.LocalGameSummary
import com.jmussel.chessgame.ui.board.GameEndings

/** The list of finished local games, and what is happening to it (`M21.3`). */
data class PastLocalGamesUiState(
    /** Finished local games, newest first, as the store returns them. */
    val games: List<LocalGameSummary> = emptyList(),
    /** Whether the list is still being read from the device. */
    val loading: Boolean = true,
)

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

    /** `"White won by checkmate"`, `"Drawn by stalemate"`, in the words every board uses (`M21.16`). */
    fun resultLabel(result: GameResult): String = GameEndings.forSides(result)

    private const val SEPARATOR = " • "
}
