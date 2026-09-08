@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.game

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.StoredGame
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.series.seriesService
import com.jmussel.chessgame.server.user.Username
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
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
 * The race `ARCHITECTURE.md` §10 names: one player takes their move back at the same moment
 * the other answers it.
 *
 * Both commands are written against the same version, both are individually legal, and they
 * lead to different positions. Exactly one may happen (`D021`), and the game must end up in
 * whichever of the two states actually won — never a mixture.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class MoveVersusUndoTest {
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
        private var round = 0

        /** A fresh game with White's first move already played, so both commands are live. */
        fun startContestedGame(): Contest {
            round += 1
            val jordan = named("auth-white-$round", "White$round")
            val alex = named("auth-black-$round", "Black$round")
            FriendshipRepository(database).add(jordan, alex)

            val opened = seriesService(database, FixedCoin(jordan < alex)).openWithGame(jordan, alex)
            val gameId = assertNotNull(opened.series.currentGameId)

            val played = commands.makeMove(jordan, gameId, 0, Move.of("g1", "f3"))
            assertTrue(played is CommandResult.Applied, "setup move failed: $played")

            return Contest(gameId = gameId, white = jordan, black = alex, version = 1)
        }

        fun named(
            subject: String,
            username: String,
        ): Uuid {
            val user = users.resolveBySubject(subject)
            users.claimUsername(user.id, Username.of(username))
            return user.id
        }

        fun game(gameId: Uuid): StoredGame = assertNotNull(games.load(gameId))
    }

    private data class Contest(
        val gameId: Uuid,
        val white: Uuid,
        val black: Uuid,
        val version: Long,
    )

    private fun withFixture(block: (Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val games = GameRepository(database)
            block(Fixture(database, UserRepository(database), games, GameCommandService(database, games, seriesService(database))))
        }

    /** Runs the undo and the answering move at the same moment; returns both results. */
    private fun raceOnce(
        fixture: Fixture,
        contest: Contest,
    ): Pair<CommandResult, CommandResult> {
        val barrier = CyclicBarrier(2)
        val pool = Executors.newFixedThreadPool(2)

        return try {
            val results =
                pool
                    .invokeAll(
                        listOf(
                            Callable {
                                barrier.await(10, TimeUnit.SECONDS)
                                fixture.commands.undoMove(contest.white, contest.gameId, contest.version)
                            },
                            Callable {
                                barrier.await(10, TimeUnit.SECONDS)
                                fixture.commands.makeMove(
                                    contest.black,
                                    contest.gameId,
                                    contest.version,
                                    Move.of("g8", "f6"),
                                )
                            },
                        ),
                    ).map { it.get() }

            results[0] to results[1]
        } finally {
            pool.shutdown()
        }
    }

    /**
     * A mutating command's read waits for an in-flight write to the same game (`M16.7`).
     *
     * This is the property that stops the loser being refused on a *rule* instead of on its
     * version. A game lives in two tables — the row and its move history — and READ
     * COMMITTED gives each statement its own snapshot, while a competing `save` commits
     * both together. An unlocked read landing either side of that commit takes the row from
     * before and the history from after: a `StoredGame` whose version still matches, so it
     * passes the version check, carrying the winner's moves. `canUndo` and `isLegal` then
     * answer about a position that never existed, and the command refuses with
     * `NothingToUndo` or `IllegalMove` — leaving the client no canonical state to recover
     * from, which is the opposite of what `D021` promises.
     *
     * Reproducing the torn read itself would mean interposing between two statements inside
     * `load`. What is worth pinning down instead is the thing that makes it impossible: a
     * command's read does not begin until any in-flight write to that game has finished, so
     * it can never straddle one. Without the row lock this returns at once with the old
     * version; with it, it waits and sees what actually won.
     */
    @Test
    fun aMutatingCommandsReadWaitsForAnInFlightWriteRatherThanStraddlingIt() {
        withFixture { fixture ->
            val contest = fixture.startContestedGame()
            val holdsTheRow = CountDownLatch(1)
            val mayCommit = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(2)

            try {
                // The winner takes the row and keeps it until the reader is demonstrably
                // waiting, which is exactly the window an unlocked read could fall into.
                val winner =
                    pool.submit(
                        Callable {
                            transaction(fixture.database) {
                                val stored = assertNotNull(fixture.games.loadForUpdate(contest.gameId))
                                holdsTheRow.countDown()
                                mayCommit.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                                fixture.games.save(
                                    id = contest.gameId,
                                    expectedVersion = contest.version,
                                    game = ChessRules.undo(stored.game, Side.WHITE),
                                    auditEvent = "MoveUndone",
                                )
                            }
                        },
                    )
                assertTrue(holdsTheRow.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "the winner never took the row")

                val reader =
                    pool.submit(
                        Callable {
                            transaction(fixture.database) { assertNotNull(fixture.games.loadForUpdate(contest.gameId)).version }
                        },
                    )

                // Without the lock this returns the pre-commit version immediately, which is
                // the read that can be half of each game. The winner is still holding the
                // row here, so a read that answers has not waited for it.
                assertNull(
                    runCatching { reader.get(1, TimeUnit.SECONDS) }.getOrNull(),
                    "the read answered while a write to the same game was still in flight",
                )

                mayCommit.countDown()
                winner.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

                assertEquals(
                    contest.version + 1,
                    reader.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "the read must see what actually won, never the version it was racing",
                )
            } finally {
                pool.shutdown()
            }
        }
    }

    @Test
    fun exactlyOneOfMoveAndUndoHappens() {
        withFixture { fixture ->
            repeat(ROUNDS) { round ->
                val contest = fixture.startContestedGame()

                val (undoResult, moveResult) = raceOnce(fixture, contest)
                val applied = listOf(undoResult, moveResult).count { it is CommandResult.Applied }
                val stale = listOf(undoResult, moveResult).count { it is CommandResult.StaleVersion }

                assertEquals(1, applied, "round $round: exactly one command may win (undo=$undoResult move=$moveResult)")
                assertEquals(1, stale, "round $round: the loser is told the version moved on (undo=$undoResult move=$moveResult)")
            }
        }
    }

    @Test
    fun theStoredGameMatchesWhicheverCommandWon() {
        withFixture { fixture ->
            repeat(ROUNDS) { round ->
                val contest = fixture.startContestedGame()

                val (undoResult, _) = raceOnce(fixture, contest)
                val stored = fixture.game(contest.gameId)

                assertEquals(contest.version + 1, stored.version, "round $round: one transition happened")

                if (undoResult is CommandResult.Applied) {
                    assertEquals(
                        ChessGame.newGame(),
                        stored.game,
                        "round $round: the undo won, so the game is back at the start",
                    )
                    assertEquals(Side.WHITE, stored.game.sideToMove)
                } else {
                    assertEquals(
                        listOf(Move.of("g1", "f3"), Move.of("g8", "f6")),
                        stored.game.moves,
                        "round $round: the move won, so both moves stand",
                    )
                    assertEquals(Side.WHITE, stored.game.sideToMove)
                }
            }
        }
    }

    @Test
    fun theHistoryIsNeverAMixtureOfTheTwo() {
        withFixture { fixture ->
            repeat(ROUNDS) { round ->
                val contest = fixture.startContestedGame()

                raceOnce(fixture, contest)

                val stored = fixture.game(contest.gameId)

                assertTrue(
                    stored.game.moves.isEmpty() ||
                        stored.game.moves == listOf(Move.of("g1", "f3"), Move.of("g8", "f6")),
                    "round $round: unexpected history ${stored.game.moves}",
                )
                // Whatever won, the position and its history agree with each other.
                assertEquals(
                    stored.game.history.size,
                    stored.game.moves.size,
                    "round $round: the stored history is self-consistent",
                )
            }
        }
    }

    @Test
    fun onlyTheWinningCommandIsAudited() {
        withFixture { fixture ->
            repeat(ROUNDS) { round ->
                val contest = fixture.startContestedGame()

                val (undoResult, _) = raceOnce(fixture, contest)
                val trail = fixture.games.auditTrail(contest.gameId)
                val expected =
                    if (undoResult is CommandResult.Applied) {
                        listOf("MoveMade", "MoveUndone")
                    } else {
                        listOf("MoveMade", "MoveMade")
                    }

                assertEquals(expected, trail, "round $round: the losing command left no trace")
            }
        }
    }

    @Test
    fun theLoserCanCarryOnFromTheStateItIsGiven() {
        withFixture { fixture ->
            val contest = fixture.startContestedGame()
            val (undoResult, moveResult) = raceOnce(fixture, contest)

            val loser = if (undoResult is CommandResult.Applied) moveResult else undoResult
            val stale = loser as CommandResult.StaleVersion

            assertEquals(
                fixture.game(contest.gameId).version,
                stale.game.version,
                "the rejection hands back the version that actually won",
            )

            // Playing on from that version is accepted, whichever way the race went.
            val next =
                if (stale.game.game.sideToMove == Side.WHITE) {
                    fixture.commands.makeMove(contest.white, contest.gameId, stale.game.version, Move.of("e2", "e4"))
                } else {
                    fixture.commands.makeMove(contest.black, contest.gameId, stale.game.version, Move.of("e7", "e5"))
                }

            assertTrue(next is CommandResult.Applied, "the loser recovers with no special handling: $next")
        }
    }

    private companion object {
        /** Enough repeats that a race that only sometimes goes wrong would show up. */
        const val ROUNDS = 15
        const val TIMEOUT_SECONDS = 10L
    }
}
