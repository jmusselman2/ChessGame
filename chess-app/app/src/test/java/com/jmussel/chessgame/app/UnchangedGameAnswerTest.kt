package com.jmussel.chessgame.app

import com.jmussel.chessgame.api.ChessApiClient
import com.jmussel.chessgame.api.ChessServerConfig
import com.jmussel.chessgame.api.RealtimeMessageDto
import com.jmussel.chessgame.api.RealtimeSource
import com.jmussel.chessgame.api.ServerWakePolicy
import com.jmussel.chessgame.api.movesJson
import com.jmussel.chessgame.auth.AnonymousSession
import com.jmussel.chessgame.auth.InMemorySessionStore
import com.jmussel.chessgame.auth.SupabaseConfig
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.local.inMemoryLocalGameStore
import com.jmussel.chessgame.ui.game.AfterGame
import com.jmussel.chessgame.ui.game.OnlineGameState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * An answer that brings back the version already on screen leaves the screen as it was
 * (`M23.11`).
 *
 * The version is the server's count of accepted changes to the game (`D021`), so a read at
 * the version on screen has nothing new to say about it. What the screen already says — a
 * refused command's message, or the next game the series moved on to — stays until a newer
 * version replaces it. Findings F1 and F2 of the 2026-10-22 adversarial test were exactly
 * those two being wiped by the realtime reload, and by the reload on coming back to the app.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UnchangedGameAnswerTest {
    private val dispatcher = StandardTestDispatcher()

    private val models = mutableListOf<ChessAppViewModel>()

    /** The moves the stubbed server has accepted; each one is a version. */
    private val played = CopyOnWriteArrayList<String>()

    /** Whether the stubbed game has been resigned, which is a version too. */
    private var resigned = false

    private val version: Long
        get() = 1L + played.size + (if (resigned) 1 else 0)

    @Before
    fun useTheTestDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun releaseTheTestDispatcher() {
        models.forEach { model ->
            listOf(model.startupJob, model.gameJob, model.moveJob, model.dashboardJob, model.updatesJob).forEach { it?.cancel() }
        }
        Dispatchers.resetMain()
    }

    @Test
    fun aRefusalsMessageSurvivesTheReloadOfTheVersionItBrought() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.openOnlineGame(GAME)
            viewModel.gameJob?.join()

            // The opponent replies before this player's move arrives, so the move is refused
            // with the newer game attached.
            played += "e7e5"
            viewModel.tapSquare(Square.parse("e2"))
            viewModel.tapSquare(Square.parse("e4"))
            viewModel.moveJob?.join()

            val refused = viewModel.game as OnlineGameState.Ready
            assertEquals(2L, refused.game.version)
            assertEquals(MOVED_ON, refused.message)

            // The opponent's move then arrives over the socket, and its reload brings back the
            // version the refusal already showed.
            viewModel.onRealtimeMessage(RealtimeMessageDto(type = RealtimeMessageDto.GAME_UPDATED, gameId = GAME, version = 2))
            viewModel.gameJob?.join()

            val reloaded = viewModel.game as OnlineGameState.Ready
            assertEquals(2L, reloaded.game.version)
            assertEquals("the refusal is still explained", MOVED_ON, reloaded.message)
        }

    @Test
    fun aNewerVersionReplacesTheMessage() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.openOnlineGame(GAME)
            viewModel.gameJob?.join()
            played += "e7e5"
            viewModel.tapSquare(Square.parse("e2"))
            viewModel.tapSquare(Square.parse("e4"))
            viewModel.moveJob?.join()

            played += "d7d5"
            viewModel.onRealtimeMessage(RealtimeMessageDto(type = RealtimeMessageDto.GAME_UPDATED, gameId = GAME, version = 3))
            viewModel.gameJob?.join()

            val moved = viewModel.game as OnlineGameState.Ready
            assertEquals(3L, moved.game.version)
            assertNull("a game that has moved on again makes the old explanation stale", moved.message)
        }

    @Test
    fun theNextGameOfferSurvivesAReloadAndComingBackToTheApp() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start()
            viewModel.startupJob?.join()
            viewModel.openOnlineGame(GAME)
            viewModel.gameJob?.join()

            viewModel.askToResign()
            viewModel.resign()
            viewModel.moveJob?.join()
            viewModel.dashboardJob?.join()
            assertEquals(AfterGame.NextGame(NEXT_GAME), (viewModel.game as OnlineGameState.Ready).after)

            // The server announces the resignation to this player's socket too.
            viewModel.onRealtimeMessage(RealtimeMessageDto(type = RealtimeMessageDto.GAME_UPDATED, gameId = GAME, version = 2))
            viewModel.gameJob?.join()
            assertEquals(
                "a reload of the finished game still offers the next one",
                AfterGame.NextGame(NEXT_GAME),
                (viewModel.game as OnlineGameState.Ready).after,
            )

            // Home and back (`D075`): what is on screen is read again.
            viewModel.onBackground()
            viewModel.onForeground()
            viewModel.gameJob?.join()
            viewModel.dashboardJob?.join()

            val back = viewModel.game as OnlineGameState.Ready
            assertEquals(2L, back.game.version)
            assertEquals("coming back to the app still offers the next game", AfterGame.NextGame(NEXT_GAME), back.after)
        }

    @Test
    fun openingTheGameAgainDoesNotRepeatWhatAnEarlierVisitWasTold() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.openOnlineGame(GAME)
            viewModel.gameJob?.join()
            played += "e7e5"
            viewModel.tapSquare(Square.parse("e2"))
            viewModel.tapSquare(Square.parse("e4"))
            viewModel.moveJob?.join()
            assertEquals(MOVED_ON, (viewModel.game as OnlineGameState.Ready).message)

            viewModel.back()
            viewModel.openOnlineGame(GAME)
            viewModel.gameJob?.join()

            assertNull((viewModel.game as OnlineGameState.Ready).message)
        }

    private fun viewModel() =
        ChessAppViewModel(
            ChessAppDependencies(
                serverConfig = ChessServerConfig("https://chess.example"),
                localGameStore = inMemoryLocalGameStore(),
                localGameDispatcher = dispatcher,
                supabaseConfig = SupabaseConfig(url = "https://supabase.example", anonKey = "publishable-key"),
                httpClient = statefulServer(),
                sessionStore = InMemorySessionStore(STORED_SESSION),
                realtime = RealtimeSource { flow { awaitCancellation() } },
                wakePolicy = IMPATIENT_WAKE,
            ),
            wakePolicy = IMPATIENT_WAKE,
        ).also(models::add)

    /**
     * One game that remembers what was done to it: a move at an older version is refused as
     * `STALE_VERSION` with the game attached (`D021`), and a resignation ends it with the
     * series moving on to [NEXT_GAME] (`D014`).
     */
    private fun statefulServer(): HttpClient {
        val engine =
            mockEngineOn(dispatcher.scheduler) { request ->
                val path = request.url.encodedPath
                val (status, content) =
                    when {
                        path == "/me" -> HttpStatusCode.OK to """{"userId":"user-1","username":"Jordan"}"""
                        path == "/dashboard" -> HttpStatusCode.OK to dashboard()
                        path.endsWith("/moves") && versionIn((request.body as TextContent).text) != version ->
                            HttpStatusCode.Conflict to staleRefusal()
                        path.endsWith("/resignation") -> {
                            resigned = true
                            HttpStatusCode.OK to gameView()
                        }
                        path.startsWith("/games/") -> HttpStatusCode.OK to gameView()
                        else -> HttpStatusCode.OK to "[]"
                    }

                respond(content = content, status = status, headers = headersOf("Content-Type", ContentType.Application.Json.toString()))
            }

        return HttpClient(engine) { install(ContentNegotiation) { json(ChessApiClient.Json) } }
    }

    /** The series this game belongs to, at the game the server started once it ended. */
    private fun dashboard(): String =
        if (!resigned) {
            "[]"
        } else {
            """
            [{"seriesId":"series-1","opponent":{"userId":"user-2","username":"Alex"},"gameId":"$NEXT_GAME",
              "version":1,"yourSide":"BLACK","sideToMove":"WHITE","moveNumber":1,"yourTurn":false,"pendingRequests":[]}]
            """.trimIndent()
        }

    private fun gameView(): String {
        val ending = if (resigned) ""","result":"BLACK_WINS","terminationReason":"RESIGNATION"""" else ""
        return """
            {"gameId":"$GAME","seriesId":"series-1","opponent":{"userId":"user-2","username":"Alex"},
             "version":$version,"yourSide":"WHITE","sideToMove":"WHITE","yourTurn":${!resigned},"inCheck":false,
             "board":["rnbqkbnr","pppppppp","........","........","........","........","PPPPPPPP","RNBQKBNR"],
             "moves":${movesJson(played)},"moveNumber":1,"halfmoveClock":0,"seriesActive":true,"hasStarted":true,
             "canUndo":false,"availableDrawClaims":[]$ending}
            """.trimIndent()
    }

    private fun staleRefusal(): String = """{"reason":"STALE_VERSION","message":"The game has moved on","game":${gameView()}}"""

    private fun versionIn(body: String): Long =
        Regex("\"expectedVersion\"\\s*:\\s*(\\d+)")
            .find(body)
            ?.groupValues
            ?.get(1)
            ?.toLong()
            ?: error("no expectedVersion in $body")

    private companion object {
        const val GAME = "game-7"
        const val NEXT_GAME = "game-8"
        const val MOVED_ON = "The game moved on. This is where it is now."

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
