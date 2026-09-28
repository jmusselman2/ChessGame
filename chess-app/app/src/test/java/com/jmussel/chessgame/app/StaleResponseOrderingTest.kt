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
import com.jmussel.chessgame.ui.game.OnlineGameState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * A late answer must not undo a newer one (`M14-02`).
 *
 * The rule is that a same-game view is only ever installed forwards: the canonical version
 * is the server's own count of accepted mutations and it only goes up (`D021`), so a
 * response carrying a lower one was decided against a position the screen has already left.
 * What the late answer still gets to do is *say* something — the player asked for a move and
 * is owed the outcome of asking, even when the board has moved on since.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StaleResponseOrderingTest {
    private val dispatcher = StandardTestDispatcher()

    private val models = mutableListOf<ChessAppViewModel>()

    /** The version the stubbed server answers a game read with. */
    private var serverVersion = 1L

    /** Held while the move request is in flight; releasing it lets the request fail. */
    private var holdMove: CompletableDeferred<Unit>? = null

    /** Completed once the move request has reached the stubbed server and is waiting. */
    private var moveArrived: CompletableDeferred<Unit>? = null

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
    fun aCommandThatFailsAfterANewerReloadReportsItselfWithoutRollingTheBoardBack() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.openOnlineGame(GAME)
            viewModel.gameJob?.join()
            assertEquals(1L, (viewModel.game as OnlineGameState.Ready).game.version)

            // The move leaves the device and then the connection goes; the opponent replies
            // in the meantime, and the socket says so.
            val arrived = CompletableDeferred<Unit>()
            val released = CompletableDeferred<Unit>()
            moveArrived = arrived
            holdMove = released
            viewModel.tapSquare(Square.parse("e2"))
            viewModel.tapSquare(Square.parse("e4"))
            arrived.await()

            serverVersion = 3L
            viewModel.onRealtimeMessage(
                RealtimeMessageDto(type = RealtimeMessageDto.GAME_UPDATED, gameId = GAME, version = serverVersion),
            )
            viewModel.gameJob?.join()

            released.complete(Unit)
            viewModel.moveJob?.join()

            val ready = viewModel.game as OnlineGameState.Ready
            assertEquals("the newer canonical state stays on the board", 3L, ready.game.version)
            assertNotNull("and the player is still told what happened to their move", ready.message)
            assertFalse("with the board usable again", ready.submitting)
        }

    private fun viewModel() =
        ChessAppViewModel(
            ChessAppDependencies(
                serverConfig = ChessServerConfig("https://chess.example"),
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
            MockEngine { request ->
                val path = request.url.encodedPath

                if (path.endsWith("/moves")) {
                    moveArrived?.complete(Unit)
                    holdMove?.await()
                    throw IOException("connection reset by peer")
                }

                respond(
                    content = gameView(),
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                )
            }

        return HttpClient(engine) { install(ContentNegotiation) { json(ChessApiClient.Json) } }
    }

    private fun gameView(): String =
        """
        {"gameId":"$GAME","seriesId":"series-1","opponent":{"userId":"user-2","username":"Alex"},
         "version":$serverVersion,"yourSide":"WHITE","sideToMove":"WHITE","yourTurn":true,"inCheck":false,
         "board":["rnbqkbnr","pppppppp","........","........","........","........","PPPPPPPP","RNBQKBNR"],
         "moves":[],"moveNumber":1,"halfmoveClock":0,"canUndo":false,"availableDrawClaims":[]}
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
