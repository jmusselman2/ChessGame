package com.jmussel.chessgame.server.realtime

import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.close
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A move by one player reaching the other player's open connection.
 *
 * The push carries no game state: it names the game and the version it reached, and the
 * client reloads over HTTPS (`D022`). These tests therefore assert who was told and which
 * version they were told about — never that the socket carried the board.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class GameUpdateBroadcastTest {
    private val tokens = TestTokens()

    @Test
    fun aMoveByOnePlayerReachesTheOther() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            val gameId = startGame(jordan, alex)
            val (mover, waiter) = order(gameId, jordan, alex)
            val waiting = waiter.connect()

            mover.move(gameId, 0, "e2", "e4")

            val update = waiting.nextMessage()

            assertEquals(RealtimeMessage.GAME_UPDATED, update.type)
            assertEquals(gameId, update.gameId)
            assertEquals(1, update.version, "the version the client should now read")

            waiting.close()
        }
    }

    @Test
    fun theMoverIsToldTooSoTheirOtherDevicesKeepUp() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            val gameId = startGame(jordan, alex)
            val (mover, _) = order(gameId, jordan, alex)
            val moversOtherDevice = mover.connect()

            mover.move(gameId, 0, "e2", "e4")

            assertEquals(RealtimeMessage.GAME_UPDATED, moversOtherDevice.nextMessage().type)

            moversOtherDevice.close()
        }
    }

    @Test
    fun everyMoveOfARallyIsAnnouncedInOrder() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            val gameId = startGame(jordan, alex)
            val (first, second) = order(gameId, jordan, alex)
            val watching = second.connect()

            first.move(gameId, 0, "e2", "e4")
            assertEquals(1, watching.nextMessage().version)

            second.move(gameId, 1, "e7", "e5")
            assertEquals(2, watching.nextMessage().version)

            first.move(gameId, 2, "g1", "f3")
            assertEquals(3, watching.nextMessage().version)

            watching.close()
        }
    }

    @Test
    fun anUndoIsAnnouncedLikeAMove() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            val gameId = startGame(jordan, alex)
            val (mover, waiter) = order(gameId, jordan, alex)
            val waiting = waiter.connect()

            mover.move(gameId, 0, "e2", "e4")
            assertEquals(1, waiting.nextMessage().version)

            assertEquals(HttpStatusCode.OK, mover.undo(gameId, 1).status)

            val update = waiting.nextMessage()

            assertEquals(RealtimeMessage.GAME_UPDATED, update.type)
            assertEquals(2, update.version, "taking a move back is a change like any other")

            waiting.close()
        }
    }

    @Test
    fun aClaimedDrawIsAnnounced() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            val gameId = startGame(jordan, alex)
            val (first, second) = order(gameId, jordan, alex)
            val watching = second.connect()

            // Knights out and back, twice: the starting position appears three times.
            val rally = listOf("g1" to "f3", "g8" to "f6", "f3" to "g1", "f6" to "g8")
            var version = 0L
            repeat(2) {
                rally.forEachIndexed { index, (from, to) ->
                    val player = if (index % 2 == 0) first else second

                    assertEquals(HttpStatusCode.OK, player.move(gameId, version, from, to).status)
                    version += 1

                    assertEquals(version, watching.nextMessage().version)
                }
            }

            assertEquals(
                HttpStatusCode.OK,
                first.claimDraw(gameId, version, "THREEFOLD_REPETITION").status,
            )

            val update = watching.nextMessage()

            assertEquals(RealtimeMessage.GAME_UPDATED, update.type)
            assertEquals(version + 1, update.version)

            watching.close()
        }
    }

    @Test
    fun aRefusedCommandAnnouncesNothing() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            val gameId = startGame(jordan, alex)
            val (mover, waiter) = order(gameId, jordan, alex)
            val waiting = waiter.connect()

            // Out of turn: nothing was written, so there is nothing to hear about.
            assertEquals(HttpStatusCode.Conflict, waiter.move(gameId, 0, "e7", "e5").status)
            assertNull(waiting.nextMessageOrNull())

            // An illegal move by the right player is refused just as quietly.
            assertEquals(HttpStatusCode.UnprocessableEntity, mover.move(gameId, 0, "e2", "e5").status)
            assertNull(waiting.nextMessageOrNull())

            waiting.close()
        }
    }

    @Test
    fun someoneElsesGameIsNotAnnouncedToOnlookers() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            val gameId = startGame(jordan, alex)
            val onlooker = PlayerClient(this, tokens, "auth-onlooker")
            onlooker.claimUsername("Onlooker")
            val watching = onlooker.connect()
            val (mover, _) = order(gameId, jordan, alex)

            mover.move(gameId, 0, "e2", "e4")

            assertNull(watching.nextMessageOrNull(), "only the two players hear about their game")

            watching.close()
        }
    }

    @Test
    fun aMoveIsAcceptedWhenNobodyIsListening() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            val gameId = startGame(jordan, alex)
            val (mover, _) = order(gameId, jordan, alex)

            // Neither player has a socket open. The command still applies: realtime
            // delivery is a convenience, not part of accepting a move.
            assertEquals(HttpStatusCode.OK, mover.move(gameId, 0, "e2", "e4").status)
            assertEquals(1, mover.readGame(gameId).version)
        }
    }
}
