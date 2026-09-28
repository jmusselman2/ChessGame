package com.jmussel.chessgame.ui.series

import com.jmussel.chessgame.api.SeriesSummaryDto
import com.jmussel.chessgame.api.UserSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The words and the choice Play offers when the pair already has a series (`D053`). */
class PlayOffersTest {
    private fun series(
        id: String,
        gameId: String?,
    ) = SeriesSummaryDto(
        seriesId = id,
        opponent = UserSummaryDto(userId = "user-1", username = "Alex"),
        status = "ACTIVE",
        currentGameId = gameId,
    )

    @Test
    fun oneSeriesIsOfferedAsIt() {
        val offer = PlayOffer(username = "Alex", existing = listOf(series("series-1", "game-1")))

        assertEquals("Already playing Alex", PlayOffers.title(offer))
        assertTrue(PlayOffers.explanation(offer).contains("a game with Alex"))
        assertEquals("game-1", offer.newestGameId)
    }

    @Test
    fun severalSeriesAreCountedAndOpenGoesToTheNewest() {
        val offer =
            PlayOffer(
                username = "Alex",
                existing = listOf(series("series-3", "game-3"), series("series-1", "game-1")),
            )

        assertTrue(PlayOffers.explanation(offer).contains("2 games with Alex"))
        assertEquals("the server sends the newest first", "game-3", offer.newestGameId)
    }

    @Test
    fun aSeriesBetweenGamesIsSkippedWhenChoosingWhatToOpen() {
        val between = PlayOffer(username = "Alex", existing = listOf(series("series-2", null), series("series-1", "game-1")))
        val nothing = PlayOffer(username = "Alex", existing = listOf(series("series-2", null)))

        assertEquals("game-1", between.newestGameId)
        assertNull(nothing.newestGameId)
    }
}
