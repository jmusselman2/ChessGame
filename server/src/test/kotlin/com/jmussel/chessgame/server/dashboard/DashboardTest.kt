@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.dashboard

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.server.api.DashboardEntry
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DashboardQueries
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.GameSeriesRepository
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.series.seriesService
import com.jmussel.chessgame.server.testModule
import com.jmussel.chessgame.server.user.Username
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The dashboard: every active series with the game it is at, in one request.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class DashboardTest {
    private val tokens = TestTokens()
    private val json = Json { ignoreUnknownKeys = true }

    private class Fixture(
        val database: Database,
        val users: UserRepository,
        val friendships: FriendshipRepository,
        val series: GameSeriesRepository,
        val games: GameRepository,
        val dashboard: DashboardQueries,
    ) {
        fun named(
            subject: String,
            username: String,
        ): Uuid {
            val user = users.resolveBySubject(subject)
            users.claimUsername(user.id, Username.of(username))
            return user.id
        }

        /** Opens a series with a game, with [caller] deterministically playing White. */
        fun playing(
            caller: Uuid,
            friend: Uuid,
        ): Uuid {
            friendships.add(caller, friend)
            val callerIsLower = caller < friend
            val service = seriesService(database, FixedCoin(callerIsLower))
            return service.openWithGame(caller, friend).series.id
        }

        fun currentGame(seriesId: Uuid) = games.load(assertNotNull(series.find(seriesId)?.currentGameId))!!
    }

    /** A coin that always lands the same way, so colours are known in a test. */
    private class FixedCoin(
        private val value: Boolean,
    ) : Random() {
        override fun nextBits(bitCount: Int): Int = 0

        override fun nextBoolean(): Boolean = value
    }

    private fun withFixture(block: (Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            block(
                Fixture(
                    database,
                    UserRepository(database),
                    FriendshipRepository(database),
                    GameSeriesRepository(database),
                    GameRepository(database),
                    DashboardQueries(database),
                ),
            )
        }

    private fun withServer(block: suspend ApplicationTestBuilder.(Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val users = UserRepository(database)
            val friendships = FriendshipRepository(database)
            testApplication {
                application {
                    testModule(tokens.verifier(), database)
                }
                block(
                    Fixture(
                        database,
                        users,
                        friendships,
                        GameSeriesRepository(database),
                        GameRepository(database),
                        DashboardQueries(database),
                    ),
                )
            }
        }

    @Test
    fun aPlayerWithNothingGoingOnSeesNothing() {
        withFixture { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")

            assertTrue(fixture.dashboard.activeSeriesFor(jordan).isEmpty())
        }
    }

    @Test
    fun anActiveSeriesShowsTheOpponentAndTheGame() {
        withFixture { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            val seriesId = fixture.playing(jordan, alex)

            val entries = fixture.dashboard.activeSeriesFor(jordan)

            assertEquals(1, entries.size)

            val entry = entries.single()

            assertEquals(seriesId, entry.seriesId)
            assertEquals("Alex", entry.opponent.username)
            assertEquals(fixture.currentGame(seriesId).id, entry.gameId)
            assertEquals(0, entry.gameVersion)
            assertEquals(1, entry.fullmoveNumber)
            assertFalse(entry.closeAfterCurrentGame)
        }
    }

    @Test
    fun itSaysWhoseMoveItIs() {
        withFixture { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            fixture.playing(jordan, alex)

            val forJordan = fixture.dashboard.activeSeriesFor(jordan).single()
            val forAlex = fixture.dashboard.activeSeriesFor(alex).single()

            assertEquals("WHITE", forJordan.yourSide)
            assertEquals("BLACK", forAlex.yourSide)
            assertTrue(forJordan.isYourTurn, "White moves first")
            assertFalse(forAlex.isYourTurn)
        }
    }

    @Test
    fun theTurnFollowsThePlayedMoves() {
        withFixture { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            val seriesId = fixture.playing(jordan, alex)
            val game = fixture.currentGame(seriesId)

            fixture.games.save(
                id = game.id,
                expectedVersion = game.version,
                game = ChessRules.applyMove(ChessGame.newGame(), Move.of("e2", "e4")),
            )

            val forJordan = fixture.dashboard.activeSeriesFor(jordan).single()
            val forAlex = fixture.dashboard.activeSeriesFor(alex).single()

            assertFalse(forJordan.isYourTurn)
            assertTrue(forAlex.isYourTurn)
            assertEquals(1, forJordan.gameVersion)
        }
    }

    @Test
    fun everyActiveSeriesIsListed() {
        withFixture { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            val sam = fixture.named("auth-3", "Sam")
            fixture.playing(jordan, alex)
            fixture.playing(jordan, sam)

            val entries = fixture.dashboard.activeSeriesFor(jordan)

            assertEquals(2, entries.size)
            assertEquals(setOf("Alex", "Sam"), entries.map { it.opponent.username }.toSet())
        }
    }

    @Test
    fun aClosedSeriesDropsOffTheDashboard() {
        withFixture { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            val seriesId = fixture.playing(jordan, alex)

            fixture.series.close(seriesId)

            assertTrue(fixture.dashboard.activeSeriesFor(jordan).isEmpty())
        }
    }

    @Test
    fun aSeriesClosingAfterThisGameSaysSo() {
        withFixture { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            val seriesId = fixture.playing(jordan, alex)

            fixture.friendships.remove(jordan, alex)

            assertTrue(
                fixture.dashboard
                    .activeSeriesFor(jordan)
                    .single()
                    .closeAfterCurrentGame,
            )
        }
    }

    @Test
    fun oneRequestAnswersForEveryFriend() {
        withServer { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            (2..5).forEach { index ->
                fixture.playing(jordan, fixture.named("auth-$index", "Friend$index"))
            }

            val response =
                client.get("/dashboard") { header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}") }

            assertEquals(HttpStatusCode.OK, response.status)

            val entries = json.decodeFromString<List<DashboardEntry>>(response.bodyAsText())

            assertEquals(4, entries.size)
            assertTrue(entries.all { it.gameId != null })
            assertTrue(entries.all { it.yourTurn })
        }
    }

    @Test
    fun theDashboardTellsTheCallerNothingPrivate() {
        withServer { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            fixture.playing(jordan, fixture.named("auth-2", "Alex"))

            val body =
                client
                    .get("/dashboard") { header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}") }
                    .bodyAsText()

            assertFalse(body.contains("auth-2"))
            assertFalse(body.contains("lastSeen"))
        }
    }

    @Test
    fun theDashboardNeedsAToken() {
        withServer {
            assertEquals(HttpStatusCode.Unauthorized, client.get("/dashboard").status)
        }
    }

    @Test
    fun aSeriesWithNoGameYetStillAppears() {
        withFixture { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            fixture.friendships.add(jordan, alex)
            fixture.series.openOrCreate(jordan, alex)

            val entry = fixture.dashboard.activeSeriesFor(jordan).single()

            assertNull(entry.gameId)
            assertNull(entry.yourSide)
            assertFalse(entry.isYourTurn)
        }
    }
}
