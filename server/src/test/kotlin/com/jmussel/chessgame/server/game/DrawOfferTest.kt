@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.game

import com.jmussel.chessgame.core.chess.DrawClaim
import com.jmussel.chessgame.core.chess.GameOutcome
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.TerminationReason
import com.jmussel.chessgame.server.api.CommandRejection
import com.jmussel.chessgame.server.api.DashboardEntry
import com.jmussel.chessgame.server.api.GameView
import com.jmussel.chessgame.server.api.PendingRequest
import com.jmussel.chessgame.server.api.RejectionReason
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DashboardQueries
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.GameSeriesRepository
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
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Draw offers by agreement (`D097`, `M22.3`).
 *
 * Either player may offer while the game runs, on either turn; the other accepts or declines.
 * An offer and an undo request may be pending together, and request-only changes leave each
 * other alone; any change to the board, active history or result clears both.
 *
 * Jordan plays White and Alex Black throughout.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class DrawOfferTest {
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

        fun offer(
            player: Uuid,
            at: Long = version,
        ): CommandResult = commands.offerDraw(player, gameId, at)

        fun accept(
            player: Uuid,
            at: Long = version,
        ): CommandResult = commands.acceptDraw(player, gameId, at)

        fun decline(
            player: Uuid,
            at: Long = version,
        ): CommandResult = commands.declineDraw(player, gameId, at)

        /** Jordan's g1f3 answered by Alex's b8c6, then Jordan asks to undo both. */
        fun undoRequested() {
            play("g1f3", "b8c6")
            assertIs<CommandResult.Applied>(commands.requestUndo(jordan, gameId, version))
        }

        fun view(viewer: Uuid): GameView = GameView.of(game(), viewer, assertNotNull(users.find(game().opponentOf(viewer))))

        fun dashboardFor(user: Uuid): DashboardEntry =
            DashboardQueries(database)
                .activeSeriesFor(user)
                .mapNotNull(DashboardEntry::of)
                .single { it.gameId == gameId.toString() }
    }

    private fun fixtureOver(database: Database): Fixture {
        val games = GameRepository(database)
        val users = UserRepository(database)
        val fixture = Fixture(database, users, games, GameCommandService(database, games, seriesService(database), users))
        fixture.startGame()
        return fixture
    }

    private fun withGame(block: (Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource -> block(fixtureOver(Databases.connect(dataSource))) }

    private fun withServer(block: suspend ApplicationTestBuilder.(Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val fixture = fixtureOver(database)
            testApplication {
                application { testModule(tokens.verifier(), database) }
                block(fixture)
            }
        }

    // --- offering ------------------------------------------------------------------------

    @Test
    fun eitherPlayerMayOfferOnEitherTurn() {
        withGame { fixture ->
            assertIs<CommandResult.Applied>(fixture.offer(fixture.alex), "Black offers on White's turn")
            assertEquals(fixture.alex, fixture.game().draw.offeredBy)
            assertEquals(1, fixture.version, "an offer is an accepted change (D021)")
            assertEquals(0, fixture.game().playVersion, "but not a change to the position")
            assertTrue(
                fixture
                    .game()
                    .game.moves
                    .isEmpty(),
            )

            assertIs<CommandResult.Applied>(fixture.decline(fixture.jordan))
            fixture.play("e2e4")
            assertIs<CommandResult.Applied>(fixture.offer(fixture.jordan), "White offers on Black's turn")
        }
    }

    @Test
    fun onlyOneOfferMayBePendingAndTheOffererCannotAnswerIt() {
        withGame { fixture ->
            fixture.offer(fixture.jordan)

            assertIs<CommandResult.CannotOfferDraw>(fixture.offer(fixture.jordan))
            assertIs<CommandResult.CannotOfferDraw>(fixture.offer(fixture.alex), "one pending per game, from either side")
            assertIs<CommandResult.NoDrawOffer>(fixture.accept(fixture.jordan))
            assertIs<CommandResult.NoDrawOffer>(fixture.decline(fixture.jordan))
            assertFalse(fixture.view(fixture.alex).canOfferDraw)
        }
    }

    @Test
    fun anOfferHasNoTimerAndNoCancel() {
        withGame { fixture ->
            fixture.offer(fixture.jordan)

            // Nothing but an answer or a change to the game ends it; there is no endpoint to
            // withdraw it, and a fresh repository still reads it.
            val fresh = GameRepository(fixture.database)
            assertEquals(fixture.jordan, assertNotNull(fresh.load(fixture.gameId)).draw.offeredBy)
        }
    }

    @Test
    fun aFinishedGameTakesNoOffer() {
        withGame { fixture ->
            fixture.commands.resign(fixture.jordan, fixture.gameId, 0)

            assertIs<CommandResult.GameOver>(fixture.offer(fixture.alex))
            assertFalse(fixture.view(fixture.alex).canOfferDraw)
        }
    }

    // --- answering -----------------------------------------------------------------------

    @Test
    fun acceptingEndsTheGameAsAnAgreedDrawAndStartsTheRematch() {
        withGame { fixture ->
            fixture.play("e2e4", "e7e5")
            fixture.offer(fixture.alex)

            assertIs<CommandResult.Applied>(fixture.accept(fixture.jordan))

            val drawn = fixture.game()
            assertEquals(GameOutcome.DRAW, drawn.game.result?.outcome)
            assertEquals(TerminationReason.AGREEMENT, drawn.game.result?.reason)
            assertNull(drawn.draw.offeredBy)
            assertEquals(2, drawn.game.moves.size, "no move is played")
            val series = assertNotNull(GameSeriesRepository(fixture.database).find(fixture.seriesId))
            assertTrue(series.currentGameId != fixture.gameId, "the series settled to its rematch (D015)")
            assertEquals(
                listOf(GameCommandService.DRAW_OFFERED, GameCommandService.DRAW_ACCEPTED, "GameEnded"),
                fixture.games.auditTrail(fixture.gameId).takeLast(3),
            )
            assertIs<CommandResult.GameOver>(fixture.commands.undoMove(fixture.alex, fixture.gameId, fixture.version), "final")
        }
    }

    @Test
    fun aDeclineBlocksNewOffersUntilAMoveIsPlayed() {
        withGame { fixture ->
            fixture.play("e2e4", "e7e5")
            fixture.offer(fixture.jordan)

            assertIs<CommandResult.Applied>(fixture.decline(fixture.alex))
            assertNull(fixture.game().draw.offeredBy)
            assertTrue(fixture.game().draw.blocked)
            assertIs<CommandResult.CannotOfferDraw>(fixture.offer(fixture.jordan))
            assertIs<CommandResult.CannotOfferDraw>(fixture.offer(fixture.alex), "the block is the game's, not one player's")

            // Undo and Redo do not lift it.
            assertIs<CommandResult.Applied>(fixture.commands.undoMove(fixture.alex, fixture.gameId, fixture.version))
            assertIs<CommandResult.CannotOfferDraw>(fixture.offer(fixture.jordan))
            assertIs<CommandResult.Applied>(fixture.commands.redo(fixture.alex, fixture.gameId, fixture.version))
            assertIs<CommandResult.CannotOfferDraw>(fixture.offer(fixture.jordan))

            fixture.play("g1f3")
            assertFalse(fixture.game().draw.blocked)
            assertIs<CommandResult.Applied>(fixture.offer(fixture.alex))
        }
    }

    @Test
    fun aMatchingContinuationMoveLiftsTheBlockLikeAnyMove() {
        withGame { fixture ->
            fixture.play("e2e4", "e7e5")
            fixture.commands.undoMove(fixture.alex, fixture.gameId, fixture.version)
            fixture.offer(fixture.jordan)
            fixture.decline(fixture.alex)

            fixture.play("e7e5")

            assertFalse(fixture.game().draw.blocked)
        }
    }

    // --- both requests ---------------------------------------------------------------------

    @Test
    fun anOfferAndAnUndoRequestCoexistInEitherDirection() {
        withGame { fixture ->
            fixture.undoRequested()

            assertIs<CommandResult.Applied>(fixture.offer(fixture.alex), "Alex offers while answering Jordan's undo request")
            assertEquals(fixture.jordan, fixture.game().undo.pendingFrom, "the undo request stands")
            assertEquals(fixture.alex, fixture.game().draw.offeredBy)

            val forJordan = fixture.view(fixture.jordan)
            assertEquals(false, forJordan.drawOffer?.fromYou)
            assertEquals(true, forJordan.undoRequest?.fromYou)
        }
    }

    @Test
    fun theSameRequesterMayHaveBoth() {
        withGame { fixture ->
            fixture.undoRequested()

            assertIs<CommandResult.Applied>(fixture.offer(fixture.jordan))

            assertEquals(fixture.jordan, fixture.game().undo.pendingFrom)
            assertEquals(fixture.jordan, fixture.game().draw.offeredBy)
        }
    }

    @Test
    fun decliningEitherKeepsTheOther() {
        withGame { fixture ->
            fixture.undoRequested()
            fixture.offer(fixture.alex)

            assertIs<CommandResult.Applied>(fixture.decline(fixture.jordan))
            assertEquals(fixture.jordan, fixture.game().undo.pendingFrom, "declining the draw keeps the undo request")

            fixture.play("e2e4")
            fixture.play("e7e5")
            fixture.commands.requestUndo(fixture.jordan, fixture.gameId, fixture.version)
            fixture.offer(fixture.alex)
            assertIs<CommandResult.Applied>(fixture.commands.declineUndo(fixture.alex, fixture.gameId, fixture.version))
            assertEquals(fixture.alex, fixture.game().draw.offeredBy, "declining the undo request keeps the draw offer")
        }
    }

    @Test
    fun anAnswerStandsThroughRequestOnlyChangesButNotThroughPlay() {
        withGame { fixture ->
            fixture.undoRequested()
            val askedAt = fixture.version
            fixture.offer(fixture.alex)

            // Alex answers the undo request from before their own offer: still the same position.
            assertIs<CommandResult.Applied>(fixture.commands.acceptUndo(fixture.alex, fixture.gameId, askedAt))
            assertTrue(
                fixture
                    .game()
                    .game.moves
                    .isEmpty(),
                "both moves were undone",
            )
        }
    }

    @Test
    fun anAnswerFromBeforeAPositionChangeIsStale() {
        withGame { fixture ->
            fixture.play("e2e4")
            fixture.offer(fixture.jordan)
            val offeredAt = fixture.version
            fixture.play("e7e5")
            fixture.offer(fixture.jordan)

            assertIs<CommandResult.StaleVersion>(fixture.accept(fixture.alex, at = offeredAt), "the position moved on")
            assertIs<CommandResult.Applied>(fixture.accept(fixture.alex))
        }
    }

    @Test
    fun anAnswerFromBeforeTheOfferNeverSawItAndIsStale() {
        withGame { fixture ->
            val before = fixture.version
            fixture.offer(fixture.jordan)

            assertIs<CommandResult.StaleVersion>(fixture.accept(fixture.alex, at = before))
            assertIs<CommandResult.StaleVersion>(fixture.decline(fixture.alex, at = before))
            assertEquals(fixture.jordan, fixture.game().draw.offeredBy)
        }
    }

    @Test
    fun anOfferFromAnOlderRequestOnlyVersionStillLands() {
        withGame { fixture ->
            fixture.undoRequested()
            val before = fixture.version - 1

            assertIs<CommandResult.Applied>(fixture.offer(fixture.alex, at = before), "only Jordan's request came between")
        }
    }

    // --- what clears both ------------------------------------------------------------------

    private fun bothPending(fixture: Fixture) {
        fixture.undoRequested()
        fixture.offer(fixture.alex)
        assertEquals(fixture.jordan, fixture.game().undo.pendingFrom)
        assertEquals(fixture.alex, fixture.game().draw.offeredBy)
    }

    private fun assertBothCleared(fixture: Fixture) {
        assertNull(fixture.game().undo.pendingFrom, "the undo request went")
        assertNull(fixture.game().draw.offeredBy, "the draw offer went")
        assertEquals(fixture.version, fixture.game().playVersion, "a change to the position")
    }

    @Test
    fun aMoveClearsBoth() {
        withGame { fixture ->
            bothPending(fixture)
            fixture.play("e2e4")
            assertBothCleared(fixture)
        }
    }

    @Test
    fun aMatchingContinuationMoveClearsBoth() {
        withGame { fixture ->
            fixture.play("e2e4", "e7e5", "g1f3", "b8c6")
            fixture.commands.undoMove(fixture.alex, fixture.gameId, fixture.version)
            // Alex to move; Jordan's g1f3 answers Alex's e7e5, so Alex may ask to undo both.
            assertIs<CommandResult.Applied>(fixture.commands.requestUndo(fixture.alex, fixture.gameId, fixture.version))
            fixture.offer(fixture.jordan)

            fixture.play("b8c6")

            assertNull(fixture.game().undo.pendingFrom)
            assertNull(fixture.game().draw.offeredBy)
            assertTrue(
                fixture
                    .game()
                    .game.redoPath
                    .isEmpty(),
                "it was the retained move (D102)",
            )
        }
    }

    @Test
    fun anUndoClearsBoth() {
        withGame { fixture ->
            fixture.play("e2e4")
            fixture.offer(fixture.alex)
            assertIs<CommandResult.Applied>(fixture.commands.undoMove(fixture.jordan, fixture.gameId, fixture.version))
            assertBothCleared(fixture)
        }
    }

    @Test
    fun aRedoClearsBoth() {
        withGame { fixture ->
            fixture.play("e2e4")
            fixture.commands.undoMove(fixture.jordan, fixture.gameId, fixture.version)
            fixture.offer(fixture.alex)
            assertIs<CommandResult.Applied>(fixture.commands.redo(fixture.jordan, fixture.gameId, fixture.version))
            assertBothCleared(fixture)
        }
    }

    @Test
    fun anAcceptedUndoRequestClearsTheOffer() {
        withGame { fixture ->
            bothPending(fixture)
            assertIs<CommandResult.Applied>(fixture.commands.acceptUndo(fixture.alex, fixture.gameId, fixture.version))
            assertBothCleared(fixture)
        }
    }

    @Test
    fun anAcceptedDrawClearsTheUndoRequest() {
        withGame { fixture ->
            fixture.undoRequested()
            fixture.offer(fixture.jordan)
            assertIs<CommandResult.Applied>(fixture.accept(fixture.alex))
            assertNull(fixture.game().undo.pendingFrom)
            assertNull(fixture.game().draw.offeredBy)
        }
    }

    @Test
    fun aResignationClearsBoth() {
        withGame { fixture ->
            bothPending(fixture)
            assertIs<CommandResult.Applied>(fixture.commands.resign(fixture.jordan, fixture.gameId, fixture.version))
            assertNull(fixture.game().undo.pendingFrom)
            assertNull(fixture.game().draw.offeredBy)
        }
    }

    @Test
    fun aDrawClaimClearsBoth() {
        withGame { fixture ->
            fixture.play("g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8")
            fixture.offer(fixture.alex)
            assertIs<CommandResult.Applied>(
                fixture.commands.claimDraw(fixture.jordan, fixture.gameId, fixture.version, DrawClaim.THREEFOLD_REPETITION),
            )
            assertNull(fixture.game().draw.offeredBy)
            assertEquals(
                TerminationReason.THREEFOLD_REPETITION_CLAIM,
                fixture
                    .game()
                    .game.result
                    ?.reason,
            )
        }
    }

    // --- the dashboard ---------------------------------------------------------------------

    @Test
    fun theDashboardListsBothRequestsUndoFirstForBothPlayers() {
        withGame { fixture ->
            assertEquals(emptyList(), fixture.dashboardFor(fixture.jordan).pendingRequests)

            // The draw offer is made first; the undo request still comes first in the list.
            fixture.play("g1f3", "b8c6")
            fixture.offer(fixture.alex)
            fixture.commands.requestUndo(fixture.jordan, fixture.gameId, fixture.version)

            val forJordan = fixture.dashboardFor(fixture.jordan)
            val forAlex = fixture.dashboardFor(fixture.alex)
            assertEquals(
                listOf(PendingRequest(PendingRequest.UNDO, waitingOnYou = false), PendingRequest(PendingRequest.DRAW, waitingOnYou = true)),
                forJordan.pendingRequests,
            )
            assertEquals(
                listOf(PendingRequest(PendingRequest.UNDO, waitingOnYou = true), PendingRequest(PendingRequest.DRAW, waitingOnYou = false)),
                forAlex.pendingRequests,
            )
            assertTrue(forJordan.yourTurn)
            assertFalse(forAlex.yourTurn, "yourTurn still means only the move")
        }
    }

    @Test
    fun theDashboardAlwaysSendsTheList() {
        withGame { fixture ->
            val empty = json.parseToJsonElement(Json.encodeToString(DashboardEntry.serializer(), fixture.dashboardFor(fixture.jordan)))
            assertEquals(0, empty.jsonObject["pendingRequests"]!!.jsonArray.size, "an empty list is sent, not left out")

            fixture.offer(fixture.jordan)
            val populated = json.parseToJsonElement(Json.encodeToString(DashboardEntry.serializer(), fixture.dashboardFor(fixture.alex)))
            assertEquals(1, populated.jsonObject["pendingRequests"]!!.jsonArray.size)

            assertFailsWith<SerializationException>("a missing list is an incompatible response") {
                Json.decodeFromString(DashboardEntry.serializer(), """{"seriesId":"s","opponent":{"userId":"u","username":"Alex"}}""")
            }
        }
    }

    // --- the endpoints ---------------------------------------------------------------------

    private suspend fun ApplicationTestBuilder.command(
        subject: String,
        path: String,
        version: Long,
        fixture: Fixture,
    ) = client.post("/games/${fixture.gameId}/$path") {
        header("Authorization", "Bearer ${tokens.tokenFor(subject)}")
        contentType(ContentType.Application.Json)
        setBody("""{"expectedVersion":$version}""")
    }

    @Test
    fun theEndpointsOfferAcceptAndDeclineWithTypedRefusals() {
        withServer { fixture ->
            val offered = command("auth-2", "draw-offer", 0, fixture)
            assertEquals(HttpStatusCode.OK, offered.status)
            val forAlex = json.decodeFromString<GameView>(offered.bodyAsText())
            assertEquals(true, forAlex.drawOffer?.fromYou)
            assertFalse(forAlex.canOfferDraw)

            val again = command("auth-2", "draw-offer", 1, fixture)
            assertEquals(HttpStatusCode.Conflict, again.status)
            assertEquals(RejectionReason.CANNOT_OFFER_DRAW, json.decodeFromString<CommandRejection>(again.bodyAsText()).reason)

            val wrongAnswerer = command("auth-2", "draw-offer/decline", 1, fixture)
            assertEquals(RejectionReason.NO_DRAW_OFFER, json.decodeFromString<CommandRejection>(wrongAnswerer.bodyAsText()).reason)

            val read =
                client.get("/games/${fixture.gameId}") { header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}") }
            assertEquals(false, json.decodeFromString<GameView>(read.bodyAsText()).drawOffer?.fromYou)

            val declined = command("auth-1", "draw-offer/decline", 1, fixture)
            assertEquals(HttpStatusCode.OK, declined.status)
            assertNull(json.decodeFromString<GameView>(declined.bodyAsText()).drawOffer)

            fixture.play("e2e4")
            command("auth-1", "draw-offer", fixture.version, fixture)
            val accepted = command("auth-2", "draw-offer/accept", fixture.version, fixture)
            assertEquals(HttpStatusCode.OK, accepted.status)
            val drawn = json.decodeFromString<GameView>(accepted.bodyAsText())
            assertEquals("DRAW", drawn.result)
            assertEquals("AGREEMENT", drawn.terminationReason)
        }
    }

    private companion object {
        fun uci(text: String): Move = Move.of(text.substring(0, 2), text.substring(2, 4))
    }
}
