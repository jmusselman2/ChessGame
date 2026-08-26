@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.game

import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.server.api.CommandRejection
import com.jmussel.chessgame.server.api.GameView
import com.jmussel.chessgame.server.api.RejectionReason
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.series.seriesService
import com.jmussel.chessgame.server.testModule
import com.jmussel.chessgame.server.user.Username
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A command written against a version that no longer exists.
 *
 * The rejection has to be clean — nothing written, a reason the client can act on, and the
 * canonical state to carry on from (`D021`).
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class StaleVersionTest {
    private val tokens = TestTokens()
    private val json = Json { ignoreUnknownKeys = true }

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
        val commands: GameCommandService,
    ) {
        lateinit var white: Uuid
        lateinit var black: Uuid
        lateinit var gameId: Uuid

        fun startGame() {
            val jordan = named("auth-1", "Jordan")
            val alex = named("auth-2", "Alex")
            FriendshipRepository(database).add(jordan, alex)

            val opened = seriesService(database, FixedCoin(jordan < alex)).openWithGame(jordan, alex)

            gameId = assertNotNull(opened.series.currentGameId)
            white = jordan
            black = alex
        }

        fun named(
            subject: String,
            username: String,
        ): Uuid {
            val user = users.resolveBySubject(subject)
            users.claimUsername(user.id, Username.of(username))
            return user.id
        }

        fun version(): Long = assertNotNull(games.load(gameId)).version

        fun moves(): List<Move> = assertNotNull(games.load(gameId)).game.moves
    }

    private fun withGame(block: (Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val games = GameRepository(database)
            val fixture =
                Fixture(database, UserRepository(database), games, GameCommandService(database, games))
            fixture.startGame()
            block(fixture)
        }

    private fun withServer(block: suspend ApplicationTestBuilder.(Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val games = GameRepository(database)
            val fixture =
                Fixture(database, UserRepository(database), games, GameCommandService(database, games))
            fixture.startGame()

            testApplication {
                application { testModule(tokens.verifier(), database) }
                block(fixture)
            }
        }

    private suspend fun ApplicationTestBuilder.move(
        subject: String,
        gameId: Uuid,
        expectedVersion: Long,
        from: String,
        to: String,
    ) = client.post("/games/$gameId/moves") {
        header("Authorization", "Bearer ${tokens.tokenFor(subject)}")
        contentType(ContentType.Application.Json)
        setBody("""{"expectedVersion":$expectedVersion,"from":"$from","to":"$to"}""")
    }

    @Test
    fun aStaleCommandWritesNothing() {
        withGame { fixture ->
            fixture.commands.makeMove(fixture.white, fixture.gameId, 0, Move.of("e2", "e4"))
            fixture.commands.makeMove(fixture.black, fixture.gameId, 1, Move.of("e7", "e5"))

            val stale = fixture.commands.makeMove(fixture.white, fixture.gameId, 0, Move.of("d2", "d4"))

            assertTrue(stale is CommandResult.StaleVersion)
            assertEquals(2, fixture.version())
            assertEquals(listOf(Move.of("e2", "e4"), Move.of("e7", "e5")), fixture.moves())
        }
    }

    @Test
    fun theRejectionCarriesTheCanonicalState() {
        withGame { fixture ->
            fixture.commands.makeMove(fixture.white, fixture.gameId, 0, Move.of("e2", "e4"))

            val stale =
                fixture.commands.makeMove(fixture.black, fixture.gameId, 0, Move.of("e7", "e5"))
                    as CommandResult.StaleVersion

            assertEquals(1, stale.game.version, "the caller is told where the game actually is")
            assertEquals(listOf(Move.of("e2", "e4")), stale.game.game.moves)
        }
    }

    @Test
    fun theEndpointSaysTheVersionIsStale() {
        withServer { fixture ->
            move("auth-1", fixture.gameId, 0, "e2", "e4")

            val response = move("auth-2", fixture.gameId, 0, "e7", "e5")

            assertEquals(HttpStatusCode.Conflict, response.status)

            val rejection = json.decodeFromString<CommandRejection>(response.bodyAsText())

            assertEquals(RejectionReason.STALE_VERSION, rejection.reason)
            assertEquals(1, rejection.game?.version)
        }
    }

    @Test
    fun aStaleRejectionIsToldApartFromAPrematureOne() {
        withServer { fixture ->
            val tooEarly = move("auth-2", fixture.gameId, 0, "e7", "e5")

            move("auth-1", fixture.gameId, 0, "e2", "e4")

            val stale = move("auth-1", fixture.gameId, 0, "d2", "d4")

            assertEquals(HttpStatusCode.Conflict, tooEarly.status)
            assertEquals(HttpStatusCode.Conflict, stale.status)
            assertEquals(
                RejectionReason.NOT_YOUR_TURN,
                json.decodeFromString<CommandRejection>(tooEarly.bodyAsText()).reason,
            )
            assertEquals(
                RejectionReason.STALE_VERSION,
                json.decodeFromString<CommandRejection>(stale.bodyAsText()).reason,
                "the same status code, but the client should react differently",
            )
        }
    }

    @Test
    fun theClientCanRetryStraightFromTheRejection() {
        withServer { fixture ->
            move("auth-1", fixture.gameId, 0, "e2", "e4")

            // Black still thinks the game is at version 0.
            val rejected = move("auth-2", fixture.gameId, 0, "e7", "e5")
            val rejection = json.decodeFromString<CommandRejection>(rejected.bodyAsText())
            val current = assertNotNull(rejection.game)

            assertTrue(current.yourTurn, "the attached state says it really is Black's move")

            val retried = move("auth-2", fixture.gameId, current.version, "e7", "e5")

            assertEquals(HttpStatusCode.OK, retried.status)
            assertEquals(2, json.decodeFromString<GameView>(retried.bodyAsText()).version)
        }
    }

    @Test
    fun refreshingWithGetGivesTheSameVersion() {
        withServer { fixture ->
            move("auth-1", fixture.gameId, 0, "e2", "e4")

            val rejection =
                json.decodeFromString<CommandRejection>(
                    move("auth-2", fixture.gameId, 0, "e7", "e5").bodyAsText(),
                )
            val refreshed =
                json.decodeFromString<GameView>(
                    client
                        .get("/games/${fixture.gameId}") {
                            header("Authorization", "Bearer ${tokens.tokenFor("auth-2")}")
                        }.bodyAsText(),
                )

            assertEquals(refreshed.version, rejection.game?.version)
            assertEquals(refreshed.board, rejection.game?.board)
        }
    }

    @Test
    fun aFinishedGameSaysSoRatherThanStale() {
        withServer { fixture ->
            listOf(
                Triple("auth-1", 0L, "f2" to "f3"),
                Triple("auth-2", 1L, "e7" to "e5"),
                Triple("auth-1", 2L, "g2" to "g4"),
                Triple("auth-2", 3L, "d8" to "h4"),
            ).forEach { (subject, version, squares) ->
                move(subject, fixture.gameId, version, squares.first, squares.second)
            }

            // With the current version in hand, the problem is the game, not the version.
            val afterwards = move("auth-1", fixture.gameId, 4, "e1", "f2")

            assertEquals(HttpStatusCode.Conflict, afterwards.status)
            assertEquals(
                RejectionReason.GAME_OVER,
                json.decodeFromString<CommandRejection>(afterwards.bodyAsText()).reason,
                "a finished game is not a version problem",
            )
        }
    }

    @Test
    fun anIllegalMoveIsNotAVersionProblemEither() {
        withServer { fixture ->
            val response = move("auth-1", fixture.gameId, 0, "e2", "e5")

            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)

            val rejection = json.decodeFromString<CommandRejection>(response.bodyAsText())

            assertEquals(RejectionReason.ILLEGAL_MOVE, rejection.reason)
            assertEquals(0, rejection.game?.version, "and the game has not moved")
        }
    }

    @Test
    fun twoCommandsOnTheSameVersionLeaveExactlyOneWinner() {
        withGame { fixture ->
            val attempts = 6
            val barrier = CyclicBarrier(attempts)
            val pool = Executors.newFixedThreadPool(attempts)
            val candidates =
                listOf(
                    Move.of("e2", "e4"),
                    Move.of("d2", "d4"),
                    Move.of("g1", "f3"),
                    Move.of("b1", "c3"),
                    Move.of("c2", "c4"),
                    Move.of("a2", "a3"),
                )

            val results =
                try {
                    pool
                        .invokeAll(
                            candidates.map { move ->
                                Callable {
                                    barrier.await(10, TimeUnit.SECONDS)
                                    fixture.commands.makeMove(fixture.white, fixture.gameId, 0, move)
                                }
                            },
                        ).map { it.get() }
                } finally {
                    pool.shutdown()
                }

            assertEquals(
                1,
                results.count { it is CommandResult.Applied },
                "exactly one command may win a version",
            )
            assertTrue(
                results.filterNot { it is CommandResult.Applied }.all { it is CommandResult.StaleVersion },
                "everyone else is stale, not something else",
            )
            assertEquals(1, fixture.version())
            assertEquals(1, fixture.moves().size)
        }
    }

    @Test
    fun aRejectionNeverLeaksAnotherPlayersView() {
        withServer { fixture ->
            move("auth-1", fixture.gameId, 0, "e2", "e4")

            val rejection =
                json.decodeFromString<CommandRejection>(
                    move("auth-2", fixture.gameId, 0, "e7", "e5").bodyAsText(),
                )

            assertEquals("BLACK", rejection.game?.yourSide, "the state is rendered for the caller")
            assertNull(rejection.game?.result)
        }
    }
}
