package com.jmussel.chessgame.server.series

import com.jmussel.chessgame.server.api.SeriesSummary
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.realtime.PlayerClient
import com.jmussel.chessgame.server.realtime.RealtimeMessage
import com.jmussel.chessgame.server.realtime.fixtureJson
import com.jmussel.chessgame.server.realtime.nextMessage
import com.jmussel.chessgame.server.realtime.nextMessageOrNull
import com.jmussel.chessgame.server.realtime.order
import com.jmussel.chessgame.server.realtime.startGame
import com.jmussel.chessgame.server.realtime.withTwoPlayers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.close
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `POST /series/{seriesId}/leave`, at the API boundary (`D052`, `D095`).
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class SeriesExitRouteTest {
    private val tokens = TestTokens()

    @Test
    fun leavingAStartedGameAnswersTheEndedSeriesAndItsResignedGame() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            val gameId = startGame(jordan, alex)
            val seriesId = jordan.readGame(gameId).seriesId
            val (first, _) = order(gameId, jordan, alex)
            first.move(gameId, 0, "e2", "e4")
            assertTrue(jordan.readGame(gameId).hasStarted)

            val response = jordan.leaveSeries(seriesId)

            assertEquals(HttpStatusCode.OK, response.status)
            val summary = fixtureJson.decodeFromString<SeriesSummary>(response.bodyAsText())
            assertEquals(seriesId, summary.seriesId)
            assertEquals("CLOSED", summary.status)
            assertEquals("Alex", summary.opponent.username)
            assertEquals(gameId, summary.currentGameId)

            for (player in listOf(jordan, alex)) {
                val game = player.readGame(gameId)
                assertFalse(game.seriesActive)
                assertEquals("RESIGNATION", game.terminationReason, "leaving resigned the game (`D095`)")
                assertTrue(player.dashboard().isEmpty(), "a series that has been left is not listed")
            }
            val leaverSide = jordan.readGame(gameId).yourSide
            assertEquals(if (leaverSide == "WHITE") "BLACK_WINS" else "WHITE_WINS", jordan.readGame(gameId).result)
        }
    }

    @Test
    fun leavingAGameNobodyMovedInDeletesIt() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            val gameId = startGame(jordan, alex)
            val seriesId = jordan.readGame(gameId).seriesId
            assertFalse(jordan.readGame(gameId).hasStarted)

            val response = alex.leaveSeries(seriesId)

            assertEquals(HttpStatusCode.OK, response.status)
            val summary = fixtureJson.decodeFromString<SeriesSummary>(response.bodyAsText())
            assertEquals("CLOSED", summary.status)
            assertNull(summary.currentGameId, "the game was deleted, not finished")
            for (player in listOf(jordan, alex)) {
                assertEquals(HttpStatusCode.NotFound, player.gameResponse(gameId).status)
                assertTrue(player.dashboard().isEmpty())
            }
        }
    }

    @Test
    fun onceASeriesIsLeftPlayStartsANewOne() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            val gameId = startGame(jordan, alex)
            val seriesId = jordan.readGame(gameId).seriesId
            alex.leaveSeries(seriesId)

            val again =
                client.post("/series") {
                    headers.append("Authorization", "Bearer ${tokens.tokenFor("auth-jordan")}")
                    setBody("Alex")
                }
            assertEquals(HttpStatusCode.Created, again.status, "Play starts a new series")
            assertNotEquals(seriesId, fixtureJson.decodeFromString<SeriesSummary>(again.bodyAsText()).seriesId)
        }
    }

    @Test
    fun leavingTwiceAnswersTheSameWay() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            val gameId = startGame(jordan, alex)
            val seriesId = jordan.readGame(gameId).seriesId

            jordan.leaveSeries(seriesId)
            val again = jordan.leaveSeries(seriesId)
            val theOther = alex.leaveSeries(seriesId)

            assertEquals(HttpStatusCode.OK, again.status)
            assertEquals(HttpStatusCode.OK, theOther.status)
            assertEquals("CLOSED", fixtureJson.decodeFromString<SeriesSummary>(theOther.bodyAsText()).status)
        }
    }

    @Test
    fun aStrangerIsToldTheSeriesDoesNotExist() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            val gameId = startGame(jordan, alex)
            val seriesId = jordan.readGame(gameId).seriesId
            val sam = PlayerClient(this, tokens, "auth-sam")
            sam.claimUsername("Sam")

            assertEquals(HttpStatusCode.NotFound, sam.leaveSeries(seriesId).status)
            assertTrue(jordan.readGame(gameId).seriesActive)
        }
    }

    @Test
    fun anIdThatIsNotASeriesIdIsRefused() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            startGame(jordan, alex)

            assertEquals(HttpStatusCode.BadRequest, jordan.leaveSeries("not-an-id").status)
            assertEquals(HttpStatusCode.NotFound, jordan.leaveSeries("00000000-0000-0000-0000-000000000000").status)
        }
    }

    @Test
    fun theOtherPlayerHearsAboutIt() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            val gameId = startGame(jordan, alex)
            val seriesId = jordan.readGame(gameId).seriesId
            val waiting = alex.connect()

            jordan.leaveSeries(seriesId)

            val update = waiting.nextMessage()
            assertEquals(RealtimeMessage.GAME_UPDATED, update.type)
            assertEquals(gameId, update.gameId, "the game the leave deleted, so their app reloads")

            waiting.close()
        }
    }

    @Test
    fun aRepeatedLeaveAnnouncesNothing() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            val gameId = startGame(jordan, alex)
            val seriesId = jordan.readGame(gameId).seriesId
            jordan.leaveSeries(seriesId)
            val waiting = alex.connect()

            jordan.leaveSeries(seriesId)

            assertNull(waiting.nextMessageOrNull())

            waiting.close()
        }
    }
}
