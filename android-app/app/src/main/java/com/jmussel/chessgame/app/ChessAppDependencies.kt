package com.jmussel.chessgame.app

import android.content.Context
import com.jmussel.chessgame.BuildConfig
import com.jmussel.chessgame.api.ChessApiClient
import com.jmussel.chessgame.api.ChessRealtimeClient
import com.jmussel.chessgame.api.ChessServerConfig
import com.jmussel.chessgame.api.RealtimeSource
import com.jmussel.chessgame.api.ServerWakePolicy
import com.jmussel.chessgame.auth.AnonymousAuthenticator
import com.jmussel.chessgame.auth.DataStoreSessionStore
import com.jmussel.chessgame.auth.SessionStore
import com.jmussel.chessgame.auth.SupabaseAuthClient
import com.jmussel.chessgame.auth.SupabaseConfig
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json

/**
 * The long-lived objects the screens are built from: one HTTP client, the anonymous
 * session, and the connection to the Chess server.
 *
 * They are made once and handed down, never built inside a composable, because a client
 * created during composition would be created again on every recomposition and never
 * closed. [ChessAppViewModel] owns an instance and closes it when the app's state is
 * discarded; the realtime client added in `M14.12` belongs here too.
 *
 * Both APIs this app talks to are JSON read leniently, so a single [HttpClient] serves
 * both — Supabase auth and the Chess server (`D004`, `D006`).
 */
class ChessAppDependencies(
    val serverConfig: ChessServerConfig,
    val supabaseConfig: SupabaseConfig,
    private val httpClient: HttpClient,
    sessionStore: SessionStore,
    realtime: RealtimeSource? = null,
    wakePolicy: ServerWakePolicy = ServerWakePolicy(),
) : AutoCloseable {
    /** Keeps one anonymous account alive across launches (`D006`). */
    val authenticator: AnonymousAuthenticator =
        AnonymousAuthenticator(
            client = SupabaseAuthClient(supabaseConfig, httpClient),
            store = sessionStore,
        )

    /**
     * The access token to send, asked for per call rather than captured.
     *
     * One provider for everything that authenticates: the HTTP client uses it now and the
     * WebSocket client (`M14.12`) uses the same one, so a token that nears expiry hours
     * into a session is refreshed underneath both without rebuilding anything.
     */
    val accessToken: suspend () -> String = { authenticator.currentSession().accessToken }

    /** The Chess server, which is authoritative for every game (`D004`). */
    val chessApi: ChessApiClient =
        ChessApiClient(
            config = serverConfig,
            httpClient = httpClient,
            accessToken = accessToken,
        )

    /** Restores or creates the session, then asks the server who it belongs to (`M14.6`). */
    val startup: AppStartup = AppStartup(supabaseConfig, authenticator, chessApi, wakePolicy)

    /**
     * Where realtime updates come from.
     *
     * The same client and the same token provider as everything else: a message on this
     * socket is only ever a nudge to reload over HTTPS (`D022`). A test supplies its own
     * source, so what the app does with a message is checked without a socket.
     */
    val realtime: RealtimeSource = realtime ?: ChessRealtimeClient(serverConfig, httpClient, accessToken)

    /** Releases the HTTP client and the connections it is holding. */
    override fun close() {
        httpClient.close()
    }

    companion object {
        /**
         * The real dependencies, reading the Supabase and Chess server configuration baked
         * in at build time.
         *
         * Takes the application context, never an `Activity`, so nothing here keeps a
         * destroyed screen alive.
         */
        fun create(context: Context): ChessAppDependencies =
            ChessAppDependencies(
                // Only a debug build may talk to a development server in the clear, which is
                // what its network security configuration permits and no other build's does
                // (`D033`). Passing the same fact here turns a beta built without
                // `-PchessServerUrl` into an immediate, named failure rather than every
                // request failing for an unexplained reason (`M15.4`).
                serverConfig =
                    ChessServerConfig(
                        baseUrl = BuildConfig.CHESS_SERVER_URL,
                        allowCleartext = BuildConfig.DEBUG,
                    ),
                supabaseConfig = SupabaseConfig(url = BuildConfig.SUPABASE_URL, anonKey = BuildConfig.SUPABASE_ANON_KEY),
                httpClient = defaultHttpClient(),
                sessionStore = DataStoreSessionStore(context.applicationContext),
            )

        /** One client for both APIs and the socket, lenient about fields it does not know. */
        fun defaultHttpClient(): HttpClient =
            HttpClient {
                install(ContentNegotiation) { json(ChessApiClient.Json) }
                install(WebSockets)
            }
    }
}
