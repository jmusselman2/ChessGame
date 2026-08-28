package com.jmussel.chessgame.app

import com.jmussel.chessgame.api.ChessApiClient
import com.jmussel.chessgame.api.ChessServerConfig
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.auth.AnonymousSession
import com.jmussel.chessgame.auth.InMemorySessionStore
import com.jmussel.chessgame.auth.SessionStore
import com.jmussel.chessgame.auth.SupabaseConfig
import com.jmussel.chessgame.navigation.AppNavigation
import com.jmussel.chessgame.navigation.Destination
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
                    content = if (refuse) refusalBody else replyTo(request, path, username, friends, removalOutcome, currentGameId),
                    status = if (refuse) refusalStatus else HttpStatusCode.OK,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                )
            }

        return HttpClient(engine) {
            install(ContentNegotiation) { json(ChessApiClient.Json) }
        }
    }

    /** What the server would say to one request. */
    private fun replyTo(
        request: HttpRequestData,
        path: String,
        username: String?,
        friends: List<String>,
        removalOutcome: String,
        currentGameId: String?,
    ): String =
        when {
            path.startsWith("/auth/") -> newSession
            path == "/me" -> identity(username)
            path == "/username" -> sentText(request)
            path == "/friends" && request.method == HttpMethod.Get -> friends.joinToString(",", "[", "]", transform = ::user)
            path == "/friends" -> sentText(request)
            path.startsWith("/friends/") -> removalOutcome
            path.startsWith("/users/") -> user(path.removePrefix("/users/"))
            path == "/series" -> series(sentText(request), currentGameId)
            else -> "[]"
        }

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

            assertEquals("restoring a valid session must not call Supabase", listOf("/me"), paths)
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
            assertEquals(listOf("/auth/v1/signup", "/me", "/username"), paths)
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

            assertEquals(listOf("/series"), paths)
            assertEquals(Destination.OnlineGame("game-7"), viewModel.navigation.current)
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

    private fun viewModel(
        httpClient: HttpClient = httpClient(),
        sessionStore: SessionStore = InMemorySessionStore(storedSession),
        supabaseConfig: SupabaseConfig = SupabaseConfig(url = "https://supabase.example", anonKey = "publishable-key"),
    ) = ChessAppViewModel(
        dependencies(httpClient = httpClient, sessionStore = sessionStore, supabaseConfig = supabaseConfig),
    )
}
