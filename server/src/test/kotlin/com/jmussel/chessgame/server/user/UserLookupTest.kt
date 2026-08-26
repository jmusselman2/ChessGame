@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.user

import com.jmussel.chessgame.server.api.UserSummary
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.module
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi

/**
 * Finding a friend by their exact username.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class UserLookupTest {
    private val tokens = TestTokens()
    private val json = Json { ignoreUnknownKeys = true }

    private fun withServer(block: suspend ApplicationTestBuilder.(UserRepository) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val users = UserRepository(Databases.connect(dataSource))
            testApplication {
                application { module(tokens.verifier(), users) }
                block(users)
            }
        }

    private fun ApplicationTestBuilder.asCaller(subject: String = "auth-caller") = tokens.tokenFor(subject)

    @Test
    fun anExactNameFindsTheUser() {
        withServer { users ->
            val friend = users.resolveBySubject("auth-friend")
            users.claimUsername(friend.id, Username.of("Jordan"))

            val response =
                client.get("/users/Jordan") { header("Authorization", "Bearer ${asCaller()}") }

            assertEquals(HttpStatusCode.OK, response.status)

            val found = json.decodeFromString<UserSummary>(response.bodyAsText())

            assertEquals("Jordan", found.username)
            assertEquals(friend.id.toString(), found.userId)
        }
    }

    @Test
    fun theLookupIsCaseInsensitive() {
        withServer { users ->
            val friend = users.resolveBySubject("auth-friend")
            users.claimUsername(friend.id, Username.of("Jordan"))

            listOf("jordan", "JORDAN", "jOrDaN").forEach { spelling ->
                val response =
                    client.get("/users/$spelling") { header("Authorization", "Bearer ${asCaller()}") }

                assertEquals(HttpStatusCode.OK, response.status, "looking up '$spelling'")
                assertEquals(
                    "Jordan",
                    json.decodeFromString<UserSummary>(response.bodyAsText()).username,
                    "the spelling the owner chose is what comes back",
                )
            }
        }
    }

    @Test
    fun anUnknownNameIsNotFound() {
        withServer {
            val response =
                client.get("/users/nobody") { header("Authorization", "Bearer ${asCaller()}") }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @Test
    fun aUserWhoHasNotClaimedANameCannotBeFound() {
        withServer { users ->
            users.resolveBySubject("auth-nameless")

            val response =
                client.get("/users/nameless") { header("Authorization", "Bearer ${asCaller()}") }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @Test
    fun thereIsNoPartialMatching() {
        withServer { users ->
            val friend = users.resolveBySubject("auth-friend")
            users.claimUsername(friend.id, Username.of("Jordan"))

            listOf("Jord", "ordan", "Jordan1").forEach { partial ->
                val response =
                    client.get("/users/$partial") { header("Authorization", "Bearer ${asCaller()}") }

                assertEquals(HttpStatusCode.NotFound, response.status, "'$partial' should not match")
            }
        }
    }

    @Test
    fun somethingThatIsNotAUsernameIsRefused() {
        withServer {
            val response =
                client.get("/users/ab") { header("Authorization", "Bearer ${asCaller()}") }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun theLookupNeedsAToken() {
        withServer {
            assertEquals(HttpStatusCode.Unauthorized, client.get("/users/jordan").status)
        }
    }

    @Test
    fun theResultTellsTheCallerNothingPrivate() {
        withServer { users ->
            val friend = users.resolveBySubject("auth-friend")
            users.claimUsername(friend.id, Username.of("Jordan"))

            val body =
                client.get("/users/Jordan") { header("Authorization", "Bearer ${asCaller()}") }.bodyAsText()

            assertFalse(body.contains("auth-friend"), "the auth subject must not leak")
            assertFalse(body.contains("lastSeen"), "activity must not leak")
        }
    }

    @Test
    fun theRepositoryFindsNothingForAnUnclaimedName() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val users = UserRepository(Databases.connect(dataSource))

            assertNull(users.findByUsername("jordan"))
        }
    }
}
