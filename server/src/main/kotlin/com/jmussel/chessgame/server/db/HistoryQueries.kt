@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.or
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
 * Three queries whatever the number of series: the caller's series, the finished games in
 * them, and the opponents those series name. It never grows a query per series or per game,
 * and it never loads a move history — a finished game is summarised here and read in full
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
                    .where { (GameSeriesTable.userAId eq userId) or (GameSeriesTable.userBId eq userId) }
                    .orderBy(GameSeriesTable.createdAt to SortOrder.DESC)
                    .toList()

            if (seriesRows.isEmpty()) return@transaction emptyList()

            val seriesIds = seriesRows.map { it[GameSeriesTable.id] }
            val gamesBySeries = finishedGames(seriesIds, userId)
            val opponents = opponentsOf(seriesRows, userId)

            seriesRows.mapNotNull { row ->
                val seriesId = row[GameSeriesTable.id]
                val games = gamesBySeries[seriesId].orEmpty()

                // A series nobody has finished a game in yet is not history.
                if (games.isEmpty()) return@mapNotNull null

                val opponentId = opponentOf(row, userId)
                val opponent = opponents[opponentId] ?: return@mapNotNull null

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
                    GamesTable.whiteUserId,
                    GamesTable.result,
                    GamesTable.terminationReason,
                    GamesTable.state,
                    GamesTable.endedAt,
                ).where { (GamesTable.seriesId inList seriesIds) and GamesTable.endedAt.isNotNull() }
                .orderBy(GamesTable.sequenceNumber to SortOrder.ASC)
                .toList()

        return rows.groupBy({ it[GamesTable.seriesId] }) { row ->
            val state = row[GamesTable.state]

            FinishedGameView(
                gameId = row[GamesTable.id],
                sequenceNumber = row[GamesTable.sequenceNumber],
                yourSide = if (row[GamesTable.whiteUserId] == userId) "WHITE" else "BLACK",
                result = row[GamesTable.result],
                terminationReason = row[GamesTable.terminationReason],
                // Full moves as the position counts them; a game is summarised, not replayed.
                moveCount = state.fullmoveNumber,
                endedAt = row[GamesTable.endedAt]?.toInstant(),
            )
        }
    }

    private fun opponentsOf(
        seriesRows: List<org.jetbrains.exposed.v1.core.ResultRow>,
        userId: Uuid,
    ): Map<Uuid, StoredUser> {
        val opponentIds = seriesRows.map { opponentOf(it, userId) }.toSet()

        return UsersTable
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
    }

    private fun opponentOf(
        row: org.jetbrains.exposed.v1.core.ResultRow,
        userId: Uuid,
    ): Uuid =
        if (row[GameSeriesTable.userAId] == userId) {
            row[GameSeriesTable.userBId]
        } else {
            row[GameSeriesTable.userAId]
        }
}
