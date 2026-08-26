@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.friends

import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.AddFriendResult
import com.jmussel.chessgame.server.db.DashboardQueries
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.FriendshipsTable
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.module
import com.jmussel.chessgame.server.series.seriesService
import com.jmussel.chessgame.server.user.Username
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Adding a friend.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class AddFriendTest {
    private val tokens = TestTokens()

    private class Fixture(
        val users: UserRepository,
        val friendships: FriendshipRepository,
        val database: org.jetbrains.exposed.v1.jdbc.Database,
    ) {
        fun named(
            subject: String,
            username: String,
        ): Uuid {
            val user = users.resolveBySubject(subject)
            users.claimUsername(user.id, Username.of(username))
            return user.id
        }

        fun rowCount(): Int = transaction(database) { FriendshipsTable.selectAll().count().toInt() }
    }

    private fun withFriends(block: (Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            block(Fixture(UserRepository(database), FriendshipRepository(database), database))
        }

    private fun withServer(block: suspend ApplicationTestBuilder.(Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val users = UserRepository(database)
            val friendships = FriendshipRepository(database)
            testApplication {
                application { module(tokens.verifier(), users, friendships, seriesService(database), DashboardQueries(database)) }
                block(Fixture(users, friendships, database))
            }
        }

    @Test
    fun addingAFriendIsImmediatelyMutual() {
        withFriends { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")

            val result = fixture.friendships.add(jordan, alex)

            assertTrue(result is AddFriendResult.Added)
            assertTrue(fixture.friendships.areFriends(jordan, alex))
            assertTrue(fixture.friendships.areFriends(alex, jordan), "friendship is mutual, not directed")
            assertEquals(listOf(alex), fixture.friendships.friendsOf(jordan))
            assertEquals(listOf(jordan), fixture.friendships.friendsOf(alex))
        }
    }

    @Test
    fun youCannotBefriendYourself() {
        withFriends { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")

            assertEquals(AddFriendResult.Yourself, fixture.friendships.add(jordan, jordan))
            assertEquals(0, fixture.rowCount())
        }
    }

    @Test
    fun addingTheSameFriendTwiceChangesNothing() {
        withFriends { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            fixture.friendships.add(jordan, alex)

            assertEquals(AddFriendResult.AlreadyFriends, fixture.friendships.add(jordan, alex))
            assertEquals(1, fixture.rowCount())
        }
    }

    @Test
    fun theReverseDirectionIsTheSameFriendship() {
        withFriends { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            fixture.friendships.add(jordan, alex)

            assertEquals(AddFriendResult.AlreadyFriends, fixture.friendships.add(alex, jordan))
            assertEquals(1, fixture.rowCount(), "one row holds the pair, whichever way round it is added")
        }
    }

    @Test
    fun theStoredPairIsAlwaysInTheSameOrder() {
        withFriends { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")

            val added = fixture.friendships.add(alex, jordan) as AddFriendResult.Added

            assertTrue(added.friendship.userAId < added.friendship.userBId)
        }
    }

    @Test
    fun aRemovedFriendCanBeAddedBackOnTheSameRow() {
        withFriends { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            fixture.friendships.add(jordan, alex)
            fixture.friendships.remove(jordan, alex)

            assertFalse(fixture.friendships.areFriends(jordan, alex))

            val again = fixture.friendships.add(jordan, alex)

            assertTrue(again is AddFriendResult.Added)
            assertTrue(fixture.friendships.areFriends(jordan, alex))
            assertEquals(1, fixture.rowCount(), "history is kept on the one row")
        }
    }

    @Test
    fun severalFriendsAreAllKept() {
        withFriends { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            val sam = fixture.named("auth-3", "Sam")

            fixture.friendships.add(jordan, alex)
            fixture.friendships.add(jordan, sam)

            assertEquals(setOf(alex, sam), fixture.friendships.friendsOf(jordan).toSet())
            assertEquals(listOf(jordan), fixture.friendships.friendsOf(sam))
        }
    }

    @Test
    fun theEndpointAddsByUsername() {
        withServer { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            fixture.named("auth-2", "Alex")

            val response =
                client.post("/friends") {
                    header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}")
                    setBody("Alex")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(1, fixture.friendships.friendsOf(jordan).size)
        }
    }

    @Test
    fun theEndpointFindsTheFriendCaseInsensitively() {
        withServer { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            fixture.named("auth-2", "Alex")

            val response =
                client.post("/friends") {
                    header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}")
                    setBody("aLeX")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(1, fixture.friendships.friendsOf(jordan).size)
        }
    }

    @Test
    fun theEndpointRefusesToAddYourself() {
        withServer { fixture ->
            fixture.named("auth-1", "Jordan")

            val response =
                client.post("/friends") {
                    header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}")
                    setBody("Jordan")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(0, fixture.rowCount())
        }
    }

    @Test
    fun theEndpointReportsADuplicate() {
        withServer { fixture ->
            fixture.named("auth-1", "Jordan")
            fixture.named("auth-2", "Alex")

            repeat(1) {
                client.post("/friends") {
                    header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}")
                    setBody("Alex")
                }
            }

            val duplicate =
                client.post("/friends") {
                    header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}")
                    setBody("Alex")
                }
            val reversed =
                client.post("/friends") {
                    header("Authorization", "Bearer ${tokens.tokenFor("auth-2")}")
                    setBody("Jordan")
                }

            assertEquals(HttpStatusCode.Conflict, duplicate.status)
            assertEquals(HttpStatusCode.Conflict, reversed.status, "the reverse is the same friendship")
            assertEquals(1, fixture.rowCount())
        }
    }

    @Test
    fun theEndpointReportsAnUnknownUser() {
        withServer { fixture ->
            fixture.named("auth-1", "Jordan")

            val response =
                client.post("/friends") {
                    header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}")
                    setBody("Nobody")
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @Test
    fun theEndpointNeedsAToken() {
        withServer {
            assertEquals(HttpStatusCode.Unauthorized, client.post("/friends") { setBody("Alex") }.status)
        }
    }
}
