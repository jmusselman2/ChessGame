@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.user

import com.jmussel.chessgame.server.db.UserRepository
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Records that a user was active, without turning every request into a write.
 *
 * Every authenticated request is meaningful activity — a move, an undo, opening the app
 * (`PRODUCT.md`). Writing `last_seen_at` on each one would be the continuous heartbeat
 * `D010` rules out, so a write happens at most once per [throttle] per user and the rest
 * are dropped. The stored value is therefore accurate to within [throttle], which is all
 * anything needs it for.
 */
class LastSeenTracker(
    private val users: UserRepository,
    private val throttle: Duration = DEFAULT_THROTTLE,
    private val clock: () -> Instant = Instant::now,
) {
    private val lastWritten = ConcurrentHashMap<Uuid, Instant>()

    /**
     * Notes activity by [userId], writing it through only if the last write was long
     * enough ago. Returns whether it wrote.
     */
    fun record(userId: Uuid): Boolean {
        val now = clock()
        val previous = lastWritten[userId]

        if (previous != null && Duration.between(previous, now) < throttle) return false

        // Whoever wins this replacement does the write; a concurrent caller sees the new
        // timestamp and skips.
        val won =
            if (previous == null) {
                lastWritten.putIfAbsent(userId, now) == null
            } else {
                lastWritten.replace(userId, previous, now)
            }

        if (!won) return false

        users.touchLastSeen(userId, now)
        return true
    }

    /** Forgets what has been written, so the next activity writes again. */
    fun reset() = lastWritten.clear()

    companion object {
        /** How long to wait between writes for one user. */
        val DEFAULT_THROTTLE: Duration = Duration.ofMinutes(5)
    }
}
