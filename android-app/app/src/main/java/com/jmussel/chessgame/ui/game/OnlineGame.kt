package com.jmussel.chessgame.ui.game

import com.jmussel.chessgame.api.ChessApiException
import com.jmussel.chessgame.api.ChessCommandRefusedException
import com.jmussel.chessgame.api.GameViewDto
import com.jmussel.chessgame.core.chess.Board
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Piece
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.ui.board.PendingPromotion

/**
 * A game the server owns, as the screen showing it has got so far.
 *
 * There is no board here until the server has sent one: an online game is never invented
 * locally, only drawn from what came back (`D004`).
 */
sealed interface OnlineGameState {
    /** The game is being fetched; [gameId] is all that is known about it. */
    data class Loading(
        val gameId: String,
    ) : OnlineGameState

    /**
     * The canonical state, as the server last described it, and what the player is doing to
     * it.
     *
     * [selected], [pendingPromotion], and [submitting] are the screen's own — they are not
     * part of the game and never reach the server as state. [game] only ever changes to
     * something the server sent.
     */
    data class Ready(
        val game: GameViewDto,
        val selected: Square? = null,
        val pendingPromotion: PendingPromotion? = null,
        val submitting: Boolean = false,
        /** Whether the player has been asked to confirm giving the game up (`D018`). */
        val confirmingResignation: Boolean = false,
        /** What follows this game, once it has finished and the series has been asked. */
        val after: AfterGame? = null,
        val message: String? = null,
    ) : OnlineGameState

    /**
     * The game could not be shown.
     *
     * [canRetry] is false when trying again cannot help — a game that is not yours stays
     * not yours, and a game that does not exist will not appear.
     */
    data class Failed(
        val gameId: String,
        val message: String,
        val canRetry: Boolean,
    ) : OnlineGameState
}

/**
 * What follows a game that has finished.
 *
 * The app never creates or confirms a rematch: the server makes the next game when it
 * finalizes this one (`D014`), and this is only what the dashboard said about the series
 * afterwards.
 */
sealed interface AfterGame {
    /** The server has not been asked yet, or the answer has not come back. */
    data object Looking : AfterGame

    /** The series is at a new game now, which is the one the server chose. */
    data class NextGame(
        val gameId: String,
    ) : AfterGame

    /** There will be no next game: the series has closed (`D013`). */
    data object SeriesOver : AfterGame
}

/**
 * What a tap on the board came to.
 *
 * Choosing a square or a promotion piece changes only what is on screen. A move is a
 * request for the server to answer, and until it does, nothing on the board moves (`D004`).
 */
sealed interface BoardTap {
    /** What the screen shows now, whether or not anything is being sent. */
    val state: OnlineGameState.Ready

    /** Nothing to send: the selection, or the promotion prompt, changed. */
    data class Showing(
        override val state: OnlineGameState.Ready,
    ) : BoardTap

    /** A move to ask the server for, decided against the version now on screen. */
    data class Submit(
        override val state: OnlineGameState.Ready,
        val move: Move,
    ) : BoardTap
}

/**
 * Turning what the server said about a game into what the screen draws, and turning taps
 * into moves to ask for.
 *
 * Every answer here comes from the server's own words — the position, whose move it is,
 * whether the mover is in check, and how it ended. Nothing is recomputed from the position,
 * because the app is not the authority on any of it (`D004`). `game-core` is used for two
 * things only: holding the pieces that were sent, so the same board renderer draws an
 * online game and a local one, and working out where a selected piece could go, which is a
 * preview and never a decision — the move still has to be accepted by the server.
 *
 * Pure, so the mapping, the wording, and what a tap means are tested without a screen.
 */
object OnlineGame {
    /**
     * The position the server sent, as a `game-core` board.
     *
     * The rows are rank 8 first, FEN-style letters, `.` for an empty square — the shape
     * `Board.toString()` produces, read back.
     */
    fun boardFrom(rows: List<String>): Board {
        val placement = mutableMapOf<Square, Piece>()

        rows.take(Square.RANKS).forEachIndexed { index, row ->
            val rank = Square.RANKS - 1 - index

            row.take(Square.FILES).forEachIndexed { file, symbol ->
                if (symbol != EMPTY_SQUARE) placement[Square.of(file, rank)] = Piece.fromSymbol(symbol)
            }
        }

        return Board.of(placement)
    }

    /** The side the viewer is playing, which decides which way the board faces. */
    fun sideOf(game: GameViewDto): Side = sideNamed(game.yourSide)

    /** The two squares of the move just played, for the highlight, or empty before the first. */
    fun lastMoveSquares(game: GameViewDto): Set<Square> {
        val move = game.lastMove ?: return emptySet()

        return setOfNotNull(Square.parseOrNull(move.from), Square.parseOrNull(move.to))
    }

    /**
     * The game replayed from the moves the server listed, for previewing where a piece may
     * go.
     *
     * `null` when the moves cannot be replayed — an app older than the server that sent
     * them, above all — which costs the preview and nothing else: the server decides
     * whether a move is legal either way.
     */
    fun replayOf(game: GameViewDto): ChessGame? =
        game.moves.fold(ChessGame.newGame() as ChessGame?) { replayed, text ->
            val position = replayed ?: return@fold null
            val move = moveOf(text) ?: return@fold null

            if (ChessRules.isLegal(position, move)) ChessRules.applyMove(position, move) else null
        }

    /** Whether the player may start a move at all: their turn, not over, nothing in flight. */
    fun canPlay(state: OnlineGameState.Ready): Boolean = state.game.yourTurn && !state.game.isOver && !state.submitting

    /**
     * Where the selected piece may legally move.
     *
     * A preview, worked out from the moves the server listed. An empty set is the honest
     * answer when nothing is selected, when it is not the player's move, or when the game
     * could not be replayed.
     */
    fun legalDestinations(state: OnlineGameState.Ready): Set<Square> {
        val from = state.selected ?: return emptySet()

        return movesFrom(state, from).mapTo(mutableSetOf()) { it.to }
    }

    /**
     * What tapping [square] comes to.
     *
     * Tapping one of the player's own pieces selects it; tapping one of its legal
     * destinations asks the server for that move. A pawn reaching the last rank raises the
     * promotion prompt instead, because the player has to choose the piece. Nothing at all
     * happens while a command is in flight, or in a game that is not the player's to move
     * in — the board is the server's (`D004`).
     */
    fun onSquareTapped(
        state: OnlineGameState.Ready,
        square: Square,
    ): BoardTap {
        val cleared = state.copy(selected = null, pendingPromotion = null)

        if (!canPlay(state)) return BoardTap.Showing(cleared)
        if (state.pendingPromotion != null) return BoardTap.Showing(cleared)
        if (square == state.selected) return BoardTap.Showing(cleared)

        val from = state.selected

        if (from != null && square in legalDestinations(state)) {
            val promotes = movesFrom(state, from).any { it.to == square && it.promotion != null }

            return if (promotes) {
                BoardTap.Showing(state.copy(pendingPromotion = PendingPromotion(from, square)))
            } else {
                BoardTap.Submit(cleared.copy(submitting = true, message = null), Move(from, square))
            }
        }

        val piece = boardFrom(state.game.board).pieceAt(square)
        val isOwnPiece = piece != null && piece.side == sideOf(state.game)

        return BoardTap.Showing(cleared.copy(selected = if (isOwnPiece) square else null))
    }

    /** The move to ask for once the player has picked [choice] for the pending promotion. */
    fun choosePromotion(
        state: OnlineGameState.Ready,
        choice: PieceType,
    ): BoardTap {
        val pending = state.pendingPromotion ?: return BoardTap.Showing(state)

        return BoardTap.Submit(
            state.copy(selected = null, pendingPromotion = null, submitting = true, message = null),
            Move(pending.from, pending.to, choice),
        )
    }

    /** The state after the player backs out of the promotion prompt. */
    fun cancelPromotion(state: OnlineGameState.Ready): OnlineGameState.Ready = state.copy(pendingPromotion = null)

    /**
     * What a claim button says.
     *
     * The names are the server's, and the two claims are labelled apart because they are
     * different rules a player may be entitled to at the same time (`D019`).
     */
    fun claimLabel(claim: String): String =
        when (claim.uppercase()) {
            THREEFOLD -> "Claim draw (threefold repetition)"
            FIFTY_MOVE -> "Claim draw (fifty-move rule)"
            else -> "Claim draw (${claim.lowercase().replace('_', ' ')})"
        }

    /** `"Alex • You are White"`. */
    fun headingFor(game: GameViewDto): String = "${game.opponent.username} $SEPARATOR You are ${sideLabel(game.yourSide)}"

    /**
     * Where the game stands: whose move it is, or how it finished.
     *
     * A finished game says what the server said finished it; nothing here decides an
     * outcome.
     */
    fun statusFor(game: GameViewDto): String {
        val result = game.result ?: return turnFor(game)
        val reason = game.terminationReason?.let(::reasonLabel)
        val verdict =
            when {
                result == DRAW -> "Drawn"
                result == "${game.yourSide}_WINS" -> "You won"
                else -> "${game.opponent.username} won"
            }

        return listOfNotNull(verdict, reason).joinToString(separator = " by ")
    }

    /** `"Move 18 • version 34"`, which is what a client has to send back with a command. */
    fun positionFor(game: GameViewDto): String = "Move ${game.moveNumber} $SEPARATOR version ${game.version}"

    /** The moves played so far, numbered in pairs as a game is written down. */
    fun moveListLines(game: GameViewDto): List<String> =
        game.moves.chunked(2).mapIndexed { index, pair ->
            val white = pair.first()
            val black = pair.getOrNull(1)

            "${index + 1}. $white${black?.let { " $it" }.orEmpty()}"
        }

    /** What to show when the server refused to show the game. */
    fun messageFor(refusal: ChessApiException): String =
        when (refusal.status) {
            FORBIDDEN -> NOT_YOURS
            NOT_FOUND -> NO_SUCH_GAME
            else -> refusal.explanation.ifBlank { REFUSED }
        }

    /**
     * What to show when the server refused a command.
     *
     * A stale command is the one worth explaining rather than repeating: the game moved on,
     * and the state that came back with the refusal is now what is on screen.
     */
    fun messageFor(refusal: ChessCommandRefusedException): String =
        when (refusal.reason) {
            STALE_VERSION -> MOVED_ON
            NOT_YOUR_TURN -> NOT_YOUR_MOVE
            GAME_OVER -> ALREADY_OVER
            ILLEGAL_MOVE -> ILLEGAL
            NO_SUCH_CLAIM -> NO_CLAIM
            NOTHING_TO_UNDO -> NOTHING_BACK
            else -> refusal.rejection.message.ifBlank { REFUSED_COMMAND }
        }

    /** Whether trying again could help; a game that is not yours will never be. */
    fun canRetry(refusal: ChessApiException): Boolean = refusal.status !in setOf(FORBIDDEN, NOT_FOUND)

    /** What to show when the request never reached the server. */
    fun unreachableMessage(): String = UNREACHABLE

    /** `"Your move"`, or `"Alex to move"`, and the check that goes with it. */
    private fun turnFor(game: GameViewDto): String {
        val turn = if (game.yourTurn) YOUR_MOVE else "${game.opponent.username} to move"

        return if (game.inCheck) "$turn $SEPARATOR $CHECK" else turn
    }

    /** The legal moves from [from] in the replayed game, or none when it cannot be replayed. */
    private fun movesFrom(
        state: OnlineGameState.Ready,
        from: Square,
    ): List<Move> {
        if (!canPlay(state)) return emptyList()
        val replayed = replayOf(state.game) ?: return emptyList()

        return ChessRules.legalMoves(replayed).filter { it.from == from }
    }

    /** `"e2e4"`, or `"e7e8q"`, as `game-core` writes a move. */
    private fun moveOf(text: String): Move? {
        val from = Square.parseOrNull(text.take(2)) ?: return null
        val to = Square.parseOrNull(text.drop(2).take(2)) ?: return null
        val promotion =
            text.drop(4).firstOrNull()?.let { letter ->
                PieceType.PROMOTION_CHOICES.firstOrNull { it.letter.equalsIgnoringCase(letter) }
            }

        return runCatching { Move(from, to, promotion) }.getOrNull()
    }

    private fun Char.equalsIgnoringCase(other: Char): Boolean = lowercaseChar() == other.lowercaseChar()

    private fun sideNamed(name: String): Side = if (name.equals(Side.BLACK.name, ignoreCase = true)) Side.BLACK else Side.WHITE

    private fun sideLabel(name: String): String = if (sideNamed(name) == Side.BLACK) "Black" else "White"

    /** `THREEFOLD_REPETITION_CLAIM` reads as `threefold repetition claim`. */
    private fun reasonLabel(reason: String): String = reason.lowercase().replace('_', ' ')

    private const val EMPTY_SQUARE = '.'
    private const val SEPARATOR = "•"
    private const val DRAW = "DRAW"
    private const val YOUR_MOVE = "Your move"
    private const val CHECK = "Check"
    private const val FORBIDDEN = 403
    private const val NOT_FOUND = 404
    private const val STALE_VERSION = "STALE_VERSION"
    private const val NOT_YOUR_TURN = "NOT_YOUR_TURN"
    private const val GAME_OVER = "GAME_OVER"
    private const val ILLEGAL_MOVE = "ILLEGAL_MOVE"
    private const val NO_SUCH_CLAIM = "NO_SUCH_CLAIM"
    private const val NOTHING_TO_UNDO = "NOTHING_TO_UNDO"
    private const val THREEFOLD = "THREEFOLD_REPETITION"
    private const val FIFTY_MOVE = "FIFTY_MOVE_RULE"
    private const val NOT_YOURS = "This game is not yours to look at."
    private const val NO_SUCH_GAME = "That game is gone."
    private const val REFUSED = "The server would not show that game. Try again."
    private const val REFUSED_COMMAND = "The server would not play that. Try again."
    private const val MOVED_ON = "The game moved on. This is where it is now."
    private const val NOT_YOUR_MOVE = "It is not your move."
    private const val ALREADY_OVER = "This game has finished."
    private const val ILLEGAL = "That move is not legal here."
    private const val NO_CLAIM = "There is no draw to claim here."
    private const val NOTHING_BACK = "There is nothing to take back."
    private const val UNREACHABLE = "Could not reach the server. Check your connection and try again."
}
