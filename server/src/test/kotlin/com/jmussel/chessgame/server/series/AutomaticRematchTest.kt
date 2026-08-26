@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.series

import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.GameSeriesRepository
import com.jmussel.chessgame.server.db.StoredGame
import com.jmussel.chessgame.server.db.StoredSeries
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.game.CommandResult
import com.jmussel.chessgame.server.game.GameCommandService
import com.jmussel.chessgame.server.user.Username
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.Database
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The next game a finished game owes its series.
 *
 * Rematches are automatic (`D015`): finishing a game in an active series starts the next
 * one without either player asking for it. What matters here is that it happens exactly
 * once — the series ends up pointing at one new game however many times the end of the old
 * one is processed.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class AutomaticRematchTest {
    private class FixedCoin(
        private val value: Boolean,
    ) : Random() {
        override fun nextBits(bitCount: Int): Int = 0

        override fun nextBoolean(): Boolean = value
    }

    private class Fixture(
        val database: Database,
        val users: UserRepository,
        val games: GameRepository,
        val seriesRepository: GameSeriesRepository,
        val series: SeriesService,
        val commands: GameCommandService,
    ) {
        lateinit var white: Uuid
        lateinit var black: Uuid
        lateinit var seriesId: Uuid
        lateinit var firstGameId: Uuid

        fun startSeries() {
            val jordan = named("auth-1", "Jordan")
            val alex = named("auth-2", "Alex")
            FriendshipRepository(database).add(jordan, alex)

            val opened = series.openWithGame(jordan, alex)

            seriesId = opened.series.id
            firstGameId = assertNotNull(opened.series.currentGameId)

            // Which of them has White is the series' coin toss to make (`D014`), not this
            // fixture's; take it from the game it created.
            val first = game(firstGameId)
            white = first.whiteUserId
            black = first.blackUserId
        }

        fun named(
            subject: String,
            username: String,
        ): Uuid {
            val user = users.resolveBySubject(subject)
            users.claimUsername(user.id, Username.of(username))
            return user.id
        }

        fun series(): StoredSeries = assertNotNull(seriesRepository.find(seriesId))

        fun game(id: Uuid): StoredGame = assertNotNull(games.load(id))

        fun currentGame(): StoredGame = game(assertNotNull(series().currentGameId))

        fun gamesInSeries(): List<StoredGame> = games.inSeries(seriesId)

        fun rematchEvents() = seriesRepository.auditEvents(seriesId).filter { it.type == REMATCH }

        /** Plays [move] for [player] at the version the game is actually at. */
        fun play(
            gameId: Uuid,
            player: Uuid,
            move: Move,
        ) {
            val result = commands.makeMove(player, gameId, game(gameId).version, move)
            assertTrue(result is CommandResult.Applied, "setup move failed: $result")
        }

        /** Plays the shortest checkmate there is in [gameId]. */
        fun playFoolsMate(gameId: Uuid) {
            val game = game(gameId)

            play(gameId, game.whiteUserId, Move.of("f2", "f3"))
            play(gameId, game.blackUserId, Move.of("e7", "e5"))
            play(gameId, game.whiteUserId, Move.of("g2", "g4"))
            play(gameId, game.blackUserId, Move.of("d8", "h4"))
        }
    }

    private fun withSeries(block: (Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val games = GameRepository(database)
            val seriesRepository = GameSeriesRepository(database)
            val series =
                SeriesService(
                    database = database,
                    series = seriesRepository,
                    games = games,
                    random = FixedCoin(true),
                )
            val fixture =
                Fixture(
                    database = database,
                    users = UserRepository(database),
                    games = games,
                    seriesRepository = seriesRepository,
                    series = series,
                    commands = GameCommandService(database, games, series),
                )
            fixture.startSeries()
            block(fixture)
        }

    @Test
    fun finishingAGameStartsTheNextOne() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)

            val next = fixture.currentGame()

            assertNotEquals(fixture.firstGameId, next.id, "the series moved on to a new game")
            assertFalse(next.isComplete, "the new game is waiting to be played")
            assertEquals(2, next.sequenceNumber, "it is the second game of the series")
            assertEquals(0, next.version)
            assertTrue(next.game.moves.isEmpty())
        }
    }

    @Test
    fun exactlyOneNextGameIsCreated() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)

            assertEquals(2, fixture.gamesInSeries().size, "one finished game and one new one")
            assertEquals(1, fixture.rematchEvents().size)
        }
    }

    @Test
    fun theSeriesPointsAtTheNewGame() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)

            val series = fixture.series()
            val next = fixture.gamesInSeries().single { it.id != fixture.firstGameId }

            assertEquals(next.id, series.currentGameId)
            assertTrue(series.isActive, "a series with a game to play is still active")
        }
    }

    @Test
    fun theFinishedGameIsLeftAsItEnded() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)

            val finished = fixture.game(fixture.firstGameId)

            assertTrue(finished.isComplete, "the rematch did not disturb the game it followed")
            assertEquals(1, finished.sequenceNumber)
            assertEquals(4, finished.game.history.size)
            assertNotNull(finished.endedAt)
        }
    }

    @Test
    fun bothPlayersAreInTheNewGame() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)

            val next = fixture.currentGame()

            assertEquals(
                setOf(fixture.white, fixture.black),
                setOf(next.whiteUserId, next.blackUserId),
                "the same two people play on",
            )
            assertEquals(fixture.seriesId, next.seriesId)
        }
    }

    @Test
    fun askingAgainAfterTheRematchChangesNothing() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)
            val afterTheFirstAsk = fixture.series().currentGameId

            // A retry of the same end-of-game: the series has already moved on, so there
            // is nothing left to owe.
            repeat(3) { fixture.series.startNextGameAfter(fixture.game(fixture.firstGameId)) }

            assertEquals(afterTheFirstAsk, fixture.series().currentGameId)
            assertEquals(2, fixture.gamesInSeries().size)
            assertEquals(1, fixture.rematchEvents().size)
        }
    }

    @Test
    fun twoEndOfGamesArrivingTogetherStillCreateOneGame() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)
            val finished = fixture.game(fixture.firstGameId)
            val alreadyCurrent = fixture.series().currentGameId

            val barrier = CyclicBarrier(2)
            val pool = Executors.newFixedThreadPool(2)

            try {
                pool
                    .invokeAll(
                        List(2) {
                            Callable {
                                barrier.await(WAIT_SECONDS, TimeUnit.SECONDS)
                                fixture.series.startNextGameAfter(finished)
                            }
                        },
                    ).forEach { it.get(WAIT_SECONDS, TimeUnit.SECONDS) }
            } finally {
                pool.shutdownNow()
            }

            assertEquals(alreadyCurrent, fixture.series().currentGameId)
            assertEquals(2, fixture.gamesInSeries().size, "the series has one game to play, not several")
            assertEquals(1, fixture.rematchEvents().size)
        }
    }

    @Test
    fun aSeriesMarkedToCloseGetsNoRematch() {
        withSeries { fixture ->
            fixture.seriesRepository.markCloseAfterCurrentGame(fixture.seriesId)

            fixture.playFoolsMate(fixture.firstGameId)

            assertEquals(1, fixture.gamesInSeries().size, "no game follows the last one (`D013`)")
            assertTrue(fixture.rematchEvents().isEmpty())
            assertEquals(fixture.firstGameId, fixture.series().currentGameId)
        }
    }

    @Test
    fun aClosedSeriesGetsNoRematch() {
        withSeries { fixture ->
            fixture.seriesRepository.close(fixture.seriesId)

            fixture.playFoolsMate(fixture.firstGameId)

            assertEquals(1, fixture.gamesInSeries().size)
            assertTrue(fixture.rematchEvents().isEmpty())
        }
    }

    @Test
    fun anUnfinishedGameOwesNothing() {
        withSeries { fixture ->
            fixture.play(fixture.firstGameId, fixture.white, Move.of("e2", "e4"))

            assertEquals(1, fixture.gamesInSeries().size)
            assertEquals(fixture.firstGameId, fixture.series().currentGameId)
            assertTrue(fixture.rematchEvents().isEmpty())
        }
    }

    @Test
    fun theRematchEventSaysWhichGameItFollowed() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)

            val payload = fixture.rematchEvents().single().payload

            assertEquals(fixture.firstGameId.toString(), payload["previousGameId"]?.jsonPrimitive?.content)
            assertEquals(2, payload["sequenceNumber"]?.jsonPrimitive?.content?.toInt())
        }
    }

    @Test
    fun theRematchReversesTheColours() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)

            val next = fixture.currentGame()

            assertEquals(fixture.black, next.whiteUserId, "whoever had Black now has White")
            assertEquals(fixture.white, next.blackUserId)
        }
    }

    @Test
    fun theColoursKeepAlternatingDownTheSeries() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)
            fixture.playFoolsMate(fixture.currentGame().id)
            fixture.playFoolsMate(fixture.currentGame().id)

            val whitePlayers = fixture.gamesInSeries().map { it.whiteUserId }

            assertEquals(
                listOf(fixture.white, fixture.black, fixture.white, fixture.black),
                whitePlayers,
                "the colours swap every game (`D014`)",
            )
            assertTrue(
                fixture.gamesInSeries().all { it.whiteUserId != it.blackUserId },
                "nobody ever plays themselves",
            )
        }
    }

    @Test
    fun theSeriesKeepsGoingGameAfterGame() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)
            val second = fixture.currentGame().id
            fixture.playFoolsMate(second)
            val third = fixture.currentGame().id
            fixture.playFoolsMate(third)

            val fourth = fixture.currentGame()

            assertEquals(4, fixture.gamesInSeries().size)
            assertEquals(4, fourth.sequenceNumber)
            assertEquals(
                listOf(1, 2, 3, 4),
                fixture.gamesInSeries().map { it.sequenceNumber },
                "each game follows the one before it",
            )
            assertEquals(3, fixture.rematchEvents().size)
        }
    }

    private companion object {
        const val WAIT_SECONDS = 10L
        const val REMATCH = SeriesService.REMATCH_CREATED
    }
}
