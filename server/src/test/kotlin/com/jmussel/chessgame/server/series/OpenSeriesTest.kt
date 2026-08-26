@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.series

import com.jmussel.chessgame.server.api.SeriesSummary
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.ACTIVE_SERIES
import com.jmussel.chessgame.server.db.CLOSED_SERIES
import com.jmussel.chessgame.server.db.DashboardQueries
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GameSeriesRepository
import com.jmussel.chessgame.server.db.GameSeriesTable
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.module
import com.jmussel.chessgame.server.user.Username
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Starting or opening the series with a friend, and the one-active-series rule.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class OpenSeriesTest {
    private val tokens = TestTokens()
    private val json = Json { ignoreUnknownKeys = true }

    private class Fixture(
        val database: Database,
        val users: UserRepository,
        val friendships: FriendshipRepository,
        val series: GameSeriesRepository,
    ) {
        fun friends(): Pair<Uuid, Uuid> {
            val jordan = named("auth-1", "Jordan")
            val alex = named("auth-2", "Alex")
            friendships.add(jordan, alex)
            return jordan to alex
        }

        fun named(
            subject: String,
            username: String,
        ): Uuid {
            val user = users.resolveBySubject(subject)
            users.claimUsername(user.id, Username.of(username))
            return user.id
        }

        fun seriesCount(): Int = transaction(database) { GameSeriesTable.selectAll().count().toInt() }

        fun close(id: Uuid) {
            transaction(database) {
                GameSeriesTable.update({ GameSeriesTable.id eq id }) { row ->
                    row[GameSeriesTable.status] = CLOSED_SERIES
                    row[GameSeriesTable.closedAt] = Instant.now().atOffset(ZoneOffset.UTC)
                }
            }
        }
    }

    private fun withSeries(block: (Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            block(
                Fixture(
                    database,
                    UserRepository(database),
                    FriendshipRepository(database),
                    GameSeriesRepository(database),
                ),
            )
        }

    private fun withServer(block: suspend ApplicationTestBuilder.(Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val users = UserRepository(database)
            val friendships = FriendshipRepository(database)
            val series = GameSeriesRepository(database)
            testApplication {
                application { module(tokens.verifier(), users, friendships, seriesService(database), DashboardQueries(database)) }
                block(Fixture(database, users, friendships, series))
            }
        }

    @Test
    fun theFirstOpenCreatesTheSeries() {
        withSeries { fixture ->
            val (jordan, alex) = fixture.friends()

            val opened = fixture.series.openOrCreate(jordan, alex)

            assertTrue(opened.created)
            assertTrue(opened.series.isActive)
            assertFalse(opened.series.closeAfterCurrentGame)
            assertNull(opened.series.currentGameId, "the first game is created separately")
            assertEquals(alex, opened.series.opponentOf(jordan))
        }
    }

    @Test
    fun openingAgainReturnsTheSameSeries() {
        withSeries { fixture ->
            val (jordan, alex) = fixture.friends()
            val first = fixture.series.openOrCreate(jordan, alex)

            val again = fixture.series.openOrCreate(jordan, alex)

            assertFalse(again.created)
            assertEquals(first.series.id, again.series.id)
            assertEquals(1, fixture.seriesCount())
        }
    }

    @Test
    fun eitherSideOpensTheSameSeries() {
        withSeries { fixture ->
            val (jordan, alex) = fixture.friends()
            val fromJordan = fixture.series.openOrCreate(jordan, alex)

            val fromAlex = fixture.series.openOrCreate(alex, jordan)

            assertEquals(fromJordan.series.id, fromAlex.series.id)
            assertEquals(1, fixture.seriesCount())
        }
    }

    @Test
    fun theStoredPairIsAlwaysInTheSameOrder() {
        withSeries { fixture ->
            val (jordan, alex) = fixture.friends()

            val series = fixture.series.openOrCreate(alex, jordan).series

            assertTrue(series.userAId < series.userBId)
        }
    }

    @Test
    fun aClosedSeriesLeavesRoomForANewOne() {
        withSeries { fixture ->
            val (jordan, alex) = fixture.friends()
            val first = fixture.series.openOrCreate(jordan, alex).series
            fixture.close(first.id)

            val second = fixture.series.openOrCreate(jordan, alex)

            assertTrue(second.created)
            assertFalse(second.series.id == first.id)
            assertEquals(2, fixture.seriesCount(), "the closed series stays for history")
            assertEquals(CLOSED_SERIES, fixture.series.find(first.id)?.status)
        }
    }

    @Test
    fun differentPairsHaveTheirOwnSeries() {
        withSeries { fixture ->
            val (jordan, alex) = fixture.friends()
            val sam = fixture.named("auth-3", "Sam")
            fixture.friendships.add(jordan, sam)

            val withAlex = fixture.series.openOrCreate(jordan, alex).series
            val withSam = fixture.series.openOrCreate(jordan, sam).series

            assertFalse(withAlex.id == withSam.id)
            assertEquals(2, fixture.seriesCount())
            assertEquals(
                setOf(withAlex.id, withSam.id),
                fixture.series
                    .seriesFor(jordan)
                    .map { it.id }
                    .toSet(),
            )
        }
    }

    @Test
    fun aSeriesNeedsTwoDifferentPlayers() {
        withSeries { fixture ->
            val (jordan, _) = fixture.friends()

            assertFailsWith<IllegalArgumentException> { fixture.series.openOrCreate(jordan, jordan) }
        }
    }

    @Test
    fun simultaneousOpensProduceExactlyOneActiveSeries() {
        withSeries { fixture ->
            val (jordan, alex) = fixture.friends()
            val attempts = 8
            val barrier = CyclicBarrier(attempts)
            val pool = Executors.newFixedThreadPool(attempts)

            val opened =
                try {
                    pool
                        .invokeAll(
                            (1..attempts).map { attempt ->
                                Callable {
                                    barrier.await(10, TimeUnit.SECONDS)
                                    // Half open it from each side, as two players tapping Play would.
                                    if (attempt % 2 == 0) {
                                        fixture.series.openOrCreate(jordan, alex)
                                    } else {
                                        fixture.series.openOrCreate(alex, jordan)
                                    }
                                }
                            },
                        ).map { it.get() }
                } finally {
                    pool.shutdown()
                }

            assertEquals(1, opened.count { it.created }, "exactly one attempt may create the series")
            assertEquals(1, opened.map { it.series.id }.toSet().size, "everyone opened the same series")
            assertEquals(1, fixture.seriesCount(), "no parallel active series exists")
        }
    }

    @Test
    fun theEndpointOpensTheSeriesWithAFriend() {
        withServer { fixture ->
            fixture.friends()

            val created =
                client.post("/series") {
                    header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}")
                    setBody("Alex")
                }

            assertEquals(HttpStatusCode.Created, created.status)

            val summary = json.decodeFromString<SeriesSummary>(created.bodyAsText())

            assertEquals("Alex", summary.opponent.username)
            assertEquals(ACTIVE_SERIES, summary.status)
            assertFalse(summary.closeAfterCurrentGame)
            assertNotNull(summary.currentGameId, "opening a series starts its first game (M9.2)")
        }
    }

    @Test
    fun theEndpointOpensTheExistingSeriesTheSecondTime() {
        withServer { fixture ->
            fixture.friends()

            val created =
                client.post("/series") {
                    header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}")
                    setBody("Alex")
                }
            val reopened =
                client.post("/series") {
                    header("Authorization", "Bearer ${tokens.tokenFor("auth-2")}")
                    setBody("Jordan")
                }

            assertEquals(HttpStatusCode.Created, created.status)
            assertEquals(HttpStatusCode.OK, reopened.status, "opening an existing series is not a creation")
            assertEquals(
                json.decodeFromString<SeriesSummary>(created.bodyAsText()).seriesId,
                json.decodeFromString<SeriesSummary>(reopened.bodyAsText()).seriesId,
            )
            assertEquals(1, fixture.seriesCount())
        }
    }

    @Test
    fun theEndpointRefusesSomeoneWhoIsNotAFriend() {
        withServer { fixture ->
            fixture.named("auth-1", "Jordan")
            fixture.named("auth-2", "Alex")

            val response =
                client.post("/series") {
                    header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}")
                    setBody("Alex")
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertEquals(0, fixture.seriesCount())
        }
    }

    @Test
    fun theEndpointRefusesYourself() {
        withServer { fixture ->
            fixture.named("auth-1", "Jordan")

            val response =
                client.post("/series") {
                    header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}")
                    setBody("Jordan")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun theEndpointNeedsAToken() {
        withServer {
            assertEquals(HttpStatusCode.Unauthorized, client.post("/series") { setBody("Alex") }.status)
        }
    }
}
