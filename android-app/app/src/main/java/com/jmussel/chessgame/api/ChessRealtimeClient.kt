package com.jmussel.chessgame.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.Serializable

/**
 * What the server pushes down the realtime connection.
 *
 * Never state: a `game-updated` says which game changed and which version it reached, and
 * the canonical state is fetched over HTTPS (`D022`). [version] is a fact about the game,
 * not the game.
 */
@Serializable
data class RealtimeMessageDto(
    val type: String,
    val gameId: String? = null,
    val version: Long? = null,
) {
    companion object {
        /** Sent once when a connection is accepted, so a client knows it is live. */
        const val CONNECTED: String = "connected"

        /** Sent when a game the client plays in has changed. */
        const val GAME_UPDATED: String = "game-updated"
    }
}

/**
 * Where realtime messages come from.
 *
 * An interface with two implementations and no third planned: the real socket, and a
 * source a test can drive. Without it, testing what the app does with a message would mean
 * standing up a WebSocket server in a unit test.
 */
fun interface RealtimeSource {
    /**
     * Messages from one connection, ending when that connection does.
     *
     * Collecting connects; cancelling the collection closes the connection. The flow
     * completing means the connection dropped, which is the caller's cue to reconnect.
     */
    fun messages(): Flow<RealtimeMessageDto>
}

/**
 * The app's realtime connection to the Chess server.
 *
 * One socket carries updates for every game the player is in. Nothing is decided here and
 * nothing on it is trusted as state: a message says a game changed, and the app reloads
 * that game over HTTPS (`D022`). The access token is asked for at connect time through the
 * same provider the HTTP calls use, so a socket opened hours into a session carries a
 * current token (`D006`).
 */
class ChessRealtimeClient(
    private val config: ChessServerConfig,
    private val httpClient: HttpClient,
    private val accessToken: suspend () -> String,
) : RealtimeSource {
    override fun messages(): Flow<RealtimeMessageDto> =
        channelFlow {
            // Asked for before the socket opens, so a connection made hours into a session
            // carries a current token rather than the one the app started with.
            val token = accessToken()

            httpClient.webSocket(
                urlString = config.webSocketUrl(PATH),
                request = { header(HttpHeaders.Authorization, "Bearer $token") },
            ) {
                for (frame in incoming) {
                    val text = (frame as? Frame.Text)?.readText() ?: continue
                    val message = runCatching { ChessApiClient.Json.decodeFromString<RealtimeMessageDto>(text) }

                    // A frame this app cannot read is not worth dropping the connection for:
                    // a newer server may send message types it has never heard of.
                    message.getOrNull()?.let { send(it) }
                }
            }
        }

    private companion object {
        const val PATH = "/ws"
    }
}
