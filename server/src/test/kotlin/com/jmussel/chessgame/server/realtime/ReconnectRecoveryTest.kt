@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.realtime

import com.jmussel.chessgame.server.api.CommandRejection
import com.jmussel.chessgame.server.api.RejectionReason
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.close
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

/**
 * A player who was disconnected while the game moved on.
 *
 * Nothing is replayed to them, and nothing needs to be: the socket is a nudge, not the
 * source of truth, so whatever they missed is already in the state they reload over HTTPS
 * (`D022`). What these tests hold the server to is that a missed message costs the client
 * nothing but a reload — the canonical state is whole, the dashboard says which version to
 * resume from, and a command sent from a stale client is refused with the current state
 * attached rather than applied to the wrong position.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class ReconnectRecoveryTest {
    private val tokens = TestTokens()

    @Test
    fun whatWasMissedWhileAwayIsInTheReloadedGame() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            val gameId = startGame(jordan, alex)
            val (first, second) = order(gameId, jordan, alex)

            val away = second.connect()
            away.close()

            first.move(gameId, 0, "e2", "e4")
            second.move(gameId, 1, "e7", "e5")
            first.move(gameId, 2, "g1", "f3")

            val reloaded = second.readGame(gameId)

            assertEquals(3, reloaded.version)
            assertEquals(listOf("e2e4", "e7e5", "g1f3"), reloaded.moves)
            assertTrue(reloaded.yourTurn, "the reload knows whose move it is")
        }
    }

    @Test
    fun theReturningPlayerSeesExactlyWhatTheOtherOneDoes() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            val gameId = startGame(jordan, alex)
            val (first, second) = order(gameId, jordan, alex)

            val staysConnected = first.connect()
            val away = second.connect()
            away.close()

            first.move(gameId, 0, "d2", "d4")
            assertEquals(1, staysConnected.nextMessage().version)

            val neverLeft = first.readGame(gameId)
            val cameBack = second.readGame(gameId)

            // Everything except the two viewer-specific fields is one shared truth.
            assertEquals(neverLeft.version, cameBack.version)
            assertEquals(neverLeft.moves, cameBack.moves)
            assertEquals(neverLeft.board, cameBack.board)
            assertEquals(neverLeft.sideToMove, cameBack.sideToMove)
            assertEquals(neverLeft.moveNumber, cameBack.moveNumber)
            assertTrue(neverLeft.yourSide != cameBack.yourSide, "they still play opposite colours")

            staysConnected.close()
        }
    }

    @Test
    fun nothingIsReplayedOnReconnecting() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            val gameId = startGame(jordan, alex)
            val (first, second) = order(gameId, jordan, alex)

            second.connect().close()
            first.move(gameId, 0, "e2", "e4")

            val back = second.connect()

            // The greeting is all a returning client gets; the missed move is in the
            // reload, not in a backlog the server had to keep for them.
            assertNull(back.nextMessageOrNull(), "no backlog is replayed")
            assertEquals(1, second.readGame(gameId).version)

            back.close()
        }
    }

    @Test
    fun theConnectionIsLiveAgainAsSoonAsTheGreetingArrives() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            val gameId = startGame(jordan, alex)
            val (first, second) = order(gameId, jordan, alex)

            second.connect().close()
            first.move(gameId, 0, "e2", "e4")

            // Reconnect, reload, then act: because the socket is registered before the
            // greeting goes out, a change after the greeting is pushed and everything
            // before it is in the reload. There is no gap between the two.
            val back = second.connect()
            val reloaded = second.readGame(gameId)
            second.move(gameId, reloaded.version, "e7", "e5")

            assertEquals(2, back.nextMessage().version)

            back.close()
        }
    }

    @Test
    fun theDashboardSaysWhichVersionToResumeFrom() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            val gameId = startGame(jordan, alex)
            val (first, second) = order(gameId, jordan, alex)

            second.connect().close()
            first.move(gameId, 0, "e2", "e4")

            // One request tells a returning client every game it plays in and where each
            // one is, so it does not have to guess what changed while it was away.
            val entry = second.dashboard().single { it.gameId == gameId }

            assertEquals(1, entry.version)
            assertTrue(entry.yourTurn)
            assertEquals(second.readGame(gameId).version, entry.version)
        }
    }

    @Test
    fun aCommandFromAStaleClientIsRefusedWithTheCanonicalState() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            val gameId = startGame(jordan, alex)
            val (first, second) = order(gameId, jordan, alex)

            // The second player read the game, then missed the first player's move.
            val stale = second.readGame(gameId)
            first.move(gameId, 0, "e2", "e4")

            val refused = second.move(gameId, stale.version, "e7", "e5")

            assertEquals(HttpStatusCode.Conflict, refused.status)

            val rejection = fixtureJson.decodeFromString<CommandRejection>(refused.bodyAsText())

            assertEquals(RejectionReason.STALE_VERSION, rejection.reason)

            // The refusal carries the state they were missing, so the retry needs no
            // second request.
            val current = assertNotNull(rejection.game, "a stale refusal shows the current game")

            assertEquals(1, current.version)
            assertEquals(listOf("e2e4"), current.moves)
            assertEquals(
                HttpStatusCode.OK,
                second.move(gameId, current.version, "e7", "e5").status,
                "the client recovers from the refusal alone",
            )
        }
    }

    @Test
    fun aMissedMessageNeverLeavesTheGameInAWrongPosition() {
        withTwoPlayers(tokens) { jordan, alex, _ ->
            val gameId = startGame(jordan, alex)
            val (first, second) = order(gameId, jordan, alex)

            // A whole game is played while the second player's socket is down and up
            // repeatedly: none of that touches what the server stores.
            first.move(gameId, 0, "e2", "e4")
            second.connect().close()
            second.move(gameId, 1, "e7", "e5")
            second.connect().close()
            first.move(gameId, 2, "f1", "c4")
            val back = second.connect()
            second.move(gameId, 3, "b8", "c6")
            assertEquals(4, back.nextMessage().version)
            first.move(gameId, 4, "d1", "h5")
            assertEquals(5, back.nextMessage().version)
            second.move(gameId, 5, "g8", "f6")
            first.move(gameId, 6, "h5", "f7")

            val finished = second.readGame(gameId)

            assertEquals(7, finished.version)
            assertEquals(
                listOf("e2e4", "e7e5", "f1c4", "b8c6", "d1h5", "g8f6", "h5f7"),
                finished.moves,
            )
            assertEquals("CHECKMATE", finished.terminationReason)

            back.close()
        }
    }

    @Test
    fun aStaleConnectionIsDroppedWhenItsUpdateCannotBeDelivered() {
        withTwoPlayers(tokens) { jordan, alex, hub ->
            val gameId = startGame(jordan, alex)
            val (first, second) = order(gameId, jordan, alex)

            val going = second.connect()
            val staying = second.connect()

            going.close()

            // The server may not have noticed the first socket yet. Either way the move is
            // accepted and the socket that is still there is told.
            assertEquals(HttpStatusCode.OK, first.move(gameId, 0, "e2", "e4").status)
            assertEquals(1, staying.nextMessage().version)

            // The dead one is forgotten, leaving only the socket that is really there.
            val userId = second.userId()

            withTimeout(WAIT_MILLIS) {
                while (hub.connectionCount(userId) > 1) {
                    delay(20)
                }
            }

            assertEquals(1, hub.connectionCount(userId))

            staying.close()
        }
    }
}
