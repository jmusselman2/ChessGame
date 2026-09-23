@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.user

import com.jmussel.chessgame.server.api.UserSummary
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
 * The "All users" testing aid: everyone the caller could add as a friend (`D071`).
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

    private suspend fun ApplicationTestBuilder.allUsers(subject: String): List<UserSummary> = json.decodeFromString(allUsersText(subject))

    @Test
    fun theListNeedsASignedInCaller() {
        withServer {
            assertEquals(HttpStatusCode.Unauthorized, client.get("/users").status)
        }
    }

    @Test
    fun onlyPeopleTheCallerCouldAddAreListed() {
        withServer { fixture ->
            val caller = fixture.named("auth-caller", "Jordan")
            val friend = fixture.named("auth-friend", "Alex")
            val former = fixture.named("auth-former", "Robin")
            fixture.named("auth-stranger", "Sam")
            fixture.users.resolveBySubject("auth-unnamed")
            fixture.friendships.add(caller, friend)
            fixture.friendships.add(caller, former)
            fixture.friendships.remove(caller, former)

            val listed = allUsers("auth-caller").map { it.username }.toSet()

            // Not the caller, not a current friend, not an account with no name. A removed
            // friend can be added again, so they are back on the list.
            assertEquals(setOf("Sam", "Robin"), listed)
        }
    }

    @Test
    fun theMostRecentlySeenComeFirst() {
        withServer { fixture ->
            val now = Instant.parse("2026-09-23T12:00:00Z")
            fixture.named("auth-caller", "Jordan")
            fixture.named("auth-old", "Old", lastSeenAt = now.minusSeconds(86_400))
            fixture.named("auth-never", "Never")
            fixture.named("auth-recent", "Recent", lastSeenAt = now)

            // Someone never seen at all goes last, below someone seen a day ago.
            assertEquals(listOf("Recent", "Old", "Never"), allUsers("auth-caller").map { it.username })
        }
    }

    @Test
    fun theListIsCapped() {
        withServer { fixture ->
            fixture.named("auth-caller", "Jordan")
            repeat(ALL_USERS_CAP + 1) { index -> fixture.named("auth-$index", "Tester$index") }

            assertEquals(ALL_USERS_CAP, allUsers("auth-caller").size)
        }
    }

    @Test
    fun eachEntryIsAUserSummaryAndNothingMore() {
        withServer { fixture ->
            fixture.named("auth-caller", "Jordan")
            fixture.named("auth-seen", "Alex", lastSeenAt = Instant.parse("2026-09-23T12:00:00Z"))

            val entries = json.parseToJsonElement(allUsersText("auth-caller")) as JsonArray

            assertEquals(1, entries.size)
            entries.forEach { entry -> assertEquals(setOf("userId", "username"), entry.jsonObject.keys) }
            // The colons cannot occur in an id, so this finds the time in any format.
            assertTrue("12:00:00" !in allUsersText("auth-caller"), "no activity time is sent")
        }
    }
}
