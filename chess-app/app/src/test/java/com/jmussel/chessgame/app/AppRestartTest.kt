package com.jmussel.chessgame.app

import com.jmussel.chessgame.api.ChessApiClient
import com.jmussel.chessgame.api.ChessServerConfig
import com.jmussel.chessgame.api.RealtimeMessageDto
import com.jmussel.chessgame.api.RealtimeSource
import com.jmussel.chessgame.api.ServerWakePolicy
import com.jmussel.chessgame.auth.AnonymousSession
import com.jmussel.chessgame.auth.InMemorySessionStore
import com.jmussel.chessgame.auth.SessionStore
import com.jmussel.chessgame.auth.SupabaseConfig
import com.jmussel.chessgame.navigation.Destination
import com.jmussel.chessgame.ui.game.OnlineGameState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * What survives the app being killed and started again (`M16.2`, `MVP` *Reliability*).
 *
 * A relaunch is modelled the way Android actually does it: a brand new
 * [ChessAppViewModel] over brand new dependencies, sharing only the one thing that is
 * written to disk — the session store. Nothing about a game is stored locally by design
 * (`D004`), so "restores state" means the app comes back as the same player and reads
 * everything else from the server again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppRestartTest {
    private val dispatcher = StandardTestDispatcher()

    private val models = mutableListOf<ChessAppViewModel>()

    /** Requests as the stubbed server saw them, across every launch in the test. */
    private val paths = CopyOnWriteArrayList<String>()

    /** The one thing that outlives a launch. */
    private val store = InMemorySessionStore(null)

    /**
     * What the app is actually given: [store], but unreachable while [offline].
     *
     * The failure is staged here rather than in the HTTP engine on purpose. Ktor's engine
     * runs on its own dispatcher, so a request that fails there is invisible to this test's
     * virtual clock and the model would still read `Loading`. The store is read on the
     * calling coroutine, so the failure — and the `Waking` that follows it — land under
     * `runCurrent`.
     */
    private val storeThatSleeps =
        object : SessionStore {
            override suspend fun read(): AnonymousSession? {
                if (offline) throw IOException("server asleep")
                return store.read()
            }

            override suspend fun write(session: AnonymousSession) = store.write(session)

            override suspend fun clear() = store.clear()
        }

    /** The username the server reports for the caller; `null` means it has not been claimed. */
    private var username: String? = "Jordan"

    /** Series the stubbed dashboard is listing. */
    private var activeSeries = 1

    /** Moves the stubbed game has, so a reopened game can be seen to carry them. */
    private val played = CopyOnWriteArrayList<String>()

    /** While true, every request fails the way a sleeping instance does. */
    private var offline = false

    /** How many tokens the stubbed auth service has issued; each one is `access-<n>`. */
    private var grants = 0

    /**
     * When set, the Chess server accepts only this bearer token and answers `401` to any
     * other — a token that verifies no more, which is what a rotated key, a project the
     * beta was re-pointed at (`D035`), or a device clock that is wrong all look like.
     */
    private var onlyTokenAccepted: String? = null

    private val silentRealtime = RealtimeSource { flow { awaitCancellation() } }

    private val impatientWake =
        ServerWakePolicy(deadlineMillis = 2_000, initialDelayMillis = 1, maxDelayMillis = 2)

    @Before
    fun useTheTestDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun releaseTheTestDispatcher() {
        models.forEach { model ->
            listOf(model.startupJob, model.dashboardJob, model.gameJob, model.moveJob, model.updatesJob)
                .forEach { job -> job?.cancel() }
        }

        Dispatchers.resetMain()
    }

    @Test
    fun aRelaunchComesBackAsTheSameAccountWithoutCreatingASecondOne() =
        runTest(dispatcher) {
            val first = launch()
            first.startupJob?.join()
            val wasSignedInAs = requireNotNull(store.read()).userId

            val second = relaunch()
            second.startupJob?.join()

            assertEquals("the stored session is reused", wasSignedInAs, requireNotNull(store.read()).userId)
            assertEquals(
                "a relaunch must not create a second anonymous account",
                1,
                paths.count { it.startsWith("/auth/") },
            )
        }

    @Test
    fun aRelaunchLandsOnTheDashboardRatherThanOnboarding() =
        runTest(dispatcher) {
            launch().startupJob?.join()

            val relaunched = relaunch()
            relaunched.startupJob?.join()
            relaunched.dashboardJob?.join()

            assertEquals(Destination.Dashboard, relaunched.navigation.current)
            assertEquals("and it is useful, not empty", 1, relaunched.dashboard.entries.size)
        }

    @Test
    fun aRelaunchBeforeAUsernameWasClaimedGoesBackToOnboarding() =
        runTest(dispatcher) {
            username = null

            val relaunched = launch()
            relaunched.startupJob?.join()

            assertEquals(Destination.UsernameOnboarding, relaunched.navigation.current)
        }

    @Test
    fun aGameInProgressReopensAfterARelaunchWithWhatTheServerHas() =
        runTest(dispatcher) {
            val first = launch()
            first.startupJob?.join()
            first.openOnlineGame(GAME)
            first.gameJob?.join()

            // The app is killed, and while it is gone the opponent replies.
            played += "e2e4"
            played += "e7e5"

            val relaunched = relaunch()
            relaunched.startupJob?.join()
            relaunched.openOnlineGame(GAME)
            relaunched.gameJob?.join()

            val ready = relaunched.game as OnlineGameState.Ready
            assertEquals("nothing about a game is kept locally, so it all comes back", played.toList(), ready.game.moves)
            assertEquals(3L, ready.game.version)
        }

    @Test
    fun aRelaunchOpensTheRealtimeConnectionAgain() =
        runTest(dispatcher) {
            var connections = 0
            val counted =
                RealtimeSource {
                    flow {
                        connections++
                        emit(RealtimeMessageDto(type = RealtimeMessageDto.CONNECTED))
                        awaitCancellation()
                    }
                }

            val relaunched = launch(realtime = counted)
            relaunched.startupJob?.join()
            runCurrent()

            assertTrue("a relaunched app is no use without updates", connections >= 1)
            assertTrue(relaunched.updatesJob?.isActive == true)
        }

    @Test
    fun aRelaunchWhileTheServerIsAsleepWaitsAndThenLands() =
        runTest(dispatcher) {
            launch().startupJob?.join()

            offline = true
            val relaunched = relaunch()
            runCurrent()

            assertTrue("a cold start is a wake, not a failure", relaunched.startup is StartupState.Waking)

            offline = false
            relaunched.startupJob?.join()
            relaunched.dashboardJob?.join()

            assertTrue(relaunched.startup is StartupState.Ready)
            assertEquals(Destination.Dashboard, relaunched.navigation.current)
        }

    @Test
    fun aRelaunchWithAnExpiringTokenRefreshesItRatherThanStartingANewAccount() =
        runTest(dispatcher) {
            launch().startupJob?.join()
            val original = requireNotNull(store.read())

            // Back after long enough that the stored token is at its end.
            store.write(original.copy(expiresAtEpochSeconds = 0))

            val relaunched = relaunch()
            relaunched.startupJob?.join()

            assertEquals("refreshed, not replaced", 1, paths.count { it.contains("signup") })
            assertTrue("the refresh really happened", paths.any { it.contains("token") })
            assertTrue(relaunched.startup is StartupState.Ready)
        }

    @Test
    fun aRelaunchWhoseStoredTokenTheServerWillNotTakeGetsAFreshOneRatherThanBeingStuck() =
        runTest(dispatcher) {
            launch().startupJob?.join()

            // The stored session still looks perfectly good to the app — it is nowhere near
            // its expiry — but the server will not take the token any more. Nothing local can
            // notice that, so without asking for a new one the app can never start again, and
            // the retry button is a button that cannot work.
            onlyTokenAccepted = "access-2"

            val relaunched = relaunch()
            relaunched.startupJob?.join()
            relaunched.dashboardJob?.join()

            assertTrue(
                "a relaunch must not be stranded by a token the server has stopped accepting, but was " +
                    "${relaunched.startup}",
                relaunched.startup is StartupState.Ready,
            )
            assertEquals(Destination.Dashboard, relaunched.navigation.current)
            assertEquals("recovered by renewing the token, not by making a new account", 1, signUps())
        }

    @Test
    fun aTokenRefusedEvenAfterRenewalIsReportedRatherThanAskedForForever() =
        runTest(dispatcher) {
            launch().startupJob?.join()

            // No token this app can obtain will be accepted, so renewing answers nothing.
            onlyTokenAccepted = "a-token-nobody-will-ever-be-issued"
            val askedBefore = paths.count { it == "/me" }

            val relaunched = relaunch()
            relaunched.startupJob?.join()

            assertTrue("a refusal that survives a new token is an answer", relaunched.startup is StartupState.Failed)
            assertEquals(
                "asked once, renewed once, asked once more — and then stopped",
                2,
                paths.count { it == "/me" } - askedBefore,
            )
        }

    // --- The stubbed server ------------------------------------------------------------

    private fun signUps(): Int = paths.count { it.endsWith("/signup") }

    /** A launch of the app: new model, new dependencies, the same store on disk. */
    private fun launch(realtime: RealtimeSource = silentRealtime): ChessAppViewModel {
        val viewModel =
            ChessAppViewModel(
                ChessAppDependencies(
                    serverConfig = ChessServerConfig("https://chess.example"),
                    supabaseConfig = SupabaseConfig(url = "https://supabase.example", anonKey = "publishable-key"),
                    httpClient = server(),
                    sessionStore = storeThatSleeps,
                    realtime = realtime,
                    wakePolicy = impatientWake,
                ),
                wakePolicy = impatientWake,
            ).also(models::add)

        viewModel.start()
        return viewModel
    }

    /** The same thing again; the name is what the test is saying. */
    private fun relaunch(realtime: RealtimeSource = silentRealtime): ChessAppViewModel = launch(realtime)

    private fun server(): HttpClient {
        val engine =
            MockEngine { request ->
                val path = request.url.encodedPath
                paths += path

                if (offline) throw IOException("connection reset by peer")

                val rejected =
                    onlyTokenAccepted != null &&
                        !path.startsWith("/auth/") &&
                        request.headers[HttpHeaders.Authorization] != "Bearer $onlyTokenAccepted"

                if (rejected) {
                    return@MockEngine respond(content = "Invalid bearer token", status = HttpStatusCode.Unauthorized)
                }

                respond(
                    content = replyTo(path),
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                )
            }

        return HttpClient(engine) { install(ContentNegotiation) { json(ChessApiClient.Json) } }
    }

    private fun replyTo(path: String): String =
        when {
            path.startsWith("/auth/") -> session()
            path == "/me" -> """{"userId":"user-1"${username?.let { ""","username":"$it"""" } ?: ""}}"""
            path == "/dashboard" -> (1..activeSeries).joinToString(",", "[", "]") { dashboardLine() }
            path == "/friends" -> "[]"
            path.startsWith("/games/") -> gameView(path.removePrefix("/games/").substringBefore("/"))
            else -> "[]"
        }

    private fun session(): String {
        grants++

        return """
            {"access_token":"access-$grants","refresh_token":"refresh-$grants","expires_in":3600,
             "expires_at":9223372036854775807,"token_type":"bearer",
             "user":{"id":"auth-user-1","is_anonymous":true}}
            """.trimIndent()
    }

    private fun dashboardLine(): String =
        """
        {"seriesId":"series-1","opponent":{"userId":"user-2","username":"Alex"},"gameId":"$GAME",
         "version":1,"yourSide":"WHITE","sideToMove":"WHITE","moveNumber":1,"yourTurn":true}
        """.trimIndent()

    private fun gameView(gameId: String): String =
        """
        {"gameId":"$gameId","seriesId":"series-1","opponent":{"userId":"user-2","username":"Alex"},
         "version":${1 + played.size},"yourSide":"WHITE","sideToMove":"WHITE","yourTurn":true,"inCheck":false,
         "board":["rnbqkbnr","pppppppp","........","........","........","........","PPPPPPPP","RNBQKBNR"],
         "moves":${played.joinToString(",", "[", "]") { "\"$it\"" }},"moveNumber":1,"halfmoveClock":0,
         "canUndo":false,"availableDrawClaims":[]}
        """.trimIndent()

    private companion object {
        const val GAME = "game-7"
    }
}
