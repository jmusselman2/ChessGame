@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
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
    val closeAfterCurrentGame: Boolean,
    val gameId: Uuid?,
    val gameVersion: Long?,
    /** `WHITE` or `BLACK` — the side the viewer is playing, or `null` with no game yet. */
    val yourSide: String?,
    val sideToMove: String?,
    val fullmoveNumber: Int?,
) {
    /** Whether it is the viewer's move. */
    val isYourTurn: Boolean
        get() = yourSide != null && yourSide == sideToMove
}

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
    /** Every active series [userId] is in, newest first, with the game each is at. */
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
                        GameSeriesTable.closeAfterCurrentGame,
                        GameSeriesTable.createdAt,
                        GamesTable.id,
                        GamesTable.version,
                        GamesTable.sideToMove,
                        GamesTable.state,
                    ).where {
                        (GameSeriesTable.tableId inSubQuery tablesSeating(userId)) and
                            (GameSeriesTable.status eq ACTIVE_SERIES)
                    }.orderBy(GameSeriesTable.createdAt to SortOrder.DESC)
                    .toList()

            if (rows.isEmpty()) return@transaction emptyList()

            val tableParticipants = participantsOfTables(rows.map { it[GameSeriesTable.tableId] }.toSet())
            val gameSeats = participantsOfGames(rows.mapNotNull { it.getOrNull(GamesTable.id) }.toSet())

            // A chess table seats two, so the opponent is whoever else is at it.
            fun opponentIdOf(tableId: Uuid): Uuid? = tableParticipants[tableId].orEmpty().singleOrNull { it != userId }

            val opponentIds = rows.mapNotNull { opponentIdOf(it[GameSeriesTable.tableId]) }.toSet()

            val opponents =
                UsersTable
                    .selectAll()
                    .where { UsersTable.id inList opponentIds }
                    .associate { row ->
                        row[UsersTable.id] to
                            StoredUser(
                                id = row[UsersTable.id],
                                authSubject = row[UsersTable.authSubject],
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
                    closeAfterCurrentGame = row[GameSeriesTable.closeAfterCurrentGame],
                    gameId = gameId,
                    gameVersion = gameId?.let { row[GamesTable.version] },
                    yourSide =
                        gameId?.let {
                            ChessSeats.sideOf(gameSeats[gameId].orEmpty().indexOf(userId)).name
                        },
                    sideToMove = gameId?.let { row[GamesTable.sideToMove] },
                    fullmoveNumber = gameId?.let { row[GamesTable.state].fullmoveNumber },
                )
            }
        }
}
