package com.jmussel.chessgame.server

import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseHealth
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.jdbc.Database
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DatabaseHealthRouteTest {
    private val tokens = TestTokens()

    @Test
    fun thePublicRouteRunsAllThreeChecksInOrderWithoutAuthentication() =
        withDatabase { _, database ->
            val statements = mutableListOf<String>()
            val health =
                DatabaseHealth { sql ->
                    statements += sql
                    !sql.contains("FROM public.games") && !sql.contains("FROM public.game_series")
                }

            testApplication {
                application { testModule(tokens.verifier(), database, databaseHealth = health) }

                val response = client.get("/health/database")

                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("""{"status":"ok"}""", response.bodyAsText())
                assertNoStore(response.headers[HttpHeaders.CacheControl])
                assertEquals(3, statements.size)
                assertEquals(true, statements[0].contains("flyway_schema_history"))
                assertEquals(true, statements[1].contains("public.games"))
                assertEquals(true, statements[2].contains("public.game_series"))
            }
        }

    @Test
    fun emptyGameTablesAreHealthyAndRepeatedProbesChangeNoRows() =
        withDatabase { dataSource, database ->
            val before = applicationRowCounts(dataSource)

            testApplication {
                application { testModule(tokens.verifier(), database) }

                repeat(3) {
                    val response = client.get("/health/database")

                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals("""{"status":"ok"}""", response.bodyAsText())
                    assertNoStore(response.headers[HttpHeaders.CacheControl])
                }
            }

            assertEquals(before, applicationRowCounts(dataSource))
            assertEquals(0L, before.getValue("games"))
            assertEquals(0L, before.getValue("game_series"))
        }

    @Test
    fun absenceOfSuccessfulFlywayHistoryIsUnavailable() =
        withDatabase { dataSource, database ->
            dataSource.connection.use { connection ->
                connection.createStatement().use { it.executeUpdate("update flyway_schema_history set success = false") }
                connection.commit()
            }

            testApplication {
                application { testModule(tokens.verifier(), database) }

                val response = client.get("/health/database")

                assertUnavailable(response.status, response.bodyAsText(), response.headers[HttpHeaders.CacheControl])
            }
        }

    @Test
    fun aMissingRequiredTableIsUnavailable() =
        withDatabase { dataSource, database ->
            dataSource.connection.use { connection ->
                connection.createStatement().use { it.execute("drop table games cascade") }
                connection.commit()
            }

            testApplication {
                application { testModule(tokens.verifier(), database) }

                val response = client.get("/health/database")

                assertUnavailable(response.status, response.bodyAsText(), response.headers[HttpHeaders.CacheControl])
            }
        }

    @Test
    fun failureToAcquireAConnectionIsUnavailable() =
        withDatabase { dataSource, database ->
            (dataSource as AutoCloseable).close()

            testApplication {
                application { testModule(tokens.verifier(), database) }

                val response = client.get("/health/database")

                assertUnavailable(response.status, response.bodyAsText(), response.headers[HttpHeaders.CacheControl])
            }
        }

    @Test
    fun everyStatementFailureAndConnectionFailureIsGenericAndUnavailable() =
        withDatabase { _, database ->
            val failures =
                listOf(
                    "flyway_schema_history" to SQLException("SELECT password FROM secrets", "42501"),
                    "public.games" to SQLException("games SQL leaked", "57014"),
                    "public.game_series" to SQLException("series SQL leaked", "42P01"),
                    "" to SQLException("jdbc:postgresql://user:password@host/db", "08006"),
                )

            failures.forEach { (failingStatement, failure) ->
                val health =
                    DatabaseHealth { sql ->
                        if (failingStatement.isEmpty() || sql.contains(failingStatement)) throw failure
                        true
                    }

                testApplication {
                    application { testModule(tokens.verifier(), database, databaseHealth = health) }

                    val response = client.get("/health/database")
                    val body = response.bodyAsText()

                    assertUnavailable(response.status, body, response.headers[HttpHeaders.CacheControl])
                    assertFalse(body.contains(failure.message.orEmpty()))
                    assertFalse(body.contains("SELECT"))
                    assertFalse(body.contains("jdbc:"))
                }
            }
        }

    private fun withDatabase(block: (DataSource, Database) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource -> block(dataSource, Databases.connect(dataSource)) }

    private fun applicationRowCounts(dataSource: DataSource): Map<String, Long> =
        dataSource.connection.use { connection ->
            val tables =
                connection
                    .createStatement()
                    .use { statement ->
                        statement
                            .executeQuery(
                                """
                                select table_name
                                from information_schema.tables
                                where table_schema = 'public'
                                  and table_type = 'BASE TABLE'
                                  and table_name <> 'flyway_schema_history'
                                order by table_name
                                """.trimIndent(),
                            ).use { rows ->
                                buildList {
                                    while (rows.next()) add(rows.getString(1))
                                }
                            }
                    }

            tables.associateWith { table ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("select count(*) from public.\"${table.replace("\"", "\"\"")}\"").use { rows ->
                        check(rows.next())
                        rows.getLong(1)
                    }
                }
            }
        }

    private fun assertUnavailable(
        status: HttpStatusCode,
        body: String,
        cacheControl: String?,
    ) {
        assertEquals(HttpStatusCode.ServiceUnavailable, status)
        assertEquals("""{"status":"unavailable"}""", body)
        assertNoStore(cacheControl)
    }

    private fun assertNoStore(value: String?) = assertEquals("no-store", value)
}
