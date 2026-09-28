package com.jmussel.chessgame.auth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The same client against the real Supabase development project.
 *
 * MockEngine proves the logic; this proves the contract — that Supabase really does hand
 * out anonymous sessions in the shape this app parses. It runs only when
 * `SUPABASE_URL` and `SUPABASE_ANON_KEY` are set, so it is a no-op on a machine (or in CI)
 * without the publishable key. Each run leaves one throwaway anonymous user in the
 * development project.
 */
class SupabaseLiveAuthTest {
    private val config: SupabaseConfig? =
        System.getenv("SUPABASE_URL")?.takeIf { it.isNotBlank() }?.let { url ->
            System
                .getenv("SUPABASE_ANON_KEY")
                ?.takeIf { it.isNotBlank() }
                ?.let { key -> SupabaseConfig(url, key) }
        }

    private fun withLiveClient(block: (SupabaseAuthClient) -> Unit) {
        val usable = config ?: return
        val httpClient = SupabaseAuthClient.defaultHttpClient()
        try {
            block(SupabaseAuthClient(usable, httpClient))
        } finally {
            httpClient.close()
        }
    }

    @Test
    fun supabaseCreatesAnAnonymousSession() {
        withLiveClient { client ->
            val created = runBlocking { client.signInAnonymously() }

            assertTrue(created.accessToken.isNotBlank())
            assertTrue(created.refreshToken.isNotBlank())
            assertNotNull(created.userId)
            assertTrue("the token should not be expired already", created.expiresAtEpochSeconds > 0)
        }
    }

    @Test
    fun supabaseRefreshesThatSessionIntoANewToken() {
        withLiveClient { client ->
            val created = runBlocking { client.signInAnonymously() }
            val refreshed = runBlocking { client.refresh(created.refreshToken) }

            assertEquals("the refreshed session is the same account", created.userId, refreshed.userId)
            assertTrue(refreshed.accessToken.isNotBlank())
        }
    }

    @Test
    fun aRestoredSessionKeepsWorkingAcrossAnAuthenticator() {
        withLiveClient { client ->
            val store = InMemorySessionStore()
            val first = runBlocking { AnonymousAuthenticator(client, store).currentSession() }

            // A second authenticator over the same store is the next app launch.
            val restored = runBlocking { AnonymousAuthenticator(client, store).currentSession() }

            assertEquals(first, restored)
            assertEquals(first.userId, runBlocking { store.read() }?.userId)
        }
    }
}
