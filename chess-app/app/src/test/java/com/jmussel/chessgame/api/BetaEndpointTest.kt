package com.jmussel.chessgame.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pointing a build at the deployed beta server, and refusing to point a beta build anywhere
 * unencrypted (`M15.4`).
 *
 * The address itself is build configuration, never a literal in application source, so what
 * is checked here is the rule the configuration has to satisfy rather than the value.
 */
class BetaEndpointTest {
    private val betaUrl = "https://chessgame-hit7.onrender.com"

    @Test
    fun `a development build keeps the emulator loopback default`() {
        val development = ChessServerConfig()

        assertEquals("http://10.0.2.2:8080", development.baseUrl)
        assertFalse(development.isSecure)
    }

    @Test
    fun `the beta address is https and its socket is wss`() {
        val beta = ChessServerConfig(baseUrl = betaUrl, allowCleartext = false)

        assertTrue(beta.isSecure)
        assertEquals("$betaUrl/games/game-1", beta.url("/games/game-1"))
        // The socket is exactly as protected as the rest of the traffic: a token travels on
        // it too (`D033`).
        assertEquals("wss://chessgame-hit7.onrender.com/updates", beta.webSocketUrl("/updates"))
    }

    @Test
    fun `a build that forbids cleartext refuses a plain http address`() {
        val thrown =
            runCatching {
                ChessServerConfig(baseUrl = "http://10.0.2.2:8080", allowCleartext = false)
            }.exceptionOrNull()

        // This is the shape of forgetting -PchessServerUrl on a beta build. Without the
        // refusal the APK installs, launches, and fails every request against a network
        // security configuration that forbids cleartext, explaining nothing.
        assertTrue(thrown is IllegalArgumentException)
        assertTrue(
            "the refusal should say how to fix it",
            thrown?.message?.contains("-PchessServerUrl") == true,
        )
    }

    @Test
    fun `a debug build may still reach a development server in the clear`() {
        val debug = ChessServerConfig(baseUrl = "http://localhost:8080", allowCleartext = true)

        assertEquals("ws://localhost:8080/updates", debug.webSocketUrl("/updates"))
    }

    @Test
    fun `a trailing slash on the beta address does not double up`() {
        val beta = ChessServerConfig(baseUrl = "$betaUrl/", allowCleartext = false)

        assertEquals("$betaUrl/me", beta.url("/me"))
        assertEquals("wss://chessgame-hit7.onrender.com/updates", beta.webSocketUrl("updates"))
    }
}
