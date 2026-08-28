package com.jmussel.chessgame.app

import com.jmussel.chessgame.api.ChessApiClient
import com.jmussel.chessgame.api.ChessServerConfig
import com.jmussel.chessgame.auth.AnonymousSession
import com.jmussel.chessgame.auth.InMemorySessionStore
import com.jmussel.chessgame.auth.SessionStore
import com.jmussel.chessgame.auth.SupabaseConfig
import com.jmussel.chessgame.navigation.AppNavigation
import com.jmussel.chessgame.navigation.Destination
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The application shell: what it is showing, what the screens are built from, and how
 * startup gets it there.
 *
 * Runs on the JVM against Ktor's `MockEngine`, so there is no Android runtime and no
 * network. The model's coroutines run on a test dispatcher, so "loading" is a state a test
 * can actually observe.
 */
class ChessAppTest {
    private val requests = mutableListOf<HttpRequestData>()

    private val dispatcher = StandardTestDispatcher()

    private val storedSession =
        AnonymousSession(
            accessToken = "access-1",
            refreshToken = "refresh-1",
            userId = "auth-user-1",
            expiresAtEpochSeconds = Long.MAX_VALUE,
        )

    private val newSession =
        """
        {
          "access_token": "access-2",
          "refresh_token": "refresh-2",
          "expires_in": 3600,
          "expires_at": 9223372036854775807,
          "token_type": "bearer",
          "user": { "id": "auth-user-2", "is_anonymous": true }
        }
        """.trimIndent()

    @Before
    fun useTheTestDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun releaseTheTestDispatcher() {
        Dispatchers.resetMain()
    }

    /** Replies to everything with [body]; the first [refusals] calls are refused instead. */
    private fun httpClient(
        body: String = "[]",
        refusals: Int = 0,
    ): HttpClient {
        var refused = 0
        val engine =
            MockEngine { request ->
                requests += request
                val refuse = refused < refusals
                refused++
                respond(
                    content = if (refuse) """{"error":"nope"}""" else body,
                    status = if (refuse) HttpStatusCode.ServiceUnavailable else HttpStatusCode.OK,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                )
            }

        return HttpClient(engine) {
            install(ContentNegotiation) { json(ChessApiClient.Json) }
        }
    }

    private fun dependencies(
        httpClient: HttpClient = httpClient(),
        sessionStore: SessionStore = InMemorySessionStore(storedSession),
        supabaseConfig: SupabaseConfig = SupabaseConfig(url = "https://supabase.example", anonKey = "publishable-key"),
    ) = ChessAppDependencies(
        serverConfig = ChessServerConfig("https://chess.example"),
        supabaseConfig = supabaseConfig,
        httpClient = httpClient,
        sessionStore = sessionStore,
    )

    @Test
    fun theChessServerIsCalledWithTheStoredSessionsToken() {
        val dependencies = dependencies()

        runBlocking { dependencies.chessApi.dashboard() }

        assertEquals("Bearer access-1", requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun oneTokenProviderServesEverythingThatAuthenticates() {
        val dependencies = dependencies()

        assertEquals("access-1", runBlocking { dependencies.accessToken() })
    }

    @Test
    fun closingTheDependenciesReleasesTheHttpClient() {
        val httpClient = httpClient()

        dependencies(httpClient).close()

        assertFalse(httpClient.isActive)
    }

    @Test
    fun theAppStartsAtStartup() {
        assertEquals(Destination.Startup, viewModel().navigation.current)
    }

    @Test
    fun startupHandsOverToTheDashboardWithNothingBehindIt() {
        val viewModel = viewModel()

        viewModel.restartAt(Destination.Dashboard)

        assertEquals(AppNavigation(listOf(Destination.Dashboard)), viewModel.navigation)
    }

    @Test
    fun openingAScreenAndGoingBackReturnsToTheOneBehindIt() {
        val viewModel = viewModel()
        viewModel.restartAt(Destination.Dashboard)

        viewModel.open(Destination.History)
        assertEquals(Destination.History, viewModel.navigation.current)

        assertTrue(viewModel.back())
        assertEquals(Destination.Dashboard, viewModel.navigation.current)
    }

    @Test
    fun goingBackWithNothingBehindTheScreenBelongsToTheSystem() {
        val viewModel = viewModel()
        viewModel.restartAt(Destination.Dashboard)

        assertFalse(viewModel.back())
        assertEquals(Destination.Dashboard, viewModel.navigation.current)
    }

    @Test
    fun theScreensAreBuiltFromOneSetOfDependencies() {
        val dependencies = dependencies()
        val viewModel = ChessAppViewModel(dependencies)

        assertEquals(dependencies, viewModel.app)
    }

    @Test
    fun theModelAndItsDependenciesAreBuiltOnlyWhenThereIsNotOneAlready() {
        var built = 0
        val factory =
            ChessAppViewModel.factory {
                built++
                dependencies()
            }

        assertEquals(0, built)
        factory.create(ChessAppViewModel::class.java)
        assertEquals(1, built)
    }

    @Test
    fun startupIsLoadingUntilThereIsASessionAndThenTheDashboard() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.start()

            assertEquals(StartupState.Loading, viewModel.startup)
            assertEquals("nothing authenticated before there is a session", Destination.Startup, viewModel.navigation.current)

            viewModel.startupJob?.join()

            assertEquals(StartupState.Ready("auth-user-1"), viewModel.startup)
            assertEquals(AppNavigation(listOf(Destination.Dashboard)), viewModel.navigation)
        }

    @Test
    fun aFirstRunSignsUpAndLandsOnTheDashboard() =
        runTest(dispatcher) {
            val viewModel = viewModel(httpClient = httpClient(body = newSession), sessionStore = InMemorySessionStore())

            viewModel.start()
            viewModel.startupJob?.join()

            assertEquals(StartupState.Ready("auth-user-2"), viewModel.startup)
            assertEquals(listOf("/auth/v1/signup"), requests.map { it.url.encodedPath })
        }

    @Test
    fun startingTwiceDoesNotCreateTwoAnonymousAccounts() =
        runTest(dispatcher) {
            val viewModel = viewModel(httpClient = httpClient(body = newSession), sessionStore = InMemorySessionStore())

            viewModel.start()
            viewModel.start()
            viewModel.startupJob?.join()
            viewModel.start()
            viewModel.startupJob?.join()

            assertEquals(listOf("/auth/v1/signup"), requests.map { it.url.encodedPath })
        }

    @Test
    fun aFailedStartupStaysOnTheStartupScreenAndCanBeTriedAgain() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    httpClient = httpClient(body = newSession, refusals = 1),
                    sessionStore = InMemorySessionStore(),
                )

            viewModel.start()
            viewModel.startupJob?.join()

            val failure = viewModel.startup as StartupState.Failed
            assertTrue(failure.canRetry)
            assertEquals(Destination.Startup, viewModel.navigation.current)

            viewModel.start()
            viewModel.startupJob?.join()

            assertEquals(StartupState.Ready("auth-user-2"), viewModel.startup)
            assertEquals(AppNavigation(listOf(Destination.Dashboard)), viewModel.navigation)
        }

    @Test
    fun aBuildWithoutASupabaseKeyStopsAtAnExplanation() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    supabaseConfig = SupabaseConfig(url = "https://supabase.example", anonKey = ""),
                    sessionStore = InMemorySessionStore(),
                )

            viewModel.start()
            viewModel.startupJob?.join()

            val failure = viewModel.startup as StartupState.Failed
            assertFalse(failure.canRetry)
            assertEquals(Destination.Startup, viewModel.navigation.current)
            assertTrue("a build that cannot sign in must not try", requests.isEmpty())
        }

    private fun viewModel(
        httpClient: HttpClient = httpClient(),
        sessionStore: SessionStore = InMemorySessionStore(storedSession),
        supabaseConfig: SupabaseConfig = SupabaseConfig(url = "https://supabase.example", anonKey = "publishable-key"),
    ) = ChessAppViewModel(
        dependencies(httpClient = httpClient, sessionStore = sessionStore, supabaseConfig = supabaseConfig),
    )
}
