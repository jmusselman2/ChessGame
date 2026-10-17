@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.dashboard

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.server.api.FriendSummary
import com.jmussel.chessgame.server.api.RecordView
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.ChessRecord
import com.jmussel.chessgame.server.db.DashboardQueries
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.GameSeriesRepository
import com.jmussel.chessgame.server.db.Participant
import com.jmussel.chessgame.server.db.TableRepository
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.game.CommandResult
import com.jmussel.chessgame.server.game.GameCommandService
import com.jmussel.chessgame.server.series.SeriesService
import com.jmussel.chessgame.server.series.seriesService
import com.jmussel.chessgame.server.testModule
import com.jmussel.chessgame.server.user.Username
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The lifetime `W-L-D` record on each friend row (`D096`, `M22.4`): counted by the server from
 * stored results, across every series, from the viewer's side.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class FriendRecordTest {
    private val tokens = TestTokens()

    private class Fixture(
        val database: Database,
    ) {
        val users = UserRepository(database)
        val games = GameRepository(database)
        val series: SeriesService = seriesService(database)
        val commands = GameCommandService(database, games, series, users)
        val dashboard = DashboardQueries(database)

        fun named(
            subject: String,
            username: String,
        ): Uuid {
            val user = users.resolveBySubject(subject)
            users.claimUsername(user.id, Username.of(username))
            return user.id
        }

        /** The pair's current game, opened or started by [caller]'s Play. */
        fun currentGame(
            caller: Uuid,
            friend: Uuid,
        ): Uuid = assertNotNull(series.play(caller, friend).series.currentGameId)

        fun play(
            gameId: Uuid,
            vararg moves: String,
        ) {
            moves.forEach { move ->
                val game = assertNotNull(games.load(gameId))
                val player = if (game.game.sideToMove == Side.WHITE) game.whiteUserId else game.blackUserId
                val parsed = Move.of(move.take(2), move.drop(2))
                assertIs<CommandResult.Applied>(commands.makeMove(player, gameId, game.version, parsed))
            }
        }

        /** [loser] resigns the current game between them; returns the next one. */
        fun resign(
            loser: Uuid,
            winner: Uuid,
        ) {
            val gameId = currentGame(loser, winner)
            assertIs<CommandResult.Applied>(commands.resign(loser, gameId, assertNotNull(games.load(gameId)).version))
        }

        fun agreeDraw(
            a: Uuid,
            b: Uuid,
        ) {
            val gameId = currentGame(a, b)
            commands.offerDraw(a, gameId, assertNotNull(games.load(gameId)).version)
            assertIs<CommandResult.Applied>(commands.acceptDraw(b, gameId, assertNotNull(games.load(gameId)).version))
        }

        fun record(
            viewer: Uuid,
            opponent: Uuid,
        ): ChessRecord = dashboard.chessRecordsFor(viewer)[opponent] ?: ChessRecord()
    }

    private fun withFixture(block: (Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource -> block(Fixture(Databases.connect(dataSource))) }

    @Test
    fun winsLossesAndDrawsAreCountedFromEachSide() {
        withFixture { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")

            fixture.resign(loser = alex, winner = jordan)
            fixture.resign(loser = alex, winner = jordan)
            fixture.resign(loser = jordan, winner = alex)
            fixture.agreeDraw(jordan, alex)

            assertEquals(ChessRecord(wins = 2, losses = 1, draws = 1), fixture.record(jordan, alex))
            assertEquals(ChessRecord(wins = 1, losses = 2, draws = 1), fixture.record(alex, jordan))
        }
    }

    @Test
    fun everyDrawCountsAndAGameStillBeingPlayedDoesNot() {
        withFixture { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")

            val repetition = fixture.currentGame(jordan, alex)
            fixture.play(repetition, "g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8")
            val atRepetition = assertNotNull(fixture.games.load(repetition))
            val claimant = if (atRepetition.game.sideToMove == Side.WHITE) atRepetition.whiteUserId else atRepetition.blackUserId
            assertIs<CommandResult.Applied>(
                fixture.commands.claimDraw(
                    claimant,
                    repetition,
                    atRepetition.version,
                    com.jmussel.chessgame.core.chess.DrawClaim.THREEFOLD_REPETITION,
                ),
            )
            fixture.agreeDraw(alex, jordan)
            fixture.play(fixture.currentGame(jordan, alex), "e2e4")

            assertEquals(ChessRecord(wins = 0, losses = 0, draws = 2), fixture.record(jordan, alex))
        }
    }

    @Test
    fun theRecordSpansEverySeriesActiveOrClosed() {
        withFixture { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")

            fixture.resign(loser = alex, winner = jordan)
            val firstSeries =
                fixture.series
                    .play(jordan, alex)
                    .series.id
            fixture.play(fixture.currentGame(jordan, alex), "e2e4")
            fixture.series.leave(alex, firstSeries)
            fixture.resign(loser = jordan, winner = alex)

            // Leaving the started game was Alex's resignation (`D095`), so Jordan won it.
            assertEquals(ChessRecord(wins = 2, losses = 1, draws = 0), fixture.record(jordan, alex))
        }
    }

    @Test
    fun aGameDeletedWhenTheSeriesWasLeftIsNotCounted() {
        withFixture { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            fixture.resign(loser = alex, winner = jordan)

            // The rematch has no move; leaving deletes it without a result.
            fixture.series.leave(
                jordan,
                fixture.series
                    .play(jordan, alex)
                    .series.id,
            )

            assertEquals(ChessRecord(wins = 1, losses = 0, draws = 0), fixture.record(jordan, alex))
        }
    }

    @Test
    fun eachOpponentHasTheirOwnRecordAndAStrangerHasNone() {
        withFixture { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            val sam = fixture.named("auth-3", "Sam")

            fixture.resign(loser = alex, winner = jordan)
            fixture.resign(loser = jordan, winner = sam)

            val records = fixture.dashboard.chessRecordsFor(jordan)
            assertEquals(ChessRecord(wins = 1), records[alex])
            assertEquals(ChessRecord(losses = 1), records[sam])
            assertEquals(setOf(alex, sam), records.keys)
            assertEquals(ChessRecord(), fixture.record(alex, sam))
        }
    }

    @Test
    fun anotherGameTypeIsNotCounted() {
        withFixture { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            transaction(fixture.database) {
                exec("insert into game_types (id, min_participants, max_participants) values ('OTHER', 2, 4)")
            }
            val table = TableRepository(fixture.database).findOrCreate("OTHER", listOf(Participant.user(jordan), Participant.user(alex)))
            val other = GameSeriesRepository(fixture.database).create(table)
            val finished = ChessRules.resign(ChessGame.newGame(), Side.BLACK)
            fixture.games.create(other.id, 1, listOf(jordan, alex), finished)

            assertEquals(ChessRecord(), fixture.record(jordan, alex))
        }
    }

    @Test
    fun theFriendsListCarriesEachRecordIncludingARealZero() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val fixture = Fixture(database)
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            val sam = fixture.named("auth-3", "Sam")
            FriendshipRepository(database).add(jordan, alex)
            FriendshipRepository(database).add(jordan, sam)
            fixture.resign(loser = alex, winner = jordan)

            testApplication {
                application { testModule(tokens.verifier(), database) }

                val body =
                    client.get("/friends") { header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}") }.bodyAsText()
                val friends = Json.decodeFromString<List<FriendSummary>>(body).associateBy { it.username }

                assertEquals(RecordView(wins = 1, losses = 0, draws = 0), friends.getValue("Alex").record)
                assertEquals(RecordView(wins = 0, losses = 0, draws = 0), friends.getValue("Sam").record)
                assert(body.contains("\"draws\":0")) { "every count is sent, a zero included: $body" }
            }
        }
    }

    @Test
    fun aFriendWithoutARecordIsAnIncompatibleResponse() {
        assertFailsWith<SerializationException> {
            Json.decodeFromString<FriendSummary>("""{"userId":"u","username":"Alex"}""")
        }
    }
}
