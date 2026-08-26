@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.friends

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DashboardQueries
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.GameSeriesTable
import com.jmussel.chessgame.server.db.RemoveFriendResult
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.module
import com.jmussel.chessgame.server.series.seriesService
import com.jmussel.chessgame.server.user.Username
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Removing a friend: the friendship ends, the history and the game in progress do not.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class RemoveFriendTest {
    private val tokens = TestTokens()

    private class Fixture(
        val database: Database,
        val users: UserRepository,
        val friendships: FriendshipRepository,
    ) {
        val games = GameRepository(database)

        fun named(
            subject: String,
            username: String,
        ): Uuid {
            val user = users.resolveBySubject(subject)
            users.claimUsername(user.id, Username.of(username))
            return user.id
        }

        /** An ACTIVE series for the pair, as `M9` will create properly. */
        fun activeSeries(
            first: Uuid,
            second: Uuid,
        ): Uuid {
            val id = Uuid.random()
            val (lower, higher) = if (first < second) first to second else second to first

            transaction(database) {
                GameSeriesTable.insert { row ->
                    row[GameSeriesTable.id] = id
                    row[GameSeriesTable.userAId] = lower
                    row[GameSeriesTable.userBId] = higher
                    row[GameSeriesTable.status] = "ACTIVE"
                    row[GameSeriesTable.closeAfterCurrentGame] = false
                    row[GameSeriesTable.createdAt] = Instant.now().atOffset(ZoneOffset.UTC)
                }
            }
            return id
        }

        fun seriesRow(id: Uuid) =
            transaction(database) {
                GameSeriesTable.selectAll().where { GameSeriesTable.id eq id }.single()
            }

        fun closesAfterCurrentGame(id: Uuid): Boolean = seriesRow(id)[GameSeriesTable.closeAfterCurrentGame]

        fun seriesStatus(id: Uuid): String = seriesRow(id)[GameSeriesTable.status]
    }

    private fun withFriends(block: (Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            block(Fixture(database, UserRepository(database), FriendshipRepository(database)))
        }

    private fun withServer(block: suspend ApplicationTestBuilder.(Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val users = UserRepository(database)
            val friendships = FriendshipRepository(database)
            testApplication {
                application { module(tokens.verifier(), users, friendships, seriesService(database), DashboardQueries(database)) }
                block(Fixture(database, users, friendships))
            }
        }

    @Test
    fun removingAFriendEndsTheFriendshipBothWays() {
        withFriends { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            fixture.friendships.add(jordan, alex)

            val result = fixture.friendships.remove(jordan, alex)

            assertTrue(result is RemoveFriendResult.Removed)
            assertFalse(fixture.friendships.areFriends(jordan, alex))
            assertFalse(fixture.friendships.areFriends(alex, jordan))
            assertTrue(fixture.friendships.friendsOf(alex).isEmpty())
        }
    }

    @Test
    fun theFriendshipIsDeactivatedRatherThanDeleted() {
        withFriends { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            fixture.friendships.add(jordan, alex)

            fixture.friendships.remove(jordan, alex)

            val row = fixture.friendships.find(jordan, alex)

            assertNotNull(row, "the history of the friendship is kept")
            assertNotNull(row.removedAt)
            assertNotNull(row.createdAt)
        }
    }

    @Test
    fun removingSomeoneYouAreNotFriendsWithChangesNothing() {
        withFriends { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")

            assertEquals(RemoveFriendResult.NotFriends, fixture.friendships.remove(jordan, alex))
        }
    }

    @Test
    fun removingTwiceIsNotAnError() {
        withFriends { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            fixture.friendships.add(jordan, alex)
            fixture.friendships.remove(jordan, alex)

            assertEquals(RemoveFriendResult.NotFriends, fixture.friendships.remove(jordan, alex))
        }
    }

    @Test
    fun theActiveSeriesIsMarkedToCloseAfterItsCurrentGame() {
        withFriends { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            fixture.friendships.add(jordan, alex)
            val series = fixture.activeSeries(jordan, alex)

            assertFalse(fixture.closesAfterCurrentGame(series))

            val result = fixture.friendships.remove(jordan, alex) as RemoveFriendResult.Removed

            assertTrue(result.seriesMarkedToClose)
            assertTrue(fixture.closesAfterCurrentGame(series))
            assertEquals("ACTIVE", fixture.seriesStatus(series), "the series stays active until the game ends")
        }
    }

    @Test
    fun theCurrentGameIsUntouched() {
        withFriends { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            fixture.friendships.add(jordan, alex)
            val series = fixture.activeSeries(jordan, alex)
            val gameId = fixture.games.create(series, 1, jordan, alex, ChessGame.newGame())

            fixture.friendships.remove(jordan, alex)

            val game = fixture.games.load(gameId)

            assertNotNull(game)
            assertFalse(game.isComplete, "the game in progress plays on")
            assertEquals(ChessGame.newGame(), game.game)
        }
    }

    @Test
    fun anotherPairsSeriesIsNotTouched() {
        withFriends { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            val sam = fixture.named("auth-3", "Sam")
            fixture.friendships.add(jordan, alex)
            fixture.friendships.add(jordan, sam)
            val withAlex = fixture.activeSeries(jordan, alex)
            val withSam = fixture.activeSeries(jordan, sam)

            fixture.friendships.remove(jordan, alex)

            assertTrue(fixture.closesAfterCurrentGame(withAlex))
            assertFalse(fixture.closesAfterCurrentGame(withSam))
        }
    }

    @Test
    fun theEndpointRemovesByUsername() {
        withServer { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            fixture.friendships.add(jordan, alex)

            val response =
                client.delete("/friends/Alex") { header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}") }

            assertEquals(HttpStatusCode.OK, response.status)
            assertFalse(fixture.friendships.areFriends(jordan, alex))
        }
    }

    @Test
    fun eitherSideCanRemoveTheFriendship() {
        withServer { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            fixture.friendships.add(jordan, alex)

            val response =
                client.delete("/friends/Jordan") { header("Authorization", "Bearer ${tokens.tokenFor("auth-2")}") }

            assertEquals(HttpStatusCode.OK, response.status)
            assertFalse(fixture.friendships.areFriends(jordan, alex))
        }
    }

    @Test
    fun theEndpointReportsSomeoneWhoIsNotAFriend() {
        withServer { fixture ->
            fixture.named("auth-1", "Jordan")
            fixture.named("auth-2", "Alex")

            val response =
                client.delete("/friends/Alex") { header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}") }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @Test
    fun theEndpointReportsAnUnknownUser() {
        withServer { fixture ->
            fixture.named("auth-1", "Jordan")

            val response =
                client.delete("/friends/Nobody") { header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}") }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @Test
    fun theEndpointNeedsAToken() {
        withServer {
            assertEquals(HttpStatusCode.Unauthorized, client.delete("/friends/Alex").status)
        }
    }
}
