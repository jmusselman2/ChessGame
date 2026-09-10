@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.realtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A message the server pushes to a connected client. */
@Serializable
data class RealtimeMessage(
    val type: String,
    val gameId: String? = null,
    val version: Long? = null,
) {
    companion object {
        /** Sent once when a connection is accepted, so a client knows it is live. */
        const val CONNECTED: String = "connected"

        /**
         * Sent when a game the client plays in has changed. It carries only the identity and
         * version — the canonical state is fetched over HTTPS, because realtime delivery is
         * a convenience layer and not the source of truth (`D022`).
         */
        const val GAME_UPDATED: String = "game-updated"

        fun connected(): RealtimeMessage = RealtimeMessage(type = CONNECTED)

        fun gameUpdated(
            gameId: Uuid,
            version: Long,
        ): RealtimeMessage =
            RealtimeMessage(
                type = GAME_UPDATED,
                gameId = gameId.toString(),
                version = version,
            )
    }
}

/** One client's open connection, as the hub sees it. */
fun interface RealtimeConnection {
    /** Delivers [message], or throws if the connection has gone. */
    suspend fun send(message: RealtimeMessage)
}

/**
 * Who is currently connected, and how to reach them.
 *
 * A user may have several connections at once — two devices, or an old one that has not
 * noticed it is gone — so subscriptions are held per user as a set. Delivery is
 * best-effort: a connection that fails is dropped rather than retried, because the client
 * reloads canonical state over HTTPS on reconnect (`D022`) and nothing here is the source
 * of truth.
 */
class RealtimeHub(
    /** How long one connection has to accept a message before it is dropped. */
    private val sendTimeout: Duration = realtimeSendTimeout,
) {
    private val connections = ConcurrentHashMap<Uuid, MutableSet<RealtimeConnection>>()

    /** Registers [connection] for [userId] until [unsubscribe]. */
    fun subscribe(
        userId: Uuid,
        connection: RealtimeConnection,
    ) {
        connections.compute(userId) { _, existing ->
            (existing ?: ConcurrentHashMap.newKeySet()).apply { add(connection) }
        }
    }

    /** Forgets [connection]. */
    fun unsubscribe(
        userId: Uuid,
        connection: RealtimeConnection,
    ) {
        connections.computeIfPresent(userId) { _, existing ->
            existing.remove(connection)
            existing.ifEmpty { null }
        }
    }

    /** How many connections [userId] has open. */
    fun connectionCount(userId: Uuid): Int = connections[userId]?.size ?: 0

    /**
     * Sends [message] to every connection [userIds] have open, dropping any that fail.
     *
     * Every connection is attempted **at the same time and on its own deadline**. Sending
     * to one after another looks harmless — a dead socket throws and is dropped — but a
     * socket that is neither dead nor draining does not throw: it suspends, under
     * backpressure or on a transport that has gone half-open without closing. Awaiting that
     * one in a loop delivers nothing to anyone behind it, and because the game routes await
     * this before they respond, it also withholds the response to the command that has
     * already committed — so the player who moved is left retrying a move the server took
     * (`M12-01`).
     *
     * A child coroutine per connection means one slow send cannot reach another, and the
     * send timeout means it cannot reach the caller either: the send is abandoned and the
     * connection dropped, which is what best-effort delivery already promises for a
     * connection that fails outright (`D022`). Publishing still finishes before the
     * response — the point is that "finishes" is now bounded by one deadline rather than by
     * the slowest client — and a client dropped this way reloads canonical state over HTTPS
     * when it reconnects.
     *
     * Cancellation of the request that is publishing stays cancellation: it is rethrown
     * rather than filed as a dead socket, so a cancelled publish does not unsubscribe
     * connections that were never given their chance.
     */
    suspend fun publish(
        userIds: Collection<Uuid>,
        message: RealtimeMessage,
    ) {
        val recipients =
            userIds.distinct().flatMap { userId ->
                connections[userId]?.toList().orEmpty().map { connection -> userId to connection }
            }

        if (recipients.isEmpty()) return

        coroutineScope {
            recipients.forEach { (userId, connection) ->
                launch {
                    try {
                        withTimeout(sendTimeout) { connection.send(message) }
                    } catch (_: TimeoutCancellationException) {
                        // Suspended past its deadline: as far as delivery goes, gone.
                        unsubscribe(userId, connection)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // The client has gone; it will catch up over HTTPS when it returns.
                        unsubscribe(userId, connection)
                    }
                }
            }
        }
    }
}

/**
 * How long one connection has to accept a realtime message before it is dropped.
 *
 * A nudge carrying an id and a version is a few dozen bytes, so a connection that has not
 * taken it in five seconds is not slow, it is not there. The deadline exists to bound the
 * command response that waits on publishing, so it is far shorter than the pong timeout
 * that decides whether the *connection* is dead (`webSocketPongTimeout`): dropping a
 * connection here costs the client one reload over HTTPS, which it does on reconnect
 * anyway (`D022`).
 *
 * A [RealtimeHub] takes it rather than reading it, so a test can assert what the deadline
 * does without waiting five seconds for it.
 */
val realtimeSendTimeout: Duration = 5.seconds
