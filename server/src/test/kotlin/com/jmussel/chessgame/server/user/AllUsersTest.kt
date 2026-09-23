@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.user

import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.testModule
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The "All users" testing aid: every user, friends listed after everyone else (`D071`).
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class AllUsersTest {
    private val tokens = TestTokens()
    private val json = Json { ignoreUnknownKeys = true }

    private class Fixture(
        val users: UserRepository,
        val friendships: FriendshipRepository,
    ) {
        fun named(
            subject: String,
            username: String,
            lastSeenAt: Instant? = null,
        ): Uuid {
            val user = users.resolveBySubject(subject)
            users.claimUsername(user.id, Username.of(username))
            lastSeenAt?.let { users.touchLastSeen(user.id, it) }
            return user.id
        }
    }

    private fun withServer(block: suspend ApplicationTestBuilder.(Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            testApplication {
                application { testModule(tokens.verifier(), database) }
                block(Fixture(UserRepository(database), FriendshipRepository(database)))
            }
        }

    private suspend fun ApplicationTestBuilder.allUsersText(subject: String): String {
        val response = client.get("/users") { header("Authorization", "Bearer ${tokens.tokenFor(subject)}") }

        assertEquals(HttpStatusCode.OK, response.status)
        return response.bodyAsText()
    }

    private suspend fun ApplicationTestBuilder.allUsers(subject: String): List<ListedUser> = json.decodeFromString(allUsersText(subject))

    /** Each name, with "(friend)" after a friend's. */
    private fun List<ListedUser>.shown(): List<String> = map { if (it.friend) "${it.username} (friend)" else it.username }

    @Test
    fun theListNeedsASignedInCaller() {
        withServer {
            assertEquals(HttpStatusCode.Unauthorized, client.get("/users").status)
        }
    }

    @Test
    fun everyoneButTheCallerIsListedWithFriendsLast() {
        withServer { fixture ->
            val now = Instant.parse("2026-09-23T12:00:00Z")
            val caller = fixture.named("auth-caller", "Jordan")
            val friend = fixture.named("auth-friend", "Alex", lastSeenAt = now)
            val former = fixture.named("auth-former", "Robin", lastSeenAt = now.minusSeconds(60))
            fixture.named("auth-stranger", "Sam", lastSeenAt = now.minusSeconds(120))
            fixture.users.resolveBySubject("auth-unnamed")
            fixture.friendships.add(caller, friend)
            fixture.friendships.add(caller, former)
            fixture.friendships.remove(caller, former)

            // Alex was seen most recently but is a friend, so comes after everyone else. A
            // removed friend is not a friend. Nobody without a name, and not the caller.
            assertEquals(listOf("Robin", "Sam", "Alex (friend)"), allUsers("auth-caller").shown())
        }
    }

    @Test
    fun eachGroupIsMostRecentlySeenFirst() {
        withServer { fixture ->
            val now = Instant.parse("2026-09-23T12:00:00Z")
            val caller = fixture.named("auth-caller", "Jordan")
            fixture.named("auth-old", "Old", lastSeenAt = now.minusSeconds(86_400))
            fixture.named("auth-never", "Never")
            fixture.named("auth-recent", "Recent", lastSeenAt = now)
            val oldFriend = fixture.named("auth-old-friend", "OldFriend", lastSeenAt = now.minusSeconds(86_400))
            val newFriend = fixture.named("auth-new-friend", "NewFriend", lastSeenAt = now)
            fixture.friendships.add(caller, oldFriend)
            fixture.friendships.add(caller, newFriend)

            // Someone never seen at all goes last in their group.
            assertEquals(
                listOf("Recent", "Old", "Never", "NewFriend (friend)", "OldFriend (friend)"),
                allUsers("auth-caller").shown(),
            )
        }
    }

    @Test
    fun theListIsCappedAndFillsWithPeopleToAddFirst() {
        withServer { fixture ->
            val caller = fixture.named("auth-caller", "Jordan")
            fixture.friendships.add(caller, fixture.named("auth-friend", "Alex", lastSeenAt = Instant.now()))
            repeat(ALL_USERS_CAP) { index -> fixture.named("auth-$index", "Tester$index") }

            val listed = allUsers("auth-caller")

            assertEquals(ALL_USERS_CAP, listed.size)
            assertTrue(listed.none { it.friend }, "the friend is the one left out")
        }
    }

    @Test
    fun friendsFillWhatPeopleToAddLeave() {
        withServer { fixture ->
            val caller = fixture.named("auth-caller", "Jordan")
            fixture.friendships.add(caller, fixture.named("auth-friend", "Alex"))
            repeat(ALL_USERS_CAP - 1) { index -> fixture.named("auth-$index", "Tester$index") }

            val listed = allUsers("auth-caller")

            assertEquals(ALL_USERS_CAP, listed.size)
            assertEquals(listOf("Alex"), listed.filter { it.friend }.map { it.username })
        }
    }

    @Test
    fun eachEntryIsANameAndWhetherTheyAreAFriendAndNothingMore() {
        withServer { fixture ->
            val seen = Instant.parse("2026-09-23T12:00:00Z")
            val caller = fixture.named("auth-caller", "Jordan")
            fixture.named("auth-seen", "Alex", lastSeenAt = seen)
            fixture.friendships.add(caller, fixture.named("auth-friend", "Sam", lastSeenAt = seen))

            val entries = json.parseToJsonElement(allUsersText("auth-caller")) as JsonArray

            assertEquals(2, entries.size)
            entries.forEach { entry -> assertEquals(setOf("userId", "username", "friend"), entry.jsonObject.keys) }
            // The colons cannot occur in an id, so this finds the time in any format.
            assertTrue("12:00:00" !in allUsersText("auth-caller"), "no activity time is sent")
        }
    }
}
