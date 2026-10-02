package com.jmussel.chessgame.computer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jmussel.chessgame.ai.ChessEngine
import com.jmussel.chessgame.ai.Difficulty
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.TerminationReason
import com.jmussel.chessgame.local.ComputerOpponent
import com.jmussel.chessgame.local.LocalGameChange
import com.jmussel.chessgame.local.LocalGameSession
import com.jmussel.chessgame.local.StoredLocalGame
import com.jmussel.chessgame.ui.board.BoardUiState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

/** A level's name: `"Very Easy"`, `"Easy"`, `"Medium"` or `"Hard"`. */
fun difficultyName(difficulty: Int): String =
    Difficulty
        .ofLevel(difficulty)
        .name
        .split('_')
        .joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() } }

/** The levels a new game offers, weakest first, with their names (`D089`). */
val LEVEL_CHOICES: List<Pair<Difficulty, String>> = Difficulty.entries.map { it to difficultyName(it.level) }

/** How the computer is named on screen and in past local games: `"Computer (Medium)"`. */
fun computerLabel(difficulty: Int): String = "Computer (${difficultyName(difficulty)})"

/** `"You played White"`. */
fun humanSideLabel(side: Side): String = if (side == Side.WHITE) "You played White" else "You played Black"

/** A game against the computer as the screen shows it (`D086`). */
data class ComputerGameUiState(
    /** The stored game this is. */
    val id: Long,
    val opponent: ComputerOpponent,
    /** The board, always from the human's side and never face to face (`D087`). */
    val boardState: BoardUiState,
    /** Whether the computer is choosing its move. The human can do nothing but resign or take back. */
    val thinking: Boolean = false,
) {
    val game: ChessGame
        get() = boardState.game

    val humanSide: Side
        get() = opponent.humanSide

    val difficulty: Difficulty
        get() = Difficulty.ofLevel(opponent.difficulty)

    /** Whether it is the human's turn in an unfinished game: the only time the board takes taps. */
    val humansTurn: Boolean
        get() = !game.isOver && game.sideToMove == humanSide

    /**
     * Whether Undo is offered: in an unfinished game with a move of the human's to take back
     * (`D086`). A game-ending move is final, whoever made it.
     */
    val canTakeBack: Boolean
        get() = !game.isOver && game.history.any { it.positionBefore.sideToMove == humanSide }
}

/**
 * What is asked before a new game against the computer starts: from "Play the computer"
 * (`M21.7`), or from New game on the game screen (`M21.10`), where the game stays in
 * [ComputerGame.state] behind the question until a level is chosen. An unfinished
 * pass-and-play game is never asked about: it is kept beside this one (`D090`).
 */
sealed interface ComputerSetup {
    /** New game on an unfinished game against the computer, which it would delete (`D084`, `D089`). */
    data object ConfirmNewGame : ComputerSetup

    /** Which of the four levels to play (`D089`). */
    data object ChooseDifficulty : ComputerSetup
}

/**
 * The logic of a game against the computer (`D086`, `M21.6`): whose turn it is, running the
 * engine, discarding stale results, takeback, and saving. The screen (`M21.7`) shows
 * [state] and calls the rest.
 *
 * - **One search per computer turn.** A search starts whenever the computer is to move, and
 *   starting one cancels any other. Leaving the game cancels it too.
 * - **Stale results are discarded.** Cancelling is not enough, since a search can finish as it
 *   is cancelled. Each search carries the turn it was started for and the game it was asked
 *   about, and its move is applied only if both are still current: never after a takeback,
 *   a new game, leaving, a newer search or the end of the game.
 * - **The human waits.** On the computer's turn the board ignores the human, who may still
 *   resign or take back.
 * - **Takeback** removes the computer's reply and the human move before it, or, while the
 *   computer is still thinking, the human move alone.
 * - **Everything is saved** through [session], in order, as it happens.
 *
 * Everything here runs on [scope]'s thread, the main thread in the app; only the engine runs
 * on [engineDispatcher].
 */
class ComputerGame(
    private val session: LocalGameSession,
    private val engine: ChessEngine,
    private val scope: CoroutineScope,
    private val engineDispatcher: CoroutineDispatcher,
    /** The human's colour in a new game from the entry: random (`D086`). Tests choose. */
    private val chooseSide: () -> Side = { if (Random.nextBoolean()) Side.WHITE else Side.BLACK },
) {
    /** The game on screen, or `null` before one has been read or started. */
    var state: ComputerGameUiState? by mutableStateOf(null)
        private set

    /** What is being asked before a new game starts, or `null` when nothing is. */
    var setup: ComputerSetup? by mutableStateOf(null)
        private set

    /** Whether the game is being read or started, so there is nothing to show yet. */
    var loading: Boolean by mutableStateOf(false)
        private set

    /** Changes whenever a search in progress stops being wanted. */
    private var turn = 0L

    private var search: Job? = null
    private var load: Job? = null

    /**
     * Shows the unfinished game against the computer, starting the computer's search if it
     * is to move. [onNone] is called instead when there is no such game.
     */
    fun resume(onNone: () -> Unit = {}) =
        show {
            session.resumeComputer() ?: run {
                onNone()
                null
            }
        }

    /**
     * What "Play the computer" does (`M21.7`): resumes the unfinished game against the
     * computer, or asks for a level for a new one. An unfinished pass-and-play game plays no
     * part: it stays as it is (`D090`).
     */
    fun open() {
        stopSearching()
        load?.cancel()
        state = null
        setup = null
        loading = true
        load =
            scope.launch {
                val unfinished = session.resumeComputer()
                loading = false
                if (unfinished == null) setup = ComputerSetup.ChooseDifficulty else display(unfinished)
            }
    }

    /** The player agreed New game should delete the unfinished game; now the level. */
    fun confirmNewGame() {
        if (setup == ComputerSetup.ConfirmNewGame) setup = ComputerSetup.ChooseDifficulty
    }

    /**
     * New game on the game screen (`D089`): the level choice, asking first when the game is
     * unfinished, because a new game deletes it (`D084`). The game carries on behind the
     * question, and nothing is deleted until a level is chosen.
     */
    fun newGame() {
        val current = state ?: return
        if (setup != null || loading) return
        setup = if (current.game.isOver) ComputerSetup.ChooseDifficulty else ComputerSetup.ConfirmNewGame
    }

    /**
     * Back out of New game's question or level choice to the game, as it is: a search in
     * progress has carried on. Returns `false` when there is no game behind the question,
     * as from "Play the computer", so Back belongs to the screen.
     */
    fun keepGame(): Boolean {
        if (state == null || setup == null) return false
        setup = null
        return true
    }

    /**
     * Starts a new game at [difficulty], the human's colour chosen at random (`D086`), in place
     * of any unfinished game, whose search stops and whose result can no longer land.
     */
    fun choose(difficulty: Difficulty) {
        if (setup != ComputerSetup.ChooseDifficulty) return
        setup = null
        start(ComputerOpponent(chooseSide(), difficulty.level))
    }

    /**
     * After a finished game: a new, independent game at the same level with the colours
     * swapped (`D086`). The finished game stays in past local games; there is no series.
     */
    fun playAgain() {
        val finished = state ?: return
        if (!finished.game.isOver) return
        start(finished.opponent.copy(humanSide = finished.humanSide.opposite))
    }

    /**
     * Starts a new game against [opponent] in place of any unfinished game against the
     * computer, which is deleted (`D084`). A pass-and-play game is untouched (`D090`).
     */
    fun start(opponent: ComputerOpponent) = show { session.startNew(opponent) }

    /** Stops thinking, as the player leaves the game. Reopening it starts again. */
    fun leave() {
        stopSearching()
        load?.cancel()
    }

    /**
     * The human's taps, worked out by the board: a selection, a move, a promotion, a draw
     * claim or a resignation.
     *
     * Ignored on the computer's turn, except a resignation. A takeback is [takeBack]'s, not
     * the board's: it follows a different rule against the computer.
     */
    fun update(next: BoardUiState) {
        val current = state ?: return
        val before = current.game
        val after = next.game
        val change = LocalGameChange.between(before, after)

        when (change) {
            null -> if (current.humansTurn) state = current.copy(boardState = next)
            LocalGameChange.Move -> {
                if (!current.humansTurn) return
                accept(current, next)
                thinkIfItsTheComputersTurn()
            }
            LocalGameChange.Result -> {
                val result = after.result ?: return
                val humanResigned = result.reason == TerminationReason.RESIGNATION && result.winner != current.humanSide
                if (!current.humansTurn && !humanResigned) return
                stopSearching()
                accept(current, next)
            }
            is LocalGameChange.TakeBack -> return
        }
    }

    /**
     * Takes back the computer's reply and the human move before it, or only the human move
     * while the computer is still thinking (`D086`). Unavailable once the game has ended.
     */
    fun takeBack() {
        val current = state ?: return
        if (!current.canTakeBack) return
        val game = current.game
        val plies = if (game.lastMover == current.humanSide) 1 else 2

        stopSearching()
        val after = (1..plies).fold(game) { taken, _ -> ChessRules.undoLastMove(taken) }
        accept(current, BoardUiState(after, orientation = current.humanSide))
    }

    private fun show(read: suspend () -> StoredLocalGame?) {
        stopSearching()
        load?.cancel()
        setup = null
        loading = true
        load =
            scope.launch {
                val stored = read()
                loading = false
                if (stored == null) state = null else display(stored)
            }
    }

    /** Shows [stored], and lets the computer think if it is to move. */
    private fun display(stored: StoredLocalGame) {
        val opponent = checkNotNull(stored.computer) { "Game ${stored.id} is not against the computer" }
        state = ComputerGameUiState(stored.id, opponent, BoardUiState(stored.game, orientation = opponent.humanSide))
        thinkIfItsTheComputersTurn()
    }

    /** Shows [next] and saves what it changed. */
    private fun accept(
        current: ComputerGameUiState,
        next: BoardUiState,
    ) {
        state = current.copy(boardState = next, thinking = false)
        session.save(current.id, current.game, next.game) { scope.launch { resume() } }
    }

    private fun thinkIfItsTheComputersTurn() {
        val current = state ?: return
        if (current.game.isOver || current.humansTurn) return

        stopSearching()
        val searchTurn = turn
        val position = current.game
        state = current.copy(thinking = true)
        search =
            scope.launch {
                val move =
                    withContext(engineDispatcher) {
                        val context = coroutineContext
                        engine.chooseMove(position.state, current.difficulty) { !context.isActive }
                    } ?: return@launch

                // Only a result for this very turn of this very game is played.
                val now = state ?: return@launch
                if (searchTurn != turn || now.id != current.id || now.game != position) return@launch
                accept(now, BoardUiState(ChessRules.applyMove(position, move), orientation = now.humanSide))
            }
    }

    private fun stopSearching() {
        turn++
        search?.cancel()
        search = null
        state?.let { if (it.thinking) state = it.copy(thinking = false) }
    }
}
