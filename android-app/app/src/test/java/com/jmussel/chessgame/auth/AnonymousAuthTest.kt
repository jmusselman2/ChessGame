package com.jmussel.chessgame.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnonymousAuthTest {
    private val config = SupabaseConfig(url = "https://project.supabase.co", anonKey = "publishable-key")

    /** One canned Supabase reply. */
    private data class Reply(
        val status: HttpStatusCode,
        val body: String,
    )

    /** Records every request so a test can assert what was, and was not, called. */
    private class Recorder {
        val requests = mutableListOf<HttpRequestData>()

        val paths: List<String>
            get() = requests.map { it.url.encodedPath }
    }

    private fun session(
        accessToken: String,
        refreshToken: String,
        expiresAt: Long,
        userId: String = "user-1",
    ) = Reply(
        HttpStatusCode.OK,
        """
        {
          "access_token": "$accessToken",
          "refresh_token": "$refreshToken",
          "expires_in": 3600,
          "expires_at": $expiresAt,
          "token_type": "bearer",
          "user": { "id": "$userId", "is_anonymous": true, "role": "authenticated" }
        }
        """.trimIndent(),
    )

    private fun failure(status: HttpStatusCode) = Reply(status, """{"error":"nope"}""")

    private fun clientReplying(
        recorder: Recorder,
        vararg replies: Reply,
    ): SupabaseAuthClient {
        var index = 0
        val engine =
            MockEngine { request ->
                recorder.requests += request
                val reply = replies[minOf(index, replies.size - 1)]
                index++
                respond(
                    content = reply.body,
                    status = reply.status,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                )
            }

        return SupabaseAuthClient(
            config = config,
            httpClient =
                HttpClient(engine) {
                    install(ContentNegotiation) { json(SupabaseAuthClient.Json) }
                },
        )
    }

    @Test
    fun aFirstRunCreatesAnAnonymousSession() {
        val recorder = Recorder()
        val client = clientReplying(recorder, session("access-1", "refresh-1", expiresAt = 5_000))
        val authenticator = AnonymousAuthenticator(client, InMemorySessionStore(), now = { 1_000 })

        val created = runBlocking { authenticator.currentSession() }

        assertEquals("access-1", created.accessToken)
        assertEquals("refresh-1", created.refreshToken)
        assertEquals("user-1", created.userId)
        assertEquals(listOf("/auth/v1/signup"), recorder.paths)
    }

    @Test
    fun theSessionIsStoredSoItSurvivesARestart() {
        val recorder = Recorder()
        val client = clientReplying(recorder, session("access-1", "refresh-1", expiresAt = 5_000))
        val store = InMemorySessionStore()

        runBlocking { AnonymousAuthenticator(client, store, now = { 1_000 }).currentSession() }

        // A new authenticator over the same store is what the next app launch looks like.
        val afterRestart = runBlocking { AnonymousAuthenticator(client, store, now = { 1_000 }).currentSession() }

        assertEquals("access-1", afterRestart.accessToken)
        assertEquals("restoring a valid session must not call Supabase", 1, recorder.requests.size)
    }

    @Test
    fun aStoredSessionIsReadableWithoutTheNetwork() {
        val stored = AnonymousSession("access-1", "refresh-1", "user-1", expiresAtEpochSeconds = 5_000)
        val recorder = Recorder()
        val client = clientReplying(recorder, session("unused", "unused", expiresAt = 9_000))
        val authenticator = AnonymousAuthenticator(client, InMemorySessionStore(stored), now = { 1_000 })

        assertEquals(stored, runBlocking { authenticator.storedSession() })
        assertTrue(recorder.requests.isEmpty())
    }

    @Test
    fun anExpiringSessionIsRefreshed() {
        val stored = AnonymousSession("old-access", "old-refresh", "user-1", expiresAtEpochSeconds = 1_030)
        val recorder = Recorder()
        val client = clientReplying(recorder, session("new-access", "new-refresh", expiresAt = 5_000))
        val store = InMemorySessionStore(stored)
        val authenticator = AnonymousAuthenticator(client, store, now = { 1_000 })

        val refreshed = runBlocking { authenticator.currentSession() }

        assertEquals("new-access", refreshed.accessToken)
        assertEquals(listOf("/auth/v1/token"), recorder.paths)
        assertEquals(
            "the refreshed session replaces the stored one",
            refreshed,
            runBlocking { store.read() },
        )
    }

    @Test
    fun aRejectedRefreshStartsANewAnonymousAccount() {
        val stored = AnonymousSession("old-access", "dead-refresh", "user-1", expiresAtEpochSeconds = 1_000)
        val recorder = Recorder()
        val client =
            clientReplying(
                recorder,
                failure(HttpStatusCode.BadRequest),
                session("fresh-access", "fresh-refresh", expiresAt = 5_000, userId = "user-2"),
            )
        val authenticator = AnonymousAuthenticator(client, InMemorySessionStore(stored), now = { 1_000 })

        val created = runBlocking { authenticator.currentSession() }

        assertEquals("user-2", created.userId)
        assertEquals(listOf("/auth/v1/token", "/auth/v1/signup"), recorder.paths)
    }

    @Test
    fun theKeyIsSentAsTheApiKeyHeader() {
        val recorder = Recorder()
        val client = clientReplying(recorder, session("access-1", "refresh-1", expiresAt = 5_000))

        runBlocking { client.signInAnonymously() }

        assertEquals("publishable-key", recorder.requests.single().headers["apikey"])
    }

    @Test
    fun aRefusedSignInIsReported() {
        val recorder = Recorder()
        val client = clientReplying(recorder, failure(HttpStatusCode.UnprocessableEntity))

        val thrown = runCatching { runBlocking { client.signInAnonymously() } }.exceptionOrNull()

        assertTrue(thrown is SupabaseAuthException)
        assertEquals(422, (thrown as SupabaseAuthException).status)
    }

    @Test
    fun signingOutForgetsTheSession() {
        val stored = AnonymousSession("access-1", "refresh-1", "user-1", expiresAtEpochSeconds = 5_000)
        val store = InMemorySessionStore(stored)
        val recorder = Recorder()
        val client = clientReplying(recorder, session("access-2", "refresh-2", expiresAt = 9_000))
        val authenticator = AnonymousAuthenticator(client, store, now = { 1_000 })

        runBlocking { authenticator.signOut() }

        assertNull(runBlocking { store.read() })
        assertNotNull(runBlocking { authenticator.currentSession() })
    }

    @Test
    fun aTokenIsRefreshedBeforeItActuallyExpires() {
        val stored = AnonymousSession("a", "r", "user-1", expiresAtEpochSeconds = 1_000)

        assertFalse(stored.needsRefresh(nowEpochSeconds = 900))
        assertTrue(stored.needsRefresh(nowEpochSeconds = 940))
        assertTrue(stored.needsRefresh(nowEpochSeconds = 1_000))
        assertEquals(60L, AnonymousSession.REFRESH_MARGIN_SECONDS)
    }

    @Test
    fun theConfigNeverPrintsTheKey() {
        assertFalse(config.toString().contains("publishable-key"))
        assertTrue(config.isUsable)
        assertFalse(SupabaseConfig(url = "https://project.supabase.co", anonKey = "").isUsable)
    }
}
