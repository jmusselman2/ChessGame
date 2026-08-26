@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.friends

import com.jmussel.chessgame.server.api.UserSummary
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.module
import com.jmussel.chessgame.server.user.Username
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Listing your friends.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class FriendsListTest {
    private val tokens = TestTokens()
    private val json = Json { ignoreUnknownKeys = true }

    private class Fixture(
        val users: UserRepository,
        val friendships: FriendshipRepository,
    ) {
        fun named(
            subject: String,
            username: String,
        ): Uuid {
            val user = users.resolveBySubject(subject)
            users.claimUsername(user.id, Username.of(username))
            return user.id
        }
    }

    private fun withServer(block: suspend ApplicationTestBuilder.(Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val users = UserRepository(database)
            val friendships = FriendshipRepository(database)
            testApplication {
                application { module(tokens.verifier(), users, friendships) }
                block(Fixture(users, friendships))
            }
        }

    private suspend fun ApplicationTestBuilder.friendsOf(subject: String): List<UserSummary> {
        val response =
            client.get("/friends") { header("Authorization", "Bearer ${tokens.tokenFor(subject)}") }

        assertEquals(HttpStatusCode.OK, response.status)
        return json.decodeFromString(response.bodyAsText())
    }

    @Test
    fun someoneWithNoFriendsSeesAnEmptyList() {
        withServer { fixture ->
            fixture.named("auth-1", "Jordan")

            assertTrue(friendsOf("auth-1").isEmpty())
        }
    }

    @Test
    fun theListShowsEachFriendsName() {
        withServer { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            val sam = fixture.named("auth-3", "Sam")
            fixture.friendships.add(jordan, alex)
            fixture.friendships.add(jordan, sam)

            val listed = friendsOf("auth-1")

            assertEquals(setOf("Alex", "Sam"), listed.map { it.username }.toSet())
            assertEquals(setOf(alex.toString(), sam.toString()), listed.map { it.userId }.toSet())
        }
    }

    @Test
    fun bothSidesSeeEachOther() {
        withServer { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            fixture.friendships.add(alex, jordan)

            assertEquals(listOf("Alex"), friendsOf("auth-1").map { it.username })
            assertEquals(listOf("Jordan"), friendsOf("auth-2").map { it.username })
        }
    }

    @Test
    fun theListIsOnlyYourOwnFriends() {
        withServer { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            val sam = fixture.named("auth-3", "Sam")
            fixture.friendships.add(jordan, alex)

            assertTrue(friendsOf("auth-3").isEmpty(), "Sam is nobody's friend yet")
            assertFalse(friendsOf("auth-2").any { it.userId == sam.toString() })
        }
    }

    @Test
    fun aRemovedFriendIsNoLongerListed() {
        withServer { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            fixture.friendships.add(jordan, alex)
            fixture.friendships.remove(jordan, alex)

            assertTrue(friendsOf("auth-1").isEmpty())
            assertTrue(friendsOf("auth-2").isEmpty())
        }
    }

    @Test
    fun theListTellsTheCallerNothingPrivate() {
        withServer { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            fixture.friendships.add(jordan, alex)

            val body =
                client
                    .get("/friends") { header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}") }
                    .bodyAsText()

            assertFalse(body.contains("auth-2"), "the auth subject must not leak")
            assertFalse(body.contains("lastSeen"))
        }
    }

    @Test
    fun theListNeedsAToken() {
        withServer {
            assertEquals(HttpStatusCode.Unauthorized, client.get("/friends").status)
        }
    }
}
