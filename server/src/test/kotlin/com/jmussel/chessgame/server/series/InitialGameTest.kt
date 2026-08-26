@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.series

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.GameSeriesRepository
import com.jmussel.chessgame.server.db.GamesTable
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.user.Username
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The first game of a series: random colours, attached to the series.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class InitialGameTest {
    private class Fixture(
        val database: Database,
        val users: UserRepository,
        val friendships: FriendshipRepository,
        val series: GameSeriesRepository,
        val games: GameRepository,
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

        fun gameCount(): Int = transaction(database) { GamesTable.selectAll().count().toInt() }

        fun service(random: Random) = seriesService(database, random)
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
                ),
            )
        }

    /** A [Random] that answers `nextBoolean` with the given values in order. */
    private class ScriptedRandom(
        private vararg val answers: Boolean,
    ) : Random() {
        private var index = 0

        override fun nextBits(bitCount: Int): Int = 0

        override fun nextBoolean(): Boolean = answers[index++ % answers.size]
    }

    @Test
    fun openingASeriesStartsItsFirstGame() {
        withFixture { fixture ->
            val (jordan, alex) = fixture.friends()

            val opened = fixture.service(Random(1)).openWithGame(jordan, alex)

            val gameId = assertNotNull(opened.series.currentGameId, "the series has a game to play")
            val game = assertNotNull(fixture.games.load(gameId))

            assertEquals(ChessGame.newGame(), game.game, "it starts from the standard position")
            assertEquals(1, game.sequenceNumber)
            assertEquals(opened.series.id, game.seriesId)
            assertEquals(1, fixture.gameCount())
        }
    }

    @Test
    fun theSeriesPointsAtThatGame() {
        withFixture { fixture ->
            val (jordan, alex) = fixture.friends()

            val opened = fixture.service(Random(1)).openWithGame(jordan, alex)
            val stored = assertNotNull(fixture.series.find(opened.series.id))

            assertEquals(opened.series.currentGameId, stored.currentGameId)
        }
    }

    @Test
    fun bothPlayersAreInTheGameOnOppositeSides() {
        withFixture { fixture ->
            val (jordan, alex) = fixture.friends()

            val opened = fixture.service(Random(1)).openWithGame(jordan, alex)
            val game = assertNotNull(fixture.games.load(opened.series.currentGameId!!))

            assertEquals(setOf(jordan, alex), setOf(game.whiteUserId, game.blackUserId))
            assertFalse(game.whiteUserId == game.blackUserId)
        }
    }

    @Test
    fun theColoursFollowTheCoinToss() {
        withFixture { fixture ->
            val (jordan, alex) = fixture.friends()
            val series = fixture.series.openOrCreate(jordan, alex).series
            val lower = series.userAId
            val higher = series.userBId

            val heads = SeriesServiceOver(fixture, ScriptedRandom(true)).open(jordan, alex)

            assertEquals(lower, heads.whiteUserId)
            assertEquals(higher, heads.blackUserId)
        }
    }

    @Test
    fun theOtherTossGivesTheOtherColours() {
        withFixture { fixture ->
            val (jordan, alex) = fixture.friends()
            val series = fixture.series.openOrCreate(jordan, alex).series

            val tails = SeriesServiceOver(fixture, ScriptedRandom(false)).open(jordan, alex)

            assertEquals(series.userBId, tails.whiteUserId)
            assertEquals(series.userAId, tails.blackUserId)
        }
    }

    @Test
    fun bothColourAssignmentsHappenAcrossManySeries() {
        withFixture { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val whitePlayers = mutableSetOf<Uuid>()

            (2..21).forEach { index ->
                val friend = fixture.named("auth-$index", "Friend$index")
                fixture.friendships.add(jordan, friend)

                val opened = fixture.service(Random.Default).openWithGame(jordan, friend)
                val game = assertNotNull(fixture.games.load(opened.series.currentGameId!!))
                whitePlayers += game.whiteUserId
            }

            assertTrue(
                whitePlayers.contains(jordan) && whitePlayers.size > 1,
                "over twenty series both sides should have played White at least once",
            )
        }
    }

    @Test
    fun openingAgainDoesNotStartASecondGame() {
        withFixture { fixture ->
            val (jordan, alex) = fixture.friends()
            val service = fixture.service(Random(1))

            val first = service.openWithGame(jordan, alex)
            val again = service.openWithGame(alex, jordan)

            assertEquals(first.series.currentGameId, again.series.currentGameId)
            assertFalse(again.created)
            assertEquals(1, fixture.gameCount())
        }
    }

    @Test
    fun theFirstGameIsWhiteToMove() {
        withFixture { fixture ->
            val (jordan, alex) = fixture.friends()

            val opened = fixture.service(Random(1)).openWithGame(jordan, alex)
            val game = assertNotNull(fixture.games.load(opened.series.currentGameId!!))

            assertEquals(Side.WHITE, game.game.sideToMove)
            assertEquals(0, game.version)
            assertFalse(game.isComplete)
        }
    }

    /** Runs one open with a specific coin toss and returns the game it created. */
    private class SeriesServiceOver(
        private val fixture: Fixture,
        private val random: Random,
    ) {
        fun open(
            caller: Uuid,
            friend: Uuid,
        ) = fixture.games.load(
            fixture
                .service(random)
                .openWithGame(caller, friend)
                .series.currentGameId!!,
        )!!
    }
}
