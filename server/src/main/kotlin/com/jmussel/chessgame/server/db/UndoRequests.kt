@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Square
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The undo request a game carries beside its rules state (`D091`).
 *
 * Who asked and who agreed are server concerns, so none of this is in `chess-core` or the
 * stored [GameStateDocument]: it lives in the game row's own columns (`V12`). Every accepted
 * command writes a new one, and the default — nothing pending, no answer — is what every
 * command other than the undo-request ones leaves, which is how "any other change clears
 * it" is kept.
 */
data class UndoRecord(
    /** The player whose undo request is pending, or `null`. */
    val pendingFrom: Uuid? = null,
    /** The last answer or crossing, shown until the next accepted change. */
    val answer: UndoAnswer? = null,
    /** The version the pending request produced; an answer is decided at or after it (`D097`). */
    val pendingSince: Long? = null,
) {
    init {
        require(pendingFrom == null || answer == null) { "Asking clears the last answer" }
        require((pendingFrom == null) == (pendingSince == null)) { "A pending request has the version it was made at" }
    }
}

/** How the last undo request ended, and the two moves it was about. */
data class UndoAnswer(
    val kind: UndoAnswerKind,
    val requester: Uuid,
    /** The requester's move the request was about. */
    val requesterMove: Move,
    /** The opponent's reply to it. */
    val replyMove: Move,
)

enum class UndoAnswerKind {
    /** The opponent accepted, with Accept or with their own Undo, so both moves were undone. */
    ACCEPTED,

    /** The opponent declined; both moves stand. */
    DECLINED,

    /** The opponent's undo arrived just after the request, and accepted it. */
    CROSSED_REQUEST_FIRST,

    /** The request arrived just after the opponent's undo, and undid the requester's move. */
    CROSSED_UNDO_FIRST,
}

/**
 * The draw offer a game carries beside its rules state (`D097`), in the game row's own columns
 * (`V15`) like the undo request.
 */
data class DrawRecord(
    /** The player whose draw offer is pending, or `null`. */
    val offeredBy: Uuid? = null,
    /** An offer was declined and no ordinary move has been accepted since, so none may be made. */
    val blocked: Boolean = false,
    /** The version the pending offer produced; an answer is decided at or after it. */
    val offeredAt: Long? = null,
) {
    init {
        require((offeredBy == null) == (offeredAt == null)) { "A pending offer has the version it was made at" }
    }
}

/** What kind of accepted command last changed a game. */
enum class ChangeKind {
    MOVE,
    UNDO,
    UNDO_REQUEST,
    UNDO_ACCEPT,
    UNDO_DECLINE,
    DRAW_CLAIM,
    RESIGNATION,

    /** A Redo restored an Undo operation (`D092`). */
    REDO,

    /** A draw was offered, accepted or declined (`D097`). */
    DRAW_OFFER,
    DRAW_ACCEPT,
    DRAW_DECLINE,
}

/**
 * The last accepted change to a game: what it was, who sent it, and the version it was
 * applied to.
 *
 * Read only to recognise a request and an undo that crossed (`D091`): the second command's
 * `expectedVersion` must be [appliedTo], and this change the only one since. [undoneMove] is
 * the move an [ChangeKind.UNDO] took back, which the crossing's note names.
 */
data class LastChange(
    val kind: ChangeKind,
    val actor: Uuid,
    val appliedTo: Long,
    val undoneMove: Move? = null,
) {
    init {
        require(undoneMove == null || kind == ChangeKind.UNDO) { "Only an undo takes a move back" }
    }
}

/** [move] as it is stored: its long algebraic form, as `toString` writes it (`g1f3`, `e7e8q`). */
internal fun storedMove(move: Move): String = move.toString()

/** A move stored by [storedMove]. */
internal fun parseStoredMove(text: String): Move =
    Move(
        from = Square.parse(text.substring(0, 2)),
        to = Square.parse(text.substring(2, 4)),
        promotion = text.getOrNull(4)?.let(PieceType::fromLetter),
    )
