package com.jmussel.chessgame.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app talking to the Chess server.
 *
 * Runs against Ktor's `MockEngine` on the JVM, so there is no Android runtime and no
 * network — the point is the request the app sends and what it makes of the reply.
 */
class ChessApiClientTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun clientReplying(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        token: suspend () -> String = { "access-1" },
    ): ChessApiClient {
        val engine =
            MockEngine { request ->
                requests += request
                respond(
                    content = body,
                    status = status,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                )
            }

        return ChessApiClient(
            config = ChessServerConfig("https://chess.example"),
            httpClient =
                HttpClient(engine) {
                    install(ContentNegotiation) { json(ChessApiClient.Json) }
                },
            accessToken = token,
        )
    }

    @Test
    fun theDashboardIsRead() {
        val client =
            clientReplying(
                """
                [
                  {
                    "seriesId": "series-1",
                    "opponent": {"userId": "user-1", "username": "Alex"},
                    "gameId": "game-1",
                    "version": 34,
                    "yourSide": "WHITE",
                    "sideToMove": "WHITE",
                    "moveNumber": 18,
                    "yourTurn": true,
                    "closeAfterCurrentGame": false
                  }
                ]
                """.trimIndent(),
            )

        val entries = runBlocking { client.dashboard() }

        assertEquals(1, entries.size)
        assertEquals("Alex", entries.single().opponent.username)
        assertEquals("game-1", entries.single().gameId)
        assertEquals(18, entries.single().moveNumber)
        assertTrue(entries.single().yourTurn)
    }

    @Test
    fun theRequestCarriesTheSessionToken() {
        val client = clientReplying("[]")

        runBlocking { client.dashboard() }

        val request = requests.single()

        assertEquals("Bearer access-1", request.headers["Authorization"])
        assertEquals("/dashboard", request.url.encodedPath)
        assertEquals("chess.example", request.url.host)
    }

    @Test
    fun theTokenIsAskedForOnEveryCall() {
        var issued = 0
        val client = clientReplying("[]", token = { "access-${++issued}" })

        runBlocking {
            client.dashboard()
            client.dashboard()
        }

        // A refresh between calls has to be picked up, so the token is not captured once.
        assertEquals(
            listOf("Bearer access-1", "Bearer access-2"),
            requests.map { it.headers["Authorization"] },
        )
    }

    @Test
    fun anEmptyDashboardIsNotAnError() {
        val client = clientReplying("[]")

        assertTrue(runBlocking { client.dashboard() }.isEmpty())
    }

    @Test
    fun aSeriesWithoutAGameIsReadAsHavingNone() {
        val client =
            clientReplying(
                """
                [
                  {
                    "seriesId": "series-1",
                    "opponent": {"userId": "user-1", "username": "Alex"},
                    "yourTurn": false,
                    "closeAfterCurrentGame": true
                  }
                ]
                """.trimIndent(),
            )

        val entry = runBlocking { client.dashboard() }.single()

        assertNull(entry.gameId)
        assertNull(entry.moveNumber)
        assertTrue(entry.closeAfterCurrentGame)
    }

    @Test
    fun fieldsThisAppDoesNotKnowAreIgnored() {
        val client =
            clientReplying(
                """
                [
                  {
                    "seriesId": "series-1",
                    "opponent": {"userId": "user-1", "username": "Alex", "somethingNew": 1},
                    "yourTurn": true,
                    "gameId": "game-1",
                    "aFieldFromALaterServer": "whatever"
                  }
                ]
                """.trimIndent(),
            )

        assertEquals("Alex", runBlocking { client.dashboard() }.single().opponent.username)
    }

    @Test
    fun theFriendListIsRead() {
        val client =
            clientReplying(
                """[{"userId": "user-1", "username": "Alex"}, {"userId": "user-2", "username": "Sam"}]""",
            )

        val friends = runBlocking { client.friends() }

        assertEquals(listOf("Alex", "Sam"), friends.map { it.username })
        assertEquals("/friends", requests.single().url.encodedPath)
    }

    @Test
    fun playingWithAFriendOpensTheSeries() {
        val client =
            clientReplying(
                """
                {
                  "seriesId": "series-1",
                  "opponent": {"userId": "user-1", "username": "Alex"},
                  "status": "ACTIVE",
                  "closeAfterCurrentGame": false,
                  "currentGameId": "game-1"
                }
                """.trimIndent(),
            )

        val series = runBlocking { client.openSeries("Alex") }

        assertEquals("game-1", series.currentGameId)
        assertEquals("ACTIVE", series.status)
        assertEquals("Alex", series.opponent.username)

        val request = requests.single()

        assertEquals("/series", request.url.encodedPath)
        assertEquals("POST", request.method.value)
    }

    @Test
    fun aRefusalIsReportedWithItsStatus() {
        val client = clientReplying("no", status = HttpStatusCode.Unauthorized)

        val failure =
            try {
                runBlocking { client.dashboard() }
                null
            } catch (e: ChessApiException) {
                e
            }

        assertEquals(401, failure?.status)
        assertTrue(failure?.message.orEmpty().contains("/dashboard"))
    }

    @Test
    fun theBaseUrlIsJoinedWithoutDoubledSlashes() {
        assertEquals("https://chess.example/dashboard", ChessServerConfig("https://chess.example").url("/dashboard"))
        assertEquals("https://chess.example/dashboard", ChessServerConfig("https://chess.example/").url("dashboard"))
    }

    @Test
    fun theDefaultAddressIsTheDevelopmentServer() {
        assertEquals(ChessServerConfig.EMULATOR_LOOPBACK, ChessServerConfig().baseUrl)
    }
}
