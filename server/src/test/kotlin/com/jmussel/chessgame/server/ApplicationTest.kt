package com.jmussel.chessgame.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApplicationTest {
    @Test
    fun healthEndpointReturnsOk() =
        testApplication {
            application {
                healthModule()
            }

            val response = client.get("/health")

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("ChessGame"))
        }

    @Test
    fun healthOnlyModeDoesNotExposeDatabaseHealth() =
        testApplication {
            application { healthModule() }

            val health = client.get("/health")
            val database = client.get("/health/database")

            assertEquals(HttpStatusCode.OK, health.status)
            assertTrue(health.bodyAsText().contains("ChessGame"))
            assertFalse(health.headers.contains("Cache-Control", "no-store"))
            assertEquals(HttpStatusCode.NotFound, database.status)
        }
}
