@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.user

import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.ClaimUsernameResult
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.module
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

/**
 * Claiming a username, against the real database.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class UsernameClaimTest {
    private val tokens = TestTokens()

    private fun withUsers(block: (UserRepository) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            block(UserRepository(Databases.connect(dataSource)))
        }

    private fun withServer(block: suspend ApplicationTestBuilder.(UserRepository) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val users = UserRepository(database)
            testApplication {
                application { module(tokens.verifier(), users, FriendshipRepository(database)) }
                block(users)
            }
        }

    @Test
    fun aUserCanClaimAName() {
        withUsers { users ->
            val user = users.resolveBySubject("auth-1")

            val result = users.claimUsername(user.id, Username.of("Jordan"))

            assertTrue(result is ClaimUsernameResult.Claimed)
            assertEquals("Jordan", users.find(user.id)?.username)
        }
    }

    @Test
    fun theNameIsFoundCaseInsensitively() {
        withUsers { users ->
            val user = users.resolveBySubject("auth-1")
            users.claimUsername(user.id, Username.of("Jordan"))

            listOf("Jordan", "jordan", "JORDAN", "jOrDaN").forEach { spelling ->
                assertEquals(user.id, users.findByUsername(spelling)?.id, "looking up '$spelling'")
            }
        }
    }

    @Test
    fun aDifferentCasingOfTheSameNameIsNotAvailable() {
        withUsers { users ->
            val first = users.resolveBySubject("auth-1")
            val second = users.resolveBySubject("auth-2")
            users.claimUsername(first.id, Username.of("Jordan"))

            val result = users.claimUsername(second.id, Username.of("jordan"))

            assertEquals(ClaimUsernameResult.Taken, result)
            assertNull(users.find(second.id)?.username)
        }
    }

    @Test
    fun reclaimingYourOwnNameIsHarmless() {
        withUsers { users ->
            val user = users.resolveBySubject("auth-1")
            users.claimUsername(user.id, Username.of("Jordan"))

            val again = users.claimUsername(user.id, Username.of("Jordan"))

            assertTrue(again is ClaimUsernameResult.Claimed)
        }
    }

    @Test
    fun aUsernameCannotBeChanged() {
        withUsers { users ->
            val user = users.resolveBySubject("auth-1")
            users.claimUsername(user.id, Username.of("Jordan"))

            val result = users.claimUsername(user.id, Username.of("Alex"))

            assertEquals(ClaimUsernameResult.AlreadyNamed("Jordan"), result)
            assertEquals("Jordan", users.find(user.id)?.username)
            assertNull(users.findByUsername("Alex"))
        }
    }

    @Test
    fun aLostAccountKeepsItsNameReserved() {
        withUsers { users ->
            val lost = users.resolveBySubject("auth-lost")
            users.claimUsername(lost.id, Username.of("Jordan"))

            // A new anonymous account, as a reinstalled app would create.
            val fresh = users.resolveBySubject("auth-fresh")

            assertEquals(ClaimUsernameResult.Taken, users.claimUsername(fresh.id, Username.of("Jordan")))
            assertEquals(lost.id, users.findByUsername("Jordan")?.id)
        }
    }

    @Test
    fun exactlyOneOfTwoSimultaneousClaimsWins() {
        withUsers { users ->
            val contenders = (1..8).map { users.resolveBySubject("auth-$it") }
            val barrier = CyclicBarrier(contenders.size)
            val pool = Executors.newFixedThreadPool(contenders.size)

            val results =
                try {
                    pool
                        .invokeAll(
                            contenders.map { contender ->
                                Callable {
                                    barrier.await(10, TimeUnit.SECONDS)
                                    users.claimUsername(contender.id, Username.of("Jordan"))
                                }
                            },
                        ).map { it.get() }
                } finally {
                    pool.shutdown()
                }

            assertEquals(1, results.count { it is ClaimUsernameResult.Claimed }, "exactly one claim must win")
            assertEquals(
                contenders.size - 1,
                results.count { it == ClaimUsernameResult.Taken },
                "everyone else must be told the name is taken",
            )
            assertNotNull(users.findByUsername("Jordan"))
            assertEquals(1, contenders.count { users.find(it.id)?.username != null })
        }
    }

    @Test
    fun theEndpointClaimsForTheCallingUser() {
        withServer { users ->
            val response =
                client.post("/username") {
                    header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}")
                    setBody("Jordan")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("Jordan", response.bodyAsText())
            assertEquals("auth-1", users.findByUsername("jordan")?.authSubject)
        }
    }

    @Test
    fun theEndpointNeedsAToken() {
        withServer {
            val response = client.post("/username") { setBody("Jordan") }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun theEndpointRejectsAnInvalidName() {
        withServer {
            listOf("ab", "a".repeat(25), "has space", "dots.here").forEach { candidate ->
                val response =
                    client.post("/username") {
                        header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}")
                        setBody(candidate)
                    }

                assertEquals(HttpStatusCode.BadRequest, response.status, "'$candidate' should be refused")
            }
        }
    }

    @Test
    fun theEndpointReportsATakenName() {
        withServer {
            client.post("/username") {
                header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}")
                setBody("Jordan")
            }

            val second =
                client.post("/username") {
                    header("Authorization", "Bearer ${tokens.tokenFor("auth-2")}")
                    setBody("JORDAN")
                }

            assertEquals(HttpStatusCode.Conflict, second.status)
        }
    }

    @Test
    fun theEndpointRefusesToRename() {
        withServer {
            client.post("/username") {
                header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}")
                setBody("Jordan")
            }

            val rename =
                client.post("/username") {
                    header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}")
                    setBody("Alex")
                }

            assertEquals(HttpStatusCode.Conflict, rename.status)
        }
    }
}
