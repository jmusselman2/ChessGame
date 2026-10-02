package com.jmussel.chessgame.ui.board

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.jmussel.chessgame.core.chess.GameResult
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.TerminationReason

/**
 * How a finished game is announced, in one wording for every board (`M21.16`): who won, or
 * that it was drawn, and what ended it, as `"White won by checkmate"` or `"Drawn by the
 * fifty-move rule"`.
 *
 * Only the words are decided here. The result is always the authoritative one: `chess-core`'s
 * for a local game, the server's for an online one (`D004`).
 */
object GameEndings {
    /** What ended a game, in a player's words: `"checkmate"`, `"the fifty-move rule"`. */
    fun reasonWords(reason: TerminationReason): String =
        when (reason) {
            TerminationReason.CHECKMATE -> "checkmate"
            TerminationReason.RESIGNATION -> "resignation"
            TerminationReason.STALEMATE -> "stalemate"
            TerminationReason.INSUFFICIENT_MATERIAL -> "insufficient material"
            TerminationReason.THREEFOLD_REPETITION_CLAIM -> "threefold repetition"
            TerminationReason.FIFTY_MOVE_RULE_CLAIM -> "the fifty-move rule"
            TerminationReason.FIVEFOLD_REPETITION -> "fivefold repetition"
            TerminationReason.SEVENTY_FIVE_MOVE_RULE -> "the seventy-five-move rule"
        }

    /**
     * The same, for a reason the server named (`"FIFTY_MOVE_RULE_CLAIM"`).
     *
     * A name this app does not know, from a newer server, still reads plainly rather than
     * failing.
     */
    fun reasonWords(serverName: String): String =
        TerminationReason.entries.firstOrNull { it.name.equals(serverName, ignoreCase = true) }?.let(::reasonWords)
            ?: serverName.lowercase().replace('_', ' ')

    /** `"White won by checkmate"`, `"Drawn by stalemate"`: a game with two players at one board. */
    fun forSides(result: GameResult): String =
        sentence(
            verdict =
                when (result.winner) {
                    Side.WHITE -> "White won"
                    Side.BLACK -> "Black won"
                    null -> DRAWN
                },
            reason = reasonWords(result.reason),
        )

    /** `"You won by checkmate"`, `"The computer won by resignation"`, `"Drawn by stalemate"` (`D086`). */
    fun forComputer(
        result: GameResult,
        humanSide: Side,
    ): String =
        sentence(
            verdict =
                when (result.winner) {
                    humanSide -> YOU_WON
                    null -> DRAWN
                    else -> "The computer won"
                },
            reason = reasonWords(result.reason),
        )

    /** A verdict and, when there is one, what decided it. */
    fun sentence(
        verdict: String,
        reason: String?,
    ): String = if (reason == null) verdict else "$verdict by $reason"

    const val DRAWN = "Drawn"
    const val YOU_WON = "You won"
}

/**
 * A finished game's ending, said prominently above the controls (`M21.16`), and marked as a
 * heading so a screen reader finds it first.
 */
@Composable
fun GameEndHeadline(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineSmall,
    )
}
