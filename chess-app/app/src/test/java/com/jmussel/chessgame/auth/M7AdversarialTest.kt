package com.jmussel.chessgame.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

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

    @Test
    fun failedPersistenceOfARotatedRefreshCanRetryTheParentTokenWithoutLosingTheAccount() {
        val stored = AnonymousSession("old-access", "old-refresh", "original-user", 1_000)
        val store = FailFirstWriteSessionStore(stored)
        val requests = mutableListOf<String>()
        val client =
            clientWithResponses(requests) {
                successSession("rotated-access", "rotated-refresh", "original-user")
            }
        val authenticator = AnonymousAuthenticator(client, store, now = { 1_000 })

        val persistenceFailure =
            runCatching { runBlocking { authenticator.currentSession() } }.exceptionOrNull()
        assertTrue(persistenceFailure is IOException)
        assertEquals("the last durable session remains available", stored, runBlocking { store.read() })

        val retry = runCatching { runBlocking { authenticator.currentSession() } }

        assertTrue("Supabase returns the active child when its parent token is retried", retry.isSuccess)
        assertEquals("original-user", retry.getOrNull()?.userId)
        assertEquals("original-user", runBlocking { store.read() }?.userId)
        assertEquals(listOf("/auth/v1/token", "/auth/v1/token"), requests)
    }

    @Test
    fun connectionFailureAndMalformedSuccessBothPreserveTheStoredAccount() {
        val stored = AnonymousSession("old-access", "live-refresh", "original-user", 1_000)

        val connectionStore = InMemorySessionStore(stored)
        val connectionRequests = mutableListOf<String>()
        val connectionEngine =
            MockEngine { request ->
                connectionRequests += request.url.encodedPath
                throw IOException("evaluator connection failure")
            }
        val connectionClient = authClient(connectionEngine)

        assertTrue(
            runCatching {
                runBlocking {
                    AnonymousAuthenticator(connectionClient, connectionStore, now = { 1_000 }).currentSession()
                }
            }.exceptionOrNull() is IOException,
        )
        assertEquals(stored, runBlocking { connectionStore.read() })
        assertEquals(listOf("/auth/v1/token"), connectionRequests)

        val malformedStore = InMemorySessionStore(stored)
        val malformedRequests = mutableListOf<String>()
        val malformedClient =
            clientWithResponses(malformedRequests) {
                Reply("""{"access_token":"incomplete"}""", HttpStatusCode.OK)
            }

        assertTrue(
            runCatching {
                runBlocking {
                    AnonymousAuthenticator(malformedClient, malformedStore, now = { 1_000 }).currentSession()
                }
            }.isFailure,
        )
        assertEquals(stored, runBlocking { malformedStore.read() })
        assertEquals(listOf("/auth/v1/token"), malformedRequests)
    }

    @Test
    fun rejectedRefreshFollowedByRejectedSignUpPreservesTheStoredAccount() {
        val stored = AnonymousSession("old-access", "dead-refresh", "original-user", 1_000)
        val store = InMemorySessionStore(stored)
        val requests = mutableListOf<String>()
        var responseNumber = 0
        val client =
            clientWithResponses(requests) {
                if (responseNumber++ == 0) {
                    failureResponse(HttpStatusCode.BadRequest)
                } else {
                    failureResponse(HttpStatusCode.ServiceUnavailable)
                }
            }

        val failure =
            runCatching {
                runBlocking { AnonymousAuthenticator(client, store, now = { 1_000 }).currentSession() }
            }.exceptionOrNull()

        assertTrue(failure is SupabaseAuthException)
        assertEquals(HttpStatusCode.ServiceUnavailable.value, (failure as SupabaseAuthException).status)
        assertEquals(stored, runBlocking { store.read() })
        assertEquals(listOf("/auth/v1/token", "/auth/v1/signup"), requests)
    }

    @Test
    fun simultaneousFirstCallersCreateAndPersistExactlyOneAccount() {
        val store = InMemorySessionStore()
        val requests = mutableListOf<String>()
        val engine =
            MockEngine { request ->
                requests += request.url.encodedPath
                delay(50)
                successSession("one-access", "one-refresh", "one-user").let { reply ->
                    respond(
                        content = reply.body,
                        status = reply.status,
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                }
            }
        val authenticator = AnonymousAuthenticator(authClient(engine), store, now = { 1_000 })

        val sessions =
            runBlocking {
                (1..8).map { async { authenticator.currentSession() } }.awaitAll()
            }

        assertEquals(1, requests.size)
        assertEquals(listOf("/auth/v1/signup"), requests)
        assertEquals(1, sessions.map { it.userId }.distinct().size)
        assertEquals("one-user", runBlocking { store.read() }?.userId)
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

    private fun clientWithResponses(
        requests: MutableList<String>,
        response: () -> Reply,
    ): SupabaseAuthClient {
        val engine =
            MockEngine { request ->
                requests += request.url.encodedPath
                val reply = response()
                respond(
                    content = reply.body,
                    status = reply.status,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                )
            }
        val httpClient =
            authClient(engine)
        return httpClient
    }

    private fun authClient(engine: MockEngine): SupabaseAuthClient =
        SupabaseAuthClient(
            SupabaseConfig("https://project.supabase.co", "key"),
            HttpClient(engine) {
                install(ContentNegotiation) { json(SupabaseAuthClient.Json) }
            },
        )

    private fun successSession(
        accessToken: String,
        refreshToken: String,
        userId: String,
    ) = Reply(
        body =
            """
            {
              "access_token": "$accessToken",
              "refresh_token": "$refreshToken",
              "expires_at": 5000,
              "user": { "id": "$userId" }
            }
            """.trimIndent(),
        status = HttpStatusCode.OK,
    )

    private fun failureResponse(status: HttpStatusCode) =
        Reply(
            body = """{"error":"rejected"}""",
            status = status,
        )

    private data class Reply(
        val body: String,
        val status: HttpStatusCode,
    )

    private class FailFirstWriteSessionStore(
        initial: AnonymousSession,
    ) : SessionStore {
        private var stored: AnonymousSession? = initial
        private var failNextWrite = true

        override suspend fun read(): AnonymousSession? = stored

        override suspend fun write(session: AnonymousSession) {
            if (failNextWrite) {
                failNextWrite = false
                throw IOException("evaluator transient DataStore failure")
            }
            stored = session
        }

        override suspend fun clear() {
            stored = null
        }
    }
}
