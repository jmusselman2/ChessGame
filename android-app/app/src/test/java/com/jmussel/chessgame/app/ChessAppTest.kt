package com.jmussel.chessgame.app

import com.jmussel.chessgame.api.ChessApiClient
import com.jmussel.chessgame.api.ChessServerConfig
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.auth.AnonymousSession
import com.jmussel.chessgame.auth.InMemorySessionStore
import com.jmussel.chessgame.auth.SessionStore
import com.jmussel.chessgame.auth.SupabaseConfig
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.navigation.AppNavigation
import com.jmussel.chessgame.navigation.Destination
import com.jmussel.chessgame.ui.dashboard.DashboardSections
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
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
        removalOutcome: String = "Removed",
        currentGameId: String? = "game-1",
        refusals: Int = 0,
        refusalPath: String? = null,
        refusalStatus: HttpStatusCode = HttpStatusCode.ServiceUnavailable,
        refusalBody: String = "nope",
    ): HttpClient {
        var refused = 0
        val engine =
            MockEngine { request ->
                requests += request
                val path = request.url.encodedPath
                val refusable = refusalPath == null || refusalPath == path
                val refuse = refusable && refused < refusals
                if (refusable) refused++

                respond(
                    content =
                        if (refuse) {
                            refusalBody
                        } else {
                            replyTo(request, path, Replies(username, friends, games, removalOutcome, currentGameId))
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
            path == "/dashboard" -> replies.games.joinToString(",", "[", "]", transform = ::entry)
            path == "/friends" && request.method == HttpMethod.Get ->
                replies.friends.joinToString(",", "[", "]", transform = ::user)
            path == "/friends" -> sentText(request)
            path.startsWith("/friends/") -> replies.removalOutcome
            path.startsWith("/users/") -> user(path.removePrefix("/users/"))
            path == "/series" -> series(sentText(request), replies.currentGameId)
            path.endsWith("/moves") -> playedGame(path.removePrefix("/games/").removeSuffix("/moves"))
            path.startsWith("/games/") -> gameView(path.removePrefix("/games/"))
            else -> "[]"
        }

    /** One dashboard line: a game with [opponent] that is waiting on the player. */
    private fun entry(opponent: String): String =
        """
        {"seriesId":"series-$opponent","opponent":${user(opponent)},"gameId":"game-$opponent",
         "version":1,"yourSide":"WHITE","sideToMove":"WHITE","moveNumber":3,"yourTurn":true}
        """.trimIndent()

    /** One game, as the stubbed server has it: a fresh board against Alex. */
    private fun gameView(gameId: String): String =
        """
        {"gameId":"$gameId","seriesId":"series-1","opponent":${user("Alex")},"version":1,
         "yourSide":"WHITE","sideToMove":"WHITE","yourTurn":true,"inCheck":false,
         "board":["rnbqkbnr","pppppppp","........","........","........","........","PPPPPPPP","RNBQKBNR"],
         "moves":[],"moveNumber":1,"halfmoveClock":0}
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

    private fun viewModel(
        httpClient: HttpClient = httpClient(),
        sessionStore: SessionStore = InMemorySessionStore(storedSession),
        supabaseConfig: SupabaseConfig = SupabaseConfig(url = "https://supabase.example", anonKey = "publishable-key"),
    ) = ChessAppViewModel(
        dependencies(httpClient = httpClient, sessionStore = sessionStore, supabaseConfig = supabaseConfig),
    )
}
