@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.DrawClaim
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.core.chess.TerminationReason
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.time.ZoneOffset
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Persisting and loading real games against real PostgreSQL.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class GameRepositoryTest {
    private fun withRepository(block: (Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            block(Fixture(dataSource, database, GameRepository(database)))
        }

    private class Fixture(
        val dataSource: DataSource,
        val database: Database,
        val repository: GameRepository,
    ) {
        val white: Uuid = Uuid.random()
        val black: Uuid = Uuid.random()
        val series: Uuid = Uuid.random()

        init {
            val now = Instant.now().atOffset(ZoneOffset.UTC)
            transaction(database) {
                listOf(white to "jordan", black to "alex").forEach { (id, name) ->
                    UsersTable.insert { row ->
                        row[UsersTable.id] = id
                        row[UsersTable.authSubject] = "auth-$name-$id"
                        row[UsersTable.username] = name
                        row[UsersTable.usernameNormalized] = name
                        row[UsersTable.createdAt] = now
                    }
                }

                val (lower, higher) = if (white < black) white to black else black to white
                GameSeriesTable.insert { row ->
                    row[GameSeriesTable.id] = series
                    row[GameSeriesTable.userAId] = lower
                    row[GameSeriesTable.userBId] = higher
                    row[GameSeriesTable.status] = "ACTIVE"
                    row[GameSeriesTable.closeAfterCurrentGame] = false
                    row[GameSeriesTable.createdAt] = now
                }
            }
        }

        fun create(game: ChessGame): Uuid = repository.create(series, 1, white, black, game)

        fun gameRowCount(): Int = transaction(database) { GamesTable.selectAll().count().toInt() }

        fun moveRowCount(gameId: Uuid): Int =
            transaction(database) {
                MovesTable
                    .selectAll()
                    .where { MovesTable.gameId eq gameId }
                    .count()
                    .toInt()
            }
    }

    private fun played(vararg moves: Move): ChessGame {
        var game = ChessGame.newGame()
        moves.forEach { game = ChessRules.applyMove(game, it) }
        return game
    }

    @Test
    fun anEmptyGameRoundTrips() {
        withRepository { fixture ->
            val id = fixture.create(ChessGame.newGame())
            val loaded = fixture.repository.load(id)

            assertEquals(ChessGame.newGame(), loaded?.game)
            assertEquals(0, loaded?.version)
            assertEquals(fixture.series, loaded?.seriesId)
            assertEquals(fixture.white, loaded?.whiteUserId)
            assertEquals(fixture.black, loaded?.blackUserId)
        }
    }

    @Test
    fun aGameWithHistoryRoundTripsExactly() {
        withRepository { fixture ->
            val game =
                played(
                    Move.of("e2", "e4"),
                    Move.of("c7", "c5"),
                    Move.of("g1", "f3"),
                    Move.of("d7", "d6"),
                )
            val id = fixture.create(game)

            val loaded = fixture.repository.load(id)?.game

            assertEquals(game, loaded, "the whole game, move history included, must come back")
            assertEquals(game.moves, loaded?.moves)
        }
    }

    @Test
    fun castlingRightsAndCountersSurviveTheRoundTrip() {
        withRepository { fixture ->
            val game =
                played(
                    Move.of("e2", "e4"),
                    Move.of("e7", "e5"),
                    Move.of("g1", "f3"),
                    Move.of("b8", "c6"),
                    Move.of("f1", "c4"),
                    Move.of("f8", "c5"),
                    Move.of("e1", "g1"),
                )
            val loaded = fixture.repository.load(fixture.create(game))?.game

            assertEquals(game.state.castlingRights, loaded?.state?.castlingRights)
            assertEquals(game.state.halfmoveClock, loaded?.state?.halfmoveClock)
            assertEquals(game.state.fullmoveNumber, loaded?.state?.fullmoveNumber)
            assertEquals(game.state.board, loaded?.state?.board)
        }
    }

    @Test
    fun anEnPassantTargetSurvivesTheRoundTrip() {
        withRepository { fixture ->
            val game = played(Move.of("e2", "e4"))
            val loaded = fixture.repository.load(fixture.create(game))?.game

            assertEquals(Square.parse("e3"), loaded?.state?.enPassantTarget)
        }
    }

    @Test
    fun theRepetitionHistorySurvivesTheRoundTrip() {
        withRepository { fixture ->
            val game =
                played(
                    Move.of("g1", "f3"),
                    Move.of("g8", "f6"),
                    Move.of("f3", "g1"),
                    Move.of("f6", "g8"),
                )
            val loaded = fixture.repository.load(fixture.create(game))?.game

            assertEquals(game.state.drawRuleState, loaded?.state?.drawRuleState)
            assertEquals(
                2,
                loaded
                    ?.state
                    ?.drawRuleState
                    ?.positionCounts
                    ?.values
                    ?.max(),
            )
        }
    }

    @Test
    fun aPromotionSurvivesTheRoundTrip() {
        withRepository { fixture ->
            var game = ChessGame.newGame()
            listOf(
                Move.of("a2", "a4"),
                Move.of("b7", "b5"),
                Move.of("a4", "b5"),
                Move.of("b8", "c6"),
                Move.of("b5", "b6"),
                Move.of("c6", "d4"),
                Move.of("b6", "b7"),
                Move.of("d4", "c6"),
                Move.of("b7", "a8", PieceType.KNIGHT),
            ).forEach { game = ChessRules.applyMove(game, it) }

            val id = fixture.create(game)
            val loaded = fixture.repository.load(id)?.game

            assertEquals(game, loaded)
            assertEquals(PieceType.KNIGHT, loaded?.lastMove?.promotion)
        }
    }

    @Test
    fun aFinishedGameKeepsItsResult() {
        withRepository { fixture ->
            val game =
                played(
                    Move.of("f2", "f3"),
                    Move.of("e7", "e5"),
                    Move.of("g2", "g4"),
                    Move.of("d8", "h4"),
                )
            val loaded = fixture.repository.load(fixture.create(game))

            assertTrue(loaded!!.isComplete)
            assertEquals(TerminationReason.CHECKMATE, loaded.game.result?.reason)
            assertEquals(Side.BLACK, loaded.game.result?.winner)
        }
    }

    @Test
    fun eachMoveIsStoredAsItsOwnRow() {
        withRepository { fixture ->
            val id = fixture.create(played(Move.of("e2", "e4"), Move.of("e7", "e5")))

            assertEquals(2, fixture.moveRowCount(id))
            assertEquals(Side.WHITE, fixture.repository.storedSideAt(id, ply = 1))
            assertEquals(Side.BLACK, fixture.repository.storedSideAt(id, ply = 2))
        }
    }

    @Test
    fun savingMovesTheVersionOn() {
        withRepository { fixture ->
            val id = fixture.create(ChessGame.newGame())
            val afterMove = ChessRules.applyMove(ChessGame.newGame(), Move.of("e2", "e4"))

            val version = fixture.repository.save(id, expectedVersion = 0, game = afterMove)
            val loaded = fixture.repository.load(id)

            assertEquals(1, version)
            assertEquals(1, loaded?.version)
            assertEquals(afterMove, loaded?.game)
        }
    }

    @Test
    fun aStaleWriteIsRejectedAndChangesNothing() {
        withRepository { fixture ->
            val id = fixture.create(ChessGame.newGame())
            val first = ChessRules.applyMove(ChessGame.newGame(), Move.of("e2", "e4"))
            fixture.repository.save(id, expectedVersion = 0, game = first)

            val stale = ChessRules.applyMove(ChessGame.newGame(), Move.of("d2", "d4"))
            val failure =
                assertFailsWith<StaleGameVersionException> {
                    fixture.repository.save(id, expectedVersion = 0, game = stale)
                }

            assertEquals(0, failure.expectedVersion)
            assertEquals(1, failure.actualVersion)
            assertEquals(first, fixture.repository.load(id)?.game, "the losing write left no trace")
            assertEquals(1, fixture.repository.load(id)?.version)
        }
    }

    @Test
    fun theHistoryIsReplacedNotAppended() {
        withRepository { fixture ->
            val id = fixture.create(played(Move.of("e2", "e4")))
            val afterUndo = ChessRules.undoLastMove(played(Move.of("e2", "e4")))

            fixture.repository.save(id, expectedVersion = 0, game = afterUndo)

            assertEquals(0, fixture.moveRowCount(id))
            assertEquals(ChessGame.newGame(), fixture.repository.load(id)?.game)
        }
    }

    @Test
    fun aFailedTransactionLeavesNothingBehind() {
        withRepository { fixture ->
            assertFailsWith<Exception> {
                // Sequence number 1 is already taken by the first game, so the second
                // insert violates the unique constraint after its moves were written.
                fixture.create(ChessGame.newGame())
                fixture.repository.create(
                    fixture.series,
                    1,
                    fixture.white,
                    fixture.black,
                    played(Move.of("e2", "e4")),
                )
            }

            assertEquals(1, fixture.gameRowCount(), "only the first game was committed")
            assertEquals(
                0,
                transaction(fixture.database) { MovesTable.selectAll().count().toInt() },
                "the rolled-back game left no move rows",
            )
        }
    }

    @Test
    fun anAuditEventIsRecordedWithTheWrite() {
        withRepository { fixture ->
            val id = fixture.create(ChessGame.newGame())
            val afterMove = ChessRules.applyMove(ChessGame.newGame(), Move.of("e2", "e4"))

            fixture.repository.save(id, expectedVersion = 0, game = afterMove, auditEvent = "MoveMade")

            assertEquals(listOf("MoveMade"), fixture.repository.auditTrail(id))
        }
    }

    @Test
    fun aClaimedDrawIsPersisted() {
        withRepository { fixture ->
            var game = ChessGame.newGame()
            repeat(2) {
                listOf(
                    Move.of("g1", "f3"),
                    Move.of("g8", "f6"),
                    Move.of("f3", "g1"),
                    Move.of("f6", "g8"),
                ).forEach { game = ChessRules.applyMove(game, it) }
            }
            val claimed = ChessRules.claimDraw(game, DrawClaim.THREEFOLD_REPETITION)

            val loaded = fixture.repository.load(fixture.create(claimed))

            assertTrue(loaded!!.isComplete)
            assertEquals(TerminationReason.THREEFOLD_REPETITION_CLAIM, loaded.game.result?.reason)
            assertEquals(claimed.moves, loaded.game.moves)
        }
    }

    @Test
    fun loadingAnUnknownGameFindsNothing() {
        withRepository { fixture ->
            assertNull(fixture.repository.load(Uuid.random()))
        }
    }

    @Test
    fun theStoredStateStaysReadableAsPlainJson() {
        withRepository { fixture ->
            val id = fixture.create(played(Move.of("e2", "e4")))

            val document =
                transaction(fixture.database) {
                    GamesTable
                        .selectAll()
                        .where { GamesTable.id eq id }
                        .single()[GamesTable.state]
                }

            assertEquals(8, document.board.size)
            assertEquals("BLACK", document.sideToMove)
            assertEquals("KQkq", document.castling)
            assertEquals("e3", document.enPassant)
            assertFalse(document.repetitions.isEmpty())
        }
    }
}
