@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.realtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Independent M12 probe for isolation between realtime recipients. */
class M12AdversarialTest {
    @Test
    fun aStalledConnectionCannotPreventAnotherUserReceivingTheUpdate() =
        runBlocking {
            val hub = RealtimeHub()
            val stalledUser = Uuid.random()
            val reachableUser = Uuid.random()
            val stalledSendReached = CompletableDeferred<Unit>()
            val reachableDelivered = CompletableDeferred<Unit>()

            hub.subscribe(stalledUser) {
                stalledSendReached.complete(Unit)
                awaitCancellation()
            }
            hub.subscribe(reachableUser) { reachableDelivered.complete(Unit) }

            val publishing =
                launch {
                    hub.publish(
                        userIds = listOf(stalledUser, reachableUser),
                        message = RealtimeMessage.gameUpdated(Uuid.random(), version = 1),
                    )
                }

            try {
                stalledSendReached.await()
                val arrived = withTimeoutOrNull(500) { reachableDelivered.await() }
                assertTrue(arrived != null, "one stalled socket blocked delivery to the next user")
            } finally {
                publishing.cancelAndJoin()
            }
        }
}
