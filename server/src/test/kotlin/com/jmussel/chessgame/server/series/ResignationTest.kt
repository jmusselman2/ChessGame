@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.series

import com.jmussel.chessgame.core.chess.GameOutcome
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.TerminationReason
import com.jmussel.chessgame.server.db.CLOSED_SERIES
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.game.CommandResult
import com.jmussel.chessgame.server.game.GameCommandService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

/**
 * Giving up, and what the series does about it.
 *
 * A resignation ends the game like any other terminal result: it is final once accepted
 * (`D018`), it finalizes the game exactly once (`M13.1`), and the series then follows the
 * same fork as after a normal finish — the automatic rematch while it is active (`D015`),
 * the end of it when it was marked to close (`D013`).
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class ResignationTest {
    @Test
    fun resigningEndsTheGameForTheOtherPlayer() {
        withSeries { fixture ->
            val result = fixture.commands.resign(fixture.white, fixture.firstGameId, 0)

            assertTrue(result is CommandResult.Applied, "expected the resignation to be accepted, got $result")

            val finished = fixture.game(fixture.firstGameId)

            assertEquals(GameOutcome.BLACK_WINS, finished.game.result?.outcome)
            assertEquals(TerminationReason.RESIGNATION, finished.game.result?.reason)
            assertEquals(1, finished.version, "an accepted resignation is a mutation like any other")
        }
    }

    @Test
    fun aPlayerMayResignWhenItIsNotTheirTurn() {
        withSeries { fixture ->
            fixture.play(fixture.firstGameId, fixture.white, Move.of("e2", "e4"))

            // It is Black's move, and White gives up anyway.
            val result = fixture.commands.resign(fixture.white, fixture.firstGameId, 1)

            assertTrue(result is CommandResult.Applied, "giving up is not a move, got $result")

            val finished = fixture.game(fixture.firstGameId)

            assertEquals(GameOutcome.BLACK_WINS, finished.game.result?.outcome)
        }
    }

    @Test
    fun theResignationIsFinalizedAndAudited() {
        withSeries { fixture ->
            fixture.commands.resign(fixture.black, fixture.firstGameId, 0)

            val finished = fixture.game(fixture.firstGameId)

            assertNotNull(finished.endedAt)
            assertEquals(
                listOf(GameCommandService.PLAYER_RESIGNED, GameRepository.GAME_ENDED),
                fixture.games.auditTrail(fixture.firstGameId),
            )
        }
    }

    @Test
    fun aResignationCannotBeUndone() {
        withSeries { fixture ->
            fixture.play(fixture.firstGameId, fixture.white, Move.of("e2", "e4"))
            fixture.commands.resign(fixture.white, fixture.firstGameId, 1)

            val version = fixture.game(fixture.firstGameId).version

            // Neither the player who gave up nor their opponent can take it back (`D018`).
            assertTrue(fixture.commands.undoMove(fixture.white, fixture.firstGameId, version) is CommandResult.GameOver)
            assertTrue(fixture.commands.undoMove(fixture.black, fixture.firstGameId, version) is CommandResult.GameOver)

            val stillResigned = fixture.game(fixture.firstGameId)

            assertEquals(TerminationReason.RESIGNATION, stillResigned.game.result?.reason)
        }
    }

    @Test
    fun aFinishedGameCannotBeResigned() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)
            val finished = fixture.game(fixture.firstGameId)

            val result = fixture.commands.resign(fixture.white, fixture.firstGameId, finished.version)

            assertTrue(result is CommandResult.GameOver)

            val stillMated = fixture.game(fixture.firstGameId)

            assertEquals(TerminationReason.CHECKMATE, stillMated.game.result?.reason)
        }
    }

    @Test
    fun aStaleResignationIsRefused() {
        withSeries { fixture ->
            fixture.play(fixture.firstGameId, fixture.white, Move.of("e2", "e4"))

            val result = fixture.commands.resign(fixture.black, fixture.firstGameId, 0)

            assertTrue(result is CommandResult.StaleVersion)
            assertTrue(fixture.game(fixture.firstGameId).game.result == null, "nothing was written")
        }
    }

    @Test
    fun someoneElseCannotResignYourGame() {
        withSeries { fixture ->
            val stranger = fixture.named("auth-3", "Stranger")

            val result = fixture.commands.resign(stranger, fixture.firstGameId, 0)

            assertTrue(result is CommandResult.NotAParticipant)
            assertTrue(fixture.game(fixture.firstGameId).game.result == null)
        }
    }

    @Test
    fun anActiveSeriesGetsItsRematch() {
        withSeries { fixture ->
            fixture.commands.resign(fixture.white, fixture.firstGameId, 0)

            val next = fixture.currentGame()

            assertNotEquals(fixture.firstGameId, next.id)
            assertEquals(2, next.sequenceNumber)
            assertEquals(fixture.black, next.whiteUserId, "the colours still reverse (`D014`)")
            assertTrue(fixture.series().isActive)
            assertEquals(1, fixture.events(SeriesService.REMATCH_CREATED).size)
        }
    }

    @Test
    fun aClosingSeriesEndsInstead() {
        withSeries { fixture ->
            fixture.seriesRepository.markCloseAfterCurrentGame(fixture.seriesId)

            fixture.commands.resign(fixture.white, fixture.firstGameId, 0)

            assertEquals(CLOSED_SERIES, fixture.series().status)
            assertEquals(1, fixture.gamesInSeries().size, "no game follows the last one")
            assertEquals(1, fixture.events(SeriesService.SERIES_CLOSED).size)
        }
    }

    @Test
    fun aDuplicateResignationChangesNothing() {
        withSeries { fixture ->
            fixture.commands.resign(fixture.white, fixture.firstGameId, 0)
            val afterTheFirst = fixture.game(fixture.firstGameId)
            val rematch = fixture.series().currentGameId

            // The same tap twice, or a retry after a timeout.
            val again = fixture.commands.resign(fixture.white, fixture.firstGameId, 0)

            assertTrue(again is CommandResult.StaleVersion)
            assertEquals(afterTheFirst.version, fixture.game(fixture.firstGameId).version)
            assertEquals(afterTheFirst.endedAt, fixture.game(fixture.firstGameId).endedAt)
            assertEquals(rematch, fixture.series().currentGameId, "one resignation, one rematch")
            assertEquals(2, fixture.gamesInSeries().size)
        }
    }

    @Test
    fun theOtherPlayerCanResignTheRematchToo() {
        withSeries { fixture ->
            fixture.commands.resign(fixture.white, fixture.firstGameId, 0)
            val second = fixture.currentGame()

            fixture.commands.resign(fixture.black, second.id, 0)

            val third = fixture.currentGame()
            val resigned = fixture.game(second.id)

            assertEquals(GameOutcome.BLACK_WINS, resigned.game.result?.outcome)
            assertEquals(3, third.sequenceNumber, "the series carries on")
            assertEquals(3, fixture.gamesInSeries().size)
        }
    }
}
