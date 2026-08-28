package com.jmussel.chessgame.app

import com.jmussel.chessgame.auth.AnonymousAuthenticator
import com.jmussel.chessgame.auth.InMemorySessionStore
import com.jmussel.chessgame.auth.SupabaseAuthClient
import com.jmussel.chessgame.auth.SupabaseConfig
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Startup against the real Supabase development project.
 *
 * `AppStartupTest` proves the logic over `MockEngine`; this proves the configured build
 * really does get a session from the project it is pointed at. It runs only when
 * `SUPABASE_URL` and `SUPABASE_ANON_KEY` are set, so it is a no-op on a machine (or in CI)
 * without the publishable key. Each run leaves one throwaway anonymous user in the
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

    private fun withLiveStartup(block: (AppStartup, InMemorySessionStore) -> Unit) {
        val usable = config ?: return
        val httpClient: HttpClient = SupabaseAuthClient.defaultHttpClient()
        val store = InMemorySessionStore()
        try {
            val authenticator = AnonymousAuthenticator(SupabaseAuthClient(usable, httpClient), store)
            block(AppStartup(usable, authenticator), store)
        } finally {
            httpClient.close()
        }
    }

    @Test
    fun aConfiguredBuildStartsUpWithARealAnonymousSession() {
        withLiveStartup { startup, store ->
            val state = runBlocking { startup.run() }

            assertTrue("startup should end with a session, not $state", state is StartupState.Ready)
            assertEquals(
                "the session that was stored is the one startup reported",
                (state as StartupState.Ready).userId,
                runBlocking { store.read() }?.userId,
            )
        }
    }

    @Test
    fun theSecondStartupRestoresTheSameAccountRatherThanCreatingAnother() {
        withLiveStartup { startup, _ ->
            val first = runBlocking { startup.run() }
            val second = runBlocking { startup.run() }

            assertEquals(first, second)
        }
    }
}
