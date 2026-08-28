@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.auth

import com.jmussel.chessgame.server.api.CurrentUser
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.testModule
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The authenticated edge of the server: a verified Supabase token, and only a verified
 * one, resolves to an internal user id.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class AuthenticatedRouteTest {
    private val tokens = TestTokens()

    private fun withServer(block: suspend ApplicationTestBuilder.(UserRepository) -> Unit) {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val users = UserRepository(database)
            testApplication {
                application {
                    testModule(tokens.verifier(), database)
                }
                block(users)
            }
        }
    }

    @Test
    fun healthNeedsNoToken() {
        withServer {
            val response = client.get("/health")

            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun aRequestWithNoTokenIsRefused() {
        withServer {
            assertEquals(HttpStatusCode.Unauthorized, client.get("/me").status)
        }
    }

    @Test
    fun aRequestWithSomethingOtherThanABearerTokenIsRefused() {
        withServer {
            val response = client.get("/me") { header("Authorization", "Basic dXNlcjpwYXNz") }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun aForgedTokenIsRefused() {
        withServer {
            val response =
                client.get("/me") {
                    header("Authorization", "Bearer ${tokens.tokenFromAnotherKey("auth-1")}")
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun anExpiredTokenIsRefused() {
        withServer {
            val expired = tokens.tokenFor("auth-1", expiresAt = Instant.now().minusSeconds(60))
            val response = client.get("/me") { header("Authorization", "Bearer $expired") }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun aGenuineTokenResolvesToAnInternalUserId() {
        withServer { users ->
            val response =
                client.get("/me") { header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}") }

            assertEquals(HttpStatusCode.OK, response.status)

            val userId = Uuid.parse(response.currentUser().userId)
            val stored = users.find(userId)

            assertEquals("auth-1", stored?.authSubject)
            assertTrue(stored?.username == null, "a new account has not claimed a username yet")
        }
    }

    @Test
    fun theSameAccountKeepsTheSameInternalUserId() {
        withServer {
            val first = client.get("/me") { header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}") }
            val second = client.get("/me") { header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}") }

            assertEquals(first.bodyAsText(), second.bodyAsText())
        }
    }

    @Test
    fun differentAccountsResolveToDifferentUsers() {
        withServer {
            val first = client.get("/me") { header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}") }
            val second = client.get("/me") { header("Authorization", "Bearer ${tokens.tokenFor("auth-2")}") }

            assertNotEquals(first.bodyAsText(), second.bodyAsText())
        }
    }

    @Test
    fun theUserRowIsCreatedOnceForAnAccount() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val users = UserRepository(Databases.connect(dataSource))

            val first = users.resolveBySubject("auth-1")
            val again = users.resolveBySubject("auth-1")

            assertEquals(first.id, again.id)
            assertEquals("auth-1", first.authSubject)
        }
    }
}

/** `GET /me` as the app reads it. */
private suspend fun HttpResponse.currentUser(): CurrentUser = Json.decodeFromString(bodyAsText())
