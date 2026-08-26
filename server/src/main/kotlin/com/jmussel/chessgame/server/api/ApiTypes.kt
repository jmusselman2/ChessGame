package com.jmussel.chessgame.server.api

import com.jmussel.chessgame.server.db.StoredUser
import kotlinx.serialization.Serializable

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
@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
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
        @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
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
