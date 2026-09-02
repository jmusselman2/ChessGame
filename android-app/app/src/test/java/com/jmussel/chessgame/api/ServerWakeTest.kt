package com.jmussel.chessgame.api

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The retry that lets a sleeping free instance wake up without looking broken (`M15.4`).
 *
 * `runTest` skips the delays, so a policy whose real deadline is well over two minutes is
 * exercised in milliseconds and the assertions are about the decisions, not the clock. Time
 * is injected, so "how long has this been going" is a fact of the test rather than of how
 * fast the machine running it happens to be.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ServerWakeTest {
    @Test
    fun `backoff grows and then stops at the cap`() {
        val policy =
            ServerWakePolicy(
                deadlineMillis = 100_000,
                initialDelayMillis = 1_000,
                maxDelayMillis = 8_000,
                multiplier = 2.0,
            )

        assertEquals(1_000L, policy.delayAfter(1))
        assertEquals(2_000L, policy.delayAfter(2))
        assertEquals(4_000L, policy.delayAfter(3))
        assertEquals(8_000L, policy.delayAfter(4))
        // Capped from here on, however many failures there have been. A single enormous
        // sleep would overshoot the deadline and leave the player waiting with nothing said.
        assertEquals(8_000L, policy.delayAfter(5))
        assertEquals(8_000L, policy.delayAfter(40))
        assertEquals(8_000L, policy.delayAfter(400))
    }

    @Test
    fun `the default policy allows comfortably more than the measured cold start`() {
        val policy = ServerWakePolicy()

        // M15.2 measured 59.0s and 64.5s. D032 records that Render publishes no guaranteed
        // maximum, so the deadline is generous against what was seen rather than fitted to
        // it -- but it must at least clear it, or the beta's normal first request fails.
        assertTrue(
            "the deadline must clear the measured cold starts",
            policy.deadlineMillis > 65_000,
        )
        assertTrue(policy.mayTryAgainAfter(64_500))
        assertFalse(policy.mayTryAgainAfter(policy.deadlineMillis))
    }

    @Test
    fun `a policy that could shrink or start at zero is rejected`() {
        assertThrows { ServerWakePolicy(deadlineMillis = 0) }
        assertThrows { ServerWakePolicy(initialDelayMillis = 0) }
        assertThrows { ServerWakePolicy(multiplier = 0.5) }
        assertThrows { ServerWakePolicy(initialDelayMillis = 5_000, maxDelayMillis = 1_000) }
    }

    @Test
    fun `a request that succeeds first time is made once and not waited on`() =
        runTest {
            var attempts = 0
            var wakings = 0

            val answer =
                withServerWake(onWaking = { wakings += 1 }) {
                    attempts += 1
                    "played"
                }

            assertEquals("played", answer)
            assertEquals(1, attempts)
            assertEquals(0, wakings)
        }

    @Test
    fun `a sleeping server is waited through and the answer is returned`() =
        runTest {
            var attempts = 0
            val waking = mutableListOf<ServerWaking>()
            var now = 0L

            val answer =
                withServerWake(
                    policy = ServerWakePolicy(deadlineMillis = 100_000),
                    elapsedMillis = { now },
                    onWaking = { waking += it },
                ) {
                    attempts += 1
                    // Three failures, as a cold start looks from the client, then the
                    // instance is up and answers.
                    now += 1_000
                    if (attempts < 4) throw IOException("connection refused") else "awake"
                }

            assertEquals("awake", answer)
            assertEquals(4, attempts)
            // Reported before each wait, so a screen can say what is happening while it
            // happens rather than only at the end.
            assertEquals(listOf(1, 2, 3), waking.map { it.failures })
        }

    @Test
    fun `a refusal the server actually issued is not retried`() =
        runTest {
            var attempts = 0
            val refusal = ChessApiException(status = 403, explanation = "not your game", message = "refused")

            val thrown =
                runCatching {
                    withServerWake {
                        attempts += 1
                        throw refusal
                    }
                }.exceptionOrNull()

            // The server answered, so it is awake and has decided. Asking again would only
            // get the same answer more slowly.
            assertSame(refusal, thrown)
            assertEquals(1, attempts)
        }

    @Test
    fun `a refused command is not retried either`() =
        runTest {
            var attempts = 0
            val refusal =
                ChessCommandRefusedException(
                    status = 409,
                    rejection = CommandRejectionDto(reason = "STALE_VERSION", message = "moved on"),
                )

            runCatching {
                withServerWake {
                    attempts += 1
                    throw refusal
                }
            }

            assertEquals(1, attempts)
        }

    @Test
    fun `giving up rethrows the last failure once the deadline is reached`() =
        runTest {
            var attempts = 0
            var now = 0L

            val thrown =
                runCatching {
                    withServerWake(
                        policy =
                            ServerWakePolicy(
                                deadlineMillis = 10_000,
                                initialDelayMillis = 1_000,
                                maxDelayMillis = 4_000,
                            ),
                        elapsedMillis = { now },
                    ) {
                        attempts += 1
                        now += 1_000
                        throw IOException("still down")
                    }
                }.exceptionOrNull()

            assertTrue(thrown is IOException)
            // It stops rather than running forever, and it stops before the deadline rather
            // than sailing past it on one last long sleep.
            assertTrue("should have tried more than once", attempts > 1)
            assertTrue("should not retry forever", attempts < 20)
        }

    @Test
    fun `an unreachable host is treated as a server that might be asleep`() {
        assertTrue(isProbablyAsleep(IOException("no route to host")))
        assertTrue(isProbablyAsleep(RuntimeException("timeout")))
        assertFalse(isProbablyAsleep(ChessApiException(status = 500, explanation = "", message = "")))
        assertFalse(
            isProbablyAsleep(
                ChessCommandRefusedException(status = 409, rejection = CommandRejectionDto()),
            ),
        )
    }

    private fun assertThrows(block: () -> Unit) {
        val thrown = runCatching(block).exceptionOrNull()

        assertTrue("expected the policy to be rejected", thrown is IllegalArgumentException)
    }
}
