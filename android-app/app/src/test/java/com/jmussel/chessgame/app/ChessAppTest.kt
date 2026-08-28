package com.jmussel.chessgame.app

import com.jmussel.chessgame.api.ChessApiClient
import com.jmussel.chessgame.api.ChessServerConfig
import com.jmussel.chessgame.api.RealtimeMessageDto
import com.jmussel.chessgame.api.RealtimeSource
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.auth.AnonymousSession
import com.jmussel.chessgame.auth.InMemorySessionStore
import com.jmussel.chessgame.auth.SessionStore
import com.jmussel.chessgame.auth.SupabaseConfig
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.navigation.AppNavigation
import com.jmussel.chessgame.navigation.Destination
import com.jmussel.chessgame.ui.dashboard.DashboardSections
import com.jmussel.chessgame.ui.game.AfterGame
import com.jmussel.chessgame.ui.game.OnlineGame
import com.jmussel.chessgame.ui.game.OnlineGameState
import com.jmussel.chessgame.ui.onboarding.UsernameClaim
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The application shell: what it is showing, what the screens are built from, and how
 * startup gets it there.
 *
 * Runs on the JVM against Ktor's `MockEngine`, so there is no Android runtime and no
 * network. The model's coroutines run on a test dispatcher, so "loading" is a state a test
 * can actually observe.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChessAppTest {
    // Written by the engine from whichever coroutine made the request and read by assertions
    // while another job may still be sending: a snapshot-safe list keeps that honest.
    private val requests = CopyOnWriteArrayList<HttpRequestData>()

    /** Every model this test built, so the jobs they started can be stopped afterwards. */
    private val models = mutableListOf<ChessAppViewModel>()

    private val dispatcher = StandardTestDispatcher()

    /**
     * A connection that stays open and says nothing.
     *
     * The default for tests that are not about realtime: a source that ended would have the
     * model waiting to reconnect for the rest of the test.
     */
    private val silentRealtime = RealtimeSource { flow { awaitCancellation() } }

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

    /**
     * Stops everything the models under test started.
     *
     * A real model is cleared with its `Activity`; here nothing clears it, and a realtime
     * loop left running would still be using `Dispatchers.Main` when the next test sets it.
     */
    @After
    fun releaseTheTestDispatcher() {
        models.forEach { model ->
            listOf(
                model.startupJob,
                model.usernameClaimJob,
                model.friendsJob,
                model.dashboardJob,
                model.gameJob,
                model.moveJob,
                model.updatesJob,
            ).forEach { job -> job?.cancel() }
        }

        Dispatchers.resetMain()
    }

    /**
     * One engine for both APIs, as the app has one client for both.
     *
     * An auth path hands out [newSession], `/me` reports [username], `/username` accepts the
     * name that was sent, `/friends` lists [friends] or accepts one, a lookup finds whoever
     * was asked for, a removal answers [removalOutcome], and `/series` opens a series on
     * [currentGameId]. The first [refusals] calls — to [refusalPath], or to anything when
     * that is null — are refused instead, which is how a failure and then a retry are
     * staged.
     */
    private fun httpClient(
        username: String? = null,
        friends: List<String> = emptyList(),
        games: List<String> = emptyList(),
        canUndo: Boolean = false,
        claims: List<String> = emptyList(),
        yourTurn: Boolean = true,
        removalOutcome: String = "Removed",
        currentGameId: String? = "game-1",
        nextGameId: String? = null,
        nextSide: String = "WHITE",
        finished: Boolean = false,
        finishOnSecondRead: Boolean = false,
        refusals: Int = 0,
        refusalPath: String? = null,
        refusalStatus: HttpStatusCode = HttpStatusCode.ServiceUnavailable,
        refusalBody: String = "nope",
    ): HttpClient {
        var refused = 0
        var gameReads = 0
        val engine =
            MockEngine { request ->
                requests += request
                val path = request.url.encodedPath
                val refusable = refusalPath == null || refusalPath == path
                val refuse = refusable && refused < refusals
                if (refusable) refused++
                if (path.startsWith("/games/") && !path.removePrefix("/games/").contains("/")) gameReads++

                respond(
                    content =
                        if (refuse) {
                            refusalBody
                        } else {
                            replyTo(
                                request,
                                path,
                                Replies(
                                    username = username,
                                    friends = friends,
                                    games = games,
                                    removalOutcome = removalOutcome,
                                    currentGameId = currentGameId,
                                    canUndo = canUndo,
                                    claims = claims,
                                    yourTurn = yourTurn,
                                    nextGameId = nextGameId,
                                    nextSide = nextSide,
                                    finished = finished || (finishOnSecondRead && gameReads > 1),
                                ),
                            )
                        },
                    status = if (refuse) refusalStatus else HttpStatusCode.OK,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                )
            }

        return HttpClient(engine) {
            install(ContentNegotiation) { json(ChessApiClient.Json) }
        }
    }

    /** Everything the stubbed server has to say, so one parameter carries it all. */
    private data class Replies(
        val username: String?,
        val friends: List<String>,
        val games: List<String>,
        val removalOutcome: String,
        val currentGameId: String?,
        val canUndo: Boolean,
        val claims: List<String>,
        val yourTurn: Boolean,
        val nextGameId: String?,
        val nextSide: String,
        val finished: Boolean,
    )

    /** What the server would say to one request. */
    private fun replyTo(
        request: HttpRequestData,
        path: String,
        replies: Replies,
    ): String =
        when {
            path.startsWith("/auth/") -> newSession
            path == "/me" -> identity(replies.username)
            path == "/username" -> sentText(request)
            path == "/dashboard" -> dashboard(replies)
            path == "/friends" && request.method == HttpMethod.Get ->
                replies.friends.joinToString(",", "[", "]", transform = ::user)
            path == "/friends" -> sentText(request)
            path.startsWith("/friends/") -> replies.removalOutcome
            path.startsWith("/users/") -> user(path.removePrefix("/users/"))
            path == "/series" -> series(sentText(request), replies.currentGameId)
            path.endsWith("/moves") -> playedGame(path.removePrefix("/games/").removeSuffix("/moves"))
            path.endsWith("/undo") -> gameView(path.removePrefix("/games/").removeSuffix("/undo"), canUndo = false)
            path.endsWith("/draw-claims") -> drawnGame(path.removePrefix("/games/").removeSuffix("/draw-claims"))
            path.endsWith("/resignation") -> resignedGame(path.removePrefix("/games/").removeSuffix("/resignation"))
            path.startsWith("/games/") ->
                gameView(
                    gameId = path.removePrefix("/games/"),
                    canUndo = replies.canUndo,
                    claims = replies.claims,
                    yourTurn = replies.yourTurn,
                    yourSide =
                        if (path.endsWith(replies.nextGameId.orEmpty()) && replies.nextGameId != null) {
                            replies.nextSide
                        } else {
                            "WHITE"
                        },
                    finished = replies.finished,
                )
            else -> "[]"
        }

    /**
     * The dashboard: one line per game in [Replies.games], plus the series this game belongs
     * to when the server has moved it on to a new game.
     */
    private fun dashboard(replies: Replies): String {
        val nextGame = replies.nextGameId?.let { listOf(seriesLine(it)) }.orEmpty()

        return (replies.games.map(::entry) + nextGame).joinToString(",", "[", "]")
    }

    /** One dashboard line: a game with [opponent] that is waiting on the player. */
    private fun entry(opponent: String): String =
        """
        {"seriesId":"series-$opponent","opponent":${user(opponent)},"gameId":"game-$opponent",
         "version":1,"yourSide":"WHITE","sideToMove":"WHITE","moveNumber":3,"yourTurn":true}
        """.trimIndent()

    /** The series the stubbed games belong to, now at [gameId]. */
    private fun seriesLine(gameId: String): String =
        """
        {"seriesId":"series-1","opponent":${user("Alex")},"gameId":"$gameId",
         "version":1,"yourSide":"BLACK","sideToMove":"WHITE","moveNumber":1,"yourTurn":false}
        """.trimIndent()

    /** One game, as the stubbed server has it: a fresh board against Alex. */
    private fun gameView(
        gameId: String,
        canUndo: Boolean = false,
        claims: List<String> = emptyList(),
        yourTurn: Boolean = true,
        yourSide: String = "WHITE",
        finished: Boolean = false,
    ): String {
        val ending = if (finished) ""","result":"WHITE_WINS","terminationReason":"CHECKMATE"""" else ""

        return """
            {"gameId":"$gameId","seriesId":"series-1","opponent":${user("Alex")},"version":1,
             "yourSide":"$yourSide","sideToMove":"WHITE","yourTurn":${yourTurn && !finished},"inCheck":false,
             "board":["rnbqkbnr","pppppppp","........","........","........","........","PPPPPPPP","RNBQKBNR"],
             "moves":[],"moveNumber":1,"halfmoveClock":0,"canUndo":${canUndo && !finished},
             "availableDrawClaims":${claims.joinToString(",", "[", "]") { "\"$it\"" }}$ending}
            """.trimIndent()
    }

    /** The same game once its White player has resigned. */
    private fun resignedGame(gameId: String): String =
        """
        {"gameId":"$gameId","seriesId":"series-1","opponent":${user("Alex")},"version":2,
         "yourSide":"WHITE","sideToMove":"WHITE","yourTurn":false,"inCheck":false,
         "board":["rnbqkbnr","pppppppp","........","........","........","........","PPPPPPPP","RNBQKBNR"],
         "moves":[],"moveNumber":1,"halfmoveClock":0,
         "result":"BLACK_WINS","terminationReason":"RESIGNATION"}
        """.trimIndent()

    /** The same game once a claimed draw has ended it. */

    private fun drawnGame(gameId: String): String =
        """
        {"gameId":"$gameId","seriesId":"series-1","opponent":${user("Alex")},"version":2,
         "yourSide":"WHITE","sideToMove":"WHITE","yourTurn":false,"inCheck":false,
         "board":["rnbqkbnr","pppppppp","........","........","........","........","PPPPPPPP","RNBQKBNR"],
         "moves":[],"moveNumber":1,"halfmoveClock":0,
         "result":"DRAW","terminationReason":"THREEFOLD_REPETITION_CLAIM"}
        """.trimIndent()

    /** The same game after 1. e2e4, which is what the stubbed server answers a move with. */
    private fun playedGame(gameId: String): String =
        """
        {"gameId":"$gameId","seriesId":"series-1","opponent":${user("Alex")},"version":2,
         "yourSide":"WHITE","sideToMove":"BLACK","yourTurn":false,"inCheck":false,
         "board":["rnbqkbnr","pppppppp","........","........","....P...","........","PPPP.PPP","RNBQKBNR"],
         "moves":["e2e4"],"lastMove":{"from":"e2","to":"e4"},"moveNumber":1,"halfmoveClock":0}
        """.trimIndent()

    /** A refusal carrying the canonical state, as the server sends one for a stale command. */
    private val staleRejection =
        """
        {"reason":"STALE_VERSION","message":"This game is at version 9","game":
         {"gameId":"game-7","seriesId":"series-1","opponent":{"userId":"user-Alex","username":"Alex"},
          "version":9,"yourSide":"WHITE","sideToMove":"WHITE","yourTurn":true,"inCheck":false,
          "board":["rnbqkbnr","pppp.ppp","........","....p...","....P...","........","PPPP.PPP","RNBQKBNR"],
          "moves":["e2e4","e7e5"],"lastMove":{"from":"e7","to":"e5"},"moveNumber":2,"halfmoveClock":0}}
        """.trimIndent()

    private fun sentText(request: HttpRequestData): String = (request.body as TextContent).text

    /** What `/me` says about a player with, or without, a name. */
    private fun identity(username: String?): String =
        if (username == null) """{"userId":"server-1"}""" else """{"userId":"server-1","username":"$username"}"""

    private fun user(username: String): String = """{"userId":"user-$username","username":"$username"}"""

    private fun series(
        opponent: String,
        currentGameId: String?,
    ): String {
        val game = currentGameId?.let { ""","currentGameId":"$it"""" }.orEmpty()

        return """{"seriesId":"series-1","opponent":${user(opponent)},"status":"ACTIVE"$game}"""
    }

    private val paths: List<String>
        get() = requests.map { it.url.encodedPath }

    /** What was actually sent, for the assertions about versions travelling with commands. */
    private val sentBodies: List<String>
        get() = requests.mapNotNull { (it.body as? TextContent)?.text }

    private fun dependencies(
        httpClient: HttpClient = httpClient(),
        sessionStore: SessionStore = InMemorySessionStore(storedSession),
        supabaseConfig: SupabaseConfig = SupabaseConfig(url = "https://supabase.example", anonKey = "publishable-key"),
        realtime: RealtimeSource = silentRealtime,
    ) = ChessAppDependencies(
        serverConfig = ChessServerConfig("https://chess.example"),
        supabaseConfig = supabaseConfig,
        httpClient = httpClient,
        sessionStore = sessionStore,
        realtime = realtime,
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
    fun startupIsLoadingUntilThereIsAnIdentityAndThenTheDashboard() =
        runTest(dispatcher) {
            val viewModel = viewModel(httpClient = httpClient(username = "Jordan"))

            viewModel.start()

            assertEquals(StartupState.Loading, viewModel.startup)
            assertEquals("nothing is opened before the server answers", Destination.Startup, viewModel.navigation.current)

            viewModel.startupJob?.join()

            assertEquals("Jordan", viewModel.currentUser?.username)
            assertEquals(AppNavigation(listOf(Destination.Dashboard)), viewModel.navigation)
        }

    @Test
    fun aReturningNamedPlayerSkipsOnboarding() =
        runTest(dispatcher) {
            val viewModel = viewModel(httpClient = httpClient(username = "Jordan"))

            viewModel.start()
            viewModel.startupJob?.join()

            // The dashboard load that follows is another job; only the startup calls matter here.
            assertEquals("restoring a valid session must not call Supabase", listOf("/me"), paths.take(1))
            assertFalse(paths.any { it.startsWith("/auth/") })
            assertEquals(Destination.Dashboard, viewModel.navigation.current)
        }

    @Test
    fun aFirstRunSignsUpAndIsSentToChooseAUsername() =
        runTest(dispatcher) {
            val viewModel = viewModel(sessionStore = InMemorySessionStore())

            viewModel.start()
            viewModel.startupJob?.join()

            assertEquals(listOf("/auth/v1/signup", "/me"), paths)
            assertEquals(AppNavigation(listOf(Destination.UsernameOnboarding)), viewModel.navigation)
        }

    @Test
    fun startingTwiceDoesNotCreateTwoAnonymousAccounts() =
        runTest(dispatcher) {
            val viewModel = viewModel(sessionStore = InMemorySessionStore())

            viewModel.start()
            viewModel.start()
            viewModel.startupJob?.join()
            viewModel.start()
            viewModel.startupJob?.join()

            assertEquals(listOf("/auth/v1/signup", "/me"), paths)
        }

    @Test
    fun aFailedStartupStaysOnTheStartupScreenAndCanBeTriedAgain() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    httpClient = httpClient(username = "Jordan", refusals = 1),
                    sessionStore = InMemorySessionStore(),
                )

            viewModel.start()
            viewModel.startupJob?.join()

            val failure = viewModel.startup as StartupState.Failed
            assertTrue(failure.canRetry)
            assertEquals(Destination.Startup, viewModel.navigation.current)

            viewModel.start()
            viewModel.startupJob?.join()

            assertEquals("Jordan", viewModel.currentUser?.username)
            assertEquals(AppNavigation(listOf(Destination.Dashboard)), viewModel.navigation)
        }

    @Test
    fun claimingAUsernameGoesStraightOnToTheDashboard() =
        runTest(dispatcher) {
            val viewModel = viewModel(sessionStore = InMemorySessionStore())
            viewModel.start()
            viewModel.startupJob?.join()

            viewModel.claimUsername("Jordan")
            viewModel.usernameClaimJob?.join()

            assertEquals(UsernameClaim.Idle, viewModel.usernameClaim)
            assertEquals("Jordan", viewModel.currentUser?.username)
            assertEquals(AppNavigation(listOf(Destination.Dashboard)), viewModel.navigation)
            assertEquals(listOf("/auth/v1/signup", "/me", "/username"), paths.take(3))
        }

    @Test
    fun aNameTheServerRefusesIsExplainedAndAnotherCanBeTried() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    httpClient =
                        httpClient(
                            refusals = 1,
                            refusalPath = "/username",
                            refusalStatus = HttpStatusCode.Conflict,
                            refusalBody = "That username is taken",
                        ),
                    sessionStore = InMemorySessionStore(storedSession),
                )
            viewModel.start()
            viewModel.startupJob?.join()

            viewModel.claimUsername("Jordan")
            viewModel.usernameClaimJob?.join()

            assertEquals(
                "the server's own words are what the player reads",
                UsernameClaim.Rejected("That username is taken"),
                viewModel.usernameClaim,
            )
            assertEquals(Destination.UsernameOnboarding, viewModel.navigation.current)

            viewModel.claimUsername("Jordan2")
            viewModel.usernameClaimJob?.join()

            assertEquals(UsernameClaim.Idle, viewModel.usernameClaim)
            assertEquals("Jordan2", viewModel.currentUser?.username)
            assertEquals(Destination.Dashboard, viewModel.navigation.current)
        }

    @Test
    fun anEmptyNameIsNotSentAnywhere() =
        runTest(dispatcher) {
            val viewModel = viewModel(sessionStore = InMemorySessionStore())
            viewModel.start()
            viewModel.startupJob?.join()

            viewModel.claimUsername("   ")
            viewModel.usernameClaimJob?.join()

            assertEquals(listOf("/auth/v1/signup", "/me"), paths)
            assertEquals(Destination.UsernameOnboarding, viewModel.navigation.current)
        }

    @Test
    fun aServerThatSaysNothingUsefulStillLeavesSomethingToRead() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    httpClient =
                        httpClient(
                            refusals = 1,
                            refusalPath = "/username",
                            refusalStatus = HttpStatusCode.BadGateway,
                            refusalBody = "",
                        ),
                    sessionStore = InMemorySessionStore(storedSession),
                )
            viewModel.start()
            viewModel.startupJob?.join()

            viewModel.claimUsername("Jordan")
            viewModel.usernameClaimJob?.join()

            val rejected = viewModel.usernameClaim as UsernameClaim.Rejected
            assertTrue(rejected.message.isNotBlank())
            assertEquals(Destination.UsernameOnboarding, viewModel.navigation.current)
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

    @Test
    fun openingFriendsShowsTheScreenAndLoadsTheList() =
        runTest(dispatcher) {
            val viewModel = viewModel(httpClient = httpClient(friends = listOf("Alex", "Sam")))

            viewModel.openFriends()
            viewModel.friendsJob?.join()

            assertEquals(Destination.Friends, viewModel.navigation.current)
            assertEquals(listOf("Alex", "Sam"), viewModel.friends.friends.map { it.username })
            assertTrue(viewModel.friends.loaded)
            assertFalse(viewModel.friends.loading)
        }

    @Test
    fun anAccountWithNoFriendsIsLoadedAndEmptyRatherThanStillWaiting() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.loadFriends()
            viewModel.friendsJob?.join()

            assertTrue(viewModel.friends.friends.isEmpty())
            assertTrue("an empty list is an answer", viewModel.friends.loaded)
        }

    @Test
    fun aListThatCannotBeLoadedLeavesSomethingToReadAndCanBeTriedAgain() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    httpClient = httpClient(friends = listOf("Alex"), refusals = 1, refusalPath = "/friends"),
                )

            viewModel.loadFriends()
            viewModel.friendsJob?.join()

            assertFalse("nothing arrived, so nothing is claimed to have", viewModel.friends.loaded)
            assertTrue(
                viewModel.friends.message
                    .orEmpty()
                    .isNotBlank(),
            )

            viewModel.loadFriends()
            viewModel.friendsJob?.join()

            assertEquals(listOf("Alex"), viewModel.friends.friends.map { it.username })
        }

    @Test
    fun aUsernameIsLookedUpBeforeItIsAdded() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.findUser("  Alex ")
            viewModel.friendsJob?.join()

            assertEquals("Alex", viewModel.friends.found?.username)
            assertEquals(listOf("/users/Alex"), paths)
        }

    @Test
    fun aNameThatBelongsToNobodyIsExplainedAndFindsNobody() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    httpClient =
                        httpClient(
                            refusals = 1,
                            refusalPath = "/users/Nobody",
                            refusalStatus = HttpStatusCode.NotFound,
                            refusalBody = "No such user",
                        ),
                )

            viewModel.findUser("Nobody")
            viewModel.friendsJob?.join()

            assertEquals("No such user", viewModel.friends.message)
            assertNull(viewModel.friends.found)
        }

    @Test
    fun addingTheFoundUserRefreshesTheList() =
        runTest(dispatcher) {
            val viewModel = viewModel(httpClient = httpClient(friends = listOf("Alex")))

            viewModel.findUser("Alex")
            viewModel.friendsJob?.join()

            viewModel.addFriend("Alex")
            viewModel.friendsJob?.join()

            assertNull("the found user has been dealt with", viewModel.friends.found)
            assertEquals(listOf("Alex"), viewModel.friends.friends.map { it.username })
            assertEquals(listOf("/users/Alex", "/friends", "/friends"), paths)
        }

    @Test
    fun addingSomeoneAlreadyAFriendIsExplainedInTheServersWords() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    httpClient =
                        httpClient(
                            refusals = 1,
                            refusalPath = "/friends",
                            refusalStatus = HttpStatusCode.Conflict,
                            refusalBody = "Already friends with Alex",
                        ),
                )

            viewModel.addFriend("Alex")
            viewModel.friendsJob?.join()

            assertEquals("Already friends with Alex", viewModel.friends.message)
        }

    @Test
    fun addingYourselfIsExplainedInTheServersWords() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    httpClient =
                        httpClient(
                            refusals = 1,
                            refusalPath = "/friends",
                            refusalStatus = HttpStatusCode.BadRequest,
                            refusalBody = "You cannot add yourself",
                        ),
                )

            viewModel.addFriend("Jordan")
            viewModel.friendsJob?.join()

            assertEquals("You cannot add yourself", viewModel.friends.message)
        }

    @Test
    fun anEmptyNameIsNeitherLookedUpNorAdded() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.findUser("   ")
            viewModel.addFriend("")
            viewModel.friendsJob?.join()

            assertTrue(paths.isEmpty())
        }

    @Test
    fun aRemovalIsConfirmedBeforeItHappens() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val alex = UserSummaryDto(userId = "user-1", username = "Alex")

            viewModel.askToRemoveFriend(alex)

            assertEquals(alex, viewModel.friends.removing)
            assertTrue("nothing is sent until it is confirmed", paths.isEmpty())

            viewModel.cancelRemoveFriend()

            assertNull(viewModel.friends.removing)
            assertTrue(paths.isEmpty())
        }

    @Test
    fun aConfirmedRemovalSaysWhatItDidAndRefreshesTheList() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    httpClient =
                        httpClient(
                            friends = emptyList(),
                            removalOutcome = "Removed Alex; your current game finishes first",
                        ),
                )
            val alex = UserSummaryDto(userId = "user-1", username = "Alex")

            viewModel.askToRemoveFriend(alex)
            viewModel.removeFriend(alex)
            viewModel.friendsJob?.join()

            assertNull(viewModel.friends.removing)
            assertEquals("Removed Alex; your current game finishes first", viewModel.friends.message)
            assertEquals(listOf("/friends/Alex", "/friends"), paths)
        }

    @Test
    fun playingAFriendAsksTheServerForTheSeriesAndOpensItsGame() =
        runTest(dispatcher) {
            val viewModel = viewModel(httpClient = httpClient(currentGameId = "game-7"))
            viewModel.restartAt(Destination.Friends)

            viewModel.playFriend(UserSummaryDto(userId = "user-1", username = "Alex"))
            viewModel.friendsJob?.join()
            viewModel.gameJob?.join()

            assertEquals("the series is asked for first", "/series", paths.first())
            assertEquals(Destination.OnlineGame("game-7"), viewModel.navigation.current)
            assertTrue("and the game it named is loaded", paths.contains("/games/game-7"))
        }

    @Test
    fun aSeriesWithNoGameYetOpensNothingAndSaysSo() =
        runTest(dispatcher) {
            val viewModel = viewModel(httpClient = httpClient(currentGameId = null))
            viewModel.restartAt(Destination.Friends)

            viewModel.playFriend(UserSummaryDto(userId = "user-1", username = "Alex"))
            viewModel.friendsJob?.join()

            assertEquals(Destination.Friends, viewModel.navigation.current)
            assertTrue(
                viewModel.friends.message
                    .orEmpty()
                    .contains("Alex"),
            )
        }

    @Test
    fun landingOnTheDashboardLoadsTheGamesAndTheFriendsTogether() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(httpClient = httpClient(username = "Jordan", friends = listOf("Alex"), games = listOf("Alex")))

            viewModel.start()
            viewModel.startupJob?.join()
            viewModel.dashboardJob?.join()

            assertEquals(Destination.Dashboard, viewModel.navigation.current)
            assertEquals(listOf("Alex"), viewModel.dashboard.entries.map { it.opponent.username })
            assertEquals(listOf("Alex"), viewModel.friends.friends.map { it.username })
            assertTrue(viewModel.dashboard.loaded)
            assertFalse(viewModel.dashboard.loading)
            assertTrue(paths.containsAll(listOf("/me", "/dashboard", "/friends")))
        }

    @Test
    fun aPlayerWithNothingToPlayHasALoadedEmptyDashboard() =
        runTest(dispatcher) {
            val viewModel = viewModel(httpClient = httpClient(username = "Jordan"))

            viewModel.start()
            viewModel.startupJob?.join()
            viewModel.dashboardJob?.join()

            assertTrue(viewModel.dashboard.entries.isEmpty())
            assertTrue("an empty dashboard is an answer", viewModel.dashboard.loaded)
            assertNull(viewModel.dashboard.message)
        }

    @Test
    fun aDashboardThatCannotBeLoadedSaysSoAndCanBeTriedAgain() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    httpClient =
                        httpClient(
                            username = "Jordan",
                            games = listOf("Alex"),
                            refusals = 1,
                            refusalPath = "/dashboard",
                        ),
                )

            viewModel.start()
            viewModel.startupJob?.join()
            viewModel.dashboardJob?.join()

            assertFalse("nothing arrived, so nothing is claimed to have", viewModel.dashboard.loaded)
            assertTrue(
                viewModel.dashboard.message
                    .orEmpty()
                    .isNotBlank(),
            )
            assertEquals("the player is still at home", Destination.Dashboard, viewModel.navigation.current)

            viewModel.loadDashboard()
            viewModel.dashboardJob?.join()

            assertTrue(viewModel.dashboard.loaded)
            assertEquals(listOf("Alex"), viewModel.dashboard.entries.map { it.opponent.username })
            assertNull(viewModel.dashboard.message)
        }

    @Test
    fun aNewPlayerSeesOnboardingAndNoDashboardRequest() =
        runTest(dispatcher) {
            val viewModel = viewModel(sessionStore = InMemorySessionStore())

            viewModel.start()
            viewModel.startupJob?.join()
            viewModel.dashboardJob?.join()

            assertEquals(Destination.UsernameOnboarding, viewModel.navigation.current)
            assertFalse("there is nothing to show a player without a name", paths.contains("/dashboard"))
        }

    @Test
    fun claimingAUsernameLandsOnALoadedDashboard() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(httpClient = httpClient(friends = listOf("Alex")), sessionStore = InMemorySessionStore())
            viewModel.start()
            viewModel.startupJob?.join()

            viewModel.claimUsername("Jordan")
            viewModel.usernameClaimJob?.join()
            viewModel.dashboardJob?.join()

            assertEquals(Destination.Dashboard, viewModel.navigation.current)
            assertEquals(listOf("Alex"), viewModel.friends.friends.map { it.username })
            assertTrue(viewModel.dashboard.loaded)
        }

    @Test
    fun selectingAGameOpensTheIdTheServerGaveIt() =
        runTest(dispatcher) {
            val viewModel = viewModel(httpClient = httpClient(username = "Jordan", games = listOf("Alex")))
            viewModel.start()
            viewModel.startupJob?.join()
            viewModel.dashboardJob?.join()

            val row = DashboardSections.yourTurn(viewModel.dashboard.entries).single()
            viewModel.openGame(row)

            assertEquals(Destination.OnlineGame("game-Alex"), viewModel.navigation.current)
            assertEquals(Destination.Dashboard, viewModel.navigation.back()?.current)
        }

    @Test
    fun playingAFriendFromTheDashboardAsksTheServerRefreshesAndOpensTheGame() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    httpClient = httpClient(username = "Jordan", friends = listOf("Alex"), currentGameId = "game-7"),
                )
            viewModel.start()
            viewModel.startupJob?.join()
            viewModel.dashboardJob?.join()

            val row = DashboardSections.friends(viewModel.friends.friends, viewModel.dashboard.entries).single()
            assertEquals("Play", row.action)

            viewModel.playFriend(row)
            viewModel.dashboardJob?.join()

            assertEquals(Destination.OnlineGame("game-7"), viewModel.navigation.current)
            assertEquals("the dashboard is reloaded after a series is opened", 2, paths.count { it == "/dashboard" })
        }

    @Test
    fun openingAFriendWithAGameUnderWayGoesThroughTheServerToo() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    httpClient =
                        httpClient(
                            username = "Jordan",
                            friends = listOf("Alex"),
                            games = listOf("Alex"),
                            currentGameId = "game-Alex",
                        ),
                )
            viewModel.start()
            viewModel.startupJob?.join()
            viewModel.dashboardJob?.join()

            val row = DashboardSections.friends(viewModel.friends.friends, viewModel.dashboard.entries).single()
            assertEquals("Open", row.action)

            viewModel.playFriend(row)
            viewModel.dashboardJob?.join()

            assertTrue("the server decides which game that is", paths.contains("/series"))
            assertEquals(Destination.OnlineGame("game-Alex"), viewModel.navigation.current)
        }

    @Test
    fun aSeriesThatWillNotOpenLeavesThePlayerOnTheDashboardWithSomethingToRead() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    httpClient =
                        httpClient(
                            username = "Jordan",
                            friends = listOf("Alex"),
                            refusals = 1,
                            refusalPath = "/series",
                            refusalStatus = HttpStatusCode.Forbidden,
                            refusalBody = "Not friends with Alex",
                        ),
                )
            viewModel.start()
            viewModel.startupJob?.join()
            viewModel.dashboardJob?.join()

            val row = DashboardSections.friends(viewModel.friends.friends, viewModel.dashboard.entries).single()
            viewModel.playFriend(row)
            viewModel.dashboardJob?.join()

            assertEquals(Destination.Dashboard, viewModel.navigation.current)
            assertEquals("Not friends with Alex", viewModel.dashboard.message)
        }

    @Test
    fun openingAGameLoadsItFromTheServerWithNothingButItsId() =
        runTest(dispatcher) {
            val viewModel = viewModel(httpClient = httpClient(username = "Jordan"))

            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()

            assertEquals(Destination.OnlineGame("game-7"), viewModel.navigation.current)

            val ready = viewModel.game as OnlineGameState.Ready
            assertEquals("game-7", ready.game.gameId)
            assertEquals("Alex", ready.game.opponent.username)
            assertEquals(listOf("/games/game-7"), paths)
        }

    @Test
    fun aGameIsLoadingUntilTheServerHasAnswered() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.openOnlineGame("game-7")

            assertEquals(OnlineGameState.Loading("game-7"), viewModel.game)

            viewModel.gameJob?.join()

            assertTrue(viewModel.game is OnlineGameState.Ready)
        }

    @Test
    fun aGameThatIsNotYoursSaysSoAndOffersNoRetry() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    httpClient =
                        httpClient(
                            refusals = 1,
                            refusalPath = "/games/game-7",
                            refusalStatus = HttpStatusCode.Forbidden,
                            refusalBody = "You are not playing this game",
                        ),
                )

            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()

            val failed = viewModel.game as OnlineGameState.Failed
            assertFalse(failed.canRetry)
            assertTrue(failed.message.isNotBlank())
        }

    @Test
    fun aGameThatDoesNotExistSaysSoAndOffersNoRetry() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    httpClient =
                        httpClient(
                            refusals = 1,
                            refusalPath = "/games/game-7",
                            refusalStatus = HttpStatusCode.NotFound,
                            refusalBody = "No such game",
                        ),
                )

            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()

            assertFalse((viewModel.game as OnlineGameState.Failed).canRetry)
        }

    @Test
    fun aGameThatFailedToLoadCanBeTriedAgain() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    httpClient =
                        httpClient(
                            refusals = 1,
                            refusalPath = "/games/game-7",
                            refusalStatus = HttpStatusCode.ServiceUnavailable,
                            refusalBody = "later",
                        ),
                )

            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()

            assertTrue((viewModel.game as OnlineGameState.Failed).canRetry)

            viewModel.reloadGame()
            viewModel.gameJob?.join()

            assertEquals("game-7", (viewModel.game as OnlineGameState.Ready).game.gameId)
        }

    @Test
    fun aGameOpenedFromTheDashboardIsLoadedTheSameWay() =
        runTest(dispatcher) {
            val viewModel = viewModel(httpClient = httpClient(username = "Jordan", games = listOf("Alex")))
            viewModel.start()
            viewModel.startupJob?.join()
            viewModel.dashboardJob?.join()

            viewModel.openGame(DashboardSections.yourTurn(viewModel.dashboard.entries).single())
            viewModel.gameJob?.join()

            assertEquals(Destination.OnlineGame("game-Alex"), viewModel.navigation.current)
            assertEquals("game-Alex", (viewModel.game as OnlineGameState.Ready).game.gameId)
        }

    @Test
    fun goingBackFromAGameReturnsToTheDashboard() =
        runTest(dispatcher) {
            val viewModel = viewModel(httpClient = httpClient(username = "Jordan", games = listOf("Alex")))
            viewModel.start()
            viewModel.startupJob?.join()
            viewModel.dashboardJob?.join()

            viewModel.openGame(DashboardSections.yourTurn(viewModel.dashboard.entries).single())
            viewModel.gameJob?.join()

            assertTrue(viewModel.back())
            assertEquals(Destination.Dashboard, viewModel.navigation.current)
        }

    @Test
    fun playingAMoveSendsItAndShowsWhatCameBack() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()

            viewModel.tapSquare(Square.parse("e2"))
            assertEquals(Square.parse("e2"), (viewModel.game as OnlineGameState.Ready).selected)

            viewModel.tapSquare(Square.parse("e4"))
            assertTrue("input is closed while the server decides", (viewModel.game as OnlineGameState.Ready).submitting)

            viewModel.moveJob?.join()

            val ready = viewModel.game as OnlineGameState.Ready
            assertEquals(listOf("e2e4"), ready.game.moves)
            assertFalse(ready.submitting)
            assertEquals(listOf("/games/game-7", "/games/game-7/moves"), paths)
        }

    @Test
    fun theBoardDoesNotMoveUntilTheServerSaysSo() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()

            val before = (viewModel.game as OnlineGameState.Ready).game.board

            viewModel.tapSquare(Square.parse("e2"))
            viewModel.tapSquare(Square.parse("e4"))

            assertEquals("nothing moves on the way out", before, (viewModel.game as OnlineGameState.Ready).game.board)

            viewModel.moveJob?.join()

            assertNotEquals(before, (viewModel.game as OnlineGameState.Ready).game.board)
        }

    @Test
    fun aSecondTapWhileAMoveIsInFlightSendsNothing() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()

            viewModel.tapSquare(Square.parse("e2"))
            viewModel.tapSquare(Square.parse("e4"))
            viewModel.tapSquare(Square.parse("d2"))
            viewModel.tapSquare(Square.parse("d4"))
            viewModel.moveJob?.join()

            assertEquals("exactly one move was sent", 1, paths.count { it.endsWith("/moves") })
        }

    @Test
    fun aStaleRefusalReplacesTheScreenWithTheStateItCarried() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    httpClient =
                        httpClient(
                            refusals = 1,
                            refusalPath = "/games/game-7/moves",
                            refusalStatus = HttpStatusCode.Conflict,
                            refusalBody = staleRejection,
                        ),
                )
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()

            viewModel.tapSquare(Square.parse("e2"))
            viewModel.tapSquare(Square.parse("e4"))
            viewModel.moveJob?.join()

            val ready = viewModel.game as OnlineGameState.Ready
            assertEquals("the canonical state came back with the refusal", 9, ready.game.version.toInt())
            assertEquals(listOf("e2e4", "e7e5"), ready.game.moves)
            assertTrue(ready.message.orEmpty().contains("moved on"))
            assertFalse(ready.submitting)
        }

    @Test
    fun aRefusedMoveLeavesTheGameWhereItWasAndSaysWhy() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    httpClient =
                        httpClient(
                            refusals = 1,
                            refusalPath = "/games/game-7/moves",
                            refusalStatus = HttpStatusCode.UnprocessableEntity,
                            refusalBody = """{"reason":"ILLEGAL_MOVE","message":"e2e4 is not legal here"}""",
                        ),
                )
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()
            val before = (viewModel.game as OnlineGameState.Ready).game

            viewModel.tapSquare(Square.parse("e2"))
            viewModel.tapSquare(Square.parse("e4"))
            viewModel.moveJob?.join()

            val ready = viewModel.game as OnlineGameState.Ready
            assertEquals(before, ready.game)
            assertTrue(ready.message.orEmpty().contains("not legal"))
        }

    @Test
    fun aMoveThatNeverReachesTheServerLeavesSomethingToRead() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    httpClient =
                        httpClient(
                            refusals = 1,
                            refusalPath = "/games/game-7/moves",
                            refusalStatus = HttpStatusCode.BadGateway,
                            refusalBody = "",
                        ),
                )
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()

            viewModel.tapSquare(Square.parse("e2"))
            viewModel.tapSquare(Square.parse("e4"))
            viewModel.moveJob?.join()

            val ready = viewModel.game as OnlineGameState.Ready
            assertTrue(ready.message.orEmpty().isNotBlank())
            assertFalse(ready.submitting)
        }

    @Test
    fun aFreshConnectionRefreshesWhatIsOnScreen() =
        runTest(dispatcher) {
            val viewModel = viewModel(httpClient = httpClient(username = "Jordan"))
            viewModel.start()
            viewModel.startupJob?.join()
            viewModel.dashboardJob?.join()
            val before = paths.size

            viewModel.onRealtimeMessage(RealtimeMessageDto(type = RealtimeMessageDto.CONNECTED))
            viewModel.dashboardJob?.join()

            assertTrue("a live connection means what is on screen may be old", paths.size > before)
            assertTrue(paths.drop(before).contains("/dashboard"))
        }

    @Test
    fun anUpdateForTheOpenGameReloadsItOverHttps() =
        runTest(dispatcher) {
            val viewModel = viewModel(httpClient = httpClient(username = "Jordan"))
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()

            viewModel.onRealtimeMessage(
                RealtimeMessageDto(type = RealtimeMessageDto.GAME_UPDATED, gameId = "game-7", version = 12),
            )
            viewModel.gameJob?.join()

            assertEquals(listOf("/games/game-7", "/games/game-7"), paths)
            assertEquals(
                "the version pushed is not the version shown; the reload decides that",
                1,
                (viewModel.game as OnlineGameState.Ready).game.version.toInt(),
            )
        }

    @Test
    fun anUpdateForAnotherGameLeavesTheOpenGameAlone() =
        runTest(dispatcher) {
            val viewModel = viewModel(httpClient = httpClient(username = "Jordan", games = listOf("Alex")))
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()
            val showing = viewModel.game

            viewModel.onRealtimeMessage(
                RealtimeMessageDto(type = RealtimeMessageDto.GAME_UPDATED, gameId = "game-other", version = 3),
            )
            viewModel.dashboardJob?.join()
            viewModel.gameJob?.join()

            assertEquals("the open game is untouched", showing, viewModel.game)
            assertTrue(paths.contains("/dashboard"))
            assertFalse(paths.contains("/games/game-other"))
        }

    @Test
    fun theSameUpdateTwiceIsHarmless() =
        runTest(dispatcher) {
            val viewModel = viewModel(httpClient = httpClient(username = "Jordan"))
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()

            val update = RealtimeMessageDto(type = RealtimeMessageDto.GAME_UPDATED, gameId = "game-7", version = 12)
            viewModel.onRealtimeMessage(update)
            viewModel.gameJob?.join()
            viewModel.onRealtimeMessage(update)
            viewModel.gameJob?.join()

            assertTrue(viewModel.game is OnlineGameState.Ready)
            assertEquals(3, paths.count { it == "/games/game-7" })
        }

    @Test
    fun aMessageThisAppDoesNotKnowIsIgnored() =
        runTest(dispatcher) {
            val viewModel = viewModel(httpClient = httpClient(username = "Jordan"))
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()
            val before = paths.size

            viewModel.onRealtimeMessage(RealtimeMessageDto(type = "something-new"))

            assertEquals(before, paths.size)
        }

    @Test
    fun theConnectionIsOpenedAndReopenedWhileTheAppLives() =
        runTest(dispatcher) {
            var connections = 0
            val source =
                RealtimeSource {
                    flow {
                        connections++
                        emit(RealtimeMessageDto(type = RealtimeMessageDto.CONNECTED))
                        // The socket drops, which ends the flow.
                    }
                }
            val viewModel = viewModel(realtime = source)

            viewModel.watchUpdates()
            advanceTimeBy(10_000)

            assertTrue("a dropped connection is opened again", connections > 1)

            viewModel.updatesJob?.cancel()
        }

    @Test
    fun aConnectionThatFailsIsTriedAgainRatherThanGivingUp() =
        runTest(dispatcher) {
            var attempts = 0
            val source =
                RealtimeSource {
                    flow {
                        attempts++
                        throw IOException("no route to host")
                    }
                }
            val viewModel = viewModel(realtime = source)

            viewModel.watchUpdates()
            advanceTimeBy(10_000)

            assertTrue(attempts > 1)

            viewModel.updatesJob?.cancel()
        }

    @Test
    fun theSocketAddressFollowsTheServersScheme() {
        assertEquals("wss://chess.example/ws", ChessServerConfig("https://chess.example").webSocketUrl("/ws"))
        assertEquals("ws://10.0.2.2:8080/ws", ChessServerConfig("http://10.0.2.2:8080").webSocketUrl("/ws"))
    }

    @Test
    fun undoIsSentOnlyWhenTheServerSaysThisPlayerMay() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()

            assertFalse("a game with nothing to take back offers nothing", (viewModel.game as OnlineGameState.Ready).game.canUndo)

            viewModel.undoMove()
            viewModel.moveJob?.join()

            assertEquals(listOf("/games/game-7"), paths)
        }

    @Test
    fun anEligibleUndoIsSentWithItsVersionAndTheAnswerBecomesTheScreen() =
        runTest(dispatcher) {
            val viewModel = viewModel(httpClient = httpClient(canUndo = true))
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()

            viewModel.undoMove()
            assertTrue((viewModel.game as OnlineGameState.Ready).submitting)

            viewModel.moveJob?.join()

            val ready = viewModel.game as OnlineGameState.Ready
            assertEquals(listOf("/games/game-7", "/games/game-7/undo"), paths)
            assertTrue("the version travels with the command", sentBodies.last().contains("\"expectedVersion\":1"))
            assertEquals("the board is whatever came back", emptyList<String>(), ready.game.moves)
            assertFalse(ready.submitting)
        }

    @Test
    fun anUndoWithNothingToTakeBackIsExplainedAndChangesNothing() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    httpClient =
                        httpClient(
                            canUndo = true,
                            refusals = 1,
                            refusalPath = "/games/game-7/undo",
                            refusalStatus = HttpStatusCode.Conflict,
                            refusalBody = """{"reason":"NOTHING_TO_UNDO","message":"There is nothing to take back"}""",
                        ),
                )
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()
            val before = (viewModel.game as OnlineGameState.Ready).game

            viewModel.undoMove()
            viewModel.moveJob?.join()

            val ready = viewModel.game as OnlineGameState.Ready
            assertEquals(before, ready.game)
            assertTrue(ready.message.orEmpty().contains("nothing to take back"))
        }

    @Test
    fun aStaleUndoIsRecoveredFromTheStateTheRefusalCarried() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    httpClient =
                        httpClient(
                            canUndo = true,
                            refusals = 1,
                            refusalPath = "/games/game-7/undo",
                            refusalStatus = HttpStatusCode.Conflict,
                            refusalBody = staleRejection,
                        ),
                )
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()

            viewModel.undoMove()
            viewModel.moveJob?.join()

            val ready = viewModel.game as OnlineGameState.Ready
            assertEquals(9, ready.game.version.toInt())
            assertEquals(listOf("e2e4", "e7e5"), ready.game.moves)
            assertTrue(ready.message.orEmpty().contains("moved on"))
        }

    @Test
    fun anUndoInAFinishedGameIsExplained() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    httpClient =
                        httpClient(
                            canUndo = true,
                            refusals = 1,
                            refusalPath = "/games/game-7/undo",
                            refusalStatus = HttpStatusCode.Conflict,
                            refusalBody = """{"reason":"GAME_OVER","message":"This game has finished"}""",
                        ),
                )
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()

            viewModel.undoMove()
            viewModel.moveJob?.join()

            assertTrue((viewModel.game as OnlineGameState.Ready).message.orEmpty().contains("finished"))
        }

    @Test
    fun anUndoAnnouncedOverTheSocketIsJustAnotherReload() =
        runTest(dispatcher) {
            val viewModel = viewModel(httpClient = httpClient(canUndo = true))
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()

            viewModel.onRealtimeMessage(
                RealtimeMessageDto(type = RealtimeMessageDto.GAME_UPDATED, gameId = "game-7", version = 4),
            )
            viewModel.gameJob?.join()

            assertEquals(2, paths.count { it == "/games/game-7" })
        }

    @Test
    fun onlyTheClaimsTheServerListedCanBeMade() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()

            viewModel.claimDraw("THREEFOLD_REPETITION")
            viewModel.moveJob?.join()

            assertEquals("a game with no claim available sends nothing", listOf("/games/game-7"), paths)
        }

    @Test
    fun aThreefoldClaimIsSentWithItsVersionAndTheResultComesBack() =
        runTest(dispatcher) {
            val viewModel = viewModel(httpClient = httpClient(claims = listOf("THREEFOLD_REPETITION")))
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()

            viewModel.claimDraw("THREEFOLD_REPETITION")
            assertTrue((viewModel.game as OnlineGameState.Ready).submitting)
            viewModel.moveJob?.join()

            val ready = viewModel.game as OnlineGameState.Ready
            assertEquals(listOf("/games/game-7", "/games/game-7/draw-claims"), paths)
            assertTrue(sentBodies.last().contains("\"expectedVersion\":1"))
            assertTrue(sentBodies.last().contains("THREEFOLD_REPETITION"))
            assertEquals("DRAW", ready.game.result)
            assertEquals("THREEFOLD_REPETITION_CLAIM", ready.game.terminationReason)
        }

    @Test
    fun aFiftyMoveClaimIsSentAsItsOwnRule() =
        runTest(dispatcher) {
            val viewModel = viewModel(httpClient = httpClient(claims = listOf("FIFTY_MOVE_RULE")))
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()

            viewModel.claimDraw("FIFTY_MOVE_RULE")
            viewModel.moveJob?.join()

            assertTrue(sentBodies.last().contains("FIFTY_MOVE_RULE"))
            assertEquals("DRAW", (viewModel.game as OnlineGameState.Ready).game.result)
        }

    @Test
    fun theTwoClaimsAreLabelledApart() {
        assertEquals("Claim draw (threefold repetition)", OnlineGame.claimLabel("THREEFOLD_REPETITION"))
        assertEquals("Claim draw (fifty-move rule)", OnlineGame.claimLabel("FIFTY_MOVE_RULE"))
    }

    @Test
    fun aClaimTheServerRefusesLeavesTheGameRunning() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    httpClient =
                        httpClient(
                            claims = listOf("THREEFOLD_REPETITION"),
                            refusals = 1,
                            refusalPath = "/games/game-7/draw-claims",
                            refusalStatus = HttpStatusCode.Conflict,
                            refusalBody = """{"reason":"NO_SUCH_CLAIM","message":"No such claim"}""",
                        ),
                )
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()
            val before = (viewModel.game as OnlineGameState.Ready).game

            viewModel.claimDraw("THREEFOLD_REPETITION")
            viewModel.moveJob?.join()

            val ready = viewModel.game as OnlineGameState.Ready
            assertEquals("the app never decides a game is drawn", before, ready.game)
            assertTrue(ready.message.orEmpty().contains("no draw to claim"))
        }

    @Test
    fun aStaleClaimIsRecoveredFromTheStateTheRefusalCarried() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    httpClient =
                        httpClient(
                            claims = listOf("THREEFOLD_REPETITION"),
                            refusals = 1,
                            refusalPath = "/games/game-7/draw-claims",
                            refusalStatus = HttpStatusCode.Conflict,
                            refusalBody = staleRejection,
                        ),
                )
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()

            viewModel.claimDraw("THREEFOLD_REPETITION")
            viewModel.moveJob?.join()

            val ready = viewModel.game as OnlineGameState.Ready
            assertEquals(9, ready.game.version.toInt())
            assertTrue(ready.message.orEmpty().contains("moved on"))
        }

    @Test
    fun aClaimInAFinishedGameIsExplained() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    httpClient =
                        httpClient(
                            claims = listOf("THREEFOLD_REPETITION"),
                            refusals = 1,
                            refusalPath = "/games/game-7/draw-claims",
                            refusalStatus = HttpStatusCode.Conflict,
                            refusalBody = """{"reason":"GAME_OVER","message":"This game has finished"}""",
                        ),
                )
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()

            viewModel.claimDraw("THREEFOLD_REPETITION")
            viewModel.moveJob?.join()

            assertTrue((viewModel.game as OnlineGameState.Ready).message.orEmpty().contains("finished"))
        }

    @Test
    fun resigningIsAskedAboutBeforeAnythingIsSent() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()

            viewModel.askToResign()

            assertTrue((viewModel.game as OnlineGameState.Ready).confirmingResignation)
            assertEquals("nothing is sent until it is confirmed", listOf("/games/game-7"), paths)
        }

    @Test
    fun cancellingLeavesTheGameExactlyAsItWas() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()
            val before = (viewModel.game as OnlineGameState.Ready).game

            viewModel.askToResign()
            viewModel.cancelResignation()

            val ready = viewModel.game as OnlineGameState.Ready
            assertFalse(ready.confirmingResignation)
            assertEquals(before, ready.game)
            assertEquals(listOf("/games/game-7"), paths)
        }

    @Test
    fun aConfirmedResignationIsSentWithItsVersionAndTheResultComesBack() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()

            viewModel.askToResign()
            viewModel.resign()
            assertTrue((viewModel.game as OnlineGameState.Ready).submitting)
            viewModel.moveJob?.join()

            val ready = viewModel.game as OnlineGameState.Ready
            assertEquals(listOf("/games/game-7", "/games/game-7/resignation"), paths)
            assertTrue(sentBodies.last().contains("\"expectedVersion\":1"))
            assertEquals("BLACK_WINS", ready.game.result)
            assertEquals("RESIGNATION", ready.game.terminationReason)
            assertFalse(ready.confirmingResignation)
        }

    @Test
    fun resigningIsOfferedWhenItIsTheOpponentsMoveToo() =
        runTest(dispatcher) {
            val viewModel = viewModel(httpClient = httpClient(yourTurn = false))
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()

            viewModel.askToResign()
            viewModel.resign()
            viewModel.moveJob?.join()

            assertEquals("BLACK_WINS", (viewModel.game as OnlineGameState.Ready).game.result)
        }

    @Test
    fun resigningAGameThatHasAlreadyFinishedIsExplained() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    httpClient =
                        httpClient(
                            refusals = 1,
                            refusalPath = "/games/game-7/resignation",
                            refusalStatus = HttpStatusCode.Conflict,
                            refusalBody = """{"reason":"GAME_OVER","message":"This game has finished"}""",
                        ),
                )
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()

            viewModel.askToResign()
            viewModel.resign()
            viewModel.moveJob?.join()

            assertTrue((viewModel.game as OnlineGameState.Ready).message.orEmpty().contains("finished"))
        }

    @Test
    fun aStaleResignationRecoversFromTheStateTheRefusalCarried() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    httpClient =
                        httpClient(
                            refusals = 1,
                            refusalPath = "/games/game-7/resignation",
                            refusalStatus = HttpStatusCode.Conflict,
                            refusalBody = staleRejection,
                        ),
                )
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()

            viewModel.askToResign()
            viewModel.resign()
            viewModel.moveJob?.join()

            assertEquals(9, (viewModel.game as OnlineGameState.Ready).game.version.toInt())
        }

    @Test
    fun aGameThatIsNotYoursCannotBeResigned() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    httpClient =
                        httpClient(
                            refusals = 1,
                            refusalPath = "/games/game-7/resignation",
                            refusalStatus = HttpStatusCode.Forbidden,
                            refusalBody = "You are not playing this game",
                        ),
                )
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()

            viewModel.askToResign()
            viewModel.resign()
            viewModel.moveJob?.join()

            val ready = viewModel.game as OnlineGameState.Ready
            assertTrue(ready.message.orEmpty().isNotBlank())
            assertNull("nothing was decided locally", ready.game.result)
        }

    @Test
    fun aGameThatEndsUnderThePlayerAsksWhatTheSeriesDidNext() =
        runTest(dispatcher) {
            val viewModel = viewModel(httpClient = httpClient(username = "Jordan", nextGameId = "game-2"))
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()

            viewModel.askToResign()
            viewModel.resign()
            viewModel.moveJob?.join()
            viewModel.dashboardJob?.join()

            val ready = viewModel.game as OnlineGameState.Ready
            assertEquals(AfterGame.NextGame("game-2"), ready.after)
            assertTrue("the client never creates a rematch", paths.none { it == "/series" })
            assertEquals("the series is read once", 1, paths.count { it == "/dashboard" })
        }

    @Test
    fun theNextGameOpensWithTheColoursTheServerChose() =
        runTest(dispatcher) {
            val viewModel = viewModel(httpClient = httpClient(username = "Jordan", nextGameId = "game-2", nextSide = "BLACK"))
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()
            viewModel.askToResign()
            viewModel.resign()
            viewModel.moveJob?.join()
            viewModel.dashboardJob?.join()

            viewModel.openNextGame()
            viewModel.gameJob?.join()

            val ready = viewModel.game as OnlineGameState.Ready
            assertEquals(Destination.OnlineGame("game-2"), viewModel.navigation.current)
            assertEquals("the side comes from canonical data, not from the game before", "BLACK", ready.game.yourSide)
            assertEquals(Side.BLACK, OnlineGame.sideOf(ready.game))
        }

    @Test
    fun aSeriesThatHasClosedOffersNoNextGame() =
        runTest(dispatcher) {
            val viewModel = viewModel(httpClient = httpClient(username = "Jordan"))
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()

            viewModel.askToResign()
            viewModel.resign()
            viewModel.moveJob?.join()
            viewModel.dashboardJob?.join()

            assertEquals(AfterGame.SeriesOver, (viewModel.game as OnlineGameState.Ready).after)
        }

    @Test
    fun aClosedSeriesLeadsBackToTheDashboardWithoutWaiting() =
        runTest(dispatcher) {
            val viewModel = viewModel(httpClient = httpClient(username = "Jordan"))
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()
            viewModel.askToResign()
            viewModel.resign()
            viewModel.moveJob?.join()
            viewModel.dashboardJob?.join()

            viewModel.returnToDashboard()
            viewModel.dashboardJob?.join()

            assertEquals(AppNavigation(listOf(Destination.Dashboard)), viewModel.navigation)
        }

    @Test
    fun aClaimedDrawEndsTheGameAndFollowsTheSeriesToo() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    httpClient =
                        httpClient(username = "Jordan", claims = listOf("THREEFOLD_REPETITION"), nextGameId = "game-2"),
                )
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()

            viewModel.claimDraw("THREEFOLD_REPETITION")
            viewModel.moveJob?.join()
            viewModel.dashboardJob?.join()

            val ready = viewModel.game as OnlineGameState.Ready
            assertEquals("DRAW", ready.game.result)
            assertEquals(AfterGame.NextGame("game-2"), ready.after)
        }

    @Test
    fun aGameThatEndsWhileTheOpponentMovesIsFollowedTheSameWay() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    httpClient = httpClient(username = "Jordan", nextGameId = "game-2", finishOnSecondRead = true),
                )
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()

            // The opponent's checkmate arrives as an ordinary invalidation; the reload brings
            // back a finished game.
            viewModel.onRealtimeMessage(
                RealtimeMessageDto(type = RealtimeMessageDto.GAME_UPDATED, gameId = "game-7", version = 5),
            )
            viewModel.gameJob?.join()
            viewModel.dashboardJob?.join()

            assertEquals(AfterGame.NextGame("game-2"), (viewModel.game as OnlineGameState.Ready).after)
        }

    @Test
    fun aFinishedGameThatIsOnlyBeingLookedAtDoesNotChaseTheSeries() =
        runTest(dispatcher) {
            val viewModel = viewModel(httpClient = httpClient(username = "Jordan", finished = true))
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()
            viewModel.dashboardJob?.join()

            val ready = viewModel.game as OnlineGameState.Ready
            assertTrue(ready.game.isOver)
            assertEquals("looking at a game that was already over asks nothing", null, ready.after)
            assertFalse(paths.contains("/dashboard"))
        }

    @Test
    fun nothingCanBePlayedInAGameThatIsOver() =
        runTest(dispatcher) {
            val viewModel = viewModel(httpClient = httpClient(username = "Jordan", finished = true))
            viewModel.openOnlineGame("game-7")
            viewModel.gameJob?.join()
            val before = viewModel.game

            viewModel.tapSquare(Square.parse("e2"))
            viewModel.undoMove()
            viewModel.claimDraw("THREEFOLD_REPETITION")
            viewModel.askToResign()
            viewModel.resign()
            viewModel.moveJob?.join()

            assertEquals(before, viewModel.game)
            assertEquals(listOf("/games/game-7"), paths)
        }

    private fun viewModel(
        httpClient: HttpClient = httpClient(),
        sessionStore: SessionStore = InMemorySessionStore(storedSession),
        supabaseConfig: SupabaseConfig = SupabaseConfig(url = "https://supabase.example", anonKey = "publishable-key"),
        realtime: RealtimeSource = silentRealtime,
    ) = ChessAppViewModel(
        dependencies(
            httpClient = httpClient,
            sessionStore = sessionStore,
            supabaseConfig = supabaseConfig,
            realtime = realtime,
        ),
    ).also(models::add)
}
