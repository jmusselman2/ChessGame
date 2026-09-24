package com.jmussel.chessgame.app

import com.jmussel.chessgame.api.ChessApiClient
import com.jmussel.chessgame.api.ChessServerConfig
import com.jmussel.chessgame.api.isProbablyAsleep
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A request that never finishes, through the app's real HTTP client (`M17.8`).
 *
 * Startup sat on "Waking the server…" for minutes on a Pixel 7 while the server answered
 * `/health` at once, and "Try again" worked immediately. `withServerWake` checks its
 * deadline only between attempts, so one request that never ended was enough.
 *
 * A real OkHttp client and a real socket are used, because the gap is in the transport.
 * OkHttp's read timeout covers only the silence *between* two reads. A peer that sends one
 * byte a little more often than that never trips it, and neither does anything else OkHttp
 * sets by default. [TricklingPeer] does exactly that and never finishes the reply.
 */
class StalledRequestTest {
    @Test
    fun aReplyThatNeverFinishesIsCutOffAndLooksLikeASleepingServer() {
        TricklingPeer().use { peer ->
            val failure = requestFrom(peer, requestTimeout = REQUEST_TIMEOUT)

            assertTrue(
                "the request should fail on its own time limit, not the test's: $failure",
                failure is HttpRequestTimeoutException,
            )
            // A transport failure, so startup and reloads wait through it (`D037`) rather
            // than reporting it as the server's answer.
            assertTrue(isProbablyAsleep(failure!!))
        }
    }

    @Test
    fun withoutTheLimitTheSameReplyIsWaitedOnForever() {
        TricklingPeer().use { peer ->
            // The app as it was: no overall limit, only OkHttp's defaults.
            val failure = requestFrom(peer, requestTimeout = Duration.INFINITE)

            assertNull("without a limit the request is still waiting when the budget runs out", failure)
        }
    }

    /**
     * Asks [peer] who the caller is, and returns how the request failed, or `null` if it
     * was still waiting when [BUDGET] ran out — which is the defect, not a slow test.
     */
    private fun requestFrom(
        peer: TricklingPeer,
        requestTimeout: Duration,
    ): Throwable? =
        ChessAppDependencies.defaultHttpClient(requestTimeout = requestTimeout).use { httpClient ->
            val api =
                ChessApiClient(
                    config = ChessServerConfig(baseUrl = peer.baseUrl, allowCleartext = true),
                    httpClient = httpClient,
                    accessToken = { "a-token" },
                )

            runBlocking {
                withTimeoutOrNull(BUDGET.inWholeMilliseconds) {
                    runCatching { api.me() }.exceptionOrNull() ?: AssertionError("the peer never answers")
                }
            }
        }

    private companion object {
        /** Short enough for a test, and longer than the gap between the peer's bytes. */
        val REQUEST_TIMEOUT = 1_000.milliseconds

        /** Several times the limit, so only a request that is never cut off runs it out. */
        val BUDGET = 4_000.milliseconds
    }
}

/**
 * An HTTP peer that starts a reply and never finishes it.
 *
 * It reads the request, sends a status line, and then sends one byte of a header that
 * never ends, every [BYTE_INTERVAL_MILLIS]. That is well inside OkHttp's ten-second read
 * timeout, so the client always has just enough to keep waiting.
 */
private class TricklingPeer : AutoCloseable {
    private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())

    @Volatile
    private var connection: Socket? = null

    val baseUrl: String get() = "http://${server.inetAddress.hostAddress}:${server.localPort}"

    private val listener =
        thread(isDaemon = true, name = "trickling-peer") {
            runCatching {
                server.accept().also { connection = it }.use { socket ->
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
                    while (!reader.readLine().isNullOrEmpty()) Unit

                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nX-Never-Ends: ".toByteArray(Charsets.ISO_8859_1))
                        flush()

                        while (true) {
                            Thread.sleep(BYTE_INTERVAL_MILLIS)
                            write('.'.code)
                            flush()
                        }
                    }
                }
            }
        }

    override fun close() {
        listener.interrupt()
        runCatching { connection?.close() }
        runCatching { server.close() }
    }

    private companion object {
        const val BYTE_INTERVAL_MILLIS = 200L
    }
}
