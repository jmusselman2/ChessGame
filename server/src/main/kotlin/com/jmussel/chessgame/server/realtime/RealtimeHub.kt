@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.realtime

import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap
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
class RealtimeHub {
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

    /** Sends [message] to every connection [userIds] have open, dropping any that fail. */
    suspend fun publish(
        userIds: Collection<Uuid>,
        message: RealtimeMessage,
    ) {
        userIds.distinct().forEach { userId ->
            connections[userId]?.toList()?.forEach { connection ->
                try {
                    connection.send(message)
                } catch (_: Exception) {
                    // The client has gone; it will catch up over HTTPS when it returns.
                    unsubscribe(userId, connection)
                }
            }
        }
    }
}
