@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.game

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.DrawClaim
import com.jmussel.chessgame.core.chess.DrawRuleState
import com.jmussel.chessgame.core.chess.GameOutcome
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.TerminationReason
import com.jmussel.chessgame.server.api.CommandRejection
import com.jmussel.chessgame.server.api.GameView
import com.jmussel.chessgame.server.api.RejectionReason
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.StoredGame
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
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Claiming a draw against the server, which decides whether the claim is real.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class ClaimDrawCommandTest {
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

        fun game(): StoredGame = assertNotNull(games.load(gameId))

        /** Shuffles the knights back and forth until the position has occurred three times. */
        fun repeatPositionThreeTimes() {
            val round =
                listOf(
                    white to Move.of("g1", "f3"),
                    black to Move.of("g8", "f6"),
                    white to Move.of("f3", "g1"),
                    black to Move.of("f6", "g8"),
                )

            repeat(2) { pass ->
                round.forEachIndexed { index, (player, move) ->
                    val version = (pass * round.size + index).toLong()
                    val result = commands.makeMove(player, gameId, version, move)
                    assertTrue(result is CommandResult.Applied, "setup move failed: $result")
                }
            }
        }

        /** Puts the game one halfmove short of the fifty-move rule, with White to move. */
        fun reachTheFiftyMoveRule() {
            val current = game()
            val state = current.game.state.copy(drawRuleState = DrawRuleState(halfmoveClock = 100))

            games.save(
                id = gameId,
                expectedVersion = current.version,
                game = ChessGame(state = state, history = current.game.history),
            )
        }
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

    private suspend fun ApplicationTestBuilder.claim(
        subject: String,
        gameId: Uuid,
        expectedVersion: Long,
        claim: String,
    ) = client.post("/games/$gameId/draw-claims") {
        header("Authorization", "Bearer ${tokens.tokenFor(subject)}")
        contentType(ContentType.Application.Json)
        setBody("""{"expectedVersion":$expectedVersion,"claim":"$claim"}""")
    }

    @Test
    fun aValidThreefoldClaimEndsTheGame() {
        withGame { fixture ->
            fixture.repeatPositionThreeTimes()
            val before = fixture.game()

            val result =
                fixture.commands.claimDraw(
                    fixture.white,
                    fixture.gameId,
                    before.version,
                    DrawClaim.THREEFOLD_REPETITION,
                )

            assertTrue(result is CommandResult.Applied, "expected the claim to be accepted, got $result")

            val after = fixture.game()

            assertTrue(after.isComplete)
            assertEquals(GameOutcome.DRAW, after.game.result?.outcome)
            assertEquals(TerminationReason.THREEFOLD_REPETITION_CLAIM, after.game.result?.reason)
            assertEquals(before.version + 1, after.version)
        }
    }

    @Test
    fun aClaimWithNoRepetitionIsRefused() {
        withGame { fixture ->
            val result =
                fixture.commands.claimDraw(
                    fixture.white,
                    fixture.gameId,
                    0,
                    DrawClaim.THREEFOLD_REPETITION,
                )

            assertTrue(result is CommandResult.NoSuchClaim)
            assertFalse(fixture.game().isComplete)
            assertEquals(0, fixture.game().version, "a refused claim writes nothing")
        }
    }

    @Test
    fun theFiftyMoveClaimNeedsTheClock() {
        withGame { fixture ->
            assertTrue(
                fixture.commands.claimDraw(fixture.white, fixture.gameId, 0, DrawClaim.FIFTY_MOVE_RULE)
                    is CommandResult.NoSuchClaim,
            )

            fixture.reachTheFiftyMoveRule()
            val ready = fixture.game()

            val result =
                fixture.commands.claimDraw(
                    fixture.white,
                    fixture.gameId,
                    ready.version,
                    DrawClaim.FIFTY_MOVE_RULE,
                )

            assertTrue(result is CommandResult.Applied)
            assertEquals(
                TerminationReason.FIFTY_MOVE_RULE_CLAIM,
                fixture
                    .game()
                    .game.result
                    ?.reason,
            )
        }
    }

    @Test
    fun theWrongClaimForThePositionIsRefused() {
        withGame { fixture ->
            fixture.repeatPositionThreeTimes()
            val ready = fixture.game()

            val result =
                fixture.commands.claimDraw(
                    fixture.white,
                    fixture.gameId,
                    ready.version,
                    DrawClaim.FIFTY_MOVE_RULE,
                )

            assertTrue(result is CommandResult.NoSuchClaim)
            assertFalse(fixture.game().isComplete)
        }
    }

    @Test
    fun onlyThePlayerToMoveMayClaim() {
        withGame { fixture ->
            fixture.repeatPositionThreeTimes()
            val ready = fixture.game()

            val result =
                fixture.commands.claimDraw(
                    fixture.black,
                    fixture.gameId,
                    ready.version,
                    DrawClaim.THREEFOLD_REPETITION,
                )

            assertTrue(result is CommandResult.NotYourTurn)
            assertFalse(fixture.game().isComplete)
        }
    }

    @Test
    fun aStrangerCannotClaim() {
        withGame { fixture ->
            fixture.repeatPositionThreeTimes()
            val stranger = fixture.named("auth-3", "Sam")
            val ready = fixture.game()

            assertEquals(
                CommandResult.NotAParticipant,
                fixture.commands.claimDraw(
                    stranger,
                    fixture.gameId,
                    ready.version,
                    DrawClaim.THREEFOLD_REPETITION,
                ),
            )
        }
    }

    @Test
    fun aStaleClaimIsRefused() {
        withGame { fixture ->
            fixture.repeatPositionThreeTimes()

            val result =
                fixture.commands.claimDraw(
                    fixture.white,
                    fixture.gameId,
                    0,
                    DrawClaim.THREEFOLD_REPETITION,
                )

            assertTrue(result is CommandResult.StaleVersion)
            assertFalse(fixture.game().isComplete)
        }
    }

    @Test
    fun aFinishedGameCannotBeClaimedAgain() {
        withGame { fixture ->
            fixture.repeatPositionThreeTimes()
            val ready = fixture.game()
            fixture.commands.claimDraw(
                fixture.white,
                fixture.gameId,
                ready.version,
                DrawClaim.THREEFOLD_REPETITION,
            )

            val again =
                fixture.commands.claimDraw(
                    fixture.white,
                    fixture.gameId,
                    ready.version + 1,
                    DrawClaim.THREEFOLD_REPETITION,
                )

            assertTrue(again is CommandResult.GameOver)
            assertEquals(ready.version + 1, fixture.game().version, "nothing further was written")
        }
    }

    @Test
    fun anAcceptedClaimIsAudited() {
        withGame { fixture ->
            fixture.repeatPositionThreeTimes()
            val ready = fixture.game()

            fixture.commands.claimDraw(
                fixture.white,
                fixture.gameId,
                ready.version,
                DrawClaim.THREEFOLD_REPETITION,
            )

            assertEquals("DrawClaimed", fixture.games.auditTrail(fixture.gameId).last())
        }
    }

    @Test
    fun theGameViewOffersTheClaimToThePlayerToMove() {
        withServer { fixture ->
            fixture.repeatPositionThreeTimes()

            val forWhite =
                json.decodeFromString<GameView>(
                    client
                        .get("/games/${fixture.gameId}") {
                            header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}")
                        }.bodyAsText(),
                )
            val forBlack =
                json.decodeFromString<GameView>(
                    client
                        .get("/games/${fixture.gameId}") {
                            header("Authorization", "Bearer ${tokens.tokenFor("auth-2")}")
                        }.bodyAsText(),
                )

            assertEquals(listOf("THREEFOLD_REPETITION"), forWhite.availableDrawClaims)
            assertTrue(forBlack.availableDrawClaims.isEmpty(), "it is not Black's move")
        }
    }

    @Test
    fun aFreshGameOffersNoClaims() {
        withServer { fixture ->
            val view =
                json.decodeFromString<GameView>(
                    client
                        .get("/games/${fixture.gameId}") {
                            header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}")
                        }.bodyAsText(),
                )

            assertTrue(view.availableDrawClaims.isEmpty())
        }
    }

    @Test
    fun theEndpointAcceptsAValidClaim() {
        withServer { fixture ->
            fixture.repeatPositionThreeTimes()
            val ready = fixture.game()

            val response = claim("auth-1", fixture.gameId, ready.version, "THREEFOLD_REPETITION")

            assertEquals(HttpStatusCode.OK, response.status)

            val view = json.decodeFromString<GameView>(response.bodyAsText())

            assertEquals("DRAW", view.result)
            assertEquals("THREEFOLD_REPETITION_CLAIM", view.terminationReason)
            assertTrue(view.isOver)
        }
    }

    @Test
    fun theEndpointRefusesAnInvalidClaim() {
        withServer { fixture ->
            val response = claim("auth-1", fixture.gameId, 0, "THREEFOLD_REPETITION")

            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)

            val rejection = json.decodeFromString<CommandRejection>(response.bodyAsText())

            assertEquals(RejectionReason.NO_SUCH_CLAIM, rejection.reason)
            assertFalse(rejection.game?.isOver ?: true)
        }
    }

    @Test
    fun theEndpointRejectsAnUnknownClaim() {
        withServer { fixture ->
            assertEquals(
                HttpStatusCode.BadRequest,
                claim("auth-1", fixture.gameId, 0, "BECAUSE_I_SAID_SO").status,
            )
        }
    }

    @Test
    fun theEndpointNeedsAToken() {
        withServer { fixture ->
            val response =
                client.post("/games/${fixture.gameId}/draw-claims") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"expectedVersion":0,"claim":"THREEFOLD_REPETITION"}""")
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }
}
