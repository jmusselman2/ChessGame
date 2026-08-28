package com.jmussel.chessgame.app

import android.content.Context
import com.jmussel.chessgame.BuildConfig
import com.jmussel.chessgame.api.ChessApiClient
import com.jmussel.chessgame.api.ChessServerConfig
import com.jmussel.chessgame.auth.AnonymousAuthenticator
import com.jmussel.chessgame.auth.DataStoreSessionStore
import com.jmussel.chessgame.auth.SessionStore
import com.jmussel.chessgame.auth.SupabaseAuthClient
import com.jmussel.chessgame.auth.SupabaseConfig
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
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
) : AutoCloseable {
    /** Keeps one anonymous account alive across launches (`D006`). */
    val authenticator: AnonymousAuthenticator =
        AnonymousAuthenticator(
            client = SupabaseAuthClient(supabaseConfig, httpClient),
            store = sessionStore,
        )

    /**
     * The Chess server, asked for the access token per request.
     *
     * The token is fetched through [authenticator] rather than captured, so a refresh part
     * way through a session is picked up without rebuilding anything.
     */
    val chessApi: ChessApiClient =
        ChessApiClient(
            config = serverConfig,
            httpClient = httpClient,
        ) { authenticator.currentSession().accessToken }

    /** Releases the HTTP client and the connections it is holding. */
    override fun close() {
        httpClient.close()
    }

    companion object {
        /**
         * The real dependencies, reading the Supabase configuration baked in at build time.
         *
         * Takes the application context, never an `Activity`, so nothing here keeps a
         * destroyed screen alive.
         */
        fun create(context: Context): ChessAppDependencies =
            ChessAppDependencies(
                serverConfig = ChessServerConfig(),
                supabaseConfig = SupabaseConfig(url = BuildConfig.SUPABASE_URL, anonKey = BuildConfig.SUPABASE_ANON_KEY),
                httpClient = defaultHttpClient(),
                sessionStore = DataStoreSessionStore(context.applicationContext),
            )

        /** One client for both APIs, lenient about fields it does not know. */
        fun defaultHttpClient(): HttpClient =
            HttpClient {
                install(ContentNegotiation) { json(ChessApiClient.Json) }
            }
    }
}
