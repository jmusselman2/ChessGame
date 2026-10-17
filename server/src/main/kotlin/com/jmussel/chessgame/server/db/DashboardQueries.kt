@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** One line of a player's dashboard: a live series and the game they are in. */
data class ActiveSeriesView(
    val seriesId: Uuid,
    val opponent: StoredUser,
    val gameId: Uuid?,
    val gameVersion: Long?,
    /** `WHITE` or `BLACK` — the side the viewer is playing, or `null` with no game yet. */
    val yourSide: String?,
    val sideToMove: String?,
    val fullmoveNumber: Int?,
    /** Whose undo request is pending in the game, or `null` (`D091`). */
    val undoRequestedBy: Uuid? = null,
    /** Whose draw offer is pending in the game, or `null` (`D097`). */
    val drawOfferedBy: Uuid? = null,
    /** Whose dashboard this line is on. */
    val viewer: Uuid? = null,
) {
    /** Whether it is the viewer's move. */
    val isYourTurn: Boolean
        get() = yourSide != null && yourSide == sideToMove
}

/**
 * A player's lifetime chess record against one opponent, from the player's side (`D096`):
 * every finished chess game between them, across every series. Every draw counts as a draw.
 */
data class ChessRecord(
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
)

/**
 * Loading the dashboard.
 *
 * A returning player lands straight on this, so it has to be cheap: four queries whatever
 * the number of series — one join of the active series onto their current game, one for
 * the participants of those series' tables, one for the seats of those games, and one
 * lookup of every opponent they name. It never grows a query per series.
 */
class DashboardQueries(
    private val database: Database,
) {
    /**
     * [viewer]'s lifetime chess record against each opponent they have finished a chess game
     * with (`D096`), keyed by opponent. Someone they have never finished a game with is absent:
     * their record is a real `0-0-0`.
     *
     * Two queries whatever the number of opponents or games: the viewer's finished chess games
     * with their seat and result, and the other seat of each. Unfinished games, games deleted
     * when a series was left before a move (`D095`) and other game types are not counted.
     */
    fun chessRecordsFor(viewer: Uuid): Map<Uuid, ChessRecord> =
        transaction(database) {
            val chessSeries =
                GameSeriesTable
                    .select(GameSeriesTable.id)
                    .where {
                        GameSeriesTable.tableId inSubQuery
                            TablesTable.select(TablesTable.id).where { TablesTable.gameType eq GameTypes.CHESS }
                    }
            val viewersGames = GameParticipantsTable.select(GameParticipantsTable.gameId).where { GameParticipantsTable.userId eq viewer }

            val finished =
                GamesTable
                    .join(GameParticipantsTable, JoinType.INNER, onColumn = GamesTable.id, otherColumn = GameParticipantsTable.gameId)
                    .select(GamesTable.id, GamesTable.result, GameParticipantsTable.seatIndex)
                    .where {
                        (GameParticipantsTable.userId eq viewer) and
                            (GamesTable.status eq GameRepository.COMPLETE_GAME) and
                            (GamesTable.seriesId inSubQuery chessSeries)
                    }.associate { row ->
                        row[GamesTable.id] to (row[GamesTable.result] to ChessSeats.sideOf(row[GameParticipantsTable.seatIndex]))
                    }

            if (finished.isEmpty()) return@transaction emptyMap()

            val opponentOf =
                GameParticipantsTable
                    .select(GameParticipantsTable.gameId, GameParticipantsTable.userId)
                    .where {
                        (GameParticipantsTable.gameId inSubQuery viewersGames) and
                            (GameParticipantsTable.userId neq viewer) and
                            GameParticipantsTable.userId.isNotNull()
                    }.mapNotNull { row -> row[GameParticipantsTable.userId]?.let { row[GameParticipantsTable.gameId] to it } }
                    .toMap()

            finished.entries
                .mapNotNull { (gameId, outcome) -> opponentOf[gameId]?.let { it to outcome } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, games) ->
                    ChessRecord(
                        wins = games.count { (result, side) -> result == "${side.name}_WINS" },
                        losses = games.count { (result, side) -> result == "${side.opposite.name}_WINS" },
                        draws = games.count { (result, _) -> result == DRAW_RESULT },
                    )
                }
        }

    /**
     * Every active series [userId] is in, newest first, with the game each is at.
     *
     * A series someone has left is not listed: leaving ended its game too (`D095`), so there is
     * nothing left in it to play.
     */
    fun activeSeriesFor(userId: Uuid): List<ActiveSeriesView> =
        transaction(database) {
            val rows =
                GameSeriesTable
                    .join(
                        GamesTable,
                        JoinType.LEFT,
                        onColumn = GameSeriesTable.currentGameId,
                        otherColumn = GamesTable.id,
                    ).select(
                        GameSeriesTable.id,
                        GameSeriesTable.tableId,
                        GameSeriesTable.createdAt,
                        GamesTable.id,
                        GamesTable.version,
                        GamesTable.sideToMove,
                        GamesTable.state,
                        GamesTable.undoRequestedBy,
                        GamesTable.drawOfferedBy,
                    ).where {
                        (GameSeriesTable.tableId inSubQuery tablesSeating(userId)) and (GameSeriesTable.status eq ACTIVE_SERIES)
                    }.orderBy(GameSeriesTable.createdAt to SortOrder.DESC)
                    .toList()

            if (rows.isEmpty()) return@transaction emptyList()

            val tableParticipants = participantsOfTables(rows.map { it[GameSeriesTable.tableId] }.toSet())
            val gameSeats = participantsOfGames(rows.mapNotNull { it.getOrNull(GamesTable.id) }.toSet())

            // A chess table seats two, so the opponent is the other person at it. Only people are
            // opponents on a dashboard: a participant that is not a user is never listed (`D051`).
            fun opponentIdOf(tableId: Uuid): Uuid? = tableParticipants[tableId].orEmpty().userIds.singleOrNull { it != userId }

            val opponentIds = rows.mapNotNull { opponentIdOf(it[GameSeriesTable.tableId]) }.toSet()

            val opponents =
                UsersTable
                    .selectAll()
                    .where { UsersTable.id inList opponentIds }
                    .associate { row ->
                        row[UsersTable.id] to
                            StoredUser(
                                id = row[UsersTable.id],
                                username = row[UsersTable.username],
                                lastSeenAt = row[UsersTable.lastSeenAt]?.toInstant(),
                            )
                    }

            rows.mapNotNull { row ->
                val opponent = opponentIdOf(row[GameSeriesTable.tableId])?.let(opponents::get) ?: return@mapNotNull null
                val gameId = row.getOrNull(GamesTable.id)

                ActiveSeriesView(
                    seriesId = row[GameSeriesTable.id],
                    opponent = opponent,
                    gameId = gameId,
                    gameVersion = gameId?.let { row[GamesTable.version] },
                    yourSide =
                        gameId?.let {
                            ChessSeats.sideOf(gameSeats[gameId].orEmpty().indexOfFirst { it.userId == userId }).name
                        },
                    sideToMove = gameId?.let { row[GamesTable.sideToMove] },
                    fullmoveNumber = gameId?.let { row[GamesTable.state].fullmoveNumber },
                    undoRequestedBy = gameId?.let { row[GamesTable.undoRequestedBy] },
                    drawOfferedBy = gameId?.let { row[GamesTable.drawOfferedBy] },
                    viewer = userId,
                )
            }
        }
}

/** `games.result` for a draw, however it was reached. */
private const val DRAW_RESULT = "DRAW"
