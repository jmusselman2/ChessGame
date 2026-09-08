package com.jmussel.chessgame.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Independent evaluator coverage for preserving the anonymous identity in M7. */
class M7AdversarialTest {
    @Test
    fun rateLimitedRefreshDoesNotReplaceTheStoredAnonymousAccount() {
        assertTransientRefreshPreservesIdentity(HttpStatusCode.TooManyRequests)
    }

    @Test
    fun unavailableRefreshDoesNotReplaceTheStoredAnonymousAccount() {
        assertTransientRefreshPreservesIdentity(HttpStatusCode.ServiceUnavailable)
    }

    private fun assertTransientRefreshPreservesIdentity(transientStatus: HttpStatusCode) {
        val stored = AnonymousSession("old-access", "live-refresh", "original-user", 1_000)
        val store = InMemorySessionStore(stored)
        val paths = mutableListOf<String>()
        var requestNumber = 0
        val engine =
            MockEngine { request ->
                paths += request.url.encodedPath
                requestNumber++
                if (requestNumber == 1) {
                    respond(
                        content = """{"error":"temporary outage"}""",
                        status = transientStatus,
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                } else {
                    respond(
                        content =
                            """
                            {
                              "access_token": "replacement-access",
                              "refresh_token": "replacement-refresh",
                              "expires_at": 5000,
                              "user": { "id": "replacement-user" }
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                }
            }
        val httpClient =
            HttpClient(engine) {
                install(ContentNegotiation) { json(SupabaseAuthClient.Json) }
            }
        val client = SupabaseAuthClient(SupabaseConfig("https://project.supabase.co", "key"), httpClient)
        val authenticator = AnonymousAuthenticator(client, store, now = { 1_000 })

        try {
            val failure = runCatching { runBlocking { authenticator.currentSession() } }.exceptionOrNull()

            assertEquals("a transient outage must not replace the account", stored, runBlocking { store.read() })
            assertEquals("a transient outage must not trigger sign-up", listOf("/auth/v1/token"), paths)
            assertTrue("the refresh failure should remain retryable", failure is SupabaseAuthException)
            assertEquals(transientStatus.value, (failure as SupabaseAuthException).status)
        } finally {
            httpClient.close()
        }
    }
}
