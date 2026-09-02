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
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.ui.game.OnlineGameState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * What an interrupted network does to a game in progress (`M16.1`).
 *
 * The interruptions modelled here are the two the beta actually has: an ordinary short
 * disconnect, and the cold start a free Render instance pays after it has spun down
 * (`D032`, `D037`). Both look the same from the client — the transport fails — and both
 * must end with the app showing what the server has, with every move counted once.
 *
 * The stubbed server is deliberately stateful: it records the moves it accepts and grows a
 * version with them, so "the move was played twice" and "the move was lost" are things an
 * assertion can actually tell apart rather than trusting a mock's call count.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NetworkInterruptionTest {
    private val dispatcher = StandardTestDispatcher()

    private val models = mutableListOf<ChessAppViewModel>()

    /** Requests as the stubbed server saw them, newest last. */
    private val paths = CopyOnWriteArrayList<String>()

    /** While true, every request fails the way a dropped connection or a sleeping host does. */
    private var offline = false

    /** The moves the stubbed server has accepted, which is the count that must not drift. */
    private val played = CopyOnWriteArrayList<String>()

    /** The version the stubbed game is at: one per accepted move, from 1. */
    private val version: Long
        get() = 1L + played.size

    /**
     * Held open to keep exactly one game read in flight, which is how an update that arrives
     * mid-reload is staged. Completing it lets that read answer.
     */
    private var holdNextGameRead: CompletableDeferred<Unit>? = null

    /**
     * Completed by the stubbed server once the held read has arrived and its answer is
     * already decided.
     *
     * Waiting on this is what makes the staging deterministic: the engine runs off the test
     * dispatcher, so "the request is in flight" is not something `runCurrent` can establish.
     * Without it a test can set up the interruption after the read has already been answered
     * and pass for the wrong reason.
     */
    private var heldReadArrived: CompletableDeferred<Unit>? = null

    /** Held open to keep one dashboard read in flight, the same way [holdNextGameRead] does. */
    private var holdNextDashboardRead: CompletableDeferred<Unit>? = null

    /** How many series the stubbed dashboard is listing. */
    private var activeSeries = 0

    /** When set, the next command is applied and *then* the reply is lost in transit. */
    private var loseNextReply = false

    private val storedSession =
        AnonymousSession(
            accessToken = "access-1",
            refreshToken = "refresh-1",
            userId = "auth-user-1",
            expiresAtEpochSeconds = Long.MAX_VALUE,
        )

    /** A connection that stays open and says nothing, for the tests that are not about it. */
    private val silentRealtime = RealtimeSource { flow { awaitCancellation() } }

    /**
     * Short enough that a test is not slow and long enough that retrying really happens.
     *
     * A deadline of a single attempt would let every "did it wait through the cold start?"
     * assertion pass without meaning anything.
     */
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

    // --- An ordinary short disconnect -------------------------------------------------

    @Test
    fun aGameThatFailedToLoadWhileOfflineIsReloadedWhenTheConnectionComesBack() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.openOnlineGame(GAME)
            viewModel.gameJob?.join()
            assertTrue(viewModel.game is OnlineGameState.Ready)

            offline = true
            viewModel.loadGame(GAME)
            viewModel.gameJob?.join()
            assertTrue("an interrupted reload leaves the screen failed", viewModel.game is OnlineGameState.Failed)

            // The socket coming back is the app's own evidence that the server is reachable
            // again; a player should not have to press anything after it.
            offline = false
            viewModel.onRealtimeMessage(RealtimeMessageDto(type = RealtimeMessageDto.CONNECTED))
            viewModel.gameJob?.join()

            val recovered = viewModel.game as OnlineGameState.Ready
            assertEquals(GAME, recovered.game.gameId)
        }

    @Test
    fun anUpdateThatArrivesWhileAReloadIsRunningIsNotDropped() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.openOnlineGame(GAME)
            viewModel.gameJob?.join()

            // A reload is issued and held open; the opponent moves while it is in flight, so
            // the answer already on its way was decided before that move.
            val arrived = CompletableDeferred<Unit>()
            val inFlight = CompletableDeferred<Unit>()
            heldReadArrived = arrived
            holdNextGameRead = inFlight
            viewModel.loadGame(GAME)
            arrived.await()

            played += "e7e5"
            viewModel.onRealtimeMessage(
                RealtimeMessageDto(type = RealtimeMessageDto.GAME_UPDATED, gameId = GAME, version = version),
            )
            runCurrent()

            inFlight.complete(Unit)
            viewModel.gameJob?.join()

            val ready = viewModel.game as OnlineGameState.Ready
            assertEquals("the update must not be lost behind the reload it interrupted", 2L, ready.game.version)
            assertEquals(listOf("e7e5"), ready.game.moves)
        }

    @Test
    fun openingAnotherGameWhileOneIsStillLoadingShowsTheOneThatWasAskedFor() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            val arrived = CompletableDeferred<Unit>()
            val inFlight = CompletableDeferred<Unit>()
            heldReadArrived = arrived
            holdNextGameRead = inFlight
            viewModel.openOnlineGame(GAME)
            arrived.await()

            viewModel.openOnlineGame(OTHER_GAME)
            inFlight.complete(Unit)
            viewModel.gameJob?.join()

            val ready = viewModel.game as OnlineGameState.Ready
            assertEquals("the game the player asked for is the one that is shown", OTHER_GAME, ready.game.gameId)
        }

    @Test
    fun anUpdateForAnotherGameThatArrivesDuringADashboardLoadIsNotDropped() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start()
            viewModel.startupJob?.join()
            viewModel.dashboardJob?.join()

            // The dashboard is being fetched — against a waking instance this can be a long
            // wait — when a game the player is not looking at changes.
            val arrived = CompletableDeferred<Unit>()
            val inFlight = CompletableDeferred<Unit>()
            heldReadArrived = arrived
            holdNextDashboardRead = inFlight
            viewModel.loadDashboard()
            arrived.await()

            activeSeries = 1
            viewModel.onRealtimeMessage(
                RealtimeMessageDto(type = RealtimeMessageDto.GAME_UPDATED, gameId = OTHER_GAME, version = 2),
            )
            runCurrent()

            inFlight.complete(Unit)
            viewModel.dashboardJob?.join()

            assertEquals("the dashboard must not be left behind the update", 1, viewModel.dashboard.entries.size)
        }

    // --- A move must be neither lost nor played twice ---------------------------------

    @Test
    fun aMoveWhoseReplyIsLostIsFoundExactlyOnceWhenTheConnectionReturns() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.openOnlineGame(GAME)
            viewModel.gameJob?.join()

            // The server accepts the move and the reply never arrives, which is the failure
            // a player cannot tell apart from the move never being sent.
            loseNextReply = true
            viewModel.tapSquare(Square.parse("e2"))
            viewModel.tapSquare(Square.parse("e4"))
            viewModel.moveJob?.join()

            assertEquals("the server really did record it", listOf("e2e4"), played.toList())
            val stranded = viewModel.game as OnlineGameState.Ready
            assertEquals("the screen is still on the state the move was decided against", 1L, stranded.game.version)
            assertNotNull("and says something happened", stranded.message)

            viewModel.onRealtimeMessage(RealtimeMessageDto(type = RealtimeMessageDto.CONNECTED))
            viewModel.gameJob?.join()

            val recovered = viewModel.game as OnlineGameState.Ready
            assertEquals("the move is there", listOf("e2e4"), recovered.game.moves)
            assertEquals(2L, recovered.game.version)
            assertEquals("and it was sent once", 1, paths.count { it == "/games/$GAME/moves" })
        }

    @Test
    fun playingTheSameMoveAgainAfterALostReplyDoesNotPlayItTwice() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.openOnlineGame(GAME)
            viewModel.gameJob?.join()

            loseNextReply = true
            viewModel.tapSquare(Square.parse("e2"))
            viewModel.tapSquare(Square.parse("e4"))
            viewModel.moveJob?.join()

            // The player, seeing nothing happen, plays it again. It goes at the version it
            // was decided against, which is what makes it a duplicate rather than a new move
            // (`D021`), so the server refuses it and attaches what it actually has.
            viewModel.tapSquare(Square.parse("e2"))
            viewModel.tapSquare(Square.parse("e4"))
            viewModel.moveJob?.join()

            assertEquals("the move is recorded once", listOf("e2e4"), played.toList())
            val ready = viewModel.game as OnlineGameState.Ready
            assertEquals("and the refusal corrected the screen", 2L, ready.game.version)
            assertEquals(listOf("e2e4"), ready.game.moves)
        }

    // --- A Render idle cold start -----------------------------------------------------

    @Test
    fun aReloadWaitsThroughAColdStartRatherThanCallingItAFailure() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.openOnlineGame(GAME)
            viewModel.gameJob?.join()

            // The instance has spun down: the first requests after it fail, and then it is up.
            offline = true
            viewModel.loadGame(GAME)
            runCurrent()
            offline = false
            viewModel.gameJob?.join()

            assertTrue("a sleeping instance is not a broken one", viewModel.game is OnlineGameState.Ready)
        }

    @Test
    fun theSocketKeepsTryingThroughAColdStartAndRefreshesOnceItConnects() =
        runTest(dispatcher) {
            var attempts = 0
            var connected = false
            val source =
                RealtimeSource {
                    flow {
                        attempts++
                        // The instance is asleep, so the socket is refused until it is not.
                        if (!connected) throw IOException("no route to host")
                        emit(RealtimeMessageDto(type = RealtimeMessageDto.CONNECTED))
                        awaitCancellation()
                    }
                }
            val viewModel = viewModel(realtime = source)
            viewModel.openOnlineGame(GAME)
            viewModel.gameJob?.join()
            val before = paths.size

            viewModel.watchUpdates()
            advanceTimeBy(20_000)
            assertTrue("the socket keeps trying while the instance wakes", attempts > 1)

            played += "e7e5"
            connected = true
            advanceTimeBy(20_000)
            runCurrent()
            viewModel.gameJob?.join()

            assertTrue("connecting refreshes what is on screen", paths.size > before)
            val ready = viewModel.game as OnlineGameState.Ready
            assertEquals("with what happened while the app was cut off", listOf("e7e5"), ready.game.moves)

            viewModel.updatesJob?.cancel()
        }

    // --- Waking, retryable, and terminal stay apart ------------------------------------

    @Test
    fun aWakingServerARetryableFailureAndATerminalRefusalStayApart() =
        runTest(dispatcher) {
            // Terminal: the server answered, and the answer will not change.
            val forbidden = viewModel(httpClient = alwaysRefusing(HttpStatusCode.Forbidden))
            forbidden.openOnlineGame(GAME)
            forbidden.gameJob?.join()
            val terminal = forbidden.game as OnlineGameState.Failed
            assertFalse("a game that is not yours stays not yours", terminal.canRetry)

            // Retryable: nothing answered at all, so trying again is worth offering.
            offline = true
            val unreachable = viewModel()
            unreachable.openOnlineGame(GAME)
            unreachable.gameJob?.join()
            val retryable = unreachable.game as OnlineGameState.Failed
            assertTrue("an unreachable server may simply be gone for a moment", retryable.canRetry)

            // Waking: the same transport failure, reported as a state of its own rather than
            // as an error, which is what stops a cold start being filed as a bug (`D037`).
            val asleep =
                viewModel(
                    sessionStore =
                        object : SessionStore {
                            override suspend fun read(): AnonymousSession? = throw IOException("server asleep")

                            override suspend fun write(session: AnonymousSession) = Unit

                            override suspend fun clear() = Unit
                        },
                    wakePolicy = ServerWakePolicy(deadlineMillis = 5_000, initialDelayMillis = 50, maxDelayMillis = 50),
                )
            asleep.start()
            runCurrent()

            assertTrue("a cold start is its own state, not an error", asleep.startup is StartupState.Waking)
            asleep.startupJob?.cancel()
        }

    // --- The stubbed server ------------------------------------------------------------

    private fun viewModel(
        httpClient: HttpClient = statefulServer(),
        realtime: RealtimeSource = silentRealtime,
        sessionStore: SessionStore = InMemorySessionStore(storedSession),
        wakePolicy: ServerWakePolicy = impatientWake,
    ) = ChessAppViewModel(
        ChessAppDependencies(
            serverConfig = ChessServerConfig("https://chess.example"),
            supabaseConfig = SupabaseConfig(url = "https://supabase.example", anonKey = "publishable-key"),
            httpClient = httpClient,
            sessionStore = sessionStore,
            realtime = realtime,
            wakePolicy = wakePolicy,
        ),
        wakePolicy = wakePolicy,
    ).also(models::add)

    /**
     * One game that remembers what has been played to it.
     *
     * A command carrying the version the game is at is accepted and recorded; one carrying
     * an older version is refused as `STALE_VERSION` with the canonical state attached,
     * exactly as the server does (`D021`). That is what lets a test tell a duplicate from a
     * new move instead of counting requests.
     */
    private fun statefulServer(): HttpClient {
        val engine =
            MockEngine { request ->
                val path = request.url.encodedPath
                paths += path

                if (offline) throw IOException("connection reset by peer")

                if (path == "/dashboard") {
                    val gate = holdNextDashboardRead

                    if (gate != null) {
                        holdNextDashboardRead = null

                        val answeredWith = replyTo(path)
                        heldReadArrived?.complete(Unit)
                        gate.await()

                        return@MockEngine respond(
                            content = answeredWith,
                            status = HttpStatusCode.OK,
                            headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                        )
                    }
                }

                if (path == "/games/$GAME" && request.method == HttpMethod.Get) {
                    val gate = holdNextGameRead

                    if (gate != null) {
                        holdNextGameRead = null

                        // Decided *before* the wait, so a held read answers with the game as it
                        // was when the request was made — which is the whole point of holding
                        // it. Building the body afterwards would quietly include the very move
                        // the test is checking cannot be missed.
                        val answeredWith = replyTo(path)
                        heldReadArrived?.complete(Unit)
                        gate.await()

                        return@MockEngine respond(
                            content = answeredWith,
                            status = HttpStatusCode.OK,
                            headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                        )
                    }
                }

                if (path.endsWith("/moves")) {
                    val sent = (request.body as TextContent).text
                    val expected = versionIn(sent)

                    if (expected != version) {
                        return@MockEngine respond(
                            content = staleRefusal(),
                            status = HttpStatusCode.Conflict,
                            headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                        )
                    }

                    played += "${squareIn(sent, "from")}${squareIn(sent, "to")}"

                    // Applied, and only then lost: the server is ahead of the client, which is
                    // the state a naive retry would double.
                    if (loseNextReply) {
                        loseNextReply = false
                        throw IOException("connection reset by peer")
                    }
                }

                respond(
                    content = replyTo(path),
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                )
            }

        return HttpClient(engine) { install(ContentNegotiation) { json(ChessApiClient.Json) } }
    }

    /** A server that refuses everything about a game with [status], and means it. */
    private fun alwaysRefusing(status: HttpStatusCode): HttpClient {
        val engine =
            MockEngine { request ->
                paths += request.url.encodedPath
                respond(content = "no", status = status)
            }

        return HttpClient(engine) { install(ContentNegotiation) { json(ChessApiClient.Json) } }
    }

    private fun replyTo(path: String): String =
        when {
            path == "/me" -> """{"userId":"user-1","username":"Jordan"}"""
            path == "/dashboard" -> (1..activeSeries).joinToString(",", "[", "]", transform = ::dashboardLine)
            path.startsWith("/games/") -> gameView(path.removePrefix("/games/").substringBefore("/"))
            else -> "[]"
        }

    private fun dashboardLine(index: Int): String =
        """
        {"seriesId":"series-$index","opponent":{"userId":"user-2","username":"Alex"},"gameId":"$OTHER_GAME",
         "version":1,"yourSide":"WHITE","sideToMove":"WHITE","moveNumber":1,"yourTurn":true}
        """.trimIndent()

    private fun gameView(gameId: String): String =
        """
        {"gameId":"$gameId","seriesId":"series-1","opponent":{"userId":"user-2","username":"Alex"},
         "version":$version,"yourSide":"WHITE","sideToMove":"WHITE","yourTurn":true,"inCheck":false,
         "board":["rnbqkbnr","pppppppp","........","........","........","........","PPPPPPPP","RNBQKBNR"],
         "moves":${played.joinToString(",", "[", "]") { "\"$it\"" }},"moveNumber":1,"halfmoveClock":0,
         "canUndo":false,"availableDrawClaims":[]}
        """.trimIndent()

    private fun staleRefusal(): String =
        """
        {"reason":"STALE_VERSION","message":"The game has moved on","game":${gameView(GAME)}}
        """.trimIndent()

    private fun versionIn(body: String): Long =
        VERSION
            .find(body)
            ?.groupValues
            ?.get(1)
            ?.toLong()
            ?: error("no expectedVersion in $body")

    private fun squareIn(
        body: String,
        field: String,
    ): String = Regex("\"$field\"\\s*:\\s*\"([a-h][1-8])\"").find(body)?.groupValues?.get(1) ?: error("no $field in $body")

    private companion object {
        const val GAME = "game-7"
        const val OTHER_GAME = "game-9"

        val VERSION = Regex("\"expectedVersion\"\\s*:\\s*(\\d+)")
    }
}
