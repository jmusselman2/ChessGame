package com.jmussel.chessgame.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * How long to keep trying a safe request while the server might merely be asleep.
 *
 * The beta runs on a free instance that spins down after about fifteen idle minutes, and
 * the first request afterwards pays a cold start — measured at 59.0 s and 64.5 s during
 * `M15.2`. A single attempt with an ordinary timeout would therefore turn "nobody has
 * played for a while" into "the server is broken", which is the failure this exists to
 * prevent.
 *
 * The measurements inform the defaults and are explicitly not treated as a bound: `D032`
 * records that Render publishes no guaranteed maximum, so [deadlineMillis] is generous
 * against the ~60 s that was actually observed rather than fitted to it, and every value
 * is a constructor parameter so a build or a test can choose its own.
 *
 * Backoff is exponential and **capped**: waits grow until [maxDelayMillis] and then stay
 * there, so a long wake is a steady poll rather than one enormous final sleep that would
 * overshoot the deadline and leave the player staring at nothing.
 */
data class ServerWakePolicy(
    /** Total time to keep trying before giving up and calling it a failure. */
    val deadlineMillis: Long = DEFAULT_DEADLINE_MILLIS,
    /** How long to wait after the first failure. */
    val initialDelayMillis: Long = DEFAULT_INITIAL_DELAY_MILLIS,
    /** The longest any single wait may become, however many failures there have been. */
    val maxDelayMillis: Long = DEFAULT_MAX_DELAY_MILLIS,
    /** What each wait is multiplied by after a failure. */
    val multiplier: Double = DEFAULT_MULTIPLIER,
) {
    init {
        require(deadlineMillis > 0) { "The deadline must be positive" }
        require(initialDelayMillis > 0) { "The first delay must be positive" }
        require(maxDelayMillis >= initialDelayMillis) { "The delay cap must not be below the first delay" }
        require(multiplier >= 1.0) { "Backoff must not shrink" }
    }

    /**
     * How long to wait after [failures] failed attempts, capped at [maxDelayMillis].
     *
     * [failures] counts attempts that have already failed, so the wait after the first is
     * [initialDelayMillis]. Computed in `Double` and clamped, so a long wake cannot
     * overflow its way to a negative delay.
     */
    fun delayAfter(failures: Int): Long {
        require(failures >= 1) { "There is nothing to back off from before the first failure" }

        val grown = initialDelayMillis * Math.pow(multiplier, (failures - 1).toDouble())

        return grown.coerceAtMost(maxDelayMillis.toDouble()).toLong().coerceAtLeast(initialDelayMillis)
    }

    /** Whether another attempt is allowed once [elapsedMillis] has already been spent. */
    fun mayTryAgainAfter(elapsedMillis: Long): Boolean = elapsedMillis < deadlineMillis

    companion object {
        /**
         * Comfortably beyond the ~60 s cold starts measured in `M15.2`, without being so
         * long that a genuinely dead server keeps a player waiting indefinitely.
         */
        const val DEFAULT_DEADLINE_MILLIS: Long = 150_000L

        const val DEFAULT_INITIAL_DELAY_MILLIS: Long = 1_000L
        const val DEFAULT_MAX_DELAY_MILLIS: Long = 8_000L
        const val DEFAULT_MULTIPLIER: Double = 2.0
    }
}

/**
 * What is happening while a safe request is being retried.
 *
 * The point of this type is the distinction the player sees: a service that is waking is
 * not an error, and saying so is what stops a beta tester reporting a cold start as a bug.
 */
data class ServerWaking(
    /** How many attempts have already failed. */
    val failures: Int,
    /** How long the retrying has been going on. */
    val elapsedMillis: Long,
)

/**
 * Runs a **safe, repeatable** [attempt] until it succeeds or [policy]'s deadline passes.
 *
 * Only ever call this for a request that can be issued twice with no consequence — a
 * startup probe or a canonical reload. A mutating command must not come through here: it
 * carries the version it was decided against, and a blind retry belongs to the
 * version/idempotency rules on the server rather than to a client loop (`D021`).
 *
 * [onWaking] is called before each wait so a screen can say the server is being woken. A
 * failure is only reported once retrying has actually been given up on, so the caller's
 * error path is reached with the last failure, not the first.
 *
 * [isWorthRetrying] decides which failures are worth waiting through. The default retries
 * anything that is not the app's own fault: a refusal the server actually issued is an
 * answer and is rethrown at once, while an unreachable host, a dropped connection, or a
 * timeout is exactly what a sleeping instance looks like.
 */
suspend fun <T> withServerWake(
    policy: ServerWakePolicy = ServerWakePolicy(),
    elapsedMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    isWorthRetrying: (Throwable) -> Boolean = ::isProbablyAsleep,
    onWaking: (ServerWaking) -> Unit = {},
    attempt: suspend () -> T,
): T {
    val startedAt = elapsedMillis()
    var failures = 0

    while (true) {
        try {
            return attempt()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            if (!isWorthRetrying(failure)) throw failure

            failures += 1

            val elapsed = elapsedMillis() - startedAt
            val wait = policy.delayAfter(failures)

            // Give up when the deadline has passed, or when waiting would only carry us
            // past it — a further attempt then costs the player time and answers nothing.
            if (!policy.mayTryAgainAfter(elapsed + wait)) throw failure

            onWaking(ServerWaking(failures = failures, elapsedMillis = elapsed))

            delay(wait)
        }
    }
}

/**
 * Whether [failure] looks like a server that is asleep rather than one that has answered.
 *
 * A [ChessApiException] or a [ChessCommandRefusedException] means the server replied, so it
 * is awake and has made a decision — retrying would only ask a question that has been
 * answered. Everything else is a transport failure of some kind, which is what a cold start
 * looks like from the client, and is worth waiting through.
 */
fun isProbablyAsleep(failure: Throwable): Boolean =
    when (failure) {
        is ChessApiException -> false
        is ChessCommandRefusedException -> false
        else -> true
    }
