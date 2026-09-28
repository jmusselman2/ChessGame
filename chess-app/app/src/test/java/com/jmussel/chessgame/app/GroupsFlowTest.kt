package com.jmussel.chessgame.app

import com.jmussel.chessgame.api.ChessApiClient
import com.jmussel.chessgame.api.ChessServerConfig
import com.jmussel.chessgame.api.RealtimeSource
import com.jmussel.chessgame.api.ServerWakePolicy
import com.jmussel.chessgame.api.UserSummaryDto
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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The group screens as the app drives them (`D076`, `M17.11`): the list, creating a group,
 * adding a friend, playing a member who is not a friend, and leaving.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupsFlowTest {
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
        models.forEach { model -> listOf(model.groupsJob, model.groupJob, model.gameJob, model.updatesJob).forEach { it?.cancel() } }
        Dispatchers.resetMain()
    }

    /**
     * A server where the player is Taylor.
     *
     * Taylor is friends with [friends]. The group "Tuesday" (`group-1`) holds Taylor, Alex and
     * Robin, and Robin is not Taylor's friend: Alex added them. `POST /series` starts a game,
     * `game-new`, or offers `game-old` when [offer] is set. The first [refusals] requests to
     * [refusalCall] are refused with [refusalStatus] and [refusalBody] instead.
     */
    private inner class Server(
        val friends: MutableList<String> = mutableListOf("Alex", "Sam"),
        val offer: Boolean = false,
        val refusalCall: String? = null,
        var refusals: Int = 0,
        val refusalStatus: HttpStatusCode = HttpStatusCode.NotFound,
        val refusalBody: String = "",
    ) {
        val groups = linkedMapOf("group-1" to ("Tuesday" to mutableListOf("Taylor", "Alex", "Robin")))

        /** When set, `GET /groups` waits for it before answering. */
        var listGate: CompletableDeferred<Unit>? = null

        /** The groups Taylor is in, in the order they were made. */
        fun mine() = groups.filterValues { "Taylor" in it.second }.toList()

        val client: HttpClient =
            HttpClient(
                MockEngine { request ->
                    requests += request
                    val path = request.url.encodedPath
                    val call = "${request.method.value} $path"
                    val sent = (request.body as? TextContent)?.text.orEmpty()

                    if (call == refusalCall && refusals > 0) {
                        refusals--
                        return@MockEngine respond(refusalBody, refusalStatus)
                    }

                    fun json(
                        body: String,
                        status: HttpStatusCode = HttpStatusCode.OK,
                    ) = respond(body, status, headersOf("Content-Type", ContentType.Application.Json.toString()))

                    val groupId = path.removePrefix("/groups/").substringBefore('/')
                    val group = groups[groupId]?.takeIf { "Taylor" in it.second }

                    when {
                        call == "GET /me" -> json("""{"userId":"user-Taylor","username":"Taylor"}""")
                        call == "GET /dashboard" -> json("[]")
                        call == "GET /friends" -> json(users(friends))
                        call == "GET /groups" -> {
                            listGate?.await()
                            json(mine().joinToString(",", "[", "]") { (id, g) -> summary(id, g) })
                        }
                        call == "POST /groups" -> {
                            val id = "group-${groups.size + 1}"
                            groups[id] = sent to mutableListOf("Taylor")
                            json(summary(id, groups.getValue(id)), HttpStatusCode.Created)
                        }
                        group == null && path.startsWith("/groups/") -> respond("No such group", HttpStatusCode.NotFound)
                        call == "GET /groups/$groupId/members" -> json(users(group!!.second))
                        call == "POST /groups/$groupId/members" ->
                            if (sent in friends) {
                                group!!.second += sent
                                respond(sent, HttpStatusCode.OK)
                            } else {
                                respond("Add $sent as a friend first", HttpStatusCode.Forbidden)
                            }
                        call == "DELETE /groups/$groupId/members/me" -> {
                            group!!.second -= "Taylor"
                            respond("Left the group; your games are unaffected", HttpStatusCode.OK)
                        }
                        call == "POST /series" && offer ->
                            json("""{"existing":[${series(sent, "game-old")}]}""", HttpStatusCode.Conflict)
                        call == "POST /series" -> json(series(sent, "game-new"), HttpStatusCode.Created)
                        path.startsWith("/games/") -> respond("Not now", HttpStatusCode.ServiceUnavailable)
                        else -> error("Nothing here answers $call")
                    }
                },
            ) { install(ContentNegotiation) { json(ChessApiClient.Json) } }
    }

    private fun users(names: List<String>): String = names.joinToString(",", "[", "]") { """{"userId":"user-$it","username":"$it"}""" }

    private fun summary(
        id: String,
        group: Pair<String, List<String>>,
    ): String = """{"groupId":"$id","name":"${group.first}","createdBy":"user-Taylor","memberCount":${group.second.size}}"""

    private fun series(
        opponent: String,
        gameId: String,
    ): String =
        """{"seriesId":"series-$opponent","opponent":{"userId":"user-$opponent","username":"$opponent"},""" +
            """"status":"ACTIVE","currentGameId":"$gameId"}"""

    private fun viewModel(httpClient: HttpClient = Server().client): ChessAppViewModel {
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

    /** Opens the groups list, then Tuesday, and waits for both. */
    private suspend fun ChessAppViewModel.openTuesday() {
        openGroups()
        groupsJob?.join()
        openGroup(groups.groups.first { it.name == "Tuesday" })
        groupJob?.join()
    }

    @Test
    fun openingGroupsShowsTheGroupsThePlayerIsIn() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.openGroups()
            viewModel.groupsJob?.join()

            assertEquals(Destination.Groups, viewModel.navigation.current)
            assertEquals(listOf("Tuesday"), viewModel.groups.groups.map { it.name })
            assertEquals(
                3,
                viewModel.groups.groups
                    .single()
                    .memberCount,
            )
            assertTrue(viewModel.groups.loaded)
            assertEquals(listOf("GET /groups"), calls)

            viewModel.back()
            assertEquals(Destination.Friends, viewModel.navigation.current)
        }

    @Test
    fun noGroupsIsLoadedAndEmptyRatherThanStillWaiting() =
        runTest(dispatcher) {
            val server = Server().apply { groups.clear() }
            val viewModel = viewModel(server.client)

            viewModel.openGroups()
            viewModel.groupsJob?.join()

            assertTrue(viewModel.groups.groups.isEmpty())
            assertTrue("an empty list is an answer", viewModel.groups.loaded)
        }

    @Test
    fun aListThatCouldNotBeLoadedSaysSoAndCanBeTriedAgain() =
        runTest(dispatcher) {
            val server = Server(refusalCall = "GET /groups", refusals = 1, refusalStatus = HttpStatusCode.InternalServerError)
            val viewModel = viewModel(server.client)

            viewModel.openGroups()
            viewModel.groupsJob?.join()

            assertFalse(viewModel.groups.loaded)
            assertEquals("The server would not do that. Try again.", viewModel.groups.message)

            viewModel.loadGroups()
            viewModel.groupsJob?.join()

            assertTrue(viewModel.groups.loaded)
            assertNull(viewModel.groups.message)
            assertEquals(listOf("Tuesday"), viewModel.groups.groups.map { it.name })
        }

    @Test
    fun aListThatCouldNotBeReachedSaysSo() =
        runTest(dispatcher) {
            val viewModel = viewModel(HttpClient(MockEngine { throw IOException("offline") }))

            viewModel.openGroups()
            viewModel.groupsJob?.join()

            assertFalse(viewModel.groups.loaded)
            assertTrue(
                viewModel.groups.message
                    .orEmpty()
                    .startsWith("Could not reach the server"),
            )
        }

    @Test
    fun creatingAGroupSendsTheTrimmedNameAndOpensIt() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.openGroups()
            viewModel.groupsJob?.join()
            requests.clear()

            viewModel.createGroup("  Family ")
            viewModel.groupsJob?.join()
            viewModel.groupJob?.join()

            assertEquals("Family", (requests.first().body as TextContent).text)
            assertEquals(Destination.Group("group-2"), viewModel.navigation.current)
            assertEquals("Family", viewModel.group.group?.name)
            assertEquals(listOf("Taylor"), viewModel.group.members.map { it.username })
            assertEquals(listOf("Alex", "Sam"), viewModel.group.friends.map { it.username })

            viewModel.back()

            assertEquals(Destination.Groups, viewModel.navigation.current)
            assertEquals("the list already has it", listOf("Tuesday", "Family"), viewModel.groups.groups.map { it.name })
        }

    @Test
    fun aNameTheServerWouldRefuseIsNotSent() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.openGroups()
            viewModel.groupsJob?.join()
            requests.clear()

            viewModel.createGroup("   ")
            viewModel.createGroup("x".repeat(49))
            viewModel.groupsJob?.join()

            assertTrue(calls.isEmpty())
            assertEquals(Destination.Groups, viewModel.navigation.current)
        }

    @Test
    fun openingAGroupLoadsItsMembersAndThePlayersFriends() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.openTuesday()

            assertEquals(Destination.Group("group-1"), viewModel.navigation.current)
            assertEquals(listOf("Taylor", "Alex", "Robin"), viewModel.group.members.map { it.username })
            assertEquals(listOf("Alex", "Sam"), viewModel.group.friends.map { it.username })
            assertTrue(viewModel.group.loaded)
            assertFalse(viewModel.group.unavailable)
            assertEquals(setOf("GET /groups/group-1/members", "GET /friends"), calls.drop(1).toSet())
        }

    @Test
    fun addingAFriendAddsThemAndReloadsTheGroupAndTheList() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.openTuesday()
            requests.clear()

            viewModel.addToGroup(UserSummaryDto(userId = "user-Sam", username = "Sam"))
            viewModel.groupJob?.join()
            viewModel.groupsJob?.join()

            assertEquals("POST /groups/group-1/members", calls.first())
            assertEquals("Sam", (requests.first().body as TextContent).text)
            assertEquals(listOf("Taylor", "Alex", "Robin", "Sam"), viewModel.group.members.map { it.username })
            assertEquals("Added Sam.", viewModel.group.message)
            assertFalse(viewModel.group.busy)
            assertEquals(
                "the list's count is current",
                4,
                viewModel.groups.groups
                    .single()
                    .memberCount,
            )
        }

    @Test
    fun aRefusedAddIsExplainedInTheServersWords() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.openTuesday()

            viewModel.addToGroup(UserSummaryDto(userId = "user-Kim", username = "Kim"))
            viewModel.groupJob?.join()

            assertEquals("Add Kim as a friend first", viewModel.group.message)
            assertFalse(viewModel.group.unavailable)
            assertEquals(3, viewModel.group.members.size)
        }

    @Test
    fun aMemberWhoIsNotAFriendIsPlayedLikeAFriend() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.openTuesday()
            requests.clear()

            val robin = viewModel.group.members.first { it.username == "Robin" }
            assertFalse("Robin is not a friend", robin.username in viewModel.group.friends.map { it.username })

            viewModel.playGroupMember(robin)
            viewModel.groupJob?.join()

            assertEquals("POST /series", calls.first())
            assertEquals("Robin", (requests.first().body as TextContent).text)
            assertEquals(Destination.OnlineGame("game-new"), viewModel.navigation.current)
        }

    @Test
    fun playingAMemberWhoAlreadyHasASeriesOffersItLikeFriendsDoes() =
        runTest(dispatcher) {
            val viewModel = viewModel(Server(offer = true).client)
            viewModel.openTuesday()

            viewModel.playGroupMember(viewModel.group.members.first { it.username == "Robin" })
            viewModel.groupJob?.join()

            val offer = viewModel.playOffer
            assertNotNull(offer)
            offer!!
            assertEquals("Robin", offer.username)
            assertEquals("game-old", offer.newestGameId)
            assertEquals(Destination.Group("group-1"), viewModel.navigation.current)
        }

    @Test
    fun leavingAsksFirstThenLeavesAndReturnsToTheListWithoutTheGroup() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.openTuesday()

            viewModel.askToLeaveGroup()
            assertTrue(viewModel.group.confirmingLeave)

            viewModel.cancelLeaveGroup()
            assertFalse(viewModel.group.confirmingLeave)
            requests.clear()

            viewModel.askToLeaveGroup()
            viewModel.leaveGroup()
            viewModel.groupJob?.join()
            viewModel.groupsJob?.join()

            assertEquals("DELETE /groups/group-1/members/me", calls.first())
            assertEquals(Destination.Groups, viewModel.navigation.current)
            assertTrue(viewModel.groups.groups.isEmpty())
            assertEquals("Left the group; your games are unaffected", viewModel.groups.message)
        }

    @Test
    fun whatLeavingSaidSurvivesAListReadAlreadyInFlight() =
        runTest(dispatcher) {
            // Found on the Pixel 7: the list's read began before leaving wrote its message, and
            // its answer put back the list as it was before the message. Here the read is started
            // first, as a return to the app would start it (`D075`), and held until after the leave.
            val server = Server()
            val viewModel = viewModel(server.client)
            viewModel.openTuesday()
            val gate = CompletableDeferred<Unit>().also { server.listGate = it }
            viewModel.loadGroups()
            runCurrent()

            viewModel.askToLeaveGroup()
            viewModel.leaveGroup()
            viewModel.groupJob?.join()
            assertEquals("the list is still on its way", true, viewModel.groupsJob?.isActive)

            gate.complete(Unit)
            viewModel.groupsJob?.join()

            assertEquals("Left the group; your games are unaffected", viewModel.groups.message)
        }

    @Test
    fun aGroupThePlayerIsNoLongerInSaysItIsNotAvailable() =
        runTest(dispatcher) {
            val server = Server()
            val viewModel = viewModel(server.client)
            viewModel.openGroups()
            viewModel.groupsJob?.join()
            server.groups.getValue("group-1").second -= "Taylor"

            viewModel.openGroup(viewModel.groups.groups.single())
            viewModel.groupJob?.join()

            assertTrue(viewModel.group.unavailable)
            assertEquals("Tuesday", viewModel.group.group?.name)
        }

    @Test
    fun comingBackToTheAppReloadsTheGroupOnScreen() =
        runTest(dispatcher) {
            val server = Server()
            val viewModel = viewModel(server.client)
            viewModel.start()
            viewModel.startupJob?.join()
            viewModel.dashboardJob?.join()
            viewModel.openTuesday()

            // Group changes are not pushed (`D076`): Alex adds Kim while the app is away.
            viewModel.onBackground()
            server.groups.getValue("group-1").second += "Kim"
            viewModel.onForeground()
            viewModel.groupJob?.join()

            assertEquals(listOf("Taylor", "Alex", "Robin", "Kim"), viewModel.group.members.map { it.username })
        }

    @Test
    fun comingBackToTheAppReloadsTheGroupsListOnScreen() =
        runTest(dispatcher) {
            val server = Server()
            val viewModel = viewModel(server.client)
            viewModel.start()
            viewModel.startupJob?.join()
            viewModel.dashboardJob?.join()
            viewModel.openGroups()
            viewModel.groupsJob?.join()

            viewModel.onBackground()
            server.groups["group-9"] = "Family" to mutableListOf("Sam", "Taylor")
            viewModel.onForeground()
            viewModel.groupsJob?.join()

            assertEquals(listOf("Tuesday", "Family"), viewModel.groups.groups.map { it.name })
        }
}
