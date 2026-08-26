@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.series

import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.GameSeriesRepository
import com.jmussel.chessgame.server.db.StoredGame
import com.jmussel.chessgame.server.db.StoredGameEvent
import com.jmussel.chessgame.server.db.StoredSeries
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.game.CommandResult
import com.jmussel.chessgame.server.game.GameCommandService
import com.jmussel.chessgame.server.user.Username
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.random.Random
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Two friends, a series, and a game that can be finished on demand.
 *
 * Shared by the tests about what happens when a game ends — the automatic rematch and the
 * series closing — because both need a real series played through the command layer rather
 * than rows written by hand.
 */
internal class SeriesEndFixture(
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

    fun events(type: String): List<StoredGameEvent> = seriesRepository.auditEvents(seriesId).filter { it.type == type }

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

/** A coin that always lands the same way, so a test's colours do not wander. */
internal class FixedCoin(
    private val value: Boolean,
) : Random() {
    override fun nextBits(bitCount: Int): Int = 0

    override fun nextBoolean(): Boolean = value
}

/** Runs [block] against a started series, or skips when there is no test database. */
internal fun withSeries(block: (SeriesEndFixture) -> Unit) =
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
            SeriesEndFixture(
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
