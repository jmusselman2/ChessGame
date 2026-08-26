@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.series

import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.TerminationReason
import com.jmussel.chessgame.server.db.CLOSED_SERIES
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

/**
 * A series that was marked to close, once its last game finishes.
 *
 * Removing a friend does not end the game in progress (`D013`): it disables the next
 * rematch, lets the current game finish normally, and closes the series when it does.
 * These tests are about that last step — the game still finalizes, no rematch follows it,
 * and the series ends up `CLOSED` exactly once.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class SeriesClosesAfterLastGameTest {
    @Test
    fun theLastGameStillFinishesNormally() {
        withSeries { fixture ->
            fixture.seriesRepository.markCloseAfterCurrentGame(fixture.seriesId)

            fixture.playFoolsMate(fixture.firstGameId)

            val finished = fixture.game(fixture.firstGameId)

            assertTrue(finished.isComplete, "a marked series does not cut its game short")
            assertEquals(TerminationReason.CHECKMATE, finished.game.result?.reason)
            assertNotNull(finished.endedAt)
        }
    }

    @Test
    fun theSeriesCloses() {
        withSeries { fixture ->
            fixture.seriesRepository.markCloseAfterCurrentGame(fixture.seriesId)

            fixture.playFoolsMate(fixture.firstGameId)

            val series = fixture.series()

            assertEquals(CLOSED_SERIES, series.status)
            assertFalse(series.isActive)
            assertNotNull(series.closedAt, "a closed series records when it closed")
        }
    }

    @Test
    fun noRematchFollowsIt() {
        withSeries { fixture ->
            fixture.seriesRepository.markCloseAfterCurrentGame(fixture.seriesId)

            fixture.playFoolsMate(fixture.firstGameId)

            assertEquals(1, fixture.gamesInSeries().size, "the series ended with its last game")
            assertTrue(fixture.events(SeriesService.REMATCH_CREATED).isEmpty())
            assertEquals(
                fixture.firstGameId,
                fixture.series().currentGameId,
                "the last game played stays readable as the series' game",
            )
        }
    }

    @Test
    fun closingIsAudited() {
        withSeries { fixture ->
            fixture.seriesRepository.markCloseAfterCurrentGame(fixture.seriesId)

            fixture.playFoolsMate(fixture.firstGameId)

            val payload = fixture.events(SeriesService.SERIES_CLOSED).single().payload

            assertEquals(fixture.firstGameId.toString(), payload["lastGameId"]?.jsonPrimitive?.content)
            assertEquals("CloseAfterCurrentGame", payload["reason"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun anUnmarkedSeriesIsNotClosed() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)

            val series = fixture.series()

            assertTrue(series.isActive, "an active series plays on (`D015`)")
            assertTrue(fixture.events(SeriesService.SERIES_CLOSED).isEmpty())
            assertEquals(2, fixture.gamesInSeries().size)
        }
    }

    @Test
    fun markingItMidGameStillLetsThatGameFinish() {
        withSeries { fixture ->
            // The friend is removed after the game has started, which is the case `D013`
            // is really about.
            fixture.play(fixture.firstGameId, fixture.white, Move.of("f2", "f3"))
            fixture.seriesRepository.markCloseAfterCurrentGame(fixture.seriesId)
            fixture.play(fixture.firstGameId, fixture.black, Move.of("e7", "e5"))
            fixture.play(fixture.firstGameId, fixture.white, Move.of("g2", "g4"))

            assertTrue(fixture.series().isActive, "still active while the game is being played")

            fixture.play(fixture.firstGameId, fixture.black, Move.of("d8", "h4"))

            assertTrue(fixture.game(fixture.firstGameId).isComplete)
            assertEquals(CLOSED_SERIES, fixture.series().status)
            assertEquals(1, fixture.gamesInSeries().size)
        }
    }

    @Test
    fun closingHappensOnceHoweverOftenItIsAsked() {
        withSeries { fixture ->
            fixture.seriesRepository.markCloseAfterCurrentGame(fixture.seriesId)
            fixture.playFoolsMate(fixture.firstGameId)

            val closedAt = assertNotNull(fixture.series().closedAt)

            repeat(3) { fixture.series.settleAfter(fixture.game(fixture.firstGameId)) }

            assertEquals(closedAt, fixture.series().closedAt, "the moment it closed did not move")
            assertEquals(1, fixture.events(SeriesService.SERIES_CLOSED).size)
            assertEquals(1, fixture.gamesInSeries().size, "a closed series does not sprout a game")
        }
    }

    @Test
    fun twoEndOfGamesArrivingTogetherCloseItOnce() {
        withSeries { fixture ->
            fixture.seriesRepository.markCloseAfterCurrentGame(fixture.seriesId)
            fixture.play(fixture.firstGameId, fixture.white, Move.of("f2", "f3"))
            fixture.play(fixture.firstGameId, fixture.black, Move.of("e7", "e5"))
            fixture.play(fixture.firstGameId, fixture.white, Move.of("g2", "g4"))
            fixture.play(fixture.firstGameId, fixture.black, Move.of("d8", "h4"))

            val finished = fixture.game(fixture.firstGameId)
            val barrier = CyclicBarrier(2)
            val pool = Executors.newFixedThreadPool(2)

            try {
                pool
                    .invokeAll(
                        List(2) {
                            Callable {
                                barrier.await(WAIT_SECONDS, TimeUnit.SECONDS)
                                fixture.series.settleAfter(finished)
                            }
                        },
                    ).forEach { it.get(WAIT_SECONDS, TimeUnit.SECONDS) }
            } finally {
                pool.shutdownNow()
            }

            assertEquals(1, fixture.events(SeriesService.SERIES_CLOSED).size)
            assertEquals(CLOSED_SERIES, fixture.series().status)
            assertEquals(1, fixture.gamesInSeries().size)
        }
    }

    @Test
    fun thePairCanStartAFreshSeriesAfterwards() {
        withSeries { fixture ->
            fixture.seriesRepository.markCloseAfterCurrentGame(fixture.seriesId)
            fixture.playFoolsMate(fixture.firstGameId)

            // Closing frees the pair: befriending again opens a new series rather than
            // reviving the old one (`D012`).
            val reopened = fixture.series.openWithGame(fixture.white, fixture.black)

            assertTrue(reopened.created, "a new series, not the closed one")
            assertTrue(reopened.series.id != fixture.seriesId)
            assertEquals(CLOSED_SERIES, fixture.series().status, "the old series stays closed")
        }
    }

    private companion object {
        const val WAIT_SECONDS = 10L
    }
}
