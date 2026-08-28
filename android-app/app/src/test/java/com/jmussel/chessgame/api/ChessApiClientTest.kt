package com.jmussel.chessgame.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    /** One game, as the server describes it. */
    private val gameBody =
        """
        {"gameId":"game-1","seriesId":"series-1","opponent":{"userId":"user-1","username":"Alex"},
         "version":8,"yourSide":"WHITE","sideToMove":"BLACK","yourTurn":false,"inCheck":false,
         "board":["rnbqkbnr","pppppppp","........","........","....P...","........","PPPP.PPP","RNBQKBNR"],
         "moves":["e2e4"],"moveNumber":1,"halfmoveClock":0}
        """.trimIndent()

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
    fun historyIsRead() {
        val client =
            clientReplying(
                """
                [
                  {
                    "seriesId": "series-1",
                    "opponent": {"userId": "user-1", "username": "Alex"},
                    "status": "CLOSED",
                    "closedAt": "2026-08-20T18:03:00Z",
                    "games": [
                      {
                        "gameId": "game-1",
                        "sequenceNumber": 1,
                        "yourSide": "WHITE",
                        "result": "WHITE_WINS",
                        "terminationReason": "CHECKMATE",
                        "moveNumber": 31,
                        "endedAt": "2026-08-20T18:02:00Z"
                      }
                    ]
                  }
                ]
                """.trimIndent(),
            )

        val series = runBlocking { client.history() }.single()

        assertEquals("/history", requests.single().url.encodedPath)
        assertEquals("CLOSED", series.status)
        assertEquals("Alex", series.opponent.username)
        assertEquals("CHECKMATE", series.games.single().terminationReason)
        assertEquals(31, series.games.single().moveNumber)
    }

    @Test
    fun aSeriesWithNoFinishedGamesReadsAsEmpty() {
        val client =
            clientReplying(
                """
                [
                  {
                    "seriesId": "series-1",
                    "opponent": {"userId": "user-1", "username": "Alex"},
                    "status": "ACTIVE"
                  }
                ]
                """.trimIndent(),
            )

        assertTrue(runBlocking { client.history() }.single().games.isEmpty())
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
    fun aNewAccountIsReadAsHavingNoUsernameYet() {
        val client = clientReplying("""{"userId":"user-1"}""")

        val me = runBlocking { client.me() }

        assertEquals("user-1", me.userId)
        assertNull("a new anonymous account has not chosen a name yet", me.username)
        assertEquals("/me", requests.single().url.encodedPath)
    }

    @Test
    fun aReturningAccountIsReadWithItsUsername() {
        val client = clientReplying("""{"userId":"user-1","username":"Jordan"}""")

        assertEquals("Jordan", runBlocking { client.me() }.username)
    }

    @Test
    fun aUsernameIsClaimedByPostingIt() {
        val client = clientReplying("Jordan")

        val claimed = runBlocking { client.claimUsername("Jordan") }

        val request = requests.single()
        assertEquals("Jordan", claimed)
        assertEquals("/username", request.url.encodedPath)
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("Jordan", (request.body as TextContent).text)
    }

    @Test
    fun aTakenUsernameComesBackWithTheServersExplanation() {
        val client = clientReplying("That username is taken", status = HttpStatusCode.Conflict)

        val failure =
            try {
                runBlocking { client.claimUsername("Jordan") }
                null
            } catch (e: ChessApiException) {
                e
            }

        assertEquals(409, failure?.status)
        assertEquals("That username is taken", failure?.explanation)
    }

    @Test
    fun anInvalidUsernameComesBackWithTheServersExplanation() {
        val client =
            clientReplying("A username needs at least 3 characters", status = HttpStatusCode.BadRequest)

        val failure =
            try {
                runBlocking { client.claimUsername("ab") }
                null
            } catch (e: ChessApiException) {
                e
            }

        assertEquals(400, failure?.status)
        assertEquals("A username needs at least 3 characters", failure?.explanation)
    }

    @Test
    fun aUserIsLookedUpByExactUsername() {
        val client = clientReplying("""{"userId":"user-1","username":"Alex"}""")

        val found = runBlocking { client.lookUpUser("Alex") }

        assertEquals("Alex", found.username)
        assertEquals("/users/Alex", requests.single().url.encodedPath)
    }

    @Test
    fun aUsernameThatBelongsToNobodyIsARefusal() {
        val client = clientReplying("No such user", status = HttpStatusCode.NotFound)

        val failure =
            try {
                runBlocking { client.lookUpUser("Nobody") }
                null
            } catch (e: ChessApiException) {
                e
            }

        assertEquals(404, failure?.status)
        assertEquals("No such user", failure?.explanation)
    }

    @Test
    fun aFriendIsAddedByPostingTheirUsername() {
        val client = clientReplying("Alex")

        val added = runBlocking { client.addFriend("Alex") }

        val request = requests.single()
        assertEquals("Alex", added)
        assertEquals("/friends", request.url.encodedPath)
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("Alex", (request.body as TextContent).text)
    }

    @Test
    fun addingSomeoneTwiceComesBackWithTheServersExplanation() {
        val client = clientReplying("Already friends with Alex", status = HttpStatusCode.Conflict)

        val failure =
            try {
                runBlocking { client.addFriend("Alex") }
                null
            } catch (e: ChessApiException) {
                e
            }

        assertEquals(409, failure?.status)
        assertEquals("Already friends with Alex", failure?.explanation)
    }

    @Test
    fun addingYourselfComesBackWithTheServersExplanation() {
        val client = clientReplying("You cannot add yourself", status = HttpStatusCode.BadRequest)

        val failure =
            try {
                runBlocking { client.addFriend("Jordan") }
                null
            } catch (e: ChessApiException) {
                e
            }

        assertEquals(400, failure?.status)
        assertEquals("You cannot add yourself", failure?.explanation)
    }

    @Test
    fun aFriendIsRemovedByName() {
        val client = clientReplying("Removed Alex; your current game finishes first")

        val outcome = runBlocking { client.removeFriend("Alex") }

        val request = requests.single()
        assertEquals("Removed Alex; your current game finishes first", outcome)
        assertEquals("/friends/Alex", request.url.encodedPath)
        assertEquals(HttpMethod.Delete, request.method)
    }

    @Test
    fun aMoveIsPostedWithTheVersionItWasDecidedAt() {
        val client = clientReplying(gameBody)

        val played = runBlocking { client.makeMove("game-1", expectedVersion = 7, from = "e2", to = "e4") }

        val request = requests.single()
        assertEquals("game-1", played.gameId)
        assertEquals("/games/game-1/moves", request.url.encodedPath)
        assertEquals(HttpMethod.Post, request.method)

        val body = (request.body as TextContent).text
        assertTrue("the version has to travel with the move", body.contains("\"expectedVersion\":7"))
        assertTrue(body.contains("\"from\":\"e2\""))
        assertTrue(body.contains("\"to\":\"e4\""))
        assertFalse("an ordinary move promotes nothing", body.contains("promotion"))
    }

    @Test
    fun aPromotionCarriesThePieceItBecomes() {
        val client = clientReplying(gameBody)

        runBlocking { client.makeMove("game-1", expectedVersion = 7, from = "g7", to = "g8", promotion = "QUEEN") }

        assertTrue((requests.single().body as TextContent).text.contains("\"promotion\":\"QUEEN\""))
    }

    @Test
    fun aRefusedMoveComesBackWithTheCanonicalStateAttached() {
        val client =
            clientReplying(
                """{"reason":"STALE_VERSION","message":"This game is at version 9","game":$gameBody}""",
                status = HttpStatusCode.Conflict,
            )

        val refusal =
            try {
                runBlocking { client.makeMove("game-1", expectedVersion = 7, from = "e2", to = "e4") }
                null
            } catch (e: ChessCommandRefusedException) {
                e
            }

        assertEquals("STALE_VERSION", refusal?.reason)
        assertEquals(409, refusal?.status)
        assertEquals("game-1", refusal?.game?.gameId)
    }

    @Test
    fun aRefusalWithNoGameToShowIsStillReadable() {
        val client =
            clientReplying(
                """{"reason":"ILLEGAL_MOVE","message":"e2e5 is not legal here"}""",
                status = HttpStatusCode.UnprocessableEntity,
            )

        val refusal =
            try {
                runBlocking { client.makeMove("game-1", expectedVersion = 7, from = "e2", to = "e5") }
                null
            } catch (e: ChessCommandRefusedException) {
                e
            }

        assertEquals("ILLEGAL_MOVE", refusal?.reason)
        assertNull(refusal?.game)
        assertEquals("e2e5 is not legal here", refusal?.rejection?.message)
    }

    @Test
    fun aRefusalThatIsNotACommandRejectionIsStillReported() {
        val client = clientReplying("Not a move", status = HttpStatusCode.BadRequest)

        val failure =
            try {
                runBlocking { client.makeMove("game-1", expectedVersion = 7, from = "e2", to = "zz") }
                null
            } catch (e: ChessApiException) {
                e
            }

        assertEquals(400, failure?.status)
        assertEquals("Not a move", failure?.explanation)
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
