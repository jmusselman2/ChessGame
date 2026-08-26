@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.game

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.DrawClaim
import com.jmussel.chessgame.core.chess.DrawRuleState
import com.jmussel.chessgame.core.chess.GameOutcome
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.TerminationReason
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.StoredGame
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.series.seriesService
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Ending a game: the result, the moment it ended, and the audit event, committed with the
 * move that caused them and written exactly once.
 *
 * A finished game is final (`D017`), so "exactly once" is not only tidiness — it is what
 * stops a duplicate or racing command from rewriting how a game ended.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class FinalizeGameTest {
    private val tokens = TestTokens()

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

        fun endEvents() = games.auditEvents(gameId).filter { it.type == GameRepository.GAME_ENDED }

        /** Plays [move] for [player] at the version the game is actually at. */
        fun play(
            player: Uuid,
            move: Move,
        ): CommandResult {
            val result = commands.makeMove(player, gameId, game().version, move)
            assertTrue(result is CommandResult.Applied, "setup move failed: $result")
            return result
        }

        /** The shortest checkmate there is, played through the command layer. */
        fun playFoolsMate() {
            play(white, Move.of("f2", "f3"))
            play(black, Move.of("e7", "e5"))
            play(white, Move.of("g2", "g4"))
            play(black, Move.of("d8", "h4"))
        }

        /** Shuffles the knights back and forth until the position has occurred three times. */
        fun repeatPositionThreeTimes() {
            val round =
                listOf(
                    white to Move.of("g1", "f3"),
                    black to Move.of("g8", "f6"),
                    white to Move.of("f3", "g1"),
                    black to Move.of("f6", "g8"),
                )

            repeat(2) { round.forEach { (player, move) -> play(player, move) } }
        }

        /** Puts the game one quiet halfmove short of the seventy-five-move rule (`D019`). */
        fun reachTheSeventyFiveMoveRule() {
            val current = game()

            games.save(
                id = gameId,
                expectedVersion = current.version,
                game =
                    ChessGame(
                        state = current.game.state.copy(drawRuleState = DrawRuleState(halfmoveClock = 149)),
                        history = current.game.history,
                    ),
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

    @Test
    fun aCheckmatingMoveFinalizesTheGame() {
        withGame { fixture ->
            fixture.playFoolsMate()

            val finished = fixture.game()

            assertTrue(finished.isComplete)
            assertEquals(GameOutcome.BLACK_WINS, finished.game.result?.outcome)
            assertEquals(TerminationReason.CHECKMATE, finished.game.result?.reason)
            assertNotNull(finished.endedAt, "a finished game records when it ended")
        }
    }

    @Test
    fun aRunningGameIsNotFinalized() {
        withGame { fixture ->
            fixture.play(fixture.white, Move.of("e2", "e4"))

            val running = fixture.game()

            assertNull(running.game.result)
            assertNull(running.endedAt, "an unfinished game has not ended")
            assertTrue(fixture.endEvents().isEmpty())
        }
    }

    @Test
    fun finalizingRecordsExactlyOneEvent() {
        withGame { fixture ->
            fixture.playFoolsMate()

            assertEquals(1, fixture.endEvents().size)
            assertEquals(
                listOf("MoveMade", "MoveMade", "MoveMade", "MoveMade", GameRepository.GAME_ENDED),
                fixture.games.auditTrail(fixture.gameId),
                "the game finished on the last move, not before it",
            )
        }
    }

    @Test
    fun theEventSaysHowTheGameEnded() {
        withGame { fixture ->
            fixture.playFoolsMate()

            val payload = fixture.endEvents().single().payload

            assertEquals(GameOutcome.BLACK_WINS.name, payload["result"]?.jsonPrimitive?.content)
            assertEquals(
                TerminationReason.CHECKMATE.name,
                payload["terminationReason"]?.jsonPrimitive?.content,
            )
            assertEquals(
                fixture.game().version,
                payload["version"]?.jsonPrimitive?.content?.toLong(),
                "the version the game was finalized at",
            )
        }
    }

    @Test
    fun theResultAndTheMoveThatCausedItAreStoredTogether() {
        withGame { fixture ->
            fixture.playFoolsMate()

            val finished = fixture.game()

            // One load, one transaction's worth of truth: the mating move is in the
            // history that the stored result describes.
            assertEquals(4, finished.game.history.size)
            assertEquals(listOf("f2f3", "e7e5", "g2g4", "d8h4"), finished.game.moves.map { it.toString() })
            assertEquals(TerminationReason.CHECKMATE, finished.game.result?.reason)
        }
    }

    @Test
    fun aFinishedGameIsNotFinalizedAgain() {
        withGame { fixture ->
            fixture.playFoolsMate()
            val finished = fixture.game()

            // Everything a client could still send is refused (`D017`).
            val movedAgain =
                fixture.commands.makeMove(fixture.white, fixture.gameId, finished.version, Move.of("e2", "e4"))
            val undone = fixture.commands.undoMove(fixture.black, fixture.gameId, finished.version)
            val claimed =
                fixture.commands.claimDraw(
                    fixture.white,
                    fixture.gameId,
                    finished.version,
                    DrawClaim.FIFTY_MOVE_RULE,
                )

            assertTrue(movedAgain is CommandResult.GameOver)
            assertTrue(undone is CommandResult.GameOver)
            assertTrue(claimed is CommandResult.GameOver)

            val after = fixture.game()

            assertEquals(1, fixture.endEvents().size)
            assertEquals(finished.version, after.version, "nothing was written")
            assertEquals(finished.endedAt, after.endedAt, "the moment it ended did not move")
            assertEquals(finished.game.result, after.game.result)
        }
    }

    @Test
    fun aDuplicateMatingMoveFinalizesTheGameOnce() {
        withGame { fixture ->
            fixture.play(fixture.white, Move.of("f2", "f3"))
            fixture.play(fixture.black, Move.of("e7", "e5"))
            fixture.play(fixture.white, Move.of("g2", "g4"))

            val version = fixture.game().version
            val mate = Move.of("d8", "h4")
            val barrier = CyclicBarrier(2)
            val pool = Executors.newFixedThreadPool(2)

            // The same command twice at the same version: a retry after a timeout, or two
            // taps. Only one may end the game.
            val results =
                try {
                    pool
                        .invokeAll(
                            List(2) {
                                Callable {
                                    barrier.await(WAIT_SECONDS, TimeUnit.SECONDS)
                                    fixture.commands.makeMove(fixture.black, fixture.gameId, version, mate)
                                }
                            },
                        ).map { it.get(WAIT_SECONDS, TimeUnit.SECONDS) }
                } finally {
                    pool.shutdownNow()
                }

            assertEquals(1, results.count { it is CommandResult.Applied }, "exactly one winner")
            assertEquals(1, results.count { it is CommandResult.StaleVersion }, "the other is stale")
            val finished = fixture.game()

            assertEquals(1, fixture.endEvents().size, "the game ended exactly once")
            assertEquals(version + 1, finished.version)
            assertEquals(TerminationReason.CHECKMATE, finished.game.result?.reason)
        }
    }

    @Test
    fun aClaimedDrawFinalizesTheGameTheSameWay() {
        withGame { fixture ->
            fixture.repeatPositionThreeTimes()

            val result =
                fixture.commands.claimDraw(
                    fixture.white,
                    fixture.gameId,
                    fixture.game().version,
                    DrawClaim.THREEFOLD_REPETITION,
                )

            assertTrue(result is CommandResult.Applied)

            val finished = fixture.game()

            assertEquals(GameOutcome.DRAW, finished.game.result?.outcome)
            assertEquals(TerminationReason.THREEFOLD_REPETITION_CLAIM, finished.game.result?.reason)
            assertNotNull(finished.endedAt)
            val payload = fixture.endEvents().single().payload

            assertEquals(
                TerminationReason.THREEFOLD_REPETITION_CLAIM.name,
                payload["terminationReason"]?.jsonPrimitive?.content,
            )
        }
    }

    @Test
    fun anAutomaticDrawFinalizesTheGameWithoutAnyoneClaimingIt() {
        withGame { fixture ->
            fixture.reachTheSeventyFiveMoveRule()

            // Nobody claims anything: the move itself crosses the line (`D019`).
            fixture.play(fixture.white, Move.of("g1", "f3"))

            val finished = fixture.game()

            assertEquals(GameOutcome.DRAW, finished.game.result?.outcome)
            assertEquals(TerminationReason.SEVENTY_FIVE_MOVE_RULE, finished.game.result?.reason)
            assertNotNull(finished.endedAt)
            assertEquals(1, fixture.endEvents().size)
        }
    }

    private companion object {
        const val WAIT_SECONDS = 10L
    }
}
