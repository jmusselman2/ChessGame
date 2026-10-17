@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.series

import com.jmussel.chessgame.core.chess.GameOutcome
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.TerminationReason
import com.jmussel.chessgame.server.db.CLOSED_SERIES
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.game.CommandResult
import com.jmussel.chessgame.server.game.GameCommandService
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
 * Leaving a series ends the series and its current game together (`D095`, `M22.1`).
 *
 * A game in which a move was ever accepted ends as the leaver's resignation, even when every
 * move has since been undone; a game nobody moved in is deleted without a result. No rematch
 * follows, and no closed series has an unfinished game. Resigning is different: it ends one
 * game and the series carries on.
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
    fun leavingAStartedGameIsTheLeaversResignation() {
        withSeries { fixture ->
            fixture.play(fixture.firstGameId, fixture.white, Move.of("e2", "e4"))

            val left = fixture.leave(fixture.black)

            val game = fixture.game(fixture.firstGameId)
            assertEquals(GameOutcome.WHITE_WINS, game.game.result?.outcome, "the leaver lost")
            assertEquals(TerminationReason.RESIGNATION, game.game.result?.reason)
            assertEquals(game, left.currentGame)
            assertEquals(listOf(Move.of("e2", "e4")), game.game.moves, "what was played stays")
            assertEquals(
                listOf(GameCommandService.MOVE_MADE, GameCommandService.PLAYER_RESIGNED, "GameEnded", SeriesService.SERIES_LEFT),
                fixture.games.auditTrail(game.id),
            )
            val resigned = fixture.games.auditEvents(game.id).single { it.type == GameCommandService.PLAYER_RESIGNED }
            assertEquals(fixture.black, resigned.actorId)
            assertEquals(1, fixture.gamesInSeries().size, "no rematch")
        }
    }

    @Test
    fun aGameWhoseMovesWereAllUndoneHasStillStarted() {
        withSeries { fixture ->
            fixture.play(fixture.firstGameId, fixture.white, Move.of("e2", "e4"))
            val played = fixture.game(fixture.firstGameId)
            assertIs<CommandResult.Applied>(fixture.commands.undoMove(fixture.white, played.id, played.version))
            val undone = fixture.game(fixture.firstGameId)
            assertTrue(undone.game.moves.isEmpty())
            assertTrue(undone.hasStarted, "Undo does not unmark a started game")

            fixture.leave(fixture.white)

            val game = fixture.game(fixture.firstGameId)
            assertEquals(GameOutcome.BLACK_WINS, game.game.result?.outcome, "a resignation, not a deletion")
            assertEquals(TerminationReason.RESIGNATION, game.game.result?.reason)
            assertEquals(emptyList(), game.game.redoPath, "a finished game offers no Redo")
        }
    }

    @Test
    fun redoDoesNotUnmarkAStartedGameEither() {
        withSeries { fixture ->
            fixture.play(fixture.firstGameId, fixture.white, Move.of("e2", "e4"))
            val played = fixture.game(fixture.firstGameId)
            fixture.commands.undoMove(fixture.white, played.id, played.version)
            val undone = fixture.game(fixture.firstGameId)
            assertIs<CommandResult.Applied>(fixture.commands.redo(fixture.white, undone.id, undone.version))

            assertTrue(fixture.game(fixture.firstGameId).hasStarted)
        }
    }

    @Test
    fun leavingAGameNobodyMovedInDeletesIt() {
        withSeries { fixture ->
            assertFalse(fixture.game(fixture.firstGameId).hasStarted)

            val left = fixture.leave(fixture.white)

            assertNull(left.currentGame)
            assertEquals(fixture.firstGameId, left.deletedGame?.id)
            assertNull(left.series.currentGameId)
            assertNull(fixture.games.load(fixture.firstGameId), "deleted without a result")
            assertTrue(fixture.gamesInSeries().isEmpty())
            assertNull(fixture.series().currentGameId)
            assertEquals(CLOSED_SERIES, fixture.series().status)
            val event = fixture.events(SeriesService.SERIES_LEFT).single()
            assertEquals(fixture.firstGameId.toString(), event.payload["deletedGameId"].toString().trim('"'))
        }
    }

    @Test
    fun leavingBetweenGamesDeletesTheRematchNobodyMovedIn() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)
            val finished = fixture.game(fixture.firstGameId)
            val rematch = fixture.currentGame()

            val left = fixture.leave(fixture.black)

            assertEquals(rematch.id, left.deletedGame?.id)
            assertNull(fixture.games.load(rematch.id))
            assertEquals(listOf(finished), fixture.gamesInSeries(), "the finished game stays for history")
            assertNull(fixture.series().currentGameId)
        }
    }

    @Test
    fun aCommandOnTheGameLeftBehindIsRefused() {
        withSeries { fixture ->
            fixture.play(fixture.firstGameId, fixture.white, Move.of("e2", "e4"))
            val version = fixture.game(fixture.firstGameId).version
            fixture.leave(fixture.white)

            assertIs<CommandResult.StaleVersion>(
                fixture.commands.makeMove(fixture.black, fixture.firstGameId, version, Move.of("e7", "e5")),
            )
            val now = fixture.game(fixture.firstGameId).version
            assertIs<CommandResult.GameOver>(fixture.commands.makeMove(fixture.black, fixture.firstGameId, now, Move.of("e7", "e5")))
        }
    }

    @Test
    fun leavingTwiceChangesNothing() {
        withSeries { fixture ->
            fixture.play(fixture.firstGameId, fixture.white, Move.of("e2", "e4"))
            val first = fixture.leave(fixture.white)
            val closedAt = fixture.series().closedAt
            val game = fixture.game(fixture.firstGameId)

            val again = fixture.leave(fixture.white)
            val theOther = fixture.leave(fixture.black)

            assertTrue(first.endedNow)
            assertFalse(again.endedNow)
            assertFalse(theOther.endedNow, "the series had already ended when the other player left")
            assertEquals(CLOSED_SERIES, again.series.status)
            assertEquals(closedAt, fixture.series().closedAt)
            assertEquals(1, fixture.events(SeriesService.SERIES_LEFT).size)
            assertEquals(game, fixture.game(fixture.firstGameId), "the result is not written twice")
            assertEquals(game, again.currentGame)
        }
    }

    @Test
    fun someoneElseCannotLeaveYourSeries() {
        withSeries { fixture ->
            val sam = fixture.named("auth-3", "Sam")

            assertEquals(LeaveOutcome.NoSuchSeries, fixture.series.leave(sam, fixture.seriesId))
            assertTrue(fixture.series().isActive)
            assertTrue(fixture.events(SeriesService.SERIES_LEFT).isEmpty())
            assertNotNull(fixture.games.load(fixture.firstGameId))
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
    fun aNewSeriesCanStartOnceTheOldOneHasEnded() {
        withSeries { fixture ->
            fixture.leave(fixture.white)

            val opened = fixture.series.play(fixture.black, fixture.white)

            assertTrue(opened.started)
            assertTrue(opened.series.id != fixture.seriesId)
        }
    }

    @Test
    fun bothPlayersLeavingAtOnceEndTheSeriesOnce() {
        withSeries { fixture ->
            fixture.play(fixture.firstGameId, fixture.white, Move.of("e2", "e4"))

            val outcomes = together({ fixture.leave(fixture.white) }, { fixture.leave(fixture.black) })

            assertEquals(1, outcomes.count { it.endedNow }, "exactly one of them ended it")
            assertEquals(1, fixture.events(SeriesService.SERIES_LEFT).size)
            assertEquals(CLOSED_SERIES, fixture.series().status)
            assertEquals(1, fixture.games.auditTrail(fixture.firstGameId).count { it == GameCommandService.PLAYER_RESIGNED })
        }
    }

    @Test
    fun leavingAsTheGameEndsGivesOneResultAClosedSeriesAndNoRematch() {
        repeat(RACES) {
            withSeries { fixture ->
                fixture.play(fixture.firstGameId, fixture.white, Move.of("f2", "f3"))
                fixture.play(fixture.firstGameId, fixture.black, Move.of("e7", "e5"))
                fixture.play(fixture.firstGameId, fixture.white, Move.of("g2", "g4"))
                val version = fixture.game(fixture.firstGameId).version

                // Bounded: a deadlock would fail `together`'s wait rather than hang.
                together(
                    { fixture.leave(fixture.white) },
                    { fixture.commands.makeMove(fixture.black, fixture.firstGameId, version, Move.of("d8", "h4")) },
                )

                val series = fixture.series()
                val game = fixture.game(fixture.firstGameId)
                val result = assertNotNull(game.game.result, "the game is over either way")

                assertEquals(CLOSED_SERIES, series.status)
                assertEquals(1, fixture.events(SeriesService.SERIES_LEFT).size)
                assertEquals(1, fixture.games.auditTrail(game.id).count { it == "GameEnded" }, "one result")
                // Leave first: White resigned and the mate was refused. Mate first: Black won
                // by checkmate, the rematch it started had no move and the leave deleted it.
                // Either way nothing unfinished is left in the closed series.
                assertEquals(GameOutcome.BLACK_WINS, result.outcome)
                assertEquals(listOf(game), fixture.gamesInSeries())
                assertTrue(fixture.gamesInSeries().all { it.isComplete })
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
