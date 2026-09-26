@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.series

import com.jmussel.chessgame.server.api.DashboardEntry
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.GameSeriesTable
import com.jmussel.chessgame.server.db.GamesTable
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.testModule
import com.jmussel.chessgame.server.user.Username
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.uuid.ExperimentalUuidApi

/** Independent M9 probes for series visibility and identity boundaries. */
class M9AdversarialTest {
    private val tokens = TestTokens()
    private val json = Json { ignoreUnknownKeys = true }

    private fun HttpRequestBuilder.authorizedAs(subject: String) = header("Authorization", "Bearer ${tokens.tokenFor(subject)}")

    @Test
    fun aNamelessCallerCannotStartAOneSidedInvisibleSeries() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val users = UserRepository(database)
            users.resolveBySubject(CALLER)
            val alex = users.resolveBySubject(FRIEND)
            users.claimUsername(alex.id, Username.of("Alex"))

            testApplication {
                application { testModule(tokens.verifier(), database) }

                val response =
                    client.post("/series") {
                        authorizedAs(CALLER)
                        setBody("Alex")
                    }
                val alexDashboard =
                    client.get("/dashboard") {
                        authorizedAs(FRIEND)
                    }
                val alexEntries = json.decodeFromString<List<DashboardEntry>>(alexDashboard.bodyAsText())

                assertEquals(emptyList(), alexEntries, "Alex cannot render a nameless opponent")
                assertEquals(
                    0,
                    transaction(database) { GameSeriesTable.selectAll().count() },
                    "${response.status} created a series the named participant cannot discover",
                )
                assertEquals(0, transaction(database) { GamesTable.selectAll().count() })
                assertFalse(response.status.isSuccess())
            }
        }
    }

    private companion object {
        const val CALLER = "m9-nameless-caller"
        const val FRIEND = "m9-named-friend"
    }
}
