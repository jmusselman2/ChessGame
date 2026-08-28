@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.realtime

import com.jmussel.chessgame.server.api.CurrentUser
import com.jmussel.chessgame.server.api.DashboardEntry
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
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Shared by the realtime tests, which all need two people playing over HTTP and a socket. */
internal val fixtureJson = Json { ignoreUnknownKeys = true }

/** One person: an HTTP client, a token, and a realtime connection when they open one. */
internal class PlayerClient(
    private val builder: ApplicationTestBuilder,
    private val tokens: TestTokens,
    private val subject: String,
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
        fixtureJson.decodeFromString(
            builder.client
                .post("/series") {
                    authorized()
                    setBody(friend)
                }.bodyAsText(),
        )

    /** The internal id the server knows this player by. */
    suspend fun userId(): Uuid =
        Uuid.parse(
            fixtureJson
                .decodeFromString<CurrentUser>(
                    builder.client
                        .get("/me") { authorized() }
                        .bodyAsText(),
                ).userId,
        )

    suspend fun dashboard(): List<DashboardEntry> =
        fixtureJson.decodeFromString(
            builder.client
                .get("/dashboard") { authorized() }
                .bodyAsText(),
        )

    suspend fun readGame(gameId: String): GameView =
        fixtureJson.decodeFromString(
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

/** The next pushed message, failing the test if none arrives. */
internal suspend fun WebSocketSession.nextMessage(): RealtimeMessage =
    withTimeout(WAIT_MILLIS) {
        fixtureJson.decodeFromString((incoming.receive() as Frame.Text).readText())
    }

/** Null when nothing arrives, so "was told nothing" is a real assertion. */
internal suspend fun WebSocketSession.nextMessageOrNull(): RealtimeMessage? =
    withTimeoutOrNull(QUIET_MILLIS) {
        fixtureJson.decodeFromString<RealtimeMessage>((incoming.receive() as Frame.Text).readText())
    }

/** The whole server over one test database, with two signed-in people. */
internal fun withTwoPlayers(
    tokens: TestTokens,
    block: suspend ApplicationTestBuilder.(PlayerClient, PlayerClient, RealtimeHub) -> Unit,
) = DatabaseTestSupport.withMigratedDatabase { dataSource ->
    val database = Databases.connect(dataSource)
    val hub = RealtimeHub()

    testApplication {
        application { testModule(tokens.verifier(), database, realtime = hub) }
        block(
            PlayerClient(this, tokens, "auth-jordan"),
            PlayerClient(this, tokens, "auth-alex"),
            hub,
        )
    }
}

/** Both sign in, become friends, and open the series; returns the game id. */
internal suspend fun startGame(
    jordan: PlayerClient,
    alex: PlayerClient,
): String {
    jordan.claimUsername("Jordan")
    alex.claimUsername("Alex")
    jordan.addFriend("Alex")

    return requireNotNull(jordan.openSeries("Alex").currentGameId) {
        "opening a series starts its first game"
    }
}

/** Whoever is to move first, then the other one. Colours are random (`D014`). */
internal suspend fun order(
    gameId: String,
    jordan: PlayerClient,
    alex: PlayerClient,
) = if (jordan.readGame(gameId).yourTurn) jordan to alex else alex to jordan

internal const val WAIT_MILLIS = 5_000L

/** Long enough that a message would have arrived, short enough to keep tests quick. */
internal const val QUIET_MILLIS = 500L
