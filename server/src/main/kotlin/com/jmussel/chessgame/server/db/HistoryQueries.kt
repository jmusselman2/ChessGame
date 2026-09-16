@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** One finished game, as history shows it. */
data class FinishedGameView(
    val gameId: Uuid,
    val sequenceNumber: Int,
    /** `WHITE` or `BLACK` — the side the viewer played. */
    val yourSide: String,
    val result: String?,
    val terminationReason: String?,
    val moveCount: Int,
    val endedAt: Instant?,
)

/** One series a player took part in, and the games in it that are over. */
data class SeriesHistoryView(
    val seriesId: Uuid,
    val opponent: StoredUser,
    val status: String,
    val closedAt: Instant?,
    /** Oldest first, the order they were played in. */
    val games: List<FinishedGameView>,
)

/**
 * Loading a player's history: the games that are over, in the series they belong to.
 *
 * Closed series stay readable forever (`D012`), and a completed game inside a series that
 * is still running is history too — what makes a game history is that it has finished, not
 * what became of the series around it.
 *
 * Five queries whatever the number of series: the caller's series, the participants of their
 * tables, the finished games in them, those games' seats, and the opponents the tables name.
 * It never grows a query per series or per game, and it never loads a move history — a finished game is summarised here and read in full
 * through `GET /games/{gameId}` when someone actually opens it.
 */
class HistoryQueries(
    private val database: Database,
) {
    /** Every series [userId] is in that has a finished game, newest series first. */
    fun historyFor(userId: Uuid): List<SeriesHistoryView> =
        transaction(database) {
            val seriesRows =
                GameSeriesTable
                    .selectAll()
                    .where { GameSeriesTable.tableId inSubQuery tablesSeating(userId) }
                    .orderBy(GameSeriesTable.createdAt to SortOrder.DESC)
                    .toList()

            if (seriesRows.isEmpty()) return@transaction emptyList()

            val seriesIds = seriesRows.map { it[GameSeriesTable.id] }
            val tableParticipants = participantsOfTables(seriesRows.map { it[GameSeriesTable.tableId] }.toSet())

            // A chess table seats two, so the opponent is whoever else is at it.
            fun opponentOf(row: ResultRow): Uuid? =
                tableParticipants[row[GameSeriesTable.tableId]].orEmpty().userIds.singleOrNull {
                    it !=
                        userId
                }

            val gamesBySeries = finishedGames(seriesIds, userId)
            val opponents = usersById(seriesRows.mapNotNull(::opponentOf).toSet())

            seriesRows.mapNotNull { row ->
                val seriesId = row[GameSeriesTable.id]
                val games = gamesBySeries[seriesId].orEmpty()

                // A series nobody has finished a game in yet is not history.
                if (games.isEmpty()) return@mapNotNull null

                val opponent = opponentOf(row)?.let(opponents::get) ?: return@mapNotNull null

                SeriesHistoryView(
                    seriesId = seriesId,
                    opponent = opponent,
                    status = row[GameSeriesTable.status],
                    closedAt = row[GameSeriesTable.closedAt]?.toInstant(),
                    games = games,
                )
            }
        }

    private fun finishedGames(
        seriesIds: List<Uuid>,
        userId: Uuid,
    ): Map<Uuid, List<FinishedGameView>> {
        val rows =
            GamesTable
                .select(
                    GamesTable.id,
                    GamesTable.seriesId,
                    GamesTable.sequenceNumber,
                    GamesTable.result,
                    GamesTable.terminationReason,
                    GamesTable.state,
                    GamesTable.endedAt,
                ).where { (GamesTable.seriesId inList seriesIds) and GamesTable.endedAt.isNotNull() }
                .orderBy(GamesTable.sequenceNumber to SortOrder.ASC)
                .toList()

        val seats = participantsOfGames(rows.map { it[GamesTable.id] })

        return rows.groupBy({ it[GamesTable.seriesId] }) { row ->
            val state = row[GamesTable.state]

            FinishedGameView(
                gameId = row[GamesTable.id],
                sequenceNumber = row[GamesTable.sequenceNumber],
                yourSide = ChessSeats.sideOf(seats[row[GamesTable.id]].orEmpty().indexOfFirst { it.userId == userId }).name,
                result = row[GamesTable.result],
                terminationReason = row[GamesTable.terminationReason],
                // Full moves as the position counts them; a game is summarised, not replayed.
                moveCount = state.fullmoveNumber,
                endedAt = row[GamesTable.endedAt]?.toInstant(),
            )
        }
    }

    private fun usersById(ids: Set<Uuid>): Map<Uuid, StoredUser> =
        UsersTable
            .selectAll()
            .where { UsersTable.id inList ids }
            .associate { row ->
                row[UsersTable.id] to
                    StoredUser(
                        id = row[UsersTable.id],
                        authSubject = row[UsersTable.authSubject],
                        username = row[UsersTable.username],
                        lastSeenAt = row[UsersTable.lastSeenAt]?.toInstant(),
                    )
            }
}
