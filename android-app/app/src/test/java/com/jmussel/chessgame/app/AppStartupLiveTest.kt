package com.jmussel.chessgame.app

import com.jmussel.chessgame.api.ChessApiClient
import com.jmussel.chessgame.api.ChessServerConfig
import com.jmussel.chessgame.auth.AnonymousAuthenticator
import com.jmussel.chessgame.auth.InMemorySessionStore
import com.jmussel.chessgame.auth.SupabaseAuthClient
import com.jmussel.chessgame.auth.SupabaseConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Startup against the real Supabase development project.
 *
 * `AppStartupTest` proves the logic over `MockEngine`; this proves the configured build
 * really does get a session from the project it is pointed at. The Chess server half is
 * stubbed, because a unit test has no server to talk to — the identity call itself is
 * covered by the server's own `IdentityRouteTest` and by `ChessApiClientTest`. It runs only
 * when `SUPABASE_URL` and `SUPABASE_ANON_KEY` are set, so it is a no-op on a machine (or in
 * CI) without the publishable key. Each run leaves one throwaway anonymous user in the
 * development project.
 */
class AppStartupLiveTest {
    private val config: SupabaseConfig? =
        System.getenv("SUPABASE_URL")?.takeIf { it.isNotBlank() }?.let { url ->
            System
                .getenv("SUPABASE_ANON_KEY")
                ?.takeIf { it.isNotBlank() }
                ?.let { key -> SupabaseConfig(url, key) }
        }

    /** What the app sends to the Chess server, recorded so the token can be checked. */
    private val tokens = mutableListOf<String>()

    private fun withLiveStartup(block: (AppStartup, InMemorySessionStore) -> Unit) {
        val usable = config ?: return
        val supabaseHttpClient: HttpClient = SupabaseAuthClient.defaultHttpClient()
        val stubbedServer =
            HttpClient(
                MockEngine { request ->
                    tokens += request.headers["Authorization"].orEmpty()
                    respond(
                        content = """{"userId":"server-1"}""",
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                },
            ) { install(ContentNegotiation) { json(ChessApiClient.Json) } }

        val store = InMemorySessionStore()
        try {
            val authenticator =
                AnonymousAuthenticator(SupabaseAuthClient(usable, supabaseHttpClient), store)
            val chessApi =
                ChessApiClient(ChessServerConfig("https://chess.example"), stubbedServer) {
                    authenticator.currentSession().accessToken
                }

            block(AppStartup(usable, authenticator, chessApi), store)
        } finally {
            supabaseHttpClient.close()
            stubbedServer.close()
        }
    }

    @Test
    fun aConfiguredBuildStartsUpWithARealAnonymousSession() {
        withLiveStartup { startup, store ->
            val state = runBlocking { startup.run() }

            assertTrue("startup should end with an identity, not $state", state is StartupState.Ready)

            val session = runBlocking { store.read() }
            assertTrue("a real session was stored", session?.accessToken.orEmpty().isNotBlank())
            assertEquals("that session's token is what the server is called with", "Bearer ${session?.accessToken}", tokens.last())
        }
    }

    @Test
    fun theSecondStartupRestoresTheSameAccountRatherThanCreatingAnother() {
        withLiveStartup { startup, store ->
            runBlocking { startup.run() }
            val first = runBlocking { store.read() }

            runBlocking { startup.run() }

            assertEquals("the same anonymous account", first?.userId, runBlocking { store.read() }?.userId)
        }
    }
}
