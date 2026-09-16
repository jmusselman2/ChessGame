@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.series

import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.TerminationReason
import com.jmussel.chessgame.server.db.CLOSED_SERIES
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.RemoveFriendResult
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

/**
 * A series outlives the friendship it started from, and ends only by being closed.
 *
 * This was `SeriesClosesAfterLastGameTest`, which pinned `D013`: removing a friend marked the
 * series to close after its current game, the game finished, no rematch followed, and the
 * series closed exactly once. `D053` superseded `D013` (`M19.5`). Removing a friend now affects
 * the friends list only, so the first half of this file asserts the opposite of what it used
 * to: the game finishes, the rematch follows, and the series stays active.
 *
 * The second half keeps what was worth keeping about an *ending* series — a closed series
 * lets its game finish, gets no rematch however often or concurrently it is asked, and stays
 * closed. A series is closed directly here; `M19.8` makes that a player's explicit action.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class SeriesOutlivesFriendshipTest {
    private fun SeriesEndFixture.unfriend() = assertIs<RemoveFriendResult.Removed>(FriendshipRepository(database).remove(white, black))

    @Test
    fun unfriendingMidGameLetsTheGameFinishAndTheRematchFollow() {
        withSeries { fixture ->
            fixture.play(fixture.firstGameId, fixture.white, Move.of("f2", "f3"))
            fixture.unfriend()
            fixture.play(fixture.firstGameId, fixture.black, Move.of("e7", "e5"))
            fixture.play(fixture.firstGameId, fixture.white, Move.of("g2", "g4"))
            fixture.play(fixture.firstGameId, fixture.black, Move.of("d8", "h4"))

            val finished = fixture.game(fixture.firstGameId)
            assertEquals(TerminationReason.CHECKMATE, finished.game.result?.reason)

            val series = fixture.series()
            assertTrue(series.isActive, "the series carries on (`D053`)")
            assertNull(series.closedAt)
            assertEquals(2, fixture.gamesInSeries().size, "and the next game follows")
            assertEquals(1, fixture.events(SeriesService.REMATCH_CREATED).size)
        }
    }

    @Test
    fun unfriendingBetweenGamesChangesNothingAboutTheSeries() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)
            val before = fixture.series()

            fixture.unfriend()

            assertEquals(before, fixture.series(), "not a column of the series moved")
            fixture.playFoolsMate(assertNotNull(before.currentGameId))
            assertEquals(3, fixture.gamesInSeries().size)
        }
    }

    @Test
    fun aClosedSeriesStillLetsItsGameFinish() {
        withSeries { fixture ->
            fixture.seriesRepository.close(fixture.seriesId)

            fixture.playFoolsMate(fixture.firstGameId)

            val finished = fixture.game(fixture.firstGameId)
            assertTrue(finished.isComplete, "closing a series does not cut its game short")
            assertNotNull(finished.endedAt)
            assertEquals(1, fixture.gamesInSeries().size, "no rematch follows it")
            assertEquals(fixture.firstGameId, fixture.series().currentGameId, "the last game stays the series' game")
        }
    }

    @Test
    fun aClosedSeriesGetsNoRematchHoweverOftenItIsAsked() {
        withSeries { fixture ->
            fixture.seriesRepository.close(fixture.seriesId)
            fixture.playFoolsMate(fixture.firstGameId)
            val closedAt = assertNotNull(fixture.series().closedAt)

            repeat(3) { fixture.series.settleAfter(fixture.game(fixture.firstGameId)) }

            assertEquals(closedAt, fixture.series().closedAt, "the moment it closed did not move")
            assertEquals(CLOSED_SERIES, fixture.series().status)
            assertEquals(1, fixture.gamesInSeries().size, "a closed series does not sprout a game")
        }
    }

    @Test
    fun twoEndOfGamesArrivingTogetherAtAClosedSeriesStartNothing() {
        withSeries { fixture ->
            fixture.seriesRepository.close(fixture.seriesId)
            fixture.playFoolsMate(fixture.firstGameId)

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

            assertEquals(CLOSED_SERIES, fixture.series().status)
            assertEquals(1, fixture.gamesInSeries().size)
        }
    }

    @Test
    fun thePairCanStartAFreshSeriesAfterOneCloses() {
        withSeries { fixture ->
            fixture.seriesRepository.close(fixture.seriesId)
            fixture.playFoolsMate(fixture.firstGameId)

            // A closed series is not offered back: Play starts a new one (`D012`, `D053`).
            val reopened = assertIs<PlayOutcome.Started>(fixture.series.play(fixture.white, fixture.black))

            assertTrue(reopened.series.id != fixture.seriesId, "a new series, not the closed one")
            assertEquals(CLOSED_SERIES, fixture.series().status, "the old series stays closed")
        }
    }

    private companion object {
        const val WAIT_SECONDS = 10L
    }
}
