package com.jmussel.chessgame.app

import com.jmussel.chessgame.api.ChessApiClient
import com.jmussel.chessgame.api.ChessServerConfig
import com.jmussel.chessgame.api.RealtimeSource
import com.jmussel.chessgame.api.ServerWakePolicy
import com.jmussel.chessgame.auth.AnonymousSession
import com.jmussel.chessgame.auth.InMemorySessionStore
import com.jmussel.chessgame.auth.SupabaseConfig
import com.jmussel.chessgame.navigation.Destination
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The "All users" page as the app drives it (`D071`): opening and loading it, and adding
 * from it through the friends screen's own add.
 *
 * Its own file, apart from [ChessAppTest], so removing the page removes this with it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AllUsersFlowTest {
    private val dispatcher = StandardTestDispatcher()
    private val requests = CopyOnWriteArrayList<HttpRequestData>()
    private val models = mutableListOf<ChessAppViewModel>()

    private val calls: List<String>
        get() = requests.map { "${it.method.value} ${it.url.encodedPath}" }

    @Before
    fun useTheTestDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun releaseTheTestDispatcher() {
        models.forEach { model -> listOf(model.allUsersJob, model.friendsJob).forEach { it?.cancel() } }
        Dispatchers.resetMain()
    }

    /**
     * A server that knows [everyone] and whom the caller is friends with.
     *
     * `GET /users` lists everyone who is not yet a friend, then the friends, and
     * `POST /friends` makes one.
     * The first [refusals] requests to [refusalCall] are refused with [refusalStatus] and
     * [refusalBody] instead.
     */
    private fun server(
        everyone: List<String> = listOf("Alex", "Sam"),
        refusalCall: String? = null,
        refusals: Int = 0,
        refusalStatus: HttpStatusCode = HttpStatusCode.NotFound,
        refusalBody: String = "",
    ): HttpClient {
        val friends = mutableListOf<String>()
        var refused = 0

        val engine =
            MockEngine { request ->
                requests += request
                val call = "${request.method.value} ${request.url.encodedPath}"

                if (call == refusalCall && refused < refusals) {
                    refused++
                    return@MockEngine respond(refusalBody, refusalStatus)
                }

                val body =
                    when {
                        call == "GET /users" -> listed(everyone - friends.toSet(), friends)
                        call == "GET /friends" -> users(friends)
                        request.method == HttpMethod.Post && request.url.encodedPath == "/friends" ->
                            (request.body as TextContent).text.also { friends += it }
                        else -> error("Nothing here answers $call")
                    }

                respond(body, HttpStatusCode.OK, headersOf("Content-Type", ContentType.Application.Json.toString()))
            }

        return HttpClient(engine) { install(ContentNegotiation) { json(ChessApiClient.Json) } }
    }

    private fun users(names: List<String>): String = names.joinToString(",", "[", "]") { """{"userId":"user-$it","username":"$it"}""" }

    private fun listed(
        others: List<String>,
        friends: List<String>,
    ): String {
        fun entry(
            name: String,
            friend: Boolean,
        ) = """{"userId":"user-$name","username":"$name","friend":$friend}"""

        return (others.map { entry(it, friend = false) } + friends.map { entry(it, friend = true) }).joinToString(",", "[", "]")
    }

    private fun viewModel(httpClient: HttpClient = server()): ChessAppViewModel {
        val dependencies =
            ChessAppDependencies(
                serverConfig = ChessServerConfig("https://chess.example"),
                supabaseConfig = SupabaseConfig(url = "https://supabase.example", anonKey = "publishable-key"),
                httpClient = httpClient,
                sessionStore =
                    InMemorySessionStore(
                        AnonymousSession(
                            accessToken = "access-1",
                            refreshToken = "refresh-1",
                            userId = "auth-user-1",
                            expiresAtEpochSeconds = Long.MAX_VALUE,
                        ),
                    ),
                realtime = RealtimeSource { flow { awaitCancellation() } },
                wakePolicy = ServerWakePolicy(deadlineMillis = 2_000, initialDelayMillis = 1, maxDelayMillis = 2),
            )

        return ChessAppViewModel(dependencies).also { model ->
            models += model
            model.restartAt(Destination.Dashboard)
            model.open(Destination.Friends)
        }
    }

    @Test
    fun openingThePageShowsItAndLoadsEveryoneTheServerLists() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.openAllUsers()
            viewModel.allUsersJob?.join()

            assertEquals(Destination.AllUsers, viewModel.navigation.current)
            assertEquals(listOf("Alex", "Sam"), viewModel.allUsers.users.map { it.username })
            assertTrue(viewModel.allUsers.loaded)
            assertFalse(viewModel.allUsers.loading)
            assertEquals(listOf("GET /users"), calls)
        }

    @Test
    fun nobodyToAddIsLoadedAndEmptyRatherThanStillWaiting() =
        runTest(dispatcher) {
            val viewModel = viewModel(server(everyone = emptyList()))

            viewModel.openAllUsers()
            viewModel.allUsersJob?.join()

            assertTrue(viewModel.allUsers.users.isEmpty())
            assertTrue("an empty list is an answer", viewModel.allUsers.loaded)
        }

    @Test
    fun aListThatWasRefusedSaysItIsNotThereAndCanBeTriedAgain() =
        runTest(dispatcher) {
            // A bare 404 is what an older app sees once the route has been removed.
            val viewModel = viewModel(server(refusalCall = "GET /users", refusals = 1))

            viewModel.openAllUsers()
            viewModel.allUsersJob?.join()

            assertFalse("nothing arrived, so nothing is claimed to have", viewModel.allUsers.loaded)
            assertEquals("The list of users is not available.", viewModel.allUsers.message)

            viewModel.loadAllUsers()
            viewModel.allUsersJob?.join()

            assertTrue(viewModel.allUsers.loaded)
            assertNull(viewModel.allUsers.message)
            assertEquals(listOf("Alex", "Sam"), viewModel.allUsers.users.map { it.username })
        }

    @Test
    fun aListThatCouldNotBeReachedSaysSo() =
        runTest(dispatcher) {
            val offline = HttpClient(MockEngine { throw IOException("offline") })
            val viewModel = viewModel(offline)

            viewModel.openAllUsers()
            viewModel.allUsersJob?.join()

            assertFalse(viewModel.allUsers.loaded)
            assertTrue(
                viewModel.allUsers.message
                    .orEmpty()
                    .startsWith("Could not reach the server"),
            )
        }

    @Test
    fun addingGoesThroughTheFriendsAddMarksTheRowAndStaysOnThePage() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.openAllUsers()
            viewModel.allUsersJob?.join()
            requests.clear()

            viewModel.addFromAllUsers(viewModel.allUsers.users.first { it.username == "Alex" })
            viewModel.allUsersJob?.join()

            // The same add as the friends screen: the name posted to /friends, then the list reloaded.
            assertEquals(listOf("POST /friends", "GET /friends"), calls)
            assertEquals("Alex", (requests.first().body as TextContent).text)

            assertEquals(Destination.AllUsers, viewModel.navigation.current)
            assertEquals(setOf("user-Alex"), viewModel.allUsers.added)
            assertNull(viewModel.allUsers.adding)
            assertEquals("the row stays, marked", listOf("Alex", "Sam"), viewModel.allUsers.users.map { it.username })

            viewModel.back()

            assertEquals(Destination.Friends, viewModel.navigation.current)
            assertEquals("Friends shows the new friend", listOf("Alex"), viewModel.friends.friends.map { it.username })
        }

    @Test
    fun aRefusedAddIsExplainedInTheServersWordsAndTheRowCanBeTriedAgain() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    server(
                        refusalCall = "POST /friends",
                        refusals = 1,
                        refusalStatus = HttpStatusCode.Conflict,
                        refusalBody = "Already friends with Alex",
                    ),
                )
            viewModel.openAllUsers()
            viewModel.allUsersJob?.join()

            viewModel.addFromAllUsers(viewModel.allUsers.users.first())
            viewModel.allUsersJob?.join()

            assertEquals("Already friends with Alex", viewModel.allUsers.message)
            assertTrue(viewModel.allUsers.added.isEmpty())
            assertNull(viewModel.allUsers.adding)
        }

    @Test
    fun reopeningThePageReloadsItAndWhoeverWasAddedIsListedWithTheFriends() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.openAllUsers()
            viewModel.allUsersJob?.join()
            viewModel.addFromAllUsers(viewModel.allUsers.users.first { it.username == "Alex" })
            viewModel.allUsersJob?.join()
            viewModel.back()

            viewModel.openAllUsers()
            viewModel.allUsersJob?.join()

            assertEquals(listOf("Sam", "Alex"), viewModel.allUsers.users.map { it.username })
            assertEquals(listOf(false, true), viewModel.allUsers.users.map { it.friend })
            assertTrue("nothing is marked on a fresh visit", viewModel.allUsers.added.isEmpty())
        }
}
