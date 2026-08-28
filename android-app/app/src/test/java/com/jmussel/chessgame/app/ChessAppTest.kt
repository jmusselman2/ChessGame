package com.jmussel.chessgame.app

import com.jmussel.chessgame.api.ChessApiClient
import com.jmussel.chessgame.api.ChessServerConfig
import com.jmussel.chessgame.auth.AnonymousSession
import com.jmussel.chessgame.auth.InMemorySessionStore
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
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The application shell: what it is showing, and what the screens are built from.
 *
 * Runs on the JVM against Ktor's `MockEngine`, so there is no Android runtime and no
 * network — the point is that the shell's state moves the way the navigation says and that
 * one HTTP client is shared and released.
 */
class ChessAppTest {
    private val requests = mutableListOf<HttpRequestData>()

    private val storedSession =
        AnonymousSession(
            accessToken = "access-1",
            refreshToken = "refresh-1",
            userId = "auth-user-1",
            expiresAtEpochSeconds = Long.MAX_VALUE,
        )

    private fun httpClient(): HttpClient {
        val engine =
            MockEngine { request ->
                requests += request
                respond(
                    content = "[]",
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                )
            }

        return HttpClient(engine) {
            install(ContentNegotiation) { json(ChessApiClient.Json) }
        }
    }

    private fun dependencies(httpClient: HttpClient = httpClient()) =
        ChessAppDependencies(
            serverConfig = ChessServerConfig("https://chess.example"),
            supabaseConfig = SupabaseConfig(url = "https://supabase.example", anonKey = "publishable-key"),
            httpClient = httpClient,
            sessionStore = InMemorySessionStore(storedSession),
        )

    @Test
    fun theChessServerIsCalledWithTheStoredSessionsToken() {
        val dependencies = dependencies()

        runBlocking { dependencies.chessApi.dashboard() }

        assertEquals("Bearer access-1", requests.single().headers[HttpHeaders.Authorization])
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

    private fun viewModel() = ChessAppViewModel(dependencies())
}
