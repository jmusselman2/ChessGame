package com.jmussel.chessgame.server

import com.jmussel.chessgame.core.GameCore
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a host needs from this process to run it.
 *
 * The port it listens on and what `/health` says are the two things a deployment depends
 * on from outside the process, and neither is exercised by a test that starts the server
 * in-memory on a port of its own choosing (`M15.2`).
 */
class DeploymentTest {
    @Test
    fun listensOnThePortTheEnvironmentAsksFor() {
        assertEquals(10000, serverPort("10000"))
    }

    @Test
    fun fallsBackToTheLocalPortWhenNothingSetsOne() {
        assertEquals(DEFAULT_PORT, serverPort(null))
        assertEquals(DEFAULT_PORT, serverPort(""))
        assertEquals(DEFAULT_PORT, serverPort("   "))
    }

    @Test
    fun ignoresSurroundingWhitespace() {
        assertEquals(10000, serverPort(" 10000\n"))
    }

    @Test
    fun refusesAValueThatIsNotAPort() {
        // Falling back would bind the wrong port and then fail as an unexplained missing
        // port, long after the value that caused it scrolled past.
        listOf("http://localhost:8080", "eight thousand", "0", "-1", "65536", "8080.0")
            .forEach { value ->
                val failure = assertFailsWith<IllegalArgumentException> { serverPort(value) }

                assertTrue(
                    value in failure.message.orEmpty(),
                    "The failure should name the value it refused, but said: ${failure.message}",
                )
            }
    }

    @Test
    fun healthSaysNothingUnusualWhenTheServerIsFullyConfigured() {
        val text = healthText(healthOnly = false)

        assertTrue(text.contains("healthy"))
        assertFalse(text.contains("health-only"))
    }

    // --- Which build is answering (`M17.2`) --------------------------------------------

    @Test
    fun healthNamesTheCommitItWasDeployedFrom() {
        val text = healthText(healthOnly = false, commit = shortCommit("a8acce7f2b1c4d5e6a7b8c9d0e1f2a3b4c5d6e7f"))

        assertTrue(text.contains("healthy"), text)
        // Short enough to read back, and it is the front of the SHA rather than any of it.
        assertTrue(text.contains("build a8acce7"), text)
        assertFalse(text.contains("f2b1c4d"), text)
    }

    @Test
    fun healthSaysNothingAboutABuildWhenTheHostNamesNone() {
        // Every local run and every test: absent means absent, not "build unknown", so the
        // body is exactly what it has always been.
        assertEquals(
            healthText(healthOnly = false, commit = null),
            "${GameCore.NAME} server is healthy",
        )
    }

    @Test
    fun aDegradedServerStillNamesItsBuild() {
        // The two answers are independent: a deploy that came up without its environment is
        // the case where knowing which commit did that matters most.
        val text = healthText(healthOnly = true, commit = shortCommit("0da5f42b7deacde"))

        assertTrue(text.contains("health-only"), text)
        assertTrue(text.contains("build 0da5f42"), text)
    }

    @Test
    fun aCommitThatIsBlankOrMissingIsNoCommitAtAll() {
        // Render sets the variable on a deploy; a host that sets it empty must not produce
        // "(build )", which reads like a bug in the server rather than an absent value.
        assertNull(shortCommit(null))
        assertNull(shortCommit(""))
        assertNull(shortCommit("   "))
    }

    @Test
    fun healthNamesTheMissingConfigurationWhenTheServerCameUpWithoutIt() =
        testApplication {
            application { healthModule() }

            val response = client.get("/health")
            val body = response.bodyAsText()

            // A host watching /health calls a health-only server a successful deploy, so the
            // body has to be the thing that says otherwise.
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(body.contains("health-only"), body)
            assertTrue(body.contains("DATABASE_URL"), body)
            assertTrue(body.contains("SUPABASE_URL"), body)
        }
}
