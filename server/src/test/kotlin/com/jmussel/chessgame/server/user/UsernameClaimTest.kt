@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.user

import com.jmussel.chessgame.server.api.CurrentUser
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.ClaimUsernameResult
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
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

/**
 * Claiming a username, against the real database.
 *
 * A name nobody has is taken; a name somebody has attaches the installation to that user
 * (`D082`, `M20.4`). The tests that expected a taken name to be refused (`M7.4`) changed
 * with that requirement, and only those.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class UsernameClaimTest {
    private val tokens = TestTokens()

    private fun withUsers(block: (UserRepository) -> Unit) = withUsersAndData { users, _ -> block(users) }

    private fun withUsersAndData(block: (UserRepository, DataSource) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            block(UserRepository(Databases.connect(dataSource)), dataSource)
        }

    private fun count(
        dataSource: DataSource,
        sql: String,
    ) = DatabaseTestSupport.count(dataSource, sql)

    private fun withServer(block: suspend ApplicationTestBuilder.(UserRepository) -> Unit) =
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
    fun anExistingNameAttachesTheInstallationToItsUser() {
        withUsersAndData { users, dataSource ->
            val first = users.resolveBySubject("auth-1")
            val second = users.resolveBySubject("auth-2")
            users.claimUsername(first.id, Username.of("Jordan"))

            val result = users.claimUsername(second.id, Username.of("Jordan"))

            assertEquals(ClaimUsernameResult.Claimed(users.find(first.id)!!, attached = true), result)
            assertEquals(first.id, users.resolveBySubject("auth-2").id, "the second installation is now that user")
            assertEquals(first.id, users.resolveBySubject("auth-1").id, "and the first still is")
            assertNull(users.find(second.id), "its nameless stand-in is gone")
            assertEquals(1, count(dataSource, "select count(*) from users"))
            assertEquals(2, count(dataSource, "select count(*) from user_auth_subjects where user_id = '${first.id}'"))
        }
    }

    @Test
    fun aNameThatMatchesOnceNormalizedAttachesToo() {
        withUsers { users ->
            val first = users.resolveBySubject("auth-1")
            users.claimUsername(first.id, Username.of("Jordan"))

            listOf("jordan", "JORDAN", "jOrDaN").forEachIndexed { index, spelling ->
                val installation = users.resolveBySubject("auth-other-$index")

                val result = assertIs<ClaimUsernameResult.Claimed>(users.claimUsername(installation.id, Username.of(spelling)))

                assertTrue(result.attached, "'$spelling' is Jordan's name")
                assertEquals(first.id, result.user.id)
                assertEquals("Jordan", result.user.username, "the name keeps the casing it was claimed with")
                assertEquals(first.id, users.resolveBySubject("auth-other-$index").id)
            }
            assertEquals("Jordan", users.find(first.id)?.username)
        }
    }

    @Test
    fun claimingAnExistingNameNeverMakesASecondUser() {
        withUsersAndData { users, dataSource ->
            val first = users.resolveBySubject("auth-1")
            users.claimUsername(first.id, Username.of("Jordan"))
            val usersBefore = count(dataSource, "select count(*) from users")

            repeat(3) { users.claimUsername(users.resolveBySubject("auth-again-$it").id, Username.of("jordan")) }

            assertEquals(usersBefore, count(dataSource, "select count(*) from users"))
            assertEquals(1, count(dataSource, "select count(*) from users where username_normalized = 'jordan'"))
        }
    }

    @Test
    fun aReplacedStandInIsNoLongerAUserToClaimFor() {
        withUsers { users ->
            users.claimUsername(users.resolveBySubject("auth-1").id, Username.of("Jordan"))
            val standIn = users.resolveBySubject("auth-2")
            users.claimUsername(standIn.id, Username.of("Jordan"))

            // What a second claim from the same installation, racing the first, would find.
            // `POST /username` resolves the installation again and claims as whoever it is now.
            assertEquals(ClaimUsernameResult.NoSuchUser, users.claimUsername(standIn.id, Username.of("Jordan")))
        }
    }

    @Test
    fun anAttachedInstallationCannotMoveToAnotherUser() {
        withUsers { users ->
            val jordan = users.resolveBySubject("auth-1")
            users.claimUsername(jordan.id, Username.of("Jordan"))
            users.claimUsername(users.resolveBySubject("auth-2").id, Username.of("Alex"))
            users.claimUsername(users.resolveBySubject("auth-3").id, Username.of("Jordan"))

            // Installation 3 is Jordan now. Claiming Alex would move it to another user,
            // which is a rename by another route, and is refused the same way (`D083`).
            val result = users.claimUsername(users.resolveBySubject("auth-3").id, Username.of("Alex"))

            assertEquals(ClaimUsernameResult.AlreadyNamed("Jordan"), result)
            assertEquals(jordan.id, users.resolveBySubject("auth-3").id)
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
    fun aLostAccountKeepsItsNameAndIsRegainedByTypingIt() {
        withUsers { users ->
            val lost = users.resolveBySubject("auth-lost")
            users.claimUsername(lost.id, Username.of("Jordan"))

            // A new anonymous account, as a reinstalled app would create.
            val fresh = users.resolveBySubject("auth-fresh")

            val result = assertIs<ClaimUsernameResult.Claimed>(users.claimUsername(fresh.id, Username.of("Jordan")))

            // The name was never released (`D008`); typing it reaches the same user (`D082`).
            assertEquals(lost.id, result.user.id)
            assertEquals(lost.id, users.findByUsername("Jordan")?.id)
            assertEquals(lost.id, users.resolveBySubject("auth-fresh").id)
        }
    }

    @Test
    fun simultaneousFirstClaimsOfOneNewNameMakeExactlyOneUser() {
        withUsersAndData { users, dataSource ->
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

            val claimed = results.map { assertIs<ClaimUsernameResult.Claimed>(it) }
            val jordan = assertNotNull(users.findByUsername("Jordan"))

            assertEquals(1, claimed.count { !it.attached }, "exactly one claim takes the name")
            assertEquals(contenders.size - 1, claimed.count { it.attached }, "everyone else is attached to it")
            assertEquals(setOf(jordan.id), claimed.map { it.user.id }.toSet())
            assertEquals(1, count(dataSource, "select count(*) from users"), "one user, and every stand-in gone")
            (1..contenders.size).forEach { assertEquals(jordan.id, users.resolveBySubject("auth-$it").id) }
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
            assertEquals(users.resolveBySubject("auth-1").id, users.findByUsername("jordan")?.id)
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
    fun theEndpointAttachesToAnExistingName() {
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

            // Answered as a new name is: the reply says nothing about whether it was in use.
            assertEquals(HttpStatusCode.OK, second.status)
            assertEquals("Jordan", second.bodyAsText())
            assertEquals(me("auth-1"), me("auth-2"))
        }
    }

    @Test
    fun theEndpointClaimingTheSameNameAgainIsHarmless() {
        withServer {
            listOf("auth-1" to "Jordan", "auth-2" to "jordan", "auth-2" to "Jordan").forEach { (subject, name) ->
                val response =
                    client.post("/username") {
                        header("Authorization", "Bearer ${tokens.tokenFor(subject)}")
                        setBody(name)
                    }

                assertEquals(HttpStatusCode.OK, response.status, "$subject claiming $name")
                assertEquals("Jordan", response.bodyAsText())
            }
        }
    }

    private suspend fun ApplicationTestBuilder.me(subject: String): CurrentUser =
        Json.decodeFromString(
            client
                .get("/me") { header("Authorization", "Bearer ${tokens.tokenFor(subject)}") }
                .bodyAsText(),
        )

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
