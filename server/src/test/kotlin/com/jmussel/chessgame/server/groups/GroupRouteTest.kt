@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.groups

import com.jmussel.chessgame.server.api.GroupSummary
import com.jmussel.chessgame.server.api.UserSummary
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GroupRepository
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.testModule
import com.jmussel.chessgame.server.user.Username
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The group routes: create, list, add a friend, leave (`D049`, `M19.2`).
 *
 * The isolation rule is the one worth testing at this level: a group the caller is not in
 * is **not found**, never forbidden, so ids cannot be probed for the existence of groups or
 * the size of them.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class GroupRouteTest {
    private val tokens = TestTokens()
    private val json = Json { ignoreUnknownKeys = true }

    private class Fixture(
        val users: UserRepository,
        val friendships: FriendshipRepository,
        val groups: GroupRepository,
    ) {
        fun named(
            subject: String,
            username: String,
        ): Uuid {
            val user = users.resolveBySubject(subject)
            users.claimUsername(user.id, Username.of(username))
            return user.id
        }

        fun nameless(subject: String): Uuid = users.resolveBySubject(subject).id

        fun befriend(
            first: Uuid,
            second: Uuid,
        ) {
            friendships.add(first, second)
        }
    }

    private fun withServer(block: suspend ApplicationTestBuilder.(Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database: Database = Databases.connect(dataSource)
            val friendships = FriendshipRepository(database)

            testApplication {
                application { testModule(tokens.verifier(), database) }
                block(
                    Fixture(
                        users = UserRepository(database),
                        friendships = friendships,
                        groups = GroupRepository(database, friendships),
                    ),
                )
            }
        }

    private suspend fun ApplicationTestBuilder.createGroup(
        subject: String,
        name: String,
    ): HttpResponse =
        client.post("/groups") {
            header("Authorization", "Bearer ${tokens.tokenFor(subject)}")
            setBody(name)
        }

    private suspend fun ApplicationTestBuilder.addMember(
        subject: String,
        groupId: String,
        username: String,
    ): HttpResponse =
        client.post("/groups/$groupId/members") {
            header("Authorization", "Bearer ${tokens.tokenFor(subject)}")
            setBody(username)
        }

    private suspend fun ApplicationTestBuilder.myGroups(subject: String): HttpResponse =
        client.get("/groups") { header("Authorization", "Bearer ${tokens.tokenFor(subject)}") }

    private suspend fun ApplicationTestBuilder.members(
        subject: String,
        groupId: String,
    ): HttpResponse = client.get("/groups/$groupId/members") { header("Authorization", "Bearer ${tokens.tokenFor(subject)}") }

    private suspend fun ApplicationTestBuilder.leave(
        subject: String,
        groupId: String,
    ): HttpResponse =
        client.delete("/groups/$groupId/members/me") {
            header("Authorization", "Bearer ${tokens.tokenFor(subject)}")
        }

    @Test
    fun creatingAGroupReturnsItWithTheCreatorAsItsOneMember() =
        withServer { fixture ->
            fixture.named("route-creator", "Creator")

            val response = createGroup("route-creator", "Tuesday Night")

            assertEquals(HttpStatusCode.Created, response.status)
            val summary = json.decodeFromString<GroupSummary>(response.bodyAsText())
            assertEquals("Tuesday Night", summary.name)
            assertEquals(1, summary.memberCount)
        }

    @Test
    fun aCallerWithNoUsernameCannotCreateAGroup() =
        withServer { fixture ->
            fixture.nameless("route-nameless")

            // The same rule as adding a friend (`D045`): a creator with no name is invisible
            // to every member of the group they made.
            assertEquals(HttpStatusCode.Forbidden, createGroup("route-nameless", "Tuesday").status)
        }

    @Test
    fun aGroupNeedsAName() =
        withServer { fixture ->
            fixture.named("route-creator-3", "Creator")

            assertEquals(HttpStatusCode.BadRequest, createGroup("route-creator-3", "   ").status)
            assertEquals(HttpStatusCode.BadRequest, createGroup("route-creator-3", "x".repeat(MAX_GROUP_NAME_LENGTH + 1)).status)
        }

    @Test
    fun theGroupsListedAreTheOnesTheCallerIsIn() =
        withServer { fixture ->
            val creator = fixture.named("route-creator-4", "Creator")
            val other = fixture.named("route-other-4", "Other")
            fixture.groups.create(creator, "Mine")
            fixture.groups.create(other, "Theirs")

            val response = myGroups("route-creator-4")

            assertEquals(HttpStatusCode.OK, response.status)
            val listed = json.decodeFromString<List<GroupSummary>>(response.bodyAsText())
            assertEquals(listOf("Mine"), listed.map { it.name })
        }

    @Test
    fun addingAFriendToAGroupWorksAndTheyCanSeeItAtOnce() =
        withServer { fixture ->
            val host = fixture.named("route-host-5", "Host")
            val friend = fixture.named("route-friend-5", "Friend")
            fixture.befriend(host, friend)
            val group = fixture.groups.create(host, "Regulars")

            assertEquals(HttpStatusCode.OK, addMember("route-host-5", group.id.toString(), "Friend").status)

            // No accept step (`D049`): the group is already on their own list.
            val theirs = json.decodeFromString<List<GroupSummary>>(myGroups("route-friend-5").bodyAsText())
            assertEquals(listOf("Regulars"), theirs.map { it.name })
            assertEquals(2, theirs.single().memberCount)
        }

    @Test
    fun addingSomeoneWhoIsNotAFriendIsRefusedAndSaysWhy() =
        withServer { fixture ->
            val host = fixture.named("route-host-6", "Host")
            fixture.named("route-stranger-6", "Stranger")
            val group = fixture.groups.create(host, "Regulars")

            val response = addMember("route-host-6", group.id.toString(), "Stranger")

            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertTrue(response.bodyAsText().contains("friend"), "the refusal names what is missing")
        }

    @Test
    fun aNonMemberIsToldTheGroupDoesNotExistRatherThanThatTheyMayNotTouchIt() =
        withServer { fixture ->
            val creator = fixture.named("route-creator-7", "Creator")
            val outsider = fixture.named("route-outsider-7", "Outsider")
            val theirFriend = fixture.named("route-outsiders-friend-7", "Theirs")
            fixture.befriend(outsider, theirFriend)
            val group = fixture.groups.create(creator, "Private")

            // Not Forbidden: a stranger must not learn that this id is a group.
            assertEquals(HttpStatusCode.NotFound, members("route-outsider-7", group.id.toString()).status)
            assertEquals(HttpStatusCode.NotFound, addMember("route-outsider-7", group.id.toString(), "Theirs").status)
            assertEquals(HttpStatusCode.NotFound, leave("route-outsider-7", group.id.toString()).status)
        }

    @Test
    fun aMemberCanSeeWhoElseIsInTheGroup() =
        withServer { fixture ->
            val host = fixture.named("route-host-8", "Host")
            val friend = fixture.named("route-friend-8", "Friend")
            fixture.befriend(host, friend)
            val group = fixture.groups.create(host, "Regulars")
            fixture.groups.addMember(group.id, host, friend)

            val response = members("route-friend-8", group.id.toString())

            assertEquals(HttpStatusCode.OK, response.status)
            val listed = json.decodeFromString<List<UserSummary>>(response.bodyAsText())
            assertEquals(listOf("Host", "Friend"), listed.map { it.username })
        }

    @Test
    fun leavingSaysThatGamesAreUnaffected() =
        withServer { fixture ->
            val host = fixture.named("route-host-9", "Host")
            val friend = fixture.named("route-friend-9", "Friend")
            fixture.befriend(host, friend)
            val group = fixture.groups.create(host, "Regulars")
            fixture.groups.addMember(group.id, host, friend)

            val response = leave("route-friend-9", group.id.toString())

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("unaffected"), "leaving is the release valve, and says so")
            assertFalse(fixture.groups.isMember(group.id, friend))
        }

    @Test
    fun aGroupIdThatIsNotAnIdIsABadRequest() =
        withServer { fixture ->
            fixture.named("route-host-10", "Host")

            assertEquals(HttpStatusCode.BadRequest, members("route-host-10", "not-a-uuid").status)
            assertEquals(HttpStatusCode.BadRequest, leave("route-host-10", "not-a-uuid").status)
        }

    @Test
    fun everyGroupRouteNeedsAToken() =
        withServer {
            val id = Uuid.random().toString()

            assertEquals(HttpStatusCode.Unauthorized, client.get("/groups").status)
            assertEquals(HttpStatusCode.Unauthorized, client.post("/groups") { setBody("Tuesday") }.status)
            assertEquals(HttpStatusCode.Unauthorized, client.get("/groups/$id/members").status)
            assertEquals(HttpStatusCode.Unauthorized, client.post("/groups/$id/members") { setBody("Friend") }.status)
            assertEquals(HttpStatusCode.Unauthorized, client.delete("/groups/$id/members/me").status)
        }
}
