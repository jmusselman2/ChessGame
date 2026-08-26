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

/** [StoredUser] as the API shows it, or `null` when they have not claimed a username yet. */
fun StoredUser.toSummaryOrNull(): UserSummary? = username?.let { UserSummary(userId = id.toString(), username = it) }

/** A series as one of its two players sees it. */
@Serializable
data class SeriesSummary(
    val seriesId: String,
    val opponent: UserSummary,
    val status: String,
    val closeAfterCurrentGame: Boolean,
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
                closeAfterCurrentGame = series.closeAfterCurrentGame,
                currentGameId = series.currentGameId?.toString(),
            )
        }
    }
}

/** One dashboard line: an active series, the game it is at, and whose move it is. */
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
    val closeAfterCurrentGame: Boolean = false,
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
                closeAfterCurrentGame = view.closeAfterCurrentGame,
            )
        }
    }
}

/** A game as one of its two players sees it: the canonical state, from their side. */
@Serializable
data class GameView(
    val gameId: String,
    val seriesId: String,
    val version: Long,
    val yourSide: String,
    val sideToMove: String,
    val yourTurn: Boolean,
    val inCheck: Boolean,
    /** Eight rows, rank 8 first, FEN-style letters with `.` for an empty square. */
    val board: List<String>,
    val moves: List<String>,
    val moveNumber: Int,
    val halfmoveClock: Int,
    val result: String? = null,
    val terminationReason: String? = null,
) {
    val isOver: Boolean
        get() = result != null

    companion object {
        fun of(
            stored: com.jmussel.chessgame.server.db.StoredGame,
            viewer: kotlin.uuid.Uuid,
        ): GameView {
            require(viewer == stored.whiteUserId || viewer == stored.blackUserId) {
                "That player is not in this game"
            }

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
                version = stored.version,
                yourSide = yourSide.name,
                sideToMove = state.sideToMove.name,
                yourTurn = !stored.game.isOver && state.sideToMove == yourSide,
                inCheck =
                    !stored.game.isOver &&
                        com.jmussel.chessgame.core.chess.Attacks
                            .isSideToMoveInCheck(state),
                board = state.board.toString().lines(),
                moves = stored.game.moves.map { it.toString() },
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
            )
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
