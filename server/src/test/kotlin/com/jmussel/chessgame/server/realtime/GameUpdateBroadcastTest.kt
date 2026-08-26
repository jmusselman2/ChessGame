@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.realtime

import com.jmussel.chessgame.server.api.GameView
import com.jmussel.chessgame.server.api.SeriesSummary
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.testModule
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi

/**
 * A move by one player reaching the other player's open connection.
 *
 * The push carries no game state: it names the game and the version it reached, and the
 * client reloads over HTTPS (`D022`). These tests therefore assert who was told and which
 * version they were told about — never that the socket carried the board.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class GameUpdateBroadcastTest {
    private val tokens = TestTokens()
    private val json = Json { ignoreUnknownKeys = true }

    /** One player: an HTTP client, and a socket once they connect. */
    private inner class Client(
        val builder: ApplicationTestBuilder,
        val subject: String,
    ) {
        suspend fun claimUsername(name: String) =
            builder.client.post("/username") {
                authorized()
                setBody(name)
            }

        suspend fun addFriend(name: String) =
            builder.client.post("/friends") {
                authorized()
                setBody(name)
            }

        suspend fun openSeries(friend: String): SeriesSummary =
            json.decodeFromString(
                builder.client
                    .post("/series") {
                        authorized()
                        setBody(friend)
                    }.bodyAsText(),
            )

        suspend fun readGame(gameId: String): GameView =
            json.decodeFromString(
                builder.client
                    .get("/games/$gameId") { authorized() }
                    .bodyAsText(),
            )

        suspend fun move(
            gameId: String,
            version: Long,
            from: String,
            to: String,
        ): HttpResponse =
            builder.client.post("/games/$gameId/moves") {
                authorized()
                contentType(ContentType.Application.Json)
                setBody("""{"expectedVersion":$version,"from":"$from","to":"$to"}""")
            }

        suspend fun undo(
            gameId: String,
            version: Long,
        ): HttpResponse =
            builder.client.post("/games/$gameId/undo") {
                authorized()
                contentType(ContentType.Application.Json)
                setBody("""{"expectedVersion":$version}""")
            }

        suspend fun claimDraw(
            gameId: String,
            version: Long,
            claim: String,
        ): HttpResponse =
            builder.client.post("/games/$gameId/draw-claims") {
                authorized()
                contentType(ContentType.Application.Json)
                setBody("""{"expectedVersion":$version,"claim":"$claim"}""")
            }

        /** Opens the realtime connection and swallows the greeting. */
        suspend fun connect(): WebSocketSession =
            builder
                .createClient {
                    install(WebSockets) { contentConverter = KotlinxWebsocketSerializationConverter(Json) }
                }.webSocketSession("/ws") { authorized() }
                .also { it.nextMessage() }

        private fun HttpRequestBuilder.authorized() = header("Authorization", "Bearer ${tokens.tokenFor(subject)}")
    }

    private suspend fun WebSocketSession.nextMessage(): RealtimeMessage =
        withTimeout(WAIT_MILLIS) {
            json.decodeFromString((incoming.receive() as Frame.Text).readText())
        }

    /** Null when nothing arrives, so "was told nothing" is a real assertion. */
    private suspend fun WebSocketSession.nextMessageOrNull(): RealtimeMessage? =
        withTimeoutOrNull(QUIET_MILLIS) {
            json.decodeFromString<RealtimeMessage>((incoming.receive() as Frame.Text).readText())
        }

    private fun withTwoClients(block: suspend ApplicationTestBuilder.(Client, Client) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)

            testApplication {
                application { testModule(tokens.verifier(), database, realtime = RealtimeHub()) }
                block(Client(this, "auth-jordan"), Client(this, "auth-alex"))
            }
        }

    /** Both sign in, become friends, and open the series; returns the game id. */
    private suspend fun startGame(
        jordan: Client,
        alex: Client,
    ): String {
        jordan.claimUsername("Jordan")
        alex.claimUsername("Alex")
        jordan.addFriend("Alex")

        return requireNotNull(jordan.openSeries("Alex").currentGameId) {
            "opening a series starts its first game"
        }
    }

    /** Whoever is to move first, then the other one. */
    private suspend fun order(
        gameId: String,
        jordan: Client,
        alex: Client,
    ) = if (jordan.readGame(gameId).yourTurn) jordan to alex else alex to jordan

    @Test
    fun aMoveByOnePlayerReachesTheOther() {
        withTwoClients { jordan, alex ->
            val gameId = startGame(jordan, alex)
            val (mover, waiter) = order(gameId, jordan, alex)
            val waiting = waiter.connect()

            mover.move(gameId, 0, "e2", "e4")

            val update = waiting.nextMessage()

            assertEquals(RealtimeMessage.GAME_UPDATED, update.type)
            assertEquals(gameId, update.gameId)
            assertEquals(1, update.version, "the version the client should now read")

            waiting.close()
        }
    }

    @Test
    fun theMoverIsToldTooSoTheirOtherDevicesKeepUp() {
        withTwoClients { jordan, alex ->
            val gameId = startGame(jordan, alex)
            val (mover, _) = order(gameId, jordan, alex)
            val moversOtherDevice = mover.connect()

            mover.move(gameId, 0, "e2", "e4")

            assertEquals(RealtimeMessage.GAME_UPDATED, moversOtherDevice.nextMessage().type)

            moversOtherDevice.close()
        }
    }

    @Test
    fun everyMoveOfARallyIsAnnouncedInOrder() {
        withTwoClients { jordan, alex ->
            val gameId = startGame(jordan, alex)
            val (first, second) = order(gameId, jordan, alex)
            val watching = second.connect()

            first.move(gameId, 0, "e2", "e4")
            assertEquals(1, watching.nextMessage().version)

            second.move(gameId, 1, "e7", "e5")
            assertEquals(2, watching.nextMessage().version)

            first.move(gameId, 2, "g1", "f3")
            assertEquals(3, watching.nextMessage().version)

            watching.close()
        }
    }

    @Test
    fun anUndoIsAnnouncedLikeAMove() {
        withTwoClients { jordan, alex ->
            val gameId = startGame(jordan, alex)
            val (mover, waiter) = order(gameId, jordan, alex)
            val waiting = waiter.connect()

            mover.move(gameId, 0, "e2", "e4")
            assertEquals(1, waiting.nextMessage().version)

            assertEquals(HttpStatusCode.OK, mover.undo(gameId, 1).status)

            val update = waiting.nextMessage()

            assertEquals(RealtimeMessage.GAME_UPDATED, update.type)
            assertEquals(2, update.version, "taking a move back is a change like any other")

            waiting.close()
        }
    }

    @Test
    fun aClaimedDrawIsAnnounced() {
        withTwoClients { jordan, alex ->
            val gameId = startGame(jordan, alex)
            val (first, second) = order(gameId, jordan, alex)
            val watching = second.connect()

            // Knights out and back, twice: the starting position appears three times.
            val rally = listOf("g1" to "f3", "g8" to "f6", "f3" to "g1", "f6" to "g8")
            var version = 0L
            repeat(2) {
                rally.forEachIndexed { index, (from, to) ->
                    val player = if (index % 2 == 0) first else second

                    assertEquals(HttpStatusCode.OK, player.move(gameId, version, from, to).status)
                    version += 1

                    assertEquals(version, watching.nextMessage().version)
                }
            }

            assertEquals(
                HttpStatusCode.OK,
                first.claimDraw(gameId, version, "THREEFOLD_REPETITION").status,
            )

            val update = watching.nextMessage()

            assertEquals(RealtimeMessage.GAME_UPDATED, update.type)
            assertEquals(version + 1, update.version)

            watching.close()
        }
    }

    @Test
    fun aRefusedCommandAnnouncesNothing() {
        withTwoClients { jordan, alex ->
            val gameId = startGame(jordan, alex)
            val (mover, waiter) = order(gameId, jordan, alex)
            val waiting = waiter.connect()

            // Out of turn: nothing was written, so there is nothing to hear about.
            assertEquals(HttpStatusCode.Conflict, waiter.move(gameId, 0, "e7", "e5").status)
            assertNull(waiting.nextMessageOrNull())

            // An illegal move by the right player is refused just as quietly.
            assertEquals(HttpStatusCode.UnprocessableEntity, mover.move(gameId, 0, "e2", "e5").status)
            assertNull(waiting.nextMessageOrNull())

            waiting.close()
        }
    }

    @Test
    fun someoneElsesGameIsNotAnnouncedToOnlookers() {
        withTwoClients { jordan, alex ->
            val gameId = startGame(jordan, alex)
            val onlooker = Client(this, "auth-onlooker")
            onlooker.claimUsername("Onlooker")
            val watching = onlooker.connect()
            val (mover, _) = order(gameId, jordan, alex)

            mover.move(gameId, 0, "e2", "e4")

            assertNull(watching.nextMessageOrNull(), "only the two players hear about their game")

            watching.close()
        }
    }

    @Test
    fun aMoveIsAcceptedWhenNobodyIsListening() {
        withTwoClients { jordan, alex ->
            val gameId = startGame(jordan, alex)
            val (mover, _) = order(gameId, jordan, alex)

            // Neither player has a socket open. The command still applies: realtime
            // delivery is a convenience, not part of accepting a move.
            assertEquals(HttpStatusCode.OK, mover.move(gameId, 0, "e2", "e4").status)
            assertEquals(1, mover.readGame(gameId).version)
        }
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L

        /** Long enough that a message would have arrived, short enough to keep tests quick. */
        const val QUIET_MILLIS = 500L
    }
}
