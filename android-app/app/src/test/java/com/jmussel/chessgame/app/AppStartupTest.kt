package com.jmussel.chessgame.app

import com.jmussel.chessgame.api.ChessApiClient
import com.jmussel.chessgame.api.ChessServerConfig
import com.jmussel.chessgame.api.CurrentUserDto
import com.jmussel.chessgame.api.ServerWakePolicy
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
 * The first thing the app does: get a session, ask the server who it belongs to, or say
 * why it could not.
 *
 * Runs on the JVM against Ktor's `MockEngine`, so there is no Android runtime and no
 * network. Restoring, refreshing, and creating the session are `AnonymousAuthenticator`'s
 * decisions and are tested in `AnonymousAuthTest`; what is checked here is the order of the
 * two questions and what startup makes of each outcome.
 */
class AppStartupTest {
    private val config = SupabaseConfig(url = "https://project.supabase.co", anonKey = "publishable-key")

    private val paths = mutableListOf<String>()

    /**
     * The wake policy for tests that are not about waking.
     *
     * These run under `runBlocking` with real delays, so the shipped policy — which waits
     * out a minute-long cold start on purpose (`M15.4`) — would make every unreachable-server
     * case take that long. A few milliseconds exercises the same decisions.
     */
    private val impatient =
        ServerWakePolicy(deadlineMillis = 30, initialDelayMillis = 1, maxDelayMillis = 4)

    private fun storedSession(expiresAt: Long) =
        AnonymousSession(
            accessToken = "access-1",
            refreshToken = "refresh-1",
            userId = "subject-1",
            expiresAtEpochSeconds = expiresAt,
        )

    private fun sessionBody(subject: String) =
        """
        {
          "access_token": "access-2",
          "refresh_token": "refresh-2",
          "expires_in": 3600,
          "expires_at": 5000,
          "token_type": "bearer",
          "user": { "id": "$subject", "is_anonymous": true }
        }
        """.trimIndent()

    private fun identityBody(username: String?) =
        if (username == null) {
            """{"userId":"server-1"}"""
        } else {
            """{"userId":"server-1","username":"$username"}"""
        }

    /**
     * Startup over one engine that answers both APIs, exactly as the app has one client for
     * both.
     */
    private fun startup(
        store: SessionStore = InMemorySessionStore(),
        supabaseConfig: SupabaseConfig = config,
        signInStatus: HttpStatusCode = HttpStatusCode.OK,
        identityStatus: HttpStatusCode = HttpStatusCode.OK,
        subject: String = "subject-2",
        username: String? = null,
        failWith: Throwable? = null,
        wakePolicy: ServerWakePolicy = impatient,
    ): AppStartup {
        val engine =
            MockEngine { request ->
                val path = request.url.encodedPath
                paths += path
                failWith?.let { throw it }

                val signIn = path.startsWith("/auth/")
                val status = if (signIn) signInStatus else identityStatus
                val body =
                    when {
                        !status.isSuccess() -> """{"error":"nope"}"""
                        signIn -> sessionBody(subject)
                        else -> identityBody(username)
                    }

                respond(
                    content = body,
                    status = status,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                )
            }

        val httpClient = HttpClient(engine) { install(ContentNegotiation) { json(ChessApiClient.Json) } }
        val authenticator =
            AnonymousAuthenticator(SupabaseAuthClient(supabaseConfig, httpClient), store, now = { 1_000 })

        return AppStartup(
            supabaseConfig = supabaseConfig,
            authenticator = authenticator,
            chessApi =
                ChessApiClient(ChessServerConfig("https://chess.example"), httpClient) {
                    authenticator.currentSession().accessToken
                },
            wakePolicy = wakePolicy,
        )
    }

    @Test
    fun aStoredSessionIsRestoredWithoutSigningInAgain() {
        val startup = startup(store = InMemorySessionStore(storedSession(expiresAt = 5_000)))

        val state = runBlocking { startup.run() }

        assertEquals(StartupState.Ready(CurrentUserDto(userId = "server-1")), state)
        assertEquals("restoring a valid session must not call Supabase", listOf("/me"), paths)
    }

    @Test
    fun anAbsentSessionCreatesAnAnonymousAccount() {
        val store = InMemorySessionStore()
        val startup = startup(store = store)

        val state = runBlocking { startup.run() }

        assertTrue(state is StartupState.Ready)
        assertEquals(listOf("/auth/v1/signup", "/me"), paths)
        assertNotNull("the new session is kept for the next launch", runBlocking { store.read() })
    }

    @Test
    fun anExpiringSessionIsRefreshedRatherThanReplaced() {
        val startup = startup(store = InMemorySessionStore(storedSession(expiresAt = 1_030)))

        val state = runBlocking { startup.run() }

        assertTrue(state is StartupState.Ready)
        assertEquals(listOf("/auth/v1/token", "/me"), paths)
    }

    @Test
    fun aDeadRefreshTokenEndsWithANewAccountRatherThanAFailure() {
        // The refresh is refused, so the authenticator signs up again before /me is asked.
        var call = 0
        val engine =
            MockEngine { request ->
                paths += request.url.encodedPath
                call++
                val refuse = call == 1
                respond(
                    content =
                        when {
                            refuse -> """{"error":"nope"}"""
                            call == 2 -> sessionBody("subject-3")
                            else -> identityBody(username = null)
                        },
                    status = if (refuse) HttpStatusCode.BadRequest else HttpStatusCode.OK,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                )
            }
        val httpClient = HttpClient(engine) { install(ContentNegotiation) { json(ChessApiClient.Json) } }
        val authenticator =
            AnonymousAuthenticator(
                SupabaseAuthClient(config, httpClient),
                InMemorySessionStore(storedSession(expiresAt = 1_000)),
                now = { 1_000 },
            )
        val startup =
            AppStartup(
                supabaseConfig = config,
                authenticator = authenticator,
                chessApi =
                    ChessApiClient(ChessServerConfig("https://chess.example"), httpClient) {
                        authenticator.currentSession().accessToken
                    },
            )

        assertTrue(runBlocking { startup.run() } is StartupState.Ready)
        assertEquals(listOf("/auth/v1/token", "/auth/v1/signup", "/me"), paths)
    }

    @Test
    fun aReturningPlayerIsReportedWithTheirUsername() {
        val startup =
            startup(store = InMemorySessionStore(storedSession(expiresAt = 5_000)), username = "Jordan")

        val state = runBlocking { startup.run() } as StartupState.Ready

        assertEquals("Jordan", state.user.username)
    }

    @Test
    fun aNewPlayerHasNoUsernameYet() {
        val startup = startup(store = InMemorySessionStore(storedSession(expiresAt = 5_000)))

        val state = runBlocking { startup.run() } as StartupState.Ready

        assertEquals(null, state.user.username)
    }

    @Test
    fun aRefusedSignInCanBeTriedAgain() {
        val startup = startup(signInStatus = HttpStatusCode.TooManyRequests)

        val state = runBlocking { startup.run() } as StartupState.Failed

        assertTrue(state.canRetry)
        assertTrue("the status is what the player is told", state.message.contains("429"))
    }

    @Test
    fun aServerThatWillNotSayWhoYouAreCanBeTriedAgain() {
        val startup =
            startup(
                store = InMemorySessionStore(storedSession(expiresAt = 5_000)),
                identityStatus = HttpStatusCode.InternalServerError,
            )

        val state = runBlocking { startup.run() } as StartupState.Failed

        assertTrue(state.canRetry)
        assertTrue(state.message.contains("500"))
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
        val refused = runBlocking { startup(signInStatus = HttpStatusCode.Unauthorized).run() } as StartupState.Failed
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
    fun aSleepingServerIsWaitedThroughAndReportedAsWakingNotAsAFailure() {
        // The free instance is down for the first two calls and then answers, which is what
        // a cold start looks like from the app (`M15.2` measured 59.0s and 64.5s).
        var call = 0
        val engine =
            MockEngine { request ->
                paths += request.url.encodedPath
                call++
                if (call <= 2) throw IOException("connection refused")

                respond(
                    content = identityBody(username = "Jordan"),
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                )
            }
        val httpClient = HttpClient(engine) { install(ContentNegotiation) { json(ChessApiClient.Json) } }
        val authenticator =
            AnonymousAuthenticator(
                SupabaseAuthClient(config, httpClient),
                InMemorySessionStore(storedSession(expiresAt = 5_000)),
                now = { 1_000 },
            )
        val startup =
            AppStartup(
                supabaseConfig = config,
                authenticator = authenticator,
                chessApi =
                    ChessApiClient(ChessServerConfig("https://chess.example"), httpClient) {
                        authenticator.currentSession().accessToken
                    },
                wakePolicy = ServerWakePolicy(deadlineMillis = 5_000, initialDelayMillis = 1, maxDelayMillis = 4),
            )

        val waking = mutableListOf<StartupState.Waking>()
        val state = runBlocking { startup.run(onWaking = { waking += it }) }

        // The cold start is survived rather than reported, which is the whole point.
        assertEquals(StartupState.Ready(CurrentUserDto(userId = "server-1", username = "Jordan")), state)
        assertEquals("the player should have been told it was waking", listOf(1, 2), waking.map { it.failures })
    }

    @Test
    fun aRefusedSignInIsNotWaitedThroughBecauseSupabaseAnswered() {
        val startup = startup(signInStatus = HttpStatusCode.Unauthorized)

        val waking = mutableListOf<StartupState.Waking>()
        val state = runBlocking { startup.run(onWaking = { waking += it }) } as StartupState.Failed

        // Supabase is a separate always-on service; a refusal from it is an answer, and
        // asking again would only get the same one more slowly.
        assertTrue(state.canRetry)
        assertTrue("a refusal is not a cold start", waking.isEmpty())
        assertEquals("it should have been asked exactly once", 1, paths.size)
    }

    @Test
    fun aServerRefusalIsNotWaitedThroughEither() {
        val startup =
            startup(
                store = InMemorySessionStore(storedSession(expiresAt = 5_000)),
                identityStatus = HttpStatusCode.InternalServerError,
            )

        val waking = mutableListOf<StartupState.Waking>()
        runBlocking { startup.run(onWaking = { waking += it }) }

        assertTrue("the Chess server answered, so it is awake", waking.isEmpty())
        assertEquals(listOf("/me"), paths)
    }

    @Test
    fun aServerThatNeverWakesEventuallyFailsWithAnOfferToTryAgain() {
        // A real deadline, because this asserts that retrying actually happened. These run
        // under `runBlocking`, so the deadline is spent against the real clock and a budget
        // as small as `impatient`'s would be used up by the first request alone -- the
        // assertion would then pass without a single retry having occurred.
        val startup =
            startup(
                failWith = IOException("still down"),
                wakePolicy = ServerWakePolicy(deadlineMillis = 1_000, initialDelayMillis = 1, maxDelayMillis = 2),
            )

        val state = runBlocking { startup.run() } as StartupState.Failed

        // It gives up rather than waiting forever, and what the player gets is a button.
        assertTrue(state.canRetry)
        assertTrue(state.message.contains("connection"))
        assertTrue("it should have tried more than once before giving up", paths.size > 1)
    }

    @Test
    fun theSessionIsInHandBeforeTheServerIsAskedAnything() {
        val store = InMemorySessionStore()
        val startup = startup(store = store)

        runBlocking { startup.run() }

        assertEquals(listOf("/auth/v1/signup", "/me"), paths)
        assertEquals("access-2", runBlocking { store.read() }?.accessToken)
    }
}
