@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.realtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * What the hub does when one of the sockets it is publishing to will not take the message
 * (`M12-01`).
 *
 * Delivery is best-effort (`D022`), and the failure that matters is not the socket that
 * throws — that one was always dropped — but the socket that neither throws nor finishes.
 * The deadline is given to the hub rather than read from the default, so these assert what
 * it does without waiting the real five seconds for it.
 */
class RealtimeFanOutTest {
    @Test
    fun aConnectionThatNeverTakesTheMessageIsDroppedAtItsDeadline() =
        runBlocking {
            val hub = RealtimeHub(sendTimeout = SHORT_DEADLINE)
            val user = Uuid.random()
            hub.subscribe(user) { awaitCancellation() }

            // The assertion is that this returns at all: without a deadline it never does.
            withTimeout(PATIENCE) { hub.publish(listOf(user), update()) }

            assertEquals(0, hub.connectionCount(user), "a send that never finishes is a connection that has gone")
        }

    @Test
    fun everyConnectionIsSentToAtTheSameTime() =
        runBlocking {
            val hub = RealtimeHub(sendTimeout = PATIENCE)
            val users = List(THREE_PEOPLE) { Uuid.random() }
            val reached = users.map { CompletableDeferred<Unit>() }

            users.forEachIndexed { index, user ->
                hub.subscribe(user) {
                    reached[index].complete(Unit)
                    // Finishes only once every connection has been reached, so a fan-out
                    // that awaited one send before starting the next could never get here:
                    // it would sit on the first until the deadline dropped it.
                    reached.forEach { arrival -> arrival.await() }
                }
            }

            hub.publish(users, update())

            assertTrue(reached.all { it.isCompleted }, "every connection was sent to")
            users.forEach { user -> assertEquals(1, hub.connectionCount(user), "and none of them was dropped") }
        }

    @Test
    fun aStalledConnectionDoesNotDelayAReachableOne() =
        runBlocking {
            val hub = RealtimeHub(sendTimeout = PATIENCE)
            val stalled = Uuid.random()
            val reachable = Uuid.random()
            val delivered = CompletableDeferred<Unit>()

            // Listed first, so a sequential fan-out would reach it first too.
            hub.subscribe(stalled) { awaitCancellation() }
            hub.subscribe(reachable) { delivered.complete(Unit) }

            val publishing = launch { hub.publish(listOf(stalled, reachable), update()) }
            val arrived = withTimeoutOrNull(SHORT_DEADLINE) { delivered.await() }
            publishing.cancelAndJoin()

            assertNotNull(arrived, "one stalled socket must not hold up the next player's update")
        }

    @Test
    fun aConnectionThatFailsOutrightStillLeavesTheOthersDelivered() =
        runBlocking {
            val hub = RealtimeHub(sendTimeout = PATIENCE)
            val broken = Uuid.random()
            val reachable = Uuid.random()
            var delivered = 0

            hub.subscribe(broken) { throw IOException("connection reset by peer") }
            hub.subscribe(reachable) { delivered++ }

            hub.publish(listOf(broken, reachable), update())

            assertEquals(1, delivered, "the other player is still told")
            assertEquals(0, hub.connectionCount(broken), "and the broken connection is forgotten")
            assertEquals(1, hub.connectionCount(reachable), "while the working one is kept")
        }

    @Test
    fun cancellingAPublishDoesNotDropTheConnectionsItWasSendingTo() =
        runBlocking {
            val hub = RealtimeHub(sendTimeout = PATIENCE)
            val user = Uuid.random()
            val reached = CompletableDeferred<Unit>()
            hub.subscribe(user) {
                reached.complete(Unit)
                awaitCancellation()
            }

            val publishing = launch { hub.publish(listOf(user), update()) }
            reached.await()
            publishing.cancelAndJoin()

            // The request went away; that says nothing about the socket, which is still open
            // and still how this player hears about the next move.
            assertEquals(1, hub.connectionCount(user), "a cancelled publish is not evidence of a dead client")
        }

    private fun update(): RealtimeMessage = RealtimeMessage.gameUpdated(Uuid.random(), version = 1)

    private companion object {
        /** Long enough that a working send is never mistaken for a stalled one. */
        val PATIENCE = 10.seconds

        /** Short enough that a test waiting out the deadline is not a slow test. */
        val SHORT_DEADLINE = 200.milliseconds

        const val THREE_PEOPLE = 3
    }
}
