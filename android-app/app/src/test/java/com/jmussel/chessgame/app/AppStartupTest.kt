package com.jmussel.chessgame.app

import com.jmussel.chessgame.auth.AnonymousAuthenticator
import com.jmussel.chessgame.auth.AnonymousSession
import com.jmussel.chessgame.auth.InMemorySessionStore
import com.jmussel.chessgame.auth.SessionStore
import com.jmussel.chessgame.auth.SupabaseAuthClient
import com.jmussel.chessgame.auth.SupabaseConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The first thing the app does: get a session, or say why it could not.
 *
 * Runs on the JVM against Ktor's `MockEngine`, so there is no Android runtime and no
 * network. Restoring, refreshing, and creating are `AnonymousAuthenticator`'s decisions and
 * are tested in `AnonymousAuthTest`; what is checked here is what startup makes of each
 * outcome.
 */
class AppStartupTest {
    private val config = SupabaseConfig(url = "https://project.supabase.co", anonKey = "publishable-key")

    private val paths = mutableListOf<String>()

    private fun storedSession(expiresAt: Long) =
        AnonymousSession(
            accessToken = "access-1",
            refreshToken = "refresh-1",
            userId = "user-1",
            expiresAtEpochSeconds = expiresAt,
        )

    private fun sessionBody(userId: String) =
        """
        {
          "access_token": "access-2",
          "refresh_token": "refresh-2",
          "expires_in": 3600,
          "expires_at": 5000,
          "token_type": "bearer",
          "user": { "id": "$userId", "is_anonymous": true }
        }
        """.trimIndent()

    private fun startup(
        store: SessionStore = InMemorySessionStore(),
        supabaseConfig: SupabaseConfig = config,
        status: HttpStatusCode = HttpStatusCode.OK,
        newUserId: String = "user-2",
        failWith: Throwable? = null,
    ): AppStartup {
        val engine =
            MockEngine { request ->
                paths += request.url.encodedPath
                failWith?.let { throw it }
                respond(
                    content = if (status.isSuccess()) sessionBody(newUserId) else """{"error":"nope"}""",
                    status = status,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                )
            }

        val client =
            SupabaseAuthClient(
                config = supabaseConfig,
                httpClient = HttpClient(engine) { install(ContentNegotiation) { json(SupabaseAuthClient.Json) } },
            )

        return AppStartup(
            supabaseConfig = supabaseConfig,
            authenticator = AnonymousAuthenticator(client, store, now = { 1_000 }),
        )
    }

    @Test
    fun aStoredSessionIsRestoredWithoutSigningInAgain() {
        val startup = startup(store = InMemorySessionStore(storedSession(expiresAt = 5_000)))

        val state = runBlocking { startup.run() }

        assertEquals(StartupState.Ready("user-1"), state)
        assertTrue("restoring a valid session must not call Supabase", paths.isEmpty())
    }

    @Test
    fun anAbsentSessionCreatesAnAnonymousAccount() {
        val store = InMemorySessionStore()
        val startup = startup(store = store, newUserId = "user-2")

        val state = runBlocking { startup.run() }

        assertEquals(StartupState.Ready("user-2"), state)
        assertEquals(listOf("/auth/v1/signup"), paths)
        assertNotNull("the new session is kept for the next launch", runBlocking { store.read() })
    }

    @Test
    fun anExpiringSessionIsRefreshedRatherThanReplaced() {
        val startup = startup(store = InMemorySessionStore(storedSession(expiresAt = 1_030)), newUserId = "user-1")

        val state = runBlocking { startup.run() }

        assertEquals(StartupState.Ready("user-1"), state)
        assertEquals(listOf("/auth/v1/token"), paths)
    }

    @Test
    fun aDeadRefreshTokenEndsWithANewAccountRatherThanAFailure() {
        // The refresh is refused, so the authenticator signs up again; both calls happen.
        val store = InMemorySessionStore(storedSession(expiresAt = 1_000))
        var call = 0
        val engine =
            MockEngine { request ->
                paths += request.url.encodedPath
                call++
                respond(
                    content = if (call == 1) """{"error":"nope"}""" else sessionBody("user-3"),
                    status = if (call == 1) HttpStatusCode.BadRequest else HttpStatusCode.OK,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                )
            }
        val startup =
            AppStartup(
                supabaseConfig = config,
                authenticator =
                    AnonymousAuthenticator(
                        SupabaseAuthClient(
                            config,
                            HttpClient(engine) { install(ContentNegotiation) { json(SupabaseAuthClient.Json) } },
                        ),
                        store,
                        now = { 1_000 },
                    ),
            )

        assertEquals(StartupState.Ready("user-3"), runBlocking { startup.run() })
        assertEquals(listOf("/auth/v1/token", "/auth/v1/signup"), paths)
    }

    @Test
    fun aRefusedSignInCanBeTriedAgain() {
        val startup = startup(status = HttpStatusCode.TooManyRequests)

        val state = runBlocking { startup.run() } as StartupState.Failed

        assertTrue(state.canRetry)
        assertTrue("the status is what the player is told", state.message.contains("429"))
    }

    @Test
    fun anUnreachableServiceCanBeTriedAgain() {
        val startup = startup(failWith = IOException("no route to host"))

        val state = runBlocking { startup.run() } as StartupState.Failed

        assertTrue(state.canRetry)
        assertTrue(state.message.contains("connection"))
    }

    @Test
    fun aBuildWithoutASupabaseKeyExplainsItselfAndOffersNoRetry() {
        val startup = startup(supabaseConfig = SupabaseConfig(url = "https://project.supabase.co", anonKey = ""))

        val state = runBlocking { startup.run() } as StartupState.Failed

        assertFalse("trying again cannot fix a build", state.canRetry)
        assertTrue(state.message.contains("SUPABASE_ANON_KEY"))
        assertTrue("a build that cannot sign in must not try", paths.isEmpty())
    }

    @Test
    fun noFailureQuotesATokenOrTheKey() {
        val refused = runBlocking { startup(status = HttpStatusCode.Unauthorized).run() } as StartupState.Failed
        val unreachable = runBlocking { startup(failWith = IOException("boom")).run() } as StartupState.Failed
        val misconfigured =
            runBlocking {
                startup(supabaseConfig = SupabaseConfig(url = "https://project.supabase.co", anonKey = "")).run()
            } as StartupState.Failed

        listOf(refused, unreachable, misconfigured).forEach { failure ->
            listOf("publishable-key", "access-1", "refresh-1", "access-2", "refresh-2").forEach { secret ->
                assertFalse("$secret must not appear in \"${failure.message}\"", failure.message.contains(secret))
            }
        }
    }

    @Test
    fun theSessionIsReadyBeforeAnythingAuthenticatedIsCalled() {
        val store = InMemorySessionStore()
        val startup = startup(store = store)

        val state = runBlocking { startup.run() }

        assertTrue(state is StartupState.Ready)
        assertEquals("access-2", runBlocking { store.read() }?.accessToken)
    }
}
