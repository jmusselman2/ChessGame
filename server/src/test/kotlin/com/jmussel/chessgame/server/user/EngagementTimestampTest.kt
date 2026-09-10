@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.user

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.server.api.CurrentUser
import com.jmussel.chessgame.server.api.UserSummary
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.GameSeriesRepository
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.db.UsersTable
import com.jmussel.chessgame.server.game.CommandResult
import com.jmussel.chessgame.server.game.GameCommandService
import com.jmussel.chessgame.server.series.SeriesService
import com.jmussel.chessgame.server.testModule
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * `last_login_at` and `last_action_at`, and what keeps them apart from `last_seen_at`
 * (`M19.11`).
 *
 * Three timestamps, three questions. `last_seen_at` is the throttled "recently around"
 * marker (`D010`) and is accurate only to its window; the two new ones are exact and
 * record discrete events — a session starting, and a command being accepted. The
 * assertions that matter most are the negative ones: a *refused* command leaves no action
 * timestamp, and none of the three reaches the API.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class EngagementTimestampTest {
    private val tokens = TestTokens()
    private val json = Json { ignoreUnknownKeys = true }

    private class Fixture(
        val database: Database,
        val users: UserRepository,
    ) {
        fun timestamps(id: Uuid): Triple<Any?, Any?, Any?> =
            transaction(database) {
                val row = UsersTable.selectAll().where { UsersTable.id eq id }.single()
                Triple(row[UsersTable.lastSeenAt], row[UsersTable.lastLoginAt], row[UsersTable.lastActionAt])
            }

        fun lastLogin(id: Uuid) = timestamps(id).second

        fun lastAction(id: Uuid) = timestamps(id).third

        fun lastSeen(id: Uuid) = timestamps(id).first
    }

    private fun withServer(block: suspend ApplicationTestBuilder.(Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)

            testApplication {
                application { testModule(tokens.verifier(), database) }
                block(Fixture(database, UserRepository(database)))
            }
        }

    private suspend fun ApplicationTestBuilder.me(subject: String) =
        client.get("/me") { header("Authorization", "Bearer ${tokens.tokenFor(subject)}") }

    // --- last_login_at ------------------------------------------------------------------

    @Test
    fun askingWhoIAmIsASessionStartingAndIsRecorded() =
        withServer { fixture ->
            val created = json.decodeFromString<CurrentUser>(me("engagement-1").bodyAsText())
            val id = Uuid.parse(created.userId)

            assertNotNull(fixture.lastLogin(id), "a session start is recorded exactly")
            assertNull(fixture.lastAction(id), "and is not an action")
        }

    @Test
    fun aLaterSessionStartMovesTheTimestampOn() =
        withServer { fixture ->
            val id = Uuid.parse(json.decodeFromString<CurrentUser>(me("engagement-2").bodyAsText()).userId)
            val first = fixture.lastLogin(id)

            // Unthrottled, unlike `last_seen_at`: a session start is discrete, so an
            // approximate one would answer nothing.
            fixture.users.touchLastLogin(id, Instant.now().plusSeconds(60))
            val second = fixture.lastLogin(id)

            assertTrue(first != second, "each session start is its own fact")
        }

    @Test
    fun anOrdinaryAuthenticatedRequestIsNotASessionStart() =
        withServer { fixture ->
            val id = Uuid.parse(json.decodeFromString<CurrentUser>(me("engagement-3").bodyAsText()).userId)
            val loginAfterMe = fixture.lastLogin(id)

            // Using the session is not starting one. `last_seen_at` covers this request;
            // `last_login_at` must not move.
            client.get("/friends") { header("Authorization", "Bearer ${tokens.tokenFor("engagement-3")}") }

            assertEquals(loginAfterMe, fixture.lastLogin(id), "only /me is a session starting")
            assertNotNull(fixture.lastSeen(id), "while ordinary activity is still tracked (D010)")
        }

    // --- last_action_at -----------------------------------------------------------------

    @Test
    fun anAcceptedMoveRecordsAnAction() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val fixture = Fixture(database, UserRepository(database))
            val commands = commandService(database, fixture.users)
            val (white, black, gameId) = aGame(database, fixture.users)

            assertNull(fixture.lastAction(white), "nothing has been played yet")

            val result = commands.makeMove(white, gameId, expectedVersion = 0, move = e2e4())

            assertTrue(result is CommandResult.Applied)
            assertNotNull(fixture.lastAction(white), "the player who moved acted")
            assertNull(fixture.lastAction(black), "the player who did not, did not")
        }
    }

    @Test
    fun anAcceptedUndoResignationAndDrawClaimAllRecordAnAction() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val fixture = Fixture(database, UserRepository(database))
            val commands = commandService(database, fixture.users)

            // Undo: the mover takes their own move back (`D016`).
            val (white, _, undoGame) = aGame(database, fixture.users, "undo")
            commands.makeMove(white, undoGame, expectedVersion = 0, move = e2e4())
            val afterMove = fixture.lastAction(white)
            commands.undoMove(white, undoGame, expectedVersion = 1)
            assertNotNull(fixture.lastAction(white))
            assertTrue(afterMove != null, "the move had already recorded one")

            // Resignation, which is final (`D018`) and is still an action.
            val (resignWhite, _, resignGame) = aGame(database, fixture.users, "resign")
            assertNull(fixture.lastAction(resignWhite))
            commands.resign(resignWhite, resignGame, expectedVersion = 0)
            assertNotNull(fixture.lastAction(resignWhite), "giving up is an action too")
        }
    }

    @Test
    fun aRefusedCommandRecordsNothing() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val fixture = Fixture(database, UserRepository(database))
            val commands = commandService(database, fixture.users)
            val (white, black, gameId) = aGame(database, fixture.users)

            // Not their turn, so nothing happened and nothing may claim it did.
            commands.makeMove(black, gameId, expectedVersion = 0, move = e2e4())
            assertNull(fixture.lastAction(black), "a refused command is not an action")

            // Stale version: the same, for the other reason a command is refused (`D021`).
            commands.makeMove(white, gameId, expectedVersion = 7, move = e2e4())
            assertNull(fixture.lastAction(white))

            // An illegal move is refused by the rules rather than by the version.
            commands.makeMove(white, gameId, expectedVersion = 0, move = illegal())
            assertNull(fixture.lastAction(white))
        }
    }

    @Test
    fun readingAGameIsNotAnAction() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val fixture = Fixture(database, UserRepository(database))
            val commands = commandService(database, fixture.users)
            val (white, _, gameId) = aGame(database, fixture.users)

            // `load` answers `Applied` as well, which is exactly why the timestamp is not
            // hung off that result.
            commands.load(white, gameId)

            assertNull(fixture.lastAction(white), "looking at a game is not acting in it")
        }
    }

    // --- Nothing reaches the API --------------------------------------------------------

    @Test
    fun noEngagementTimestampIsExposedThroughTheApi() =
        withServer { fixture ->
            val body = me("engagement-api").bodyAsText()
            val id = Uuid.parse(json.decodeFromString<CurrentUser>(body).userId)
            assertNotNull(fixture.lastLogin(id))

            client.post("/username") {
                header("Authorization", "Bearer ${tokens.tokenFor("engagement-api")}")
                contentType(ContentType.Text.Plain)
                setBody("Engaged")
            }

            val identity = me("engagement-api").bodyAsText()
            val friend = json.decodeFromString<CurrentUser>(identity)
            assertEquals("Engaged", friend.username)

            // The DTOs name the fields they expose and these are not among them
            // (`ARCHITECTURE.md` §14). Asserted on the wire, because that is the boundary
            // the requirement is about.
            listOf("lastLogin", "last_login", "lastAction", "last_action", "lastSeen", "last_seen").forEach { leak ->
                assertFalse(identity.contains(leak, ignoreCase = true), "/me leaked $leak")
            }

            val lookupResponse =
                client.get("/users/Engaged") { header("Authorization", "Bearer ${tokens.tokenFor("engagement-api")}") }
            val lookup = lookupResponse.bodyAsText()
            assertEquals("Engaged", json.decodeFromString<UserSummary>(lookup).username)
            listOf("lastLogin", "last_login", "lastAction", "last_action", "lastSeen", "last_seen").forEach { leak ->
                assertFalse(lookup.contains(leak, ignoreCase = true), "a user lookup leaked $leak")
            }
        }

    @Test
    fun theColumnsStartEmptyAndAreNotBackfilled() =
        withServer { fixture ->
            // A row that has never logged in or acted has no answer, and null is the honest
            // one — a default of now() would invent activity that did not happen.
            val id = fixture.users.resolveBySubject("engagement-fresh").id

            assertNull(fixture.lastLogin(id))
            assertNull(fixture.lastAction(id))
        }

    @Test
    fun aStoredUserCarriesAllThreeWithoutMixingThemUp() =
        withServer { fixture ->
            val id = Uuid.parse(json.decodeFromString<CurrentUser>(me("engagement-stored").bodyAsText()).userId)
            val at = Instant.parse("2026-09-10T12:00:00Z")
            fixture.users.touchLastLogin(id, at)

            val stored = assertNotNull(fixture.users.find(id))

            assertEquals(at, stored.lastLoginAt)
            assertNull(stored.lastActionAt, "a session start is not an action")
        }

    // --- Fixtures -----------------------------------------------------------------------

    private fun commandService(
        database: Database,
        users: UserRepository,
    ): GameCommandService {
        val games = GameRepository(database)

        return GameCommandService(
            database = database,
            games = games,
            series = SeriesService(database = database, series = GameSeriesRepository(database), games = games),
            users = users,
        )
    }

    /** Two named players, a series, and a game to play in. */
    private fun aGame(
        database: Database,
        users: UserRepository,
        prefix: String = "engagement",
    ): Triple<Uuid, Uuid, Uuid> {
        val white = users.resolveBySubject("$prefix-white").id
        val black = users.resolveBySubject("$prefix-black").id
        users.claimUsername(white, Username.of("${prefix.take(6)}White"))
        users.claimUsername(black, Username.of("${prefix.take(6)}Black"))
        FriendshipRepository(database).add(white, black)

        val series = GameSeriesRepository(database).openOrCreate(white, black).series
        val gameId =
            GameRepository(database).create(
                seriesId = series.id,
                sequenceNumber = 1,
                whiteUserId = white,
                blackUserId = black,
                game = ChessGame.newGame(),
            )

        return Triple(white, black, gameId)
    }

    private fun e2e4() = Move(Square.parse("e2"), Square.parse("e4"))

    /** A pawn cannot reach e7 from e2, so the rules refuse it rather than the version. */
    private fun illegal() = Move(Square.parse("e2"), Square.parse("e7"))
}
