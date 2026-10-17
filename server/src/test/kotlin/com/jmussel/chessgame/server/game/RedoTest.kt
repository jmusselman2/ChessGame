@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.game

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.DrawClaim
import com.jmussel.chessgame.core.chess.Move
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
import com.jmussel.chessgame.server.series.startSeries
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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.Database
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Redo online (`D092`, `M21.21`): the server keeps the recorded Undo operations, offers Redo
 * to the player to move, and restores the next operation exactly, once. A move that repeats
 * the next recorded move advances that path instead of clearing it (`D102`, `M22.2`).
 *
 * Jordan plays White and Alex Black throughout.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class RedoTest {
    private val tokens = TestTokens()
    private val json = Json { ignoreUnknownKeys = true }

    private class FixedCoin(
        private val value: Boolean,
    ) : Random() {
        override fun nextBits(bitCount: Int): Int = 0

        override fun nextBoolean(): Boolean = value

        override fun nextInt(until: Int): Int = if (value) 0 else until - 1
    }

    private class Fixture(
        val database: Database,
        val users: UserRepository,
        val games: GameRepository,
        val commands: GameCommandService,
    ) {
        lateinit var jordan: Uuid
        lateinit var alex: Uuid
        lateinit var seriesId: Uuid
        lateinit var gameId: Uuid

        fun startGame() {
            jordan = named("auth-1", "Jordan")
            alex = named("auth-2", "Alex")
            FriendshipRepository(database).add(jordan, alex)
            val opened = seriesService(database, FixedCoin(jordan < alex)).startSeries(jordan, alex)
            seriesId = opened.id
            gameId = assertNotNull(opened.currentGameId)
            check(game().whiteUserId == jordan) { "Jordan plays White" }
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

        val version: Long
            get() = game().version

        /** Whoever is to move plays each of [moves] in turn. */
        fun play(vararg moves: String) {
            moves.forEach { move ->
                val player = if (game().game.sideToMove == com.jmussel.chessgame.core.chess.Side.WHITE) jordan else alex
                assertIs<CommandResult.Applied>(commands.makeMove(player, gameId, version, uci(move)), "setup move $move")
            }
        }

        fun undo(player: Uuid): CommandResult = commands.undoMove(player, gameId, version)

        fun redo(player: Uuid): CommandResult = commands.redo(player, gameId, version)

        fun view(viewer: Uuid): GameView = GameView.of(game(), viewer, assertNotNull(users.find(game().opponentOf(viewer))))
    }

    private fun withGame(block: (Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val games = GameRepository(database)
            val users = UserRepository(database)
            val fixture = Fixture(database, users, games, GameCommandService(database, games, seriesService(database), users))
            fixture.startGame()
            block(fixture)
        }

    private fun withServer(block: suspend ApplicationTestBuilder.(Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val games = GameRepository(database)
            val users = UserRepository(database)
            val fixture = Fixture(database, users, games, GameCommandService(database, games, seriesService(database), users))
            fixture.startGame()

            testApplication {
                application { testModule(tokens.verifier(), database) }
                block(fixture)
            }
        }

    // --- one-ply Undo and Redo -------------------------------------------------------------

    @Test
    fun redoRestoresAnUndoneMoveExactlyAndIncrementsTheVersionOnce() {
        withGame { fixture ->
            fixture.play("e2e4", "e7e5", "g1f3")
            val before = fixture.game().game
            fixture.undo(fixture.jordan)
            assertEquals(4, fixture.version)

            val result = fixture.redo(fixture.jordan)

            assertIs<CommandResult.Applied>(result)
            assertEquals(before, fixture.game().game, "board, side to move, castling, en passant, clocks, repetitions")
            assertEquals(5, fixture.version, "one increment")
        }
    }

    @Test
    fun onlyThePlayerToMoveMayRedoAndNothingIsRedoneWithoutAnUndo() {
        withGame { fixture ->
            fixture.play("e2e4")
            assertIs<CommandResult.NotYourTurn>(fixture.redo(fixture.jordan))
            assertIs<CommandResult.NothingToRedo>(fixture.redo(fixture.alex), "nothing has been undone")

            fixture.undo(fixture.jordan)

            assertIs<CommandResult.NotYourTurn>(fixture.redo(fixture.alex), "Redo continues from the position the Undo left Jordan")
            assertTrue(fixture.view(fixture.jordan).canRedo)
            assertFalse(fixture.view(fixture.alex).canRedo)
        }
    }

    @Test
    fun undoUndoRedoRedoRestoresBothOperationsInOrder() {
        withGame { fixture ->
            fixture.play("e2e4", "e7e5")
            val full = fixture.game().game
            fixture.undo(fixture.alex)
            fixture.undo(fixture.jordan)
            assertSame(ChessGame.newGame(), fixture.game().game)

            assertIs<CommandResult.Applied>(fixture.redo(fixture.jordan))
            assertEquals(listOf(uci("e2e4")), fixture.game().game.moves)
            assertIs<CommandResult.Applied>(fixture.redo(fixture.alex))

            assertEquals(full, fixture.game().game)
            assertFalse(fixture.view(fixture.jordan).canRedo)
            assertFalse(fixture.view(fixture.alex).canRedo)
        }
    }

    @Test
    fun undoUndoRedoUndoKeepsTheCompleteForwardPath() {
        withGame { fixture ->
            fixture.play("e2e4", "e7e5")
            val full = fixture.game().game
            fixture.undo(fixture.alex)
            fixture.undo(fixture.jordan)
            val twiceUndone = fixture.game().game
            fixture.redo(fixture.jordan)
            fixture.undo(fixture.jordan)

            assertEquals(twiceUndone, fixture.game().game, "the same two operations, in the same order")
            fixture.redo(fixture.jordan)
            fixture.redo(fixture.alex)
            assertEquals(full, fixture.game().game)
        }
    }

    @Test
    fun aPathLongerThanTwoOperationsAlternatesAtBothEnds() {
        withGame { fixture ->
            fixture.play("e2e4", "e7e5", "g1f3")
            val full = fixture.game().game
            fixture.undo(fixture.jordan)
            fixture.undo(fixture.alex)
            fixture.undo(fixture.jordan)
            assertSame(ChessGame.newGame(), fixture.game().game)
            assertEquals(
                3,
                fixture
                    .game()
                    .game.redoPath.size,
            )

            // At the start: redo one, undo it again.
            fixture.redo(fixture.jordan)
            fixture.undo(fixture.jordan)
            assertEquals(
                3,
                fixture
                    .game()
                    .game.redoPath.size,
            )

            fixture.redo(fixture.jordan)
            fixture.redo(fixture.alex)
            fixture.redo(fixture.jordan)
            assertEquals(full, fixture.game().game)

            // At the end: undo one, redo it again.
            fixture.undo(fixture.jordan)
            fixture.redo(fixture.jordan)
            assertEquals(full, fixture.game().game)
        }
    }

    // --- what clears the path ---------------------------------------------------------------

    @Test
    fun aDifferentMoveClearsThePath() {
        withGame { fixture ->
            fixture.play("e2e4", "e7e5")
            fixture.undo(fixture.alex)
            fixture.undo(fixture.jordan)

            fixture.play("d2d4")

            assertTrue(
                fixture
                    .game()
                    .game.redoPath
                    .isEmpty(),
            )
            assertFalse(fixture.view(fixture.alex).canRedo)
            assertIs<CommandResult.NothingToRedo>(fixture.redo(fixture.alex))
        }
    }

    @Test
    fun resignationClearsThePathAndStaysFinal() {
        withGame { fixture ->
            fixture.play("e2e4")
            fixture.undo(fixture.jordan)

            assertIs<CommandResult.Applied>(fixture.commands.resign(fixture.alex, fixture.gameId, fixture.version))

            assertTrue(
                fixture
                    .game()
                    .game.redoPath
                    .isEmpty(),
            )
            assertFalse(fixture.view(fixture.jordan).canRedo)
            assertIs<CommandResult.GameOver>(fixture.redo(fixture.jordan))
        }
    }

    @Test
    fun aSuccessfulDrawClaimClearsThePathAndStaysFinal() {
        withGame { fixture ->
            fixture.play("g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8", "e2e4")
            fixture.undo(fixture.jordan)
            assertTrue(DrawClaim.THREEFOLD_REPETITION in ChessRules.availableDrawClaims(fixture.game().game))

            val claimed = fixture.commands.claimDraw(fixture.jordan, fixture.gameId, fixture.version, DrawClaim.THREEFOLD_REPETITION)

            assertIs<CommandResult.Applied>(claimed)
            assertTrue(
                fixture
                    .game()
                    .game.redoPath
                    .isEmpty(),
            )
            assertIs<CommandResult.GameOver>(fixture.redo(fixture.jordan))
        }
    }

    @Test
    fun aNewlyPlayedGameEndingMoveClearsThePathAndStaysFinal() {
        withGame { fixture ->
            fixture.play("f2f3", "e7e5", "g2g4", "a7a6")
            fixture.undo(fixture.alex)
            assertTrue(fixture.view(fixture.alex).canRedo)

            fixture.play("d8h4")

            val finished = fixture.game()
            assertTrue(finished.isComplete, "checkmate")
            assertTrue(finished.game.redoPath.isEmpty())
            listOf(fixture.jordan, fixture.alex).forEach { assertFalse(fixture.view(it).canRedo) }
        }
    }

    // --- undo requests (D091) --------------------------------------------------------------

    @Test
    fun anAcceptedUndoRequestIsRedoneAsOneTwoPlyOperation() {
        withGame { fixture ->
            fixture.play("g1f3", "b8c6")
            val answered = fixture.game().game
            fixture.commands.requestUndo(fixture.jordan, fixture.gameId, 2)
            assertIs<CommandResult.Applied>(fixture.commands.acceptUndo(fixture.alex, fixture.gameId, 3))
            assertEquals(
                listOf(uci("g1f3"), uci("b8c6")),
                fixture
                    .game()
                    .game.redoPath
                    .single()
                    .moves,
            )
            assertTrue(fixture.view(fixture.jordan).canRedo)

            assertIs<CommandResult.Applied>(fixture.redo(fixture.jordan))

            assertEquals(answered, fixture.game().game, "both plies at once")
            assertEquals(5, fixture.version, "one increment for both")
        }
    }

    @Test
    fun bothCrossingOrdersLeaveTheSameRedoableTwoPlyOperation() {
        var requestFirst: ChessGame? = null
        withGame { fixture ->
            fixture.play("g1f3", "b8c6")
            fixture.commands.requestUndo(fixture.jordan, fixture.gameId, 2)
            assertIs<CommandResult.Applied>(fixture.commands.undoMove(fixture.alex, fixture.gameId, 2))
            requestFirst = fixture.game().game
        }
        var undoFirst: ChessGame? = null
        withGame { fixture ->
            fixture.play("g1f3", "b8c6")
            fixture.commands.undoMove(fixture.alex, fixture.gameId, 2)
            assertIs<CommandResult.Applied>(fixture.commands.requestUndo(fixture.jordan, fixture.gameId, 2))
            undoFirst = fixture.game().game
            assertEquals(listOf(uci("g1f3"), uci("b8c6")), undoFirst!!.redoPath.single().moves)

            assertIs<CommandResult.Applied>(fixture.redo(fixture.jordan))
            assertEquals(listOf(uci("g1f3"), uci("b8c6")), fixture.game().game.moves)
        }

        assertEquals(requestFirst, undoFirst)
    }

    @Test
    fun askingAndDecliningLeaveThePathAndRedoClearsTheRequest() {
        withGame { fixture ->
            fixture.play("e2e4", "e7e5", "g1f3", "b8c6", "f1c4")
            fixture.undo(fixture.jordan)
            val path = fixture.game().game.redoPath
            assertEquals(1, path.size)

            // Jordan to move, Alex's b8c6 the latest move with Jordan's g1f3 before it: Jordan may ask.
            assertIs<CommandResult.Applied>(fixture.commands.requestUndo(fixture.jordan, fixture.gameId, fixture.version))
            assertEquals(path, fixture.game().game.redoPath, "asking changes no move, so the path stays")
            assertIs<CommandResult.Applied>(fixture.commands.declineUndo(fixture.alex, fixture.gameId, fixture.version))
            assertEquals(path, fixture.game().game.redoPath, "nor does declining")

            fixture.commands.requestUndo(fixture.jordan, fixture.gameId, fixture.version)
            assertIs<CommandResult.Applied>(fixture.redo(fixture.jordan))

            assertNull(fixture.game().undo.pendingFrom, "Redo changes the board, so it clears the request (D091)")
            assertEquals(
                5,
                fixture
                    .game()
                    .game.moves.size,
            )
        }
    }

    @Test
    fun redoClearsTheLastAnswerNote() {
        withGame { fixture ->
            fixture.play("g1f3", "b8c6")
            fixture.commands.requestUndo(fixture.jordan, fixture.gameId, 2)
            fixture.commands.acceptUndo(fixture.alex, fixture.gameId, 3)
            assertNotNull(fixture.view(fixture.jordan).undoNote)

            fixture.redo(fixture.jordan)

            assertNull(fixture.view(fixture.jordan).undoNote)
        }
    }

    // --- matching play advances the continuation (`D102`, `M22.2`) ---------------------------

    @Test
    fun theNextMovePlayedByHandAdvancesThePathAndKeepsTheRest() {
        withGame { fixture ->
            fixture.play("e2e4", "e7e5")
            val full = fixture.game().game
            fixture.undo(fixture.alex)
            fixture.undo(fixture.jordan)
            val before = fixture.version

            fixture.play("e2e4")

            assertEquals(before + 1, fixture.version, "one accepted change")
            assertEquals(
                listOf(uci("e7e5")),
                fixture
                    .game()
                    .game.redoPath
                    .single()
                    .moves,
                "Alex's e7e5 is still ahead",
            )
            assertTrue(fixture.view(fixture.alex).canRedo)
            assertFalse(fixture.view(fixture.jordan).canRedo, "the consumed move is not redoable")
            assertEquals(GameCommandService.MOVE_MADE, fixture.games.auditTrail(fixture.gameId).last(), "a move, not a Redo")

            assertIs<CommandResult.Applied>(fixture.redo(fixture.alex))
            assertEquals(full, fixture.game().game)
        }
    }

    @Test
    fun matchingTheFirstMoveOfATwoPlyOperationNeverPlaysTheOpponentsReply() {
        withGame { fixture ->
            fixture.play("g1f3", "b8c6")
            val answered = fixture.game().game
            fixture.commands.requestUndo(fixture.jordan, fixture.gameId, 2)
            fixture.commands.acceptUndo(fixture.alex, fixture.gameId, 3)

            fixture.play("g1f3")

            val partly = fixture.game().game
            assertEquals(listOf(uci("g1f3")), partly.moves, "Alex's reply is not played for them")
            assertEquals(listOf(uci("b8c6")), partly.redoPath.single().moves)
            assertTrue(fixture.view(fixture.alex).canRedo, "the reply stays Alex's to redo")
            assertFalse(fixture.view(fixture.jordan).canRedo)

            // Alex repeats it by hand, which advances it too.
            fixture.play("b8c6")
            assertEquals(answered, fixture.game().game)
            assertEquals(
                listOf(GameCommandService.MOVE_MADE, GameCommandService.MOVE_MADE),
                fixture.games.auditTrail(fixture.gameId).takeLast(2),
            )
        }
    }

    @Test
    fun theOpponentMayInsteadBranchFromTheRetainedReply() {
        withGame { fixture ->
            fixture.play("g1f3", "b8c6")
            fixture.commands.requestUndo(fixture.jordan, fixture.gameId, 2)
            fixture.commands.acceptUndo(fixture.alex, fixture.gameId, 3)
            fixture.play("g1f3")

            fixture.play("g8f6")

            assertTrue(
                fixture
                    .game()
                    .game.redoPath
                    .isEmpty(),
            )
            assertIs<CommandResult.NothingToRedo>(fixture.redo(fixture.jordan))
        }
    }

    @Test
    fun aMatchingMoveClearsAPendingUndoRequest() {
        withGame { fixture ->
            fixture.play("e2e4", "e7e5", "g1f3", "b8c6")
            fixture.undo(fixture.alex)
            // Alex to move, with Jordan's g1f3 answering their e7e5: Alex may ask to undo both.
            assertIs<CommandResult.Applied>(fixture.commands.requestUndo(fixture.alex, fixture.gameId, fixture.version))
            assertEquals(fixture.alex, fixture.game().undo.pendingFrom)

            fixture.play("b8c6")

            assertNull(fixture.game().undo.pendingFrom, "a matching move changes the board, so the request goes (D091)")
            assertTrue(
                fixture
                    .game()
                    .game.redoPath
                    .isEmpty(),
                "and it consumed the last retained move",
            )
        }
    }

    @Test
    fun aPartlyConsumedPathSurvivesANewRepositoryAndService() {
        withGame { fixture ->
            fixture.play("g1f3", "b8c6")
            fixture.commands.requestUndo(fixture.jordan, fixture.gameId, 2)
            fixture.commands.acceptUndo(fixture.alex, fixture.gameId, 3)
            fixture.play("g1f3")
            val stored = fixture.game().game

            val freshGames = GameRepository(fixture.database)
            val freshCommands = GameCommandService(fixture.database, freshGames, seriesService(fixture.database))
            assertEquals(stored, assertNotNull(freshGames.load(fixture.gameId)).game)

            assertIs<CommandResult.Applied>(freshCommands.redo(fixture.alex, fixture.gameId, fixture.version))
            assertEquals(listOf(uci("g1f3"), uci("b8c6")), fixture.game().game.moves)
        }
    }

    @Test
    fun aStaleMatchingMoveIsRefusedAndConcurrentOnesApplyOnce() {
        withGame { fixture ->
            fixture.play("e2e4", "e7e5")
            fixture.undo(fixture.alex)
            fixture.undo(fixture.jordan)
            val at = fixture.version

            assertIs<CommandResult.StaleVersion>(fixture.commands.makeMove(fixture.jordan, fixture.gameId, at - 1, uci("e2e4")))

            val start = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(2)
            val attempts =
                (1..2).map {
                    pool.submit<CommandResult> {
                        start.await()
                        fixture.commands.makeMove(fixture.jordan, fixture.gameId, at, uci("e2e4"))
                    }
                }
            start.countDown()
            val results = attempts.map { it.get(30, TimeUnit.SECONDS) }
            pool.shutdown()

            assertEquals(1, results.count { it is CommandResult.Applied }, "$results")
            assertEquals(1, results.count { it is CommandResult.StaleVersion }, "$results")
            assertEquals(listOf(uci("e2e4")), fixture.game().game.moves)
            assertEquals(
                listOf(uci("e7e5")),
                fixture
                    .game()
                    .game.redoPath
                    .single()
                    .moves,
                "consumed once",
            )
            assertEquals(at + 1, fixture.version)
        }
    }

    // --- persistence, concurrency, audit ---------------------------------------------------

    @Test
    fun thePathSurvivesANewRepositoryAndService() {
        withGame { fixture ->
            fixture.play("e2e4", "e7e5")
            fixture.undo(fixture.alex)
            fixture.undo(fixture.jordan)
            val stored = fixture.game().game

            val freshGames = GameRepository(fixture.database)
            val freshCommands = GameCommandService(fixture.database, freshGames, seriesService(fixture.database))
            assertEquals(stored, assertNotNull(freshGames.load(fixture.gameId)).game)

            assertIs<CommandResult.Applied>(freshCommands.redo(fixture.jordan, fixture.gameId, fixture.version))
            assertIs<CommandResult.Applied>(freshCommands.redo(fixture.alex, fixture.gameId, fixture.version))
            assertEquals(listOf(uci("e2e4"), uci("e7e5")), fixture.game().game.moves)
        }
    }

    @Test
    fun aStaleRedoIsRefused() {
        withGame { fixture ->
            fixture.play("e2e4")
            fixture.undo(fixture.jordan)

            assertIs<CommandResult.StaleVersion>(fixture.commands.redo(fixture.jordan, fixture.gameId, 1))
            assertTrue(
                fixture
                    .game()
                    .game.moves
                    .isEmpty(),
            )
        }
    }

    @Test
    fun concurrentRedosApplyAtMostOnce() {
        withGame { fixture ->
            fixture.play("e2e4", "e7e5")
            fixture.undo(fixture.alex)
            fixture.undo(fixture.jordan)
            val at = fixture.version

            val start = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(2)
            val attempts =
                (1..2).map {
                    pool.submit<CommandResult> {
                        start.await()
                        fixture.commands.redo(fixture.jordan, fixture.gameId, at)
                    }
                }
            start.countDown()
            val results = attempts.map { it.get(30, TimeUnit.SECONDS) }
            pool.shutdown()

            assertEquals(1, results.count { it is CommandResult.Applied }, "$results")
            assertEquals(1, results.count { it is CommandResult.StaleVersion }, "$results")
            assertEquals(listOf(uci("e2e4")), fixture.game().game.moves, "one operation restored, not two")
            assertEquals(at + 1, fixture.version)
        }
    }

    @Test
    fun aRedoIsAuditedWithItsActorSeriesMovesAndSize() {
        withGame { fixture ->
            fixture.play("g1f3", "b8c6", "e2e4")
            fixture.undo(fixture.jordan)
            fixture.redo(fixture.jordan)
            fixture.undo(fixture.jordan)
            fixture.commands.requestUndo(fixture.jordan, fixture.gameId, fixture.version)
            fixture.commands.acceptUndo(fixture.alex, fixture.gameId, fixture.version)
            fixture.redo(fixture.jordan)

            val redone = fixture.games.auditEvents(fixture.gameId).filter { it.type == GameCommandService.MOVE_REDONE }

            assertEquals(2, redone.size)
            assertTrue(redone.all { it.actorId == fixture.jordan && it.seriesId == fixture.seriesId })
            assertEquals(listOf("e2e4"), redone[0].payload["moves"]!!.jsonArray.map { it.jsonPrimitive.content })
            assertEquals(1, redone[0].payload["plies"]!!.jsonPrimitive.int)
            assertEquals(listOf("g1f3", "b8c6"), redone[1].payload["moves"]!!.jsonArray.map { it.jsonPrimitive.content })
            assertEquals(2, redone[1].payload["plies"]!!.jsonPrimitive.int)
        }
    }

    // --- the endpoint and the view -----------------------------------------------------------

    private suspend fun ApplicationTestBuilder.redo(
        subject: String,
        gameId: Uuid,
        expectedVersion: Long,
    ) = client.post("/games/$gameId/redo") {
        header("Authorization", "Bearer ${tokens.tokenFor(subject)}")
        contentType(ContentType.Application.Json)
        setBody("""{"expectedVersion":$expectedVersion}""")
    }

    private suspend fun ApplicationTestBuilder.read(
        subject: String,
        gameId: Uuid,
    ): GameView =
        json.decodeFromString(
            client
                .get("/games/$gameId") { header("Authorization", "Bearer ${tokens.tokenFor(subject)}") }
                .bodyAsText(),
        )

    @Test
    fun anotherInstallationOfTheSamePlayerSeesAndUsesTheSamePath() {
        withServer { fixture ->
            fixture.play("e2e4")
            fixture.undo(fixture.jordan)
            // A second installation claims Jordan's name and so becomes Jordan (D082).
            fixture.named("auth-1-tablet", "Jordan")
            assertEquals(fixture.jordan, fixture.users.resolveBySubject("auth-1-tablet").id)

            assertTrue(read("auth-1-tablet", fixture.gameId).canRedo)
            assertFalse(read("auth-2", fixture.gameId).canRedo)

            val response = redo("auth-1-tablet", fixture.gameId, fixture.version)
            assertEquals(HttpStatusCode.OK, response.status)
            val view = json.decodeFromString<GameView>(response.bodyAsText())
            assertEquals(listOf("e2e4"), view.moves)
            assertEquals("e2", view.lastMove?.from)
            assertFalse(view.canRedo)
        }
    }

    @Test
    fun theEndpointRefusesWithATypedReasonAndTheViewCarriesNoPath() {
        withServer { fixture ->
            fixture.play("e2e4")

            val refused = redo("auth-2", fixture.gameId, fixture.version)
            assertEquals(HttpStatusCode.Conflict, refused.status)
            assertEquals(RejectionReason.NOTHING_TO_REDO, json.decodeFromString<CommandRejection>(refused.bodyAsText()).reason)

            fixture.undo(fixture.jordan)
            val raw =
                client
                    .get("/games/${fixture.gameId}") { header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}") }
                    .bodyAsText()
            assertTrue("\"canRedo\":true" in raw)
            listOf("redoPath", "stateAfter", "positionBefore", "records").forEach { field ->
                assertFalse(field in raw, "$field never leaves the server (D069)")
            }
        }
    }

    private fun assertSame(
        expected: ChessGame,
        actual: ChessGame,
    ) {
        assertEquals(expected.state, actual.state)
        assertEquals(expected.history, actual.history)
    }

    private companion object {
        fun uci(text: String): Move = Move.of(text.substring(0, 2), text.substring(2, 4))
    }
}
