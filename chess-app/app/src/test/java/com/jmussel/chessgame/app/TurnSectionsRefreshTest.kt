package com.jmussel.chessgame.app

import com.jmussel.chessgame.api.ChessApiClient
import com.jmussel.chessgame.api.ChessServerConfig
import com.jmussel.chessgame.api.RealtimeMessageDto
import com.jmussel.chessgame.api.RealtimeSource
import com.jmussel.chessgame.api.ServerWakePolicy
import com.jmussel.chessgame.auth.AnonymousSession
import com.jmussel.chessgame.auth.InMemorySessionStore
import com.jmussel.chessgame.auth.SupabaseConfig
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.local.inMemoryLocalGameStore
import com.jmussel.chessgame.navigation.Destination
import com.jmussel.chessgame.ui.dashboard.DashboardSections
import com.jmussel.chessgame.ui.game.OnlineGameState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
 * The dashboard's turn sections after playing online (`M21.20`).
 *
 * A game opened from YOUR TURN must move to THEIR TURN once the player has moved in it and
 * pressed Back, because Back reads the dashboard again. The app never works out whose turn it
 * is: the stubbed server's dashboard says so, and it changes only when a command lands
 * (`D004`, `D022`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TurnSectionsRefreshTest {
    private val dispatcher = StandardTestDispatcher()

    private val models = mutableListOf<ChessAppViewModel>()

    /** Whose move the stubbed server says it is in [GAME]. */
    private var yourTurn = true

    /** The stubbed server's version of [GAME]. */
    private var version = 1L

    /** Every path the stubbed server was asked for, in order. */
    private val paths = mutableListOf<String>()

    @Before
    fun useTheTestDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun releaseTheTestDispatcher() {
        models.forEach { model -> listOf(model.gameJob, model.moveJob, model.dashboardJob).forEach { it?.cancel() } }
        Dispatchers.resetMain()
    }

    @Test
    fun aGameOpenedFromYourTurnIsUnderTheirTurnAfterAMoveAndBack() =
        runTest(dispatcher) {
            val viewModel = onTheDashboard()
            assertEquals(listOf(GAME), yourTurnGames(viewModel))

            viewModel.openGame(DashboardSections.yourTurn(viewModel.dashboard.entries).single())
            viewModel.gameJob?.join()
            play(viewModel)

            assertTrue("Back goes to the dashboard", viewModel.back())
            assertEquals(Destination.Dashboard, viewModel.navigation.current)
            viewModel.dashboardJob?.join()

            assertEquals("the refreshed entry is under THEIR TURN", listOf(GAME), theirTurnGames(viewModel))
            assertTrue("and no longer under YOUR TURN", yourTurnGames(viewModel).isEmpty())
        }

    @Test
    fun backFromAnOnlineGameReadsTheDashboardAgain() =
        runTest(dispatcher) {
            val viewModel = onTheDashboard()
            viewModel.openOnlineGame(GAME)
            viewModel.gameJob?.join()
            paths.clear()

            viewModel.back()
            viewModel.dashboardJob?.join()

            assertTrue("a fresh GET /dashboard", "/dashboard" in paths)
        }

    @Test
    fun anUpdateBeforeBackReloadsTheGameAndBackStillRefreshesTheDashboard() =
        runTest(dispatcher) {
            val viewModel = onTheDashboard()
            viewModel.openOnlineGame(GAME)
            viewModel.gameJob?.join()
            play(viewModel)

            viewModel.onRealtimeMessage(RealtimeMessageDto(type = RealtimeMessageDto.GAME_UPDATED, gameId = GAME, version = version))
            viewModel.gameJob?.join()
            viewModel.back()
            viewModel.dashboardJob?.join()

            assertEquals(listOf(GAME), theirTurnGames(viewModel))
            assertTrue(yourTurnGames(viewModel).isEmpty())
        }

    @Test
    fun anUpdateAfterBackRefreshesTheDashboardAndNotTheGameNobodyCanSee() =
        runTest(dispatcher) {
            val viewModel = onTheDashboard()
            viewModel.openOnlineGame(GAME)
            viewModel.gameJob?.join()
            play(viewModel)

            viewModel.back()
            paths.clear()
            viewModel.onRealtimeMessage(RealtimeMessageDto(type = RealtimeMessageDto.GAME_UPDATED, gameId = GAME, version = version))
            advanceUntilIdle()

            assertFalse("the game left behind is not reloaded: $paths", "/games/$GAME" in paths)
            assertTrue("/dashboard" in paths)
            assertEquals(listOf(GAME), theirTurnGames(viewModel))
        }

    @Test
    fun aGameOpenedThroughFriendsIsUnderTheirTurnWhenBackReachesTheDashboard() =
        runTest(dispatcher) {
            // Finding F6 of the 2026-10-22 adversarial UI test: Dashboard → Friends → Play → move →
            // Back → Back left the game under YOUR TURN (`M23.13`).
            val viewModel = onTheDashboard()
            viewModel.open(Destination.Friends)
            viewModel.openOnlineGame(GAME)
            viewModel.gameJob?.join()
            play(viewModel)

            assertTrue(viewModel.back())
            assertEquals(Destination.Friends, viewModel.navigation.current)
            assertTrue(viewModel.back())
            assertEquals(Destination.Dashboard, viewModel.navigation.current)
            viewModel.dashboardJob?.join()

            assertEquals("the refreshed entry is under THEIR TURN", listOf(GAME), theirTurnGames(viewModel))
            assertTrue(yourTurnGames(viewModel).isEmpty())
        }

    @Test
    fun backFromAScreenWithNoGamePlayedDoesNotReadTheDashboardAgain() =
        runTest(dispatcher) {
            val viewModel = onTheDashboard()
            viewModel.open(Destination.Friends)
            paths.clear()

            viewModel.back()
            advanceUntilIdle()

            assertFalse("no game was left, so nothing on the dashboard can be behind: $paths", "/dashboard" in paths)
        }

    @Test
    fun theDashboardIsReadOnceOnArrivalNotOnEveryLaterReturn() =
        runTest(dispatcher) {
            val viewModel = onTheDashboard()
            viewModel.open(Destination.History)
            viewModel.openOnlineGame(GAME)
            viewModel.gameJob?.join()
            play(viewModel)
            viewModel.back()
            viewModel.back()
            viewModel.dashboardJob?.join()

            viewModel.open(Destination.Friends)
            paths.clear()
            viewModel.back()
            advanceUntilIdle()

            assertFalse("the arrival after playing already read it: $paths", "/dashboard" in paths)
        }

    @Test
    fun anUndoMovesTheGameBackUnderYourTurnAfterBack() =
        runTest(dispatcher) {
            yourTurn = false
            val viewModel = onTheDashboard()
            assertEquals(listOf(GAME), theirTurnGames(viewModel))

            viewModel.openOnlineGame(GAME)
            viewModel.gameJob?.join()
            viewModel.undoMove()
            viewModel.moveJob?.join()
            viewModel.back()
            viewModel.dashboardJob?.join()

            assertEquals("an accepted undo that is not a move refreshes too", listOf(GAME), yourTurnGames(viewModel))
        }

    @Test
    fun aFailedRefreshKeepsTheDashboardErrorAndStillLeavesTheGame() =
        runTest(dispatcher) {
            val viewModel = onTheDashboard()
            viewModel.openOnlineGame(GAME)
            viewModel.gameJob?.join()
            play(viewModel)
            dashboardDown = true

            assertTrue(viewModel.back())
            viewModel.dashboardJob?.join()

            assertEquals(Destination.Dashboard, viewModel.navigation.current)
            assertTrue("the existing error and retry", viewModel.dashboard.message != null)
            assertEquals("no turn is made up: the last answer stays", listOf(GAME), yourTurnGames(viewModel))
            assertEquals("no command is sent again", 1, paths.count { it.endsWith("/moves") })
        }

    /** When true, the dashboard read fails. */
    private var dashboardDown = false

    private suspend fun onTheDashboard(): ChessAppViewModel {
        val viewModel = viewModel()
        viewModel.restartAt(Destination.Dashboard)
        viewModel.loadDashboard()
        viewModel.dashboardJob?.join()
        return viewModel
    }

    private suspend fun play(viewModel: ChessAppViewModel) {
        viewModel.tapSquare(Square.parse("e2"))
        viewModel.tapSquare(Square.parse("e4"))
        viewModel.moveJob?.join()
        assertFalse("the move was accepted", (viewModel.game as OnlineGameState.Ready).game.yourTurn)
    }

    private fun yourTurnGames(viewModel: ChessAppViewModel) = DashboardSections.yourTurn(viewModel.dashboard.entries).map { it.gameId }

    private fun theirTurnGames(viewModel: ChessAppViewModel) = DashboardSections.theirTurn(viewModel.dashboard.entries).map { it.gameId }

    private fun viewModel() =
        ChessAppViewModel(
            ChessAppDependencies(
                serverConfig = ChessServerConfig("https://chess.example"),
                localGameStore = inMemoryLocalGameStore(),
                localGameDispatcher = dispatcher,
                supabaseConfig = SupabaseConfig(url = "https://supabase.example", anonKey = "publishable-key"),
                httpClient = stubbedServer(),
                sessionStore = InMemorySessionStore(STORED_SESSION),
                realtime = RealtimeSource { flow { awaitCancellation() } },
                wakePolicy = IMPATIENT_WAKE,
            ),
            wakePolicy = IMPATIENT_WAKE,
        ).also(models::add)

    private fun stubbedServer(): HttpClient {
        val engine =
            mockEngineOn(dispatcher.scheduler) { request ->
                val path = request.url.encodedPath
                paths += path

                when {
                    path.endsWith("/moves") -> {
                        yourTurn = false
                        version++
                    }
                    path.endsWith("/undo") -> {
                        yourTurn = true
                        version++
                    }
                }

                val down = dashboardDown && path == "/dashboard"
                respond(
                    content =
                        when {
                            down -> "unavailable"
                            path == "/dashboard" -> dashboard()
                            path == "/friends" -> "[]"
                            path == "/history" -> "[]"
                            else -> gameView()
                        },
                    status = if (down) HttpStatusCode.ServiceUnavailable else HttpStatusCode.OK,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                )
            }

        return HttpClient(engine) { install(ContentNegotiation) { json(ChessApiClient.Json) } }
    }

    private fun dashboard(): String =
        """
        [{"seriesId":"series-1","opponent":{"userId":"user-2","username":"Alex"},"gameId":"$GAME",
          "version":$version,"yourSide":"WHITE","sideToMove":"${if (yourTurn) "WHITE" else "BLACK"}",
          "moveNumber":1,"yourTurn":$yourTurn,"pendingRequests":[]}]
        """.trimIndent()

    private fun gameView(): String =
        """
        {"gameId":"$GAME","seriesId":"series-1","opponent":{"userId":"user-2","username":"Alex"},
         "version":$version,"yourSide":"WHITE","sideToMove":"${if (yourTurn) "WHITE" else "BLACK"}",
         "yourTurn":$yourTurn,"inCheck":false,
         "board":["rnbqkbnr","pppppppp","........","........","........","........","PPPPPPPP","RNBQKBNR"],
         "moves":[],"moveNumber":1,"halfmoveClock":0,"canUndo":${!yourTurn},"availableDrawClaims":[]}
        """.trimIndent()

    private companion object {
        const val GAME = "game-7"

        val STORED_SESSION =
            AnonymousSession(
                accessToken = "access-1",
                refreshToken = "refresh-1",
                userId = "auth-user-1",
                expiresAtEpochSeconds = Long.MAX_VALUE,
            )

        val IMPATIENT_WAKE = ServerWakePolicy(deadlineMillis = 2_000, initialDelayMillis = 1, maxDelayMillis = 2)
    }
}
