@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.game

import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.server.api.GameView
import com.jmussel.chessgame.server.api.MoveView
import com.jmussel.chessgame.server.api.UndoNoteKind
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.StoredGame
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.series.seriesService
import com.jmussel.chessgame.server.series.startSeries
import com.jmussel.chessgame.server.user.Username
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Every move a game view names is a structured move with required SAN (`D101`, `M22.7`),
 * worked out on the server from the position each move was recorded as played from.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class SanProjectionTest {
    private class FixedCoin(
        private val value: Boolean,
    ) : Random() {
        override fun nextBits(bitCount: Int): Int = 0

        override fun nextBoolean(): Boolean = value

        override fun nextInt(until: Int): Int = if (value) 0 else until - 1
    }

    private class Fixture(
        val database: Database,
    ) {
        val users = UserRepository(database)
        val games = GameRepository(database)
        val commands = GameCommandService(database, games, seriesService(database), users)
        lateinit var jordan: Uuid
        lateinit var alex: Uuid
        lateinit var gameId: Uuid

        fun start() {
            jordan = named("auth-1", "Jordan")
            alex = named("auth-2", "Alex")
            FriendshipRepository(database).add(jordan, alex)
            gameId = assertNotNull(seriesService(database, FixedCoin(jordan < alex)).startSeries(jordan, alex).currentGameId)
            check(game().whiteUserId == jordan)
        }

        private fun named(
            subject: String,
            username: String,
        ): Uuid {
            val user = users.resolveBySubject(subject)
            users.claimUsername(user.id, Username.of(username))
            return user.id
        }

        fun game(): StoredGame = assertNotNull(games.load(gameId))

        fun play(vararg moves: String) {
            moves.forEach { move ->
                val player = if (game().game.sideToMove == Side.WHITE) jordan else alex
                val parsed = Move.of(move.take(2), move.drop(2))
                assertIs<CommandResult.Applied>(commands.makeMove(player, gameId, game().version, parsed), move)
            }
        }

        fun view(viewer: Uuid): GameView = GameView.of(game(), viewer, assertNotNull(users.find(game().opponentOf(viewer))))
    }

    private fun withGame(block: (Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource -> block(Fixture(Databases.connect(dataSource)).also { it.start() }) }

    @Test
    fun theMoveListAndTheLastMoveCarryCoordinatesAndSan() {
        withGame { fixture ->
            fixture.play("e2e4", "e7e5", "f1c4", "b8c6", "d1h5", "g8f6", "h5f7")

            val view = fixture.view(fixture.alex)

            assertEquals(listOf("e4", "e5", "Bc4", "Nc6", "Qh5", "Nf6", "Qxf7#"), view.moves.map { it.san })
            assertEquals(MoveView(from = "h5", to = "f7", promotion = null, san = "Qxf7#"), view.lastMove)
            assertEquals(view.moves.last(), view.lastMove)
        }
    }

    @Test
    fun castlingIsNamedAsCastling() {
        withGame { fixture ->
            fixture.play("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6", "e1g1")

            assertEquals("O-O", fixture.view(fixture.jordan).lastMove?.san)
        }
    }

    @Test
    fun aPendingRequestNamesItsMovesInSan() {
        withGame { fixture ->
            fixture.play("g1f3", "b8c6")
            fixture.commands.requestUndo(fixture.jordan, fixture.gameId, fixture.game().version)

            val request = assertNotNull(fixture.view(fixture.alex).undoRequest)

            assertEquals("Nf3", request.requesterMove.san)
            assertEquals("Nc6", request.replyMove.san)
            assertEquals("g1" to "f3", request.requesterMove.from to request.requesterMove.to)
        }
    }

    @Test
    fun aDeclinedNoteNamesTheActiveMovesInSan() {
        withGame { fixture ->
            fixture.play("g1f3", "b8c6")
            fixture.commands.requestUndo(fixture.jordan, fixture.gameId, fixture.game().version)
            fixture.commands.declineUndo(fixture.alex, fixture.gameId, fixture.game().version)

            val note = assertNotNull(fixture.view(fixture.jordan).undoNote)

            assertEquals(UndoNoteKind.DECLINED, note.kind)
            assertEquals("Nf3" to "Nc6", note.requesterMove.san to note.replyMove.san)
        }
    }

    @Test
    fun anAcceptedNoteNamesTheUndoneMovesFromTheRedoOperation() {
        withGame { fixture ->
            fixture.play("e2e4", "d7d5", "e4d5", "d8d5")
            fixture.commands.requestUndo(fixture.jordan, fixture.gameId, fixture.game().version)
            fixture.commands.acceptUndo(fixture.alex, fixture.gameId, fixture.game().version)

            val note = assertNotNull(fixture.view(fixture.jordan).undoNote)

            assertEquals(UndoNoteKind.ACCEPTED, note.kind)
            assertEquals("exd5" to "Qxd5", note.requesterMove.san to note.replyMove.san, "from their own recorded positions")
        }
    }

    @Test
    fun aCrossedNoteNamesItsMovesInSanToo() {
        withGame { fixture ->
            fixture.play("g1f3", "b8c6")
            val before = fixture.game().version
            // Alex undoes the reply; Jordan's request from the version before crosses it.
            assertIs<CommandResult.Applied>(fixture.commands.undoMove(fixture.alex, fixture.gameId, before))
            assertIs<CommandResult.Applied>(fixture.commands.requestUndo(fixture.jordan, fixture.gameId, before))

            val forJordan = assertNotNull(fixture.view(fixture.jordan).undoNote)
            val forAlex = assertNotNull(fixture.view(fixture.alex).undoNote)

            assertEquals(UndoNoteKind.UNDONE_TOO, forJordan.kind)
            assertEquals(UndoNoteKind.BOTH_UNDONE, forAlex.kind)
            assertEquals("Nf3" to "Nc6", forJordan.requesterMove.san to forJordan.replyMove.san)
        }
    }

    @Test
    fun aMoveWithoutSanIsAnIncompatibleResponse() {
        assertFailsWith<SerializationException> {
            Json.decodeFromString(MoveView.serializer(), """{"from":"e2","to":"e4"}""")
        }
        assertEquals("""{"from":"e2","to":"e4","san":"e4"}""", Json.encodeToString(MoveView.serializer(), MoveView("e2", "e4", null, "e4")))
    }
}
