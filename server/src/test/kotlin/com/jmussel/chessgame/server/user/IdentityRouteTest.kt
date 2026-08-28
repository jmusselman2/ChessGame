@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.user

import com.jmussel.chessgame.server.api.CurrentUser
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.testModule
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi

/**
 * `GET /me`: who the caller is, and whether they have a name yet.
 *
 * This is the question the app asks on startup, and the answer decides whether the player
 * sees onboarding or the dashboard. Skipped when this machine has no test database (see
 * [DatabaseTestSupport]).
 */
class IdentityRouteTest {
    private val tokens = TestTokens()

    private val json = Json { ignoreUnknownKeys = true }

    private fun withServer(block: suspend ApplicationTestBuilder.(UserRepository) -> Unit) {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            testApplication {
                application {
                    testModule(tokens.verifier(), database)
                }
                block(UserRepository(database))
            }
        }
    }

    private suspend fun ApplicationTestBuilder.me(subject: String): CurrentUser {
        val response = client.get("/me") { header("Authorization", "Bearer ${tokens.tokenFor(subject)}") }

        assertEquals(HttpStatusCode.OK, response.status)
        return json.decodeFromString(response.bodyAsText())
    }

    @Test
    fun aNewAccountHasAnIdAndNoUsername() {
        withServer {
            val me = me("auth-1")

            assertNotEquals("", me.userId)
            assertNull(me.username, "a new anonymous account has not chosen a name yet")
        }
    }

    @Test
    fun aNamedUserIsReportedWithTheirUsername() {
        withServer {
            val before = me("auth-1")

            val claim =
                client.post("/username") {
                    header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}")
                    setBody("Jordan")
                }
            assertEquals(HttpStatusCode.OK, claim.status)

            val after = me("auth-1")

            assertEquals("Jordan", after.username)
            assertEquals(before.userId, after.userId, "claiming a name does not change who you are")
        }
    }

    @Test
    fun theSameAccountIsAlwaysTheSameUser() {
        withServer {
            assertEquals(me("auth-1").userId, me("auth-1").userId)
        }
    }

    @Test
    fun differentAccountsAreDifferentUsers() {
        withServer {
            assertNotEquals(me("auth-1").userId, me("auth-2").userId)
        }
    }

    @Test
    fun anUnauthenticatedRequestIsRefused() {
        withServer {
            assertEquals(HttpStatusCode.Unauthorized, client.get("/me").status)
        }
    }

    @Test
    fun anUnverifiableTokenIsRefused() {
        withServer {
            val response = client.get("/me") { header("Authorization", "Bearer not-a-token") }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }
}
