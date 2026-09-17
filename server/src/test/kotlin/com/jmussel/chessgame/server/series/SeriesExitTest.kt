@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.series

import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.TerminationReason
import com.jmussel.chessgame.server.db.CLOSED_SERIES
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.game.CommandResult
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Leaving a series: its own action, separate from resigning a game (`D052`, `M19.8`).
 *
 * Leaving ends the series for the table and touches no game. The current game — under way,
 * or a rematch nobody has moved in yet — stays exactly as it was and can be played to its end,
 * and no rematch follows it (`D068`). Resigning ends one game and the series carries on.
 *
 * N ≥ 3 resignation (the game continuing without the resigner, and the continue prompts) is
 * not here: it needs a second ruleset and is `M20.2`.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class SeriesExitTest {
    private fun SeriesEndFixture.leave(player: Uuid): LeaveOutcome.Left = assertIs<LeaveOutcome.Left>(series.leave(player, seriesId))

    @Test
    fun leavingEndsTheSeriesAndRecordsWhoLeft() {
        withSeries { fixture ->
            val left = fixture.leave(fixture.white)

            assertTrue(left.endedNow)
            assertEquals(CLOSED_SERIES, left.series.status)
            assertEquals(CLOSED_SERIES, fixture.series().status)
            assertNotNull(fixture.series().closedAt)

            val event = fixture.events(SeriesService.SERIES_LEFT).single()
            assertEquals(fixture.white.toString(), event.payload["userId"].toString().trim('"'))
        }
    }

    @Test
    fun eitherPlayerMayLeave() {
        withSeries { fixture ->
            assertTrue(fixture.leave(fixture.black).endedNow)
            assertFalse(fixture.series().isActive)
        }
    }

    @Test
    fun leavingMidGameDoesNotTouchTheGame() {
        withSeries { fixture ->
            fixture.play(fixture.firstGameId, fixture.white, Move.of("e2", "e4"))
            val before = fixture.game(fixture.firstGameId)

            val left = fixture.leave(fixture.black)

            assertEquals(before.id, left.currentGame?.id)
            assertEquals(before, fixture.game(fixture.firstGameId), "not a column of the game moved")
            assertNull(fixture.game(fixture.firstGameId).game.result)
        }
    }

    @Test
    fun theGameInProgressCanStillBeFinishedAndNoRematchFollows() {
        withSeries { fixture ->
            fixture.play(fixture.firstGameId, fixture.white, Move.of("f2", "f3"))
            fixture.leave(fixture.white)

            fixture.play(fixture.firstGameId, fixture.black, Move.of("e7", "e5"))
            fixture.play(fixture.firstGameId, fixture.white, Move.of("g2", "g4"))
            fixture.play(fixture.firstGameId, fixture.black, Move.of("d8", "h4"))

            assertEquals(
                TerminationReason.CHECKMATE,
                fixture
                    .game(fixture.firstGameId)
                    .game.result
                    ?.reason,
            )
            assertEquals(1, fixture.gamesInSeries().size, "the game was the last one")
            assertTrue(fixture.events(SeriesService.REMATCH_CREATED).isEmpty())
            assertEquals(CLOSED_SERIES, fixture.series().status)
        }
    }

    @Test
    fun leavingBetweenGamesKeepsTheRematchAlreadyStartedAsTheLastGame() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)
            val rematch = fixture.currentGame()

            val left = fixture.leave(fixture.black)

            assertEquals(rematch.id, left.currentGame?.id)
            assertEquals(rematch, fixture.game(rematch.id), "the rematch nobody has moved in is untouched")

            fixture.commands.resign(rematch.whiteUserId, rematch.id, rematch.version)

            assertEquals(2, fixture.gamesInSeries().size, "and nothing follows it")
            assertEquals(rematch.id, fixture.series().currentGameId)
        }
    }

    @Test
    fun leavingTwiceChangesNothing() {
        withSeries { fixture ->
            val first = fixture.leave(fixture.white)
            val closedAt = fixture.series().closedAt

            val again = fixture.leave(fixture.white)
            val theOther = fixture.leave(fixture.black)

            assertTrue(first.endedNow)
            assertFalse(again.endedNow)
            assertFalse(theOther.endedNow, "the series had already ended when the other player left")
            assertEquals(CLOSED_SERIES, again.series.status)
            assertEquals(closedAt, fixture.series().closedAt)
            assertEquals(1, fixture.events(SeriesService.SERIES_LEFT).size)
        }
    }

    @Test
    fun someoneElseCannotLeaveYourSeries() {
        withSeries { fixture ->
            val sam = fixture.named("auth-3", "Sam")

            assertEquals(LeaveOutcome.NoSuchSeries, fixture.series.leave(sam, fixture.seriesId))
            assertTrue(fixture.series().isActive)
            assertTrue(fixture.events(SeriesService.SERIES_LEFT).isEmpty())
        }
    }

    @Test
    fun aSeriesThatDoesNotExistCannotBeLeft() {
        withSeries { fixture ->
            assertEquals(LeaveOutcome.NoSuchSeries, fixture.series.leave(fixture.white, Uuid.random()))
        }
    }

    @Test
    fun resigningIsNotLeaving() {
        withSeries { fixture ->
            val result = fixture.commands.resign(fixture.white, fixture.firstGameId, 0)

            assertIs<CommandResult.Applied>(result)
            assertTrue(fixture.series().isActive, "the series carries on after a resignation (`D052`)")
            assertEquals(2, fixture.gamesInSeries().size, "to its rematch")
            assertTrue(fixture.events(SeriesService.SERIES_LEFT).isEmpty())
        }
    }

    @Test
    fun bothPlayersLeavingAtOnceEndTheSeriesOnce() {
        withSeries { fixture ->
            val outcomes = together({ fixture.leave(fixture.white) }, { fixture.leave(fixture.black) })

            assertEquals(1, outcomes.count { it.endedNow }, "exactly one of them ended it")
            assertEquals(1, fixture.events(SeriesService.SERIES_LEFT).size)
            assertEquals(CLOSED_SERIES, fixture.series().status)
        }
    }

    @Test
    fun leavingAsTheLastGameEndsHappensInOneOrderOrTheOther() {
        repeat(RACES) {
            withSeries { fixture ->
                fixture.play(fixture.firstGameId, fixture.white, Move.of("f2", "f3"))
                fixture.play(fixture.firstGameId, fixture.black, Move.of("e7", "e5"))
                fixture.play(fixture.firstGameId, fixture.white, Move.of("g2", "g4"))
                val version = fixture.game(fixture.firstGameId).version

                together(
                    { fixture.leave(fixture.white) },
                    { fixture.commands.makeMove(fixture.black, fixture.firstGameId, version, Move.of("d8", "h4")) },
                )

                val series = fixture.series()
                val games = fixture.gamesInSeries()

                assertEquals(CLOSED_SERIES, series.status)
                assertEquals(1, fixture.events(SeriesService.SERIES_LEFT).size)
                assertTrue(fixture.events(SeriesService.REMATCH_CREATED).size <= 1)
                assertEquals(games.size - 1, fixture.events(SeriesService.REMATCH_CREATED).size)
                // Leave first: the checkmate gets no rematch. Checkmate first: the rematch it
                // started is the last game, untouched. Never a closed series pointing at a
                // game that is not its newest.
                assertEquals(games.maxBy { it.sequenceNumber }.id, series.currentGameId)
            }
        }
    }

    /** Runs both at the same moment and returns what each came to. */
    private fun <T> together(vararg actions: () -> T): List<T> {
        val pool = Executors.newFixedThreadPool(actions.size)
        val barrier = CyclicBarrier(actions.size)

        try {
            return actions
                .map { action ->
                    pool.submit(
                        Callable {
                            barrier.await()
                            action()
                        },
                    )
                }.map { it.get(WAIT_SECONDS, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }

    private companion object {
        const val RACES = 5
        const val WAIT_SECONDS = 30L
    }
}
