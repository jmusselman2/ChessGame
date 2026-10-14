@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.game

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.DrawClaim
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.server.api.CommandRejection
import com.jmussel.chessgame.server.api.DashboardEntry
import com.jmussel.chessgame.server.api.GameView
import com.jmussel.chessgame.server.api.PendingRequest
import com.jmussel.chessgame.server.api.RejectionReason
import com.jmussel.chessgame.server.api.UndoNoteKind
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DashboardQueries
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.StoredGame
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.db.UsersTable
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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
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
 * Undo requests (`D091`, `M21.19`): once Alex has replied, Jordan may ask Alex to undo
 * Jordan's move and the reply. Alex accepts or declines, and Alex's own Undo accepts.
 *
 * Jordan plays White throughout, so "Jordan plays g1f3, Alex replies b8c6" is the decision's
 * own example.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class UndoRequestTest {
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

        fun play(
            player: Uuid,
            move: String,
        ): StoredGame {
            val result = commands.makeMove(player, gameId, version, uci(move))
            assertIs<CommandResult.Applied>(result, "setup move $move failed: $result")
            return result.game
        }

        /** Jordan's g1f3 answered by Alex's b8c6, Jordan to move at version 2. */
        fun answered() {
            play(jordan, "g1f3")
            play(alex, "b8c6")
        }

        fun request(at: Long = version): CommandResult = commands.requestUndo(jordan, gameId, at)

        fun view(viewer: Uuid): GameView = GameView.of(game(), viewer, assertNotNull(users.find(game().opponentOf(viewer))))

        fun lastAction(user: Uuid) =
            transaction(database) {
                UsersTable
                    .selectAll()
                    .where { UsersTable.id eq user }
                    .single()[UsersTable.lastActionAt]
            }

        fun dashboardFor(user: Uuid): DashboardEntry =
            DashboardQueries(database)
                .activeSeriesFor(user)
                .mapNotNull(DashboardEntry::of)
                .single { it.gameId == gameId.toString() }
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

    // --- asking --------------------------------------------------------------------------

    @Test
    fun aPlayerMayAskOnceTheirMoveHasBeenAnswered() {
        withGame { fixture ->
            fixture.answered()

            val result = fixture.request()

            assertIs<CommandResult.Applied>(result)
            assertEquals(3, fixture.version, "asking is an accepted change (D021)")
            assertEquals(fixture.jordan, fixture.game().undo.pendingFrom)
            assertEquals(listOf(uci("g1f3"), uci("b8c6")), fixture.game().game.moves, "asking changes no move")
        }
    }

    @Test
    fun askingIsRefusedBeforeAnyMoveOfTheirsIsAnswered() {
        withGame { fixture ->
            assertIs<CommandResult.CannotRequestUndo>(fixture.request(), "no move at all")

            fixture.play(fixture.jordan, "g1f3")
            fixture.play(fixture.alex, "b8c6")
            fixture.play(fixture.jordan, "e2e4")

            // Alex to move, with Jordan's e2e4 latest: Alex's b8c6 is answered, so Alex may ask.
            assertIs<CommandResult.Applied>(fixture.commands.requestUndo(fixture.alex, fixture.gameId, 3))
        }
    }

    @Test
    fun blackCannotAskWhenWhiteHasMadeTheOnlyMove() {
        withGame { fixture ->
            fixture.play(fixture.jordan, "g1f3")

            val result = fixture.commands.requestUndo(fixture.alex, fixture.gameId, 1)

            assertIs<CommandResult.CannotRequestUndo>(result, "Alex has no move before Jordan's")
            assertEquals(1, fixture.version)
        }
    }

    @Test
    fun askingIsRefusedWhenItIsNotTheirTurn() {
        withGame { fixture ->
            fixture.play(fixture.jordan, "g1f3")

            assertIs<CommandResult.NotYourTurn>(fixture.request())
            assertNull(fixture.game().undo.pendingFrom)
        }
    }

    @Test
    fun askingTwiceIsRefused() {
        withGame { fixture ->
            fixture.answered()
            fixture.request()

            assertIs<CommandResult.CannotRequestUndo>(fixture.request())
            assertEquals(3, fixture.version)
        }
    }

    @Test
    fun askingIsRefusedOnAFinishedGame() {
        withGame { fixture ->
            fixture.answered()
            fixture.commands.resign(fixture.alex, fixture.gameId, 2)

            assertIs<CommandResult.GameOver>(fixture.request())
        }
    }

    @Test
    fun aStaleRequestIsRefused() {
        withGame { fixture ->
            fixture.answered()

            assertIs<CommandResult.StaleVersion>(fixture.request(at = 1))
            assertNull(fixture.game().undo.pendingFrom)
        }
    }

    @Test
    fun aStrangerCannotAsk() {
        withGame { fixture ->
            fixture.answered()
            val stranger = fixture.named("auth-3", "Sam")

            assertEquals(CommandResult.NotAParticipant, fixture.commands.requestUndo(stranger, fixture.gameId, 2))
        }
    }

    // --- accepting -----------------------------------------------------------------------

    @Test
    fun acceptingRestoresThePositionBeforeTheRequestersMove() {
        withGame { fixture ->
            fixture.play(fixture.jordan, "e2e4")
            fixture.play(fixture.alex, "e7e5")
            val before = fixture.game().game
            fixture.answered()
            fixture.request()

            val result = fixture.commands.acceptUndo(fixture.alex, fixture.gameId, 5)

            assertIs<CommandResult.Applied>(result)
            val after = fixture.game()
            assertEquals(before, after.game, "board, side to move, castling and draw-rule state all restored")
            assertEquals(Side.WHITE, after.game.sideToMove, "Jordan to move")
            assertEquals(6, after.version, "one increment")
            assertNull(after.undo.pendingFrom, "the request is cleared")
        }
    }

    @Test
    fun aStaleAcceptIsRefused() {
        withGame { fixture ->
            fixture.answered()
            fixture.request()

            assertIs<CommandResult.StaleVersion>(fixture.commands.acceptUndo(fixture.alex, fixture.gameId, 2))
            assertEquals(
                2,
                fixture
                    .game()
                    .game.moves.size,
            )
            assertEquals(fixture.jordan, fixture.game().undo.pendingFrom)
        }
    }

    @Test
    fun theRequesterCannotAnswerTheirOwnRequest() {
        withGame { fixture ->
            fixture.answered()
            fixture.request()

            assertIs<CommandResult.NoUndoRequest>(fixture.commands.acceptUndo(fixture.jordan, fixture.gameId, 3))
            assertIs<CommandResult.NoUndoRequest>(fixture.commands.declineUndo(fixture.jordan, fixture.gameId, 3))
            assertEquals(3, fixture.version)
        }
    }

    @Test
    fun nothingCanBeAnsweredWithNoRequestPending() {
        withGame { fixture ->
            fixture.answered()

            assertIs<CommandResult.NoUndoRequest>(fixture.commands.acceptUndo(fixture.alex, fixture.gameId, 2))
            assertIs<CommandResult.NoUndoRequest>(fixture.commands.declineUndo(fixture.alex, fixture.gameId, 2))
            assertEquals(2, fixture.version)
        }
    }

    @Test
    fun theOpponentsUndoWhileARequestWaitsAccepts() {
        withGame { fixture ->
            fixture.answered()
            fixture.request()

            val result = fixture.commands.undoMove(fixture.alex, fixture.gameId, 3)

            assertIs<CommandResult.Applied>(result)
            assertEquals(ChessGame.newGame(), fixture.game().game, "both moves are undone")
            assertEquals(4, fixture.version)
            assertEquals(
                listOf("MoveMade", "MoveMade", "UndoRequested", "UndoAccepted"),
                fixture.games.auditTrail(fixture.gameId),
            )
            assertEquals(UndoNoteKind.ACCEPTED, fixture.view(fixture.jordan).undoNote?.kind, "Jordan is told Alex accepted")
            assertNull(fixture.view(fixture.alex).undoNote, "Alex accepted, and needs no note")
        }
    }

    @Test
    fun anAcceptAndTheRequestersMoveRaceAndExactlyOneWins() {
        withGame { fixture ->
            fixture.answered()
            fixture.request()

            val start = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(2)
            val accept =
                pool.submit<CommandResult> {
                    start.await()
                    fixture.commands.acceptUndo(fixture.alex, fixture.gameId, 3)
                }
            val move =
                pool.submit<CommandResult> {
                    start.await()
                    fixture.commands.makeMove(fixture.jordan, fixture.gameId, 3, uci("e2e4"))
                }
            start.countDown()
            val results = listOf(accept.get(30, TimeUnit.SECONDS), move.get(30, TimeUnit.SECONDS))
            pool.shutdown()

            assertEquals(1, results.count { it is CommandResult.Applied }, "exactly one wins: $results")
            assertEquals(1, results.count { it is CommandResult.StaleVersion }, "the other is stale: $results")
            assertEquals(4, fixture.version)
            assertNull(fixture.game().undo.pendingFrom)
        }
    }

    // --- declining -----------------------------------------------------------------------

    @Test
    fun aDeclineLetsTheRequesterAskAgainAndTheOpponentUndoTheirReplyAlone() {
        withGame { fixture ->
            fixture.answered()
            fixture.request()

            assertIs<CommandResult.Applied>(fixture.commands.declineUndo(fixture.alex, fixture.gameId, 3))
            assertEquals(4, fixture.version)
            assertNull(fixture.game().undo.pendingFrom)
            assertEquals(
                2,
                fixture
                    .game()
                    .game.moves.size,
                "both moves stand",
            )
            assertEquals(UndoNoteKind.DECLINED, fixture.view(fixture.jordan).undoNote?.kind)
            assertTrue(fixture.view(fixture.jordan).canRequestUndo, "Jordan may ask again at once")

            val undone = fixture.commands.undoMove(fixture.alex, fixture.gameId, 4)

            assertIs<CommandResult.Applied>(undone)
            assertEquals(listOf(uci("g1f3")), fixture.game().game.moves, "Alex's undo undoes b8c6 alone again (D016)")
        }
    }

    @Test
    fun theRequesterMayAskAgainAfterADecline() {
        withGame { fixture ->
            fixture.answered()
            fixture.request()
            fixture.commands.declineUndo(fixture.alex, fixture.gameId, 3)

            assertIs<CommandResult.Applied>(fixture.request())
            assertEquals(fixture.jordan, fixture.game().undo.pendingFrom)
            assertNull(fixture.view(fixture.jordan).undoNote, "asking again clears the old answer")
        }
    }

    // --- crossing ------------------------------------------------------------------------

    @Test
    fun requestFirstTheOpponentsUndoFromThePreRequestVersionAccepts() {
        withGame { fixture ->
            fixture.answered()
            fixture.request(at = 2)

            val result = fixture.commands.undoMove(fixture.alex, fixture.gameId, 2)

            assertIs<CommandResult.Applied>(result, "stale by exactly the request, so it accepts it")
            assertEquals(ChessGame.newGame(), fixture.game().game)
            assertEquals(Side.WHITE, fixture.game().game.sideToMove)
            assertEquals(4, fixture.version, "each command increments the version once")

            val jordanNote = assertNotNull(fixture.view(fixture.jordan).undoNote)
            assertEquals(UndoNoteKind.ACCEPTED, jordanNote.kind)
            val alexNote = assertNotNull(fixture.view(fixture.alex).undoNote)
            assertEquals(UndoNoteKind.BOTH_UNDONE, alexNote.kind)
            assertEquals("g1f3", alexNote.requesterMove)
            assertEquals("b8c6", alexNote.replyMove)

            val events = fixture.games.auditEvents(fixture.gameId).takeLast(2)
            assertEquals(listOf("UndoRequested", "UndoAccepted"), events.map { it.type })
            assertEquals(listOf(fixture.jordan, fixture.alex), events.map { it.actorId })
            assertTrue(events[1].payload["crossing"]!!.jsonPrimitive.boolean, "the accept is marked as a crossing")
            assertNull(events[0].payload["crossing"])
        }
    }

    @Test
    fun undoFirstTheRequestFromThePreUndoVersionUndoesTheRequestersMove() {
        withGame { fixture ->
            fixture.answered()
            assertIs<CommandResult.Applied>(fixture.commands.undoMove(fixture.alex, fixture.gameId, 2))

            val result = fixture.request(at = 2)

            assertIs<CommandResult.Applied>(result, "stale by exactly the undo, so it completes it")
            assertEquals(ChessGame.newGame(), fixture.game().game)
            assertEquals(4, fixture.version)
            assertNull(fixture.game().undo.pendingFrom, "completed at once, so nothing is pending")

            val jordanNote = assertNotNull(fixture.view(fixture.jordan).undoNote)
            assertEquals(UndoNoteKind.UNDONE_TOO, jordanNote.kind)
            assertEquals("g1f3", jordanNote.requesterMove)
            assertEquals("b8c6", jordanNote.replyMove)
            assertEquals(UndoNoteKind.BOTH_UNDONE, fixture.view(fixture.alex).undoNote?.kind)

            val events = fixture.games.auditEvents(fixture.gameId).takeLast(2)
            assertEquals(listOf("MoveUndone", "UndoRequested"), events.map { it.type })
            assertEquals(listOf(fixture.alex, fixture.jordan), events.map { it.actorId })
            assertTrue(events[1].payload["crossing"]!!.jsonPrimitive.boolean)
            assertFalse("UndoAccepted" in fixture.games.auditTrail(fixture.gameId), "Alex accepted nothing")
        }
    }

    @Test
    fun requestFirstWithADeclineInBetweenTheUndoIsStale() {
        withGame { fixture ->
            fixture.answered()
            fixture.request(at = 2)
            fixture.commands.declineUndo(fixture.alex, fixture.gameId, 3)

            assertIs<CommandResult.StaleVersion>(fixture.commands.undoMove(fixture.alex, fixture.gameId, 2))
            assertEquals(
                2,
                fixture
                    .game()
                    .game.moves.size,
            )
        }
    }

    @Test
    fun requestFirstWithAMoveInBetweenTheUndoIsStale() {
        withGame { fixture ->
            fixture.answered()
            fixture.request(at = 2)
            fixture.play(fixture.jordan, "e2e4")

            assertIs<CommandResult.StaleVersion>(fixture.commands.undoMove(fixture.alex, fixture.gameId, 2))
            assertEquals(
                3,
                fixture
                    .game()
                    .game.moves.size,
            )
        }
    }

    @Test
    fun undoFirstWithAMoveInBetweenTheRequestIsStale() {
        withGame { fixture ->
            fixture.answered()
            fixture.commands.undoMove(fixture.alex, fixture.gameId, 2)
            fixture.play(fixture.alex, "g8f6")

            assertIs<CommandResult.StaleVersion>(fixture.request(at = 2))
            assertEquals(
                2,
                fixture
                    .game()
                    .game.moves.size,
            )
        }
    }

    @Test
    fun aRequestTwoVersionsBehindAnUndoIsStale() {
        withGame { fixture ->
            fixture.answered()
            fixture.commands.undoMove(fixture.alex, fixture.gameId, 2)

            assertIs<CommandResult.StaleVersion>(fixture.request(at = 1))
            assertEquals(listOf(uci("g1f3")), fixture.game().game.moves)
        }
    }

    // --- the answer, and clearing ---------------------------------------------------------

    @Test
    fun theLastAnswerIsSeenByTheRequesterOnlyAndClearedByTheNextChange() {
        withGame { fixture ->
            fixture.answered()
            fixture.request()
            fixture.commands.acceptUndo(fixture.alex, fixture.gameId, 3)

            val note = assertNotNull(fixture.view(fixture.jordan).undoNote)
            assertEquals(UndoNoteKind.ACCEPTED, note.kind)
            assertEquals("g1f3", note.requesterMove)
            assertEquals("b8c6", note.replyMove)
            assertNull(fixture.view(fixture.alex).undoNote)
            assertNotNull(fixture.view(fixture.jordan).undoNote, "it shows on every read, not once")

            fixture.play(fixture.jordan, "e2e4")

            assertNull(fixture.view(fixture.jordan).undoNote, "the next accepted change clears it")
        }
    }

    @Test
    fun everyOtherCommandClearsAPendingRequest() {
        val clearers: List<Pair<String, (Fixture) -> CommandResult>> =
            listOf(
                "the requester's move" to { f -> f.commands.makeMove(f.jordan, f.gameId, 3, uci("e2e4")) },
                "the requester's resignation" to { f -> f.commands.resign(f.jordan, f.gameId, 3) },
                "the opponent's resignation" to { f -> f.commands.resign(f.alex, f.gameId, 3) },
            )

        clearers.forEach { (name, clear) ->
            withGame { fixture ->
                fixture.answered()
                fixture.request()

                assertIs<CommandResult.Applied>(clear(fixture), name)
                assertNull(fixture.game().undo.pendingFrom, "$name clears the request")
            }
        }
    }

    @Test
    fun aDrawClaimClearsAPendingRequest() {
        withGame { fixture ->
            // Knights out and back twice: the start position for the third time, White to move.
            listOf("g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8").forEachIndexed { index, move ->
                fixture.play(if (index % 2 == 0) fixture.jordan else fixture.alex, move)
            }
            assertTrue(DrawClaim.THREEFOLD_REPETITION in ChessRules.availableDrawClaims(fixture.game().game.state))
            fixture.request()

            val claimed = fixture.commands.claimDraw(fixture.jordan, fixture.gameId, 9, DrawClaim.THREEFOLD_REPETITION)

            assertIs<CommandResult.Applied>(claimed)
            assertNull(fixture.game().undo.pendingFrom)
        }
    }

    @Test
    fun leavingTheSeriesKeepsThePendingRequest() {
        withGame { fixture ->
            fixture.answered()
            fixture.request()

            seriesService(fixture.database).leave(fixture.alex, fixture.seriesId)

            assertEquals(fixture.jordan, fixture.game().undo.pendingFrom, "leaving is not a game command (D068)")
            assertEquals(3, fixture.version)
        }
    }

    @Test
    fun aFinishedGameNeverHasARequestPending() {
        withGame { fixture ->
            fixture.answered()
            fixture.request()
            fixture.commands.resign(fixture.alex, fixture.gameId, 3)

            val finished = fixture.game()
            assertTrue(finished.isComplete)
            assertNull(finished.undo.pendingFrom)
            assertFalse(fixture.view(fixture.jordan).canRequestUndo)
            assertNull(fixture.view(fixture.alex).undoRequest)
        }
    }

    // --- audit and activity ---------------------------------------------------------------

    @Test
    fun requestAcceptAndDeclineAreAuditedWithTheirActorAndSeries() {
        withGame { fixture ->
            fixture.answered()
            fixture.request()
            fixture.commands.declineUndo(fixture.alex, fixture.gameId, 3)
            fixture.request()
            fixture.commands.acceptUndo(fixture.alex, fixture.gameId, 5)

            val events = fixture.games.auditEvents(fixture.gameId).drop(2)

            assertEquals(listOf("UndoRequested", "UndoDeclined", "UndoRequested", "UndoAccepted"), events.map { it.type })
            assertEquals(listOf(fixture.jordan, fixture.alex, fixture.jordan, fixture.alex), events.map { it.actorId })
            assertTrue(events.all { it.seriesId == fixture.seriesId })
            assertEquals(
                listOf(3L, 4L, 5L, 6L),
                events.map {
                    it.payload["version"]!!
                        .jsonPrimitive.content
                        .toLong()
                },
            )
        }
    }

    @Test
    fun requestAcceptAndDeclineEachRecordTheActorsAction() {
        withGame { fixture ->
            fixture.answered()
            val afterMoves = listOf(fixture.lastAction(fixture.jordan), fixture.lastAction(fixture.alex))

            fixture.request()
            val afterRequest = fixture.lastAction(fixture.jordan)
            fixture.commands.declineUndo(fixture.alex, fixture.gameId, 3)
            val afterDecline = fixture.lastAction(fixture.alex)
            fixture.request()
            fixture.commands.acceptUndo(fixture.alex, fixture.gameId, 5)
            val afterAccept = fixture.lastAction(fixture.alex)

            assertTrue(afterRequest!! >= afterMoves[0]!!)
            assertTrue(afterDecline!! >= afterMoves[1]!!)
            assertTrue(afterAccept!! >= afterDecline)
        }
    }

    // --- what each player sees ------------------------------------------------------------

    @Test
    fun eachPlayersViewSaysWhetherTheyMayAskAndWhatIsPending() {
        withGame { fixture ->
            fixture.answered()

            assertTrue(fixture.view(fixture.jordan).canRequestUndo)
            assertFalse(fixture.view(fixture.jordan).canUndo, "Ask to undo and Undo your move are never offered together")
            assertFalse(fixture.view(fixture.alex).canRequestUndo)
            assertTrue(fixture.view(fixture.alex).canUndo)

            fixture.request()

            val forJordan = fixture.view(fixture.jordan)
            val forAlex = fixture.view(fixture.alex)
            assertFalse(forJordan.canRequestUndo)
            assertEquals(true, forJordan.undoRequest?.fromYou)
            assertEquals(false, forAlex.undoRequest?.fromYou)
            assertEquals("g1f3", forAlex.undoRequest?.requesterMove)
            assertEquals("b8c6", forAlex.undoRequest?.replyMove)
            assertTrue(forAlex.canUndo, "Alex's Undo stays, and accepts")
            assertTrue(forJordan.yourTurn, "Jordan can still play")
        }
    }

    @Test
    fun theDashboardNamesTheRequestForBothPlayersWithoutTouchingYourTurn() {
        withGame { fixture ->
            fixture.answered()
            assertNull(fixture.dashboardFor(fixture.alex).pendingRequest)

            fixture.request()

            val alexRow = fixture.dashboardFor(fixture.alex)
            val jordanRow = fixture.dashboardFor(fixture.jordan)
            assertEquals(PendingRequest(kind = PendingRequest.UNDO, waitingOnYou = true), alexRow.pendingRequest)
            assertFalse(alexRow.yourTurn, "yourTurn still means it is your move")
            assertEquals(PendingRequest(kind = PendingRequest.UNDO, waitingOnYou = false), jordanRow.pendingRequest)
            assertTrue(jordanRow.yourTurn)

            fixture.commands.declineUndo(fixture.alex, fixture.gameId, 3)

            assertNull(fixture.dashboardFor(fixture.alex).pendingRequest)
            assertNull(fixture.dashboardFor(fixture.jordan).pendingRequest)
        }
    }

    // --- the endpoints --------------------------------------------------------------------

    private suspend fun ApplicationTestBuilder.command(
        subject: String,
        path: String,
        expectedVersion: Long,
    ) = client.post(path) {
        header("Authorization", "Bearer ${tokens.tokenFor(subject)}")
        contentType(ContentType.Application.Json)
        setBody("""{"expectedVersion":$expectedVersion}""")
    }

    @Test
    fun theEndpointsAskAndAccept() {
        withServer { fixture ->
            fixture.answered()

            val asked = command("auth-1", "/games/${fixture.gameId}/undo-request", 2)
            assertEquals(HttpStatusCode.OK, asked.status)
            assertEquals(true, json.decodeFromString<GameView>(asked.bodyAsText()).undoRequest?.fromYou)

            val forAlex =
                json.decodeFromString<GameView>(
                    client
                        .get("/games/${fixture.gameId}") {
                            header("Authorization", "Bearer ${tokens.tokenFor("auth-2")}")
                        }.bodyAsText(),
                )
            assertEquals(false, forAlex.undoRequest?.fromYou)

            val accepted = command("auth-2", "/games/${fixture.gameId}/undo-request/accept", 3)
            assertEquals(HttpStatusCode.OK, accepted.status)
            assertTrue(json.decodeFromString<GameView>(accepted.bodyAsText()).moves.isEmpty())
        }
    }

    @Test
    fun theEndpointsDeclineAndRefuseWithTypedReasons() {
        withServer { fixture ->
            fixture.answered()

            val early = command("auth-2", "/games/${fixture.gameId}/undo-request/decline", 2)
            assertEquals(HttpStatusCode.Conflict, early.status)
            assertEquals(RejectionReason.NO_UNDO_REQUEST, json.decodeFromString<CommandRejection>(early.bodyAsText()).reason)

            command("auth-1", "/games/${fixture.gameId}/undo-request", 2)
            val twice = command("auth-1", "/games/${fixture.gameId}/undo-request", 3)
            assertEquals(HttpStatusCode.Conflict, twice.status)
            assertEquals(RejectionReason.CANNOT_REQUEST_UNDO, json.decodeFromString<CommandRejection>(twice.bodyAsText()).reason)

            val declined = command("auth-2", "/games/${fixture.gameId}/undo-request/decline", 3)
            assertEquals(HttpStatusCode.OK, declined.status)
            assertNull(json.decodeFromString<GameView>(declined.bodyAsText()).undoRequest)
        }
    }

    private companion object {
        fun uci(text: String): Move = Move.of(text.substring(0, 2), text.substring(2, 4))
    }
}
