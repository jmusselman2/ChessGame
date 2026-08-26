@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.series

import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

/**
 * The next game a finished game owes its series.
 *
 * Rematches are automatic (`D015`): finishing a game in an active series starts the next
 * one without either player asking for it. What matters here is that it happens exactly
 * once — the series ends up pointing at one new game however many times the end of the old
 * one is processed.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class AutomaticRematchTest {
    @Test
    fun finishingAGameStartsTheNextOne() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)

            val next = fixture.currentGame()

            assertNotEquals(fixture.firstGameId, next.id, "the series moved on to a new game")
            assertFalse(next.isComplete, "the new game is waiting to be played")
            assertEquals(2, next.sequenceNumber, "it is the second game of the series")
            assertEquals(0, next.version)
            assertTrue(next.game.moves.isEmpty())
        }
    }

    @Test
    fun exactlyOneNextGameIsCreated() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)

            assertEquals(2, fixture.gamesInSeries().size, "one finished game and one new one")
            assertEquals(1, fixture.events(REMATCH).size)
        }
    }

    @Test
    fun theSeriesPointsAtTheNewGame() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)

            val series = fixture.series()
            val next = fixture.gamesInSeries().single { it.id != fixture.firstGameId }

            assertEquals(next.id, series.currentGameId)
            assertTrue(series.isActive, "a series with a game to play is still active")
        }
    }

    @Test
    fun theFinishedGameIsLeftAsItEnded() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)

            val finished = fixture.game(fixture.firstGameId)

            assertTrue(finished.isComplete, "the rematch did not disturb the game it followed")
            assertEquals(1, finished.sequenceNumber)
            assertEquals(4, finished.game.history.size)
            assertNotNull(finished.endedAt)
        }
    }

    @Test
    fun bothPlayersAreInTheNewGame() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)

            val next = fixture.currentGame()

            assertEquals(
                setOf(fixture.white, fixture.black),
                setOf(next.whiteUserId, next.blackUserId),
                "the same two people play on",
            )
            assertEquals(fixture.seriesId, next.seriesId)
        }
    }

    @Test
    fun askingAgainAfterTheRematchChangesNothing() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)
            val afterTheFirstAsk = fixture.series().currentGameId

            // A retry of the same end-of-game: the series has already moved on, so there
            // is nothing left to owe.
            repeat(3) { fixture.series.settleAfter(fixture.game(fixture.firstGameId)) }

            assertEquals(afterTheFirstAsk, fixture.series().currentGameId)
            assertEquals(2, fixture.gamesInSeries().size)
            assertEquals(1, fixture.events(REMATCH).size)
        }
    }

    @Test
    fun twoEndOfGamesArrivingTogetherStillCreateOneGame() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)
            val finished = fixture.game(fixture.firstGameId)
            val alreadyCurrent = fixture.series().currentGameId

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

            assertEquals(alreadyCurrent, fixture.series().currentGameId)
            assertEquals(2, fixture.gamesInSeries().size, "the series has one game to play, not several")
            assertEquals(1, fixture.events(REMATCH).size)
        }
    }

    @Test
    fun aSeriesMarkedToCloseGetsNoRematch() {
        withSeries { fixture ->
            fixture.seriesRepository.markCloseAfterCurrentGame(fixture.seriesId)

            fixture.playFoolsMate(fixture.firstGameId)

            assertEquals(1, fixture.gamesInSeries().size, "no game follows the last one (`D013`)")
            assertTrue(fixture.events(REMATCH).isEmpty())
            assertEquals(fixture.firstGameId, fixture.series().currentGameId)
        }
    }

    @Test
    fun aClosedSeriesGetsNoRematch() {
        withSeries { fixture ->
            fixture.seriesRepository.close(fixture.seriesId)

            fixture.playFoolsMate(fixture.firstGameId)

            assertEquals(1, fixture.gamesInSeries().size)
            assertTrue(fixture.events(REMATCH).isEmpty())
        }
    }

    @Test
    fun anUnfinishedGameOwesNothing() {
        withSeries { fixture ->
            fixture.play(fixture.firstGameId, fixture.white, Move.of("e2", "e4"))

            assertEquals(1, fixture.gamesInSeries().size)
            assertEquals(fixture.firstGameId, fixture.series().currentGameId)
            assertTrue(fixture.events(REMATCH).isEmpty())
        }
    }

    @Test
    fun theRematchEventSaysWhichGameItFollowed() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)

            val payload = fixture.events(REMATCH).single().payload

            assertEquals(fixture.firstGameId.toString(), payload["previousGameId"]?.jsonPrimitive?.content)
            assertEquals(2, payload["sequenceNumber"]?.jsonPrimitive?.content?.toInt())
        }
    }

    @Test
    fun theRematchReversesTheColours() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)

            val next = fixture.currentGame()

            assertEquals(fixture.black, next.whiteUserId, "whoever had Black now has White")
            assertEquals(fixture.white, next.blackUserId)
        }
    }

    @Test
    fun theColoursKeepAlternatingDownTheSeries() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)
            fixture.playFoolsMate(fixture.currentGame().id)
            fixture.playFoolsMate(fixture.currentGame().id)

            val whitePlayers = fixture.gamesInSeries().map { it.whiteUserId }

            assertEquals(
                listOf(fixture.white, fixture.black, fixture.white, fixture.black),
                whitePlayers,
                "the colours swap every game (`D014`)",
            )
            assertTrue(
                fixture.gamesInSeries().all { it.whiteUserId != it.blackUserId },
                "nobody ever plays themselves",
            )
        }
    }

    @Test
    fun theSeriesKeepsGoingGameAfterGame() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)
            val second = fixture.currentGame().id
            fixture.playFoolsMate(second)
            val third = fixture.currentGame().id
            fixture.playFoolsMate(third)

            val fourth = fixture.currentGame()

            assertEquals(4, fixture.gamesInSeries().size)
            assertEquals(4, fourth.sequenceNumber)
            assertEquals(
                listOf(1, 2, 3, 4),
                fixture.gamesInSeries().map { it.sequenceNumber },
                "each game follows the one before it",
            )
            assertEquals(3, fixture.events(REMATCH).size)
        }
    }

    private companion object {
        const val WAIT_SECONDS = 10L
        const val REMATCH = SeriesService.REMATCH_CREATED
    }
}
