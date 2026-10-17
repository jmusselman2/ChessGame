@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.api

import com.jmussel.chessgame.server.db.StoredUser
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi

/**
 * A user as other users see them: the internal id everything references, and the name they
 * chose.
 *
 * Deliberately does not carry the auth subject, `lastSeenAt`, or anything else about the
 * account — the API tells one user only what they need to play with another.
 */
@Serializable
data class UserSummary(
    val userId: String,
    val username: String,
)

/**
 * A friend as the dashboard's FRIENDS section lists them (`D096`): who they are and the
 * caller's lifetime chess record against them. [record] is required and always sent, a real
 * `0-0-0` included, so a missing one is an incompatible response rather than "no games".
 */
@Serializable
data class FriendSummary(
    val userId: String,
    val username: String,
    val record: RecordView,
)

/** A lifetime `W-L-D` chess record from the caller's side (`D096`). Every count is always sent. */
@Serializable
data class RecordView(
    val wins: Int,
    val losses: Int,
    val draws: Int,
) {
    companion object {
        fun of(record: com.jmussel.chessgame.server.db.ChessRecord): RecordView =
            RecordView(wins = record.wins, losses = record.losses, draws = record.draws)
    }
}

/**
 * Another user's ChessGame profile, as one viewer may see it (`D099`): their name, the UTC month
 * and year they joined, the viewer's lifetime chess record against them (`D096`) and only the
 * groups the two share. A per-viewer projection, never cached across viewers.
 */
@Serializable
data class ProfileView(
    val userId: String,
    val username: String,
    val joinedYear: Int,
    /** 1 for January. */
    val joinedMonth: Int,
    val record: RecordView,
    val sharedGroups: List<SharedGroupView>,
)

/** A group the viewer and a profile's subject are both in now. */
@Serializable
data class SharedGroupView(
    val groupId: String,
    val name: String,
)

/** [StoredUser] as the API shows it, or `null` when they have not claimed a username yet. */
fun StoredUser.toSummaryOrNull(): UserSummary? = username?.let { UserSummary(userId = id.toString(), username = it) }

/**
 * The caller, as told to the app signed in as them.
 *
 * The only place [username] may be `null`: a new anonymous account has an id from its first
 * authenticated request but has not chosen a name yet, and telling the two apart is what
 * sends a returning player past onboarding and a new one into it. Like [UserSummary] it
 * carries nothing about the account itself — not the auth subject, not `lastSeenAt`.
 */
@Serializable
data class CurrentUser(
    val userId: String,
    val username: String? = null,
)

/** [StoredUser] as the API shows it to that user themselves. */
fun StoredUser.toCurrentUser(): CurrentUser = CurrentUser(userId = id.toString(), username = username)

/**
 * A group as one of its members sees it (`D049`).
 *
 * Carries no members: who is in a group is its own request, because a group can hold more
 * people than a dashboard line has room for, and the list is the part that changes.
 * [createdBy] is who made it, not who owns it — a group has no owner and no admin.
 */
@Serializable
data class GroupSummary(
    val groupId: String,
    val name: String,
    val createdBy: String,
    val memberCount: Int,
) {
    companion object {
        fun of(
            group: com.jmussel.chessgame.server.db.StoredGroup,
            memberCount: Int,
        ): GroupSummary =
            GroupSummary(
                groupId = group.id.toString(),
                name = group.name,
                createdBy = group.createdBy.toString(),
                memberCount = memberCount,
            )
    }
}

/** A series as one of its two players sees it. */
@Serializable
data class SeriesSummary(
    val seriesId: String,
    val opponent: UserSummary,
    val status: String,
    val currentGameId: String? = null,
) {
    companion object {
        fun of(
            series: com.jmussel.chessgame.server.db.StoredSeries,
            opponent: StoredUser,
            viewer: kotlin.uuid.Uuid,
        ): SeriesSummary {
            require(series.opponentOf(viewer) == opponent.id) { "That opponent is not in this series" }

            return SeriesSummary(
                seriesId = series.id.toString(),
                opponent =
                    requireNotNull(opponent.toSummaryOrNull()) { "An opponent always has a username" },
                status = series.status,
                currentGameId = series.currentGameId?.toString(),
            )
        }
    }
}

/**
 * One dashboard line: an active series, the game it is at, and whose move it is.
 *
 * Only active series are listed: leaving a series ends its game too, so a series that has been
 * left has nothing to finish (`D095`).
 */
@Serializable
data class DashboardEntry(
    val seriesId: String,
    val opponent: UserSummary,
    val gameId: String? = null,
    val version: Long? = null,
    val yourSide: String? = null,
    val sideToMove: String? = null,
    val moveNumber: Int? = null,
    val yourTurn: Boolean = false,
    /**
     * The requests pending in this game other than a move, undo request first and then draw
     * offer (`D091`, `D097`). Always sent, empty or not: it has no default, so a client never
     * reads a missing list as "nothing pending". A request waiting on the viewer lists the game
     * as needing them; [yourTurn] still means only "it is your move".
     */
    val pendingRequests: List<PendingRequest>,
) {
    companion object {
        fun of(view: com.jmussel.chessgame.server.db.ActiveSeriesView): DashboardEntry? {
            val opponent = view.opponent.toSummaryOrNull() ?: return null

            return DashboardEntry(
                seriesId = view.seriesId.toString(),
                opponent = opponent,
                gameId = view.gameId?.toString(),
                version = view.gameVersion,
                yourSide = view.yourSide,
                sideToMove = view.sideToMove,
                moveNumber = view.fullmoveNumber,
                yourTurn = view.isYourTurn,
                pendingRequests =
                    listOfNotNull(
                        view.undoRequestedBy?.let { requester ->
                            PendingRequest(kind = PendingRequest.UNDO, waitingOnYou = requester != view.viewer)
                        },
                        view.drawOfferedBy?.let { offerer ->
                            PendingRequest(kind = PendingRequest.DRAW, waitingOnYou = offerer != view.viewer)
                        },
                    ),
            )
        }
    }
}

/** A request pending in a game, as a dashboard line names it (`D091`, `D097`). */
@Serializable
data class PendingRequest(
    /** What is asked: [UNDO] or [DRAW]. */
    val kind: String,
    /** `true` when the viewer must answer it, `false` when they sent it. */
    val waitingOnYou: Boolean,
) {
    companion object {
        const val UNDO: String = "UNDO"
        const val DRAW: String = "DRAW"
    }
}

/** A draw offer pending in a game, as one of its players sees it (`D097`). */
@Serializable
data class DrawOfferView(
    /** `true` for the player who offered, `false` for the one who must answer. */
    val fromYou: Boolean,
)

/**
 * An undo request pending in a game, as one of its players sees it (`D091`).
 *
 * [requesterMove] and [replyMove] are the two moves accepting would undo, the latest two.
 */
@Serializable
data class UndoRequestView(
    /** `true` for the player who asked, `false` for the one who must answer. */
    val fromYou: Boolean,
    val requesterMove: MoveView,
    val replyMove: MoveView,
)

/**
 * What a player is told about the last undo request, until the next accepted change
 * (`D091`). Only the player it concerns is told.
 */
@Serializable
data class UndoNoteView(
    val kind: UndoNoteKind,
    val requesterMove: MoveView,
    val replyMove: MoveView,
)

@Serializable
enum class UndoNoteKind {
    /** To the requester: the opponent accepted, so both moves were undone. */
    ACCEPTED,

    /** To the requester: the opponent declined. */
    DECLINED,

    /** To the requester: the opponent had undone the reply, so the requester's move went too. */
    UNDONE_TOO,

    /** To the opponent: the request and their undo crossed, so both moves were undone. */
    BOTH_UNDONE,
}

/** One finished game, as history lists it. */
@Serializable
data class FinishedGameEntry(
    val gameId: String,
    val sequenceNumber: Int,
    val yourSide: String,
    val result: String? = null,
    val terminationReason: String? = null,
    val moveNumber: Int,
    val endedAt: String? = null,
) {
    companion object {
        fun of(game: com.jmussel.chessgame.server.db.FinishedGameView): FinishedGameEntry =
            FinishedGameEntry(
                gameId = game.gameId.toString(),
                sequenceNumber = game.sequenceNumber,
                yourSide = game.yourSide,
                result = game.result,
                terminationReason = game.terminationReason,
                moveNumber = game.moveCount,
                endedAt = game.endedAt?.toString(),
            )
    }
}

/**
 * One series a player took part in, with the games in it that are over.
 *
 * A closed series stays here forever (`D012`), and a series that is still running appears
 * too once it has a finished game — what makes a game history is that it has finished.
 */
@Serializable
data class SeriesHistoryEntry(
    val seriesId: String,
    val opponent: UserSummary,
    val status: String,
    val closedAt: String? = null,
    val games: List<FinishedGameEntry> = emptyList(),
) {
    companion object {
        fun of(view: com.jmussel.chessgame.server.db.SeriesHistoryView): SeriesHistoryEntry? {
            val opponent = view.opponent.toSummaryOrNull() ?: return null

            return SeriesHistoryEntry(
                seriesId = view.seriesId.toString(),
                opponent = opponent,
                status = view.status,
                closedAt = view.closedAt?.toString(),
                games = view.games.map(FinishedGameEntry::of),
            )
        }
    }
}

/**
 * One played move (`D101`): its canonical coordinates, which are its identity, and its
 * required standard algebraic notation, which is what a player reads. [san] is derived on
 * the server from the move and the position it was recorded as played from; it is never
 * stored, and a client never falls back to the coordinates for display.
 */
@Serializable
data class MoveView(
    val from: String,
    val to: String,
    val promotion: String? = null,
    val san: String,
) {
    companion object {
        /** [record] as a player sees it, its notation worked out from its own recorded position. */
        fun of(record: com.jmussel.chessgame.core.chess.MoveRecord): MoveView =
            MoveView(
                from = record.move.from.toString(),
                to = record.move.to.toString(),
                promotion = record.move.promotion?.name,
                san =
                    com.jmussel.chessgame.core.chess.ChessRules
                        .san(record.positionBefore, record.move),
            )
    }
}

/**
 * A game as one of its two players sees it: the canonical state, from their side.
 *
 * Everything a client needs to draw the game is here, including who the opponent is and
 * which move was last played, so a screen opened from a notification or rebuilt after the
 * process was recreated shows the same thing as one opened from the dashboard.
 */
@Serializable
data class GameView(
    val gameId: String,
    val seriesId: String,
    val opponent: UserSummary,
    val version: Long,
    val yourSide: String,
    val sideToMove: String,
    val yourTurn: Boolean,
    val inCheck: Boolean,
    /** Eight rows, rank 8 first, FEN-style letters with `.` for an empty square. */
    val board: List<String>,
    /** The active move history, oldest first, each in coordinates and SAN (`D101`). */
    val moves: List<MoveView>,
    /** The move just played, or `null` before the first one. */
    val lastMove: MoveView? = null,
    val moveNumber: Int,
    val halfmoveClock: Int,
    val result: String? = null,
    val terminationReason: String? = null,
    /** The draws the viewer could claim right now, if it is their move (`D019`). */
    val availableDrawClaims: List<String> = emptyList(),
    /**
     * Whether the viewer may take their latest move back right now (`D016`). While an undo
     * request waits on them, their Undo accepts it (`D091`).
     */
    val canUndo: Boolean = false,
    /** Whether the viewer may send an undo request right now (`D091`). */
    val canRequestUndo: Boolean = false,
    /**
     * Whether the viewer may Redo right now (`D092`): restore the operation the latest Undo took
     * back. Only whether; the recorded path itself never leaves the server (`D069`).
     */
    val canRedo: Boolean = false,
    /** The undo request pending in this game, whichever player sent it, or `null`. */
    val undoRequest: UndoRequestView? = null,
    /** What the viewer is told about the last undo request, or `null`. */
    val undoNote: UndoNoteView? = null,
    /** Whether the viewer may offer a draw right now (`D097`). */
    val canOfferDraw: Boolean = false,
    /** The draw offer pending in this game, whichever player made it, or `null`. */
    val drawOffer: DrawOfferView? = null,
    /**
     * Whether this game's series is still active. `false` once a player has left it (`D052`),
     * which also ended this game (`D095`): no game follows it.
     */
    val seriesActive: Boolean = true,
    /**
     * Whether a move has ever been accepted in this game (`D095`), even if every move has since
     * been undone. Leaving the series resigns a started game and deletes one that has not.
     */
    val hasStarted: Boolean = false,
) {
    val isOver: Boolean
        get() = result != null

    companion object {
        fun of(
            stored: com.jmussel.chessgame.server.db.StoredGame,
            viewer: kotlin.uuid.Uuid,
            opponent: StoredUser,
            seriesActive: Boolean = true,
        ): GameView {
            require(viewer == stored.whiteUserId || viewer == stored.blackUserId) {
                "That player is not in this game"
            }
            require(opponent.id == stored.opponentOf(viewer)) { "That opponent is not in this game" }

            val yourSide =
                if (viewer == stored.whiteUserId) {
                    com.jmussel.chessgame.core.chess.Side.WHITE
                } else {
                    com.jmussel.chessgame.core.chess.Side.BLACK
                }
            val state = stored.game.state

            return GameView(
                gameId = stored.id.toString(),
                seriesId = stored.seriesId.toString(),
                opponent = requireNotNull(opponent.toSummaryOrNull()) { "An opponent always has a username" },
                version = stored.version,
                yourSide = yourSide.name,
                sideToMove = state.sideToMove.name,
                yourTurn = !stored.game.isOver && state.sideToMove == yourSide,
                inCheck =
                    !stored.game.isOver &&
                        com.jmussel.chessgame.core.chess.Attacks
                            .isSideToMoveInCheck(state),
                board = state.board.toString().lines(),
                moves = stored.game.history.map(MoveView::of),
                lastMove =
                    stored.game.history
                        .lastOrNull()
                        ?.let(MoveView::of),
                moveNumber = state.fullmoveNumber,
                halfmoveClock = state.halfmoveClock,
                result =
                    stored.game.result
                        ?.outcome
                        ?.name,
                terminationReason =
                    stored.game.result
                        ?.reason
                        ?.name,
                canUndo =
                    com.jmussel.chessgame.core.chess.ChessRules
                        .canUndo(stored.game, yourSide),
                seriesActive = seriesActive,
                hasStarted = stored.hasStarted,
                canRequestUndo =
                    com.jmussel.chessgame.server.game.GameCommandService
                        .mayRequestUndo(stored, yourSide),
                canRedo =
                    com.jmussel.chessgame.core.chess.ChessRules
                        .canRedo(stored.game, yourSide),
                undoRequest = undoRequestOf(stored, viewer),
                canOfferDraw =
                    com.jmussel.chessgame.server.game.GameCommandService
                        .mayOfferDraw(stored),
                drawOffer = stored.draw.offeredBy?.let { DrawOfferView(fromYou = it == viewer) },
                undoNote = undoNoteOf(stored, viewer),
                availableDrawClaims =
                    if (state.sideToMove == yourSide) {
                        com.jmussel.chessgame.core.chess.ChessRules
                            .availableDrawClaims(state)
                            .map { it.name }
                            .sorted()
                    } else {
                        emptyList()
                    },
            )
        }

        private fun undoRequestOf(
            stored: com.jmussel.chessgame.server.db.StoredGame,
            viewer: kotlin.uuid.Uuid,
        ): UndoRequestView? {
            val requester = stored.undo.pendingFrom ?: return null
            // A pending request is about the two latest active moves (`D101`).
            val records = stored.game.history

            return UndoRequestView(
                fromYou = requester == viewer,
                requesterMove = MoveView.of(records[records.size - 2]),
                replyMove = MoveView.of(records.last()),
            )
        }

        /**
         * The note [viewer] sees about the last answer. The requester hears every answer; the
         * opponent hears only of a crossing, which undid one move more than they asked.
         */
        private fun undoNoteOf(
            stored: com.jmussel.chessgame.server.db.StoredGame,
            viewer: kotlin.uuid.Uuid,
        ): UndoNoteView? {
            val answer = stored.undo.answer ?: return null
            val isRequester = answer.requester == viewer
            val kind =
                when (answer.kind) {
                    com.jmussel.chessgame.server.db.UndoAnswerKind.ACCEPTED ->
                        UndoNoteKind.ACCEPTED.takeIf { isRequester }
                    com.jmussel.chessgame.server.db.UndoAnswerKind.DECLINED ->
                        UndoNoteKind.DECLINED.takeIf { isRequester }
                    com.jmussel.chessgame.server.db.UndoAnswerKind.CROSSED_REQUEST_FIRST ->
                        if (isRequester) UndoNoteKind.ACCEPTED else UndoNoteKind.BOTH_UNDONE
                    com.jmussel.chessgame.server.db.UndoAnswerKind.CROSSED_UNDO_FIRST ->
                        if (isRequester) UndoNoteKind.UNDONE_TOO else UndoNoteKind.BOTH_UNDONE
                } ?: return null

            return UndoNoteView(
                kind = kind,
                requesterMove = MoveView.of(recordOf(stored, answer, answer.requesterMove)),
                replyMove = MoveView.of(recordOf(stored, answer, answer.replyMove)),
            )
        }

        /**
         * The recorded move an answer names, with the position it was played from (`D101`). A
         * declined request's moves are still the latest active records; an accepted or crossed
         * one's were undone, and are the first records of the next Redo operation. Missing
         * context is an invariant failure, never a reason to show coordinates.
         */
        private fun recordOf(
            stored: com.jmussel.chessgame.server.db.StoredGame,
            answer: com.jmussel.chessgame.server.db.UndoAnswer,
            move: com.jmussel.chessgame.core.chess.Move,
        ): com.jmussel.chessgame.core.chess.MoveRecord {
            val candidates =
                if (answer.kind == com.jmussel.chessgame.server.db.UndoAnswerKind.DECLINED) {
                    stored.game.history.takeLast(2)
                } else {
                    stored.game.redoPath
                        .lastOrNull()
                        ?.records
                        .orEmpty()
                }

            return requireNotNull(candidates.firstOrNull { it.move == move }) {
                "Game ${stored.id}: the undo note's $move has no recorded position"
            }
        }
    }
}

/** Why a command was refused, in a form a client can branch on. */
@Serializable
enum class RejectionReason {
    /** The game has already finished; nothing more can be played. */
    GAME_OVER,

    /** It is the other player's move. Wait for them. */
    NOT_YOUR_TURN,

    /**
     * The game moved on since the caller read it. The canonical state is attached: take it
     * and decide again (`D021`).
     */
    STALE_VERSION,

    /** The move is not legal in this position. */
    ILLEGAL_MOVE,

    /** No such draw may be claimed in this position (`D019`). */
    NO_SUCH_CLAIM,

    /** There is nothing for this player to take back right now (`D016`). */
    NOTHING_TO_UNDO,

    /**
     * This player may not send an undo request now: no move of theirs has been answered, or
     * they already have one pending (`D091`).
     */
    CANNOT_REQUEST_UNDO,

    /** No undo request waits on this player to answer (`D091`). */
    NO_UNDO_REQUEST,

    /** This player has nothing to redo right now (`D092`). */
    NOTHING_TO_REDO,

    /**
     * No draw may be offered now: one is pending, or the last was declined and no move has been
     * played since (`D097`).
     */
    CANNOT_OFFER_DRAW,

    /** No draw offer waits on this player to answer (`D097`). */
    NO_DRAW_OFFER,
}

/**
 * A refused command.
 *
 * [game] carries the canonical state as the server sees it right now, so a client that is
 * behind can refresh from this reply rather than making a second request. [reason] is what
 * tells a stale command apart from a premature one — both are conflicts, but only one is
 * worth retrying.
 */
@Serializable
data class CommandRejection(
    val reason: RejectionReason,
    val message: String,
    val game: GameView? = null,
)
