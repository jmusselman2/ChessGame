package com.jmussel.chessgame.server.series

import com.jmussel.chessgame.server.api.SeriesSummary
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.realtime.PlayerClient
import com.jmussel.chessgame.server.realtime.RealtimeMessage
import com.jmussel.chessgame.server.realtime.fixtureJson
import com.jmussel.chessgame.server.realtime.nextMessage
import com.jmussel.chessgame.server.realtime.nextMessageOrNull
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `POST /series/{seriesId}/leave`, at the API boundary (`D052`, `M19.8`).
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class SeriesExitRouteTest {
    private val tokens = TestTokens()

    @Test
    fun leavingAnswersTheEndedSeries() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            val gameId = startGame(jordan, alex)
            val seriesId = jordan.readGame(gameId).seriesId

            val response = jordan.leaveSeries(seriesId)

            assertEquals(HttpStatusCode.OK, response.status)
            val summary = fixtureJson.decodeFromString<SeriesSummary>(response.bodyAsText())
            assertEquals(seriesId, summary.seriesId)
            assertEquals("CLOSED", summary.status)
            assertEquals("Alex", summary.opponent.username)
            assertEquals(gameId, summary.currentGameId)
        }
    }

    @Test
    fun bothPlayersSeeTheirGameAsTheLastOneAndCanStillReachIt() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            val gameId = startGame(jordan, alex)
            val seriesId = jordan.readGame(gameId).seriesId
            assertTrue(alex.readGame(gameId).seriesActive)

            jordan.leaveSeries(seriesId)

            for (player in listOf(jordan, alex)) {
                val game = player.readGame(gameId)
                assertFalse(game.seriesActive)
                assertNull(game.result, "the game is not over because the series is")

                val entry = player.dashboard().single()
                assertEquals(gameId, entry.gameId)
                assertFalse(entry.seriesActive)
            }
        }
    }

    @Test
    fun onceTheLastGameIsOverTheSeriesIsGoneAndPlayStartsANewOne() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            val gameId = startGame(jordan, alex)
            val seriesId = jordan.readGame(gameId).seriesId
            alex.leaveSeries(seriesId)

            assertEquals(HttpStatusCode.OK, jordan.resign(gameId, jordan.readGame(gameId).version).status)

            assertTrue(jordan.dashboard().isEmpty(), "no rematch followed")
            assertTrue(alex.dashboard().isEmpty())

            val again =
                client.post("/series") {
                    headers.append("Authorization", "Bearer ${tokens.tokenFor("auth-jordan")}")
                    setBody("Alex")
                }
            assertEquals(HttpStatusCode.Created, again.status, "Play starts a new series; the old one is not offered")
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
            assertEquals(gameId, update.gameId, "the game that is now the last one")

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
