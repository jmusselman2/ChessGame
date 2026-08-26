@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.realtime

import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.testModule
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

/**
 * Opening the realtime connection.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class RealtimeConnectionTest {
    private val tokens = TestTokens()
    private val json = Json { ignoreUnknownKeys = true }

    private fun withServer(block: suspend ApplicationTestBuilder.(UserRepository, RealtimeHub) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val hub = RealtimeHub()

            testApplication {
                application { testModule(tokens.verifier(), database, realtime = hub) }
                block(UserRepository(database), hub)
            }
        }

    private fun ApplicationTestBuilder.realtimeClient() =
        createClient {
            install(WebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(Json)
            }
        }

    private suspend fun io.ktor.websocket.WebSocketSession.nextMessage(): RealtimeMessage =
        withTimeout(WAIT_MILLIS) {
            val frame = incoming.receive()
            json.decodeFromString((frame as Frame.Text).readText())
        }

    @Test
    fun anAuthenticatedClientCanConnect() {
        withServer { _, _ ->
            val session =
                realtimeClient().webSocketSession("/ws") {
                    header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}")
                }

            val hello = session.nextMessage()

            assertEquals(RealtimeMessage.CONNECTED, hello.type)

            session.close()
        }
    }

    @Test
    fun theConnectionIsRegisteredWhileItIsOpen() {
        withServer { users, hub ->
            val session =
                realtimeClient().webSocketSession("/ws") {
                    header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}")
                }
            session.nextMessage()

            val userId = users.resolveBySubject("auth-1").id

            assertEquals(1, hub.connectionCount(userId))

            session.close()
        }
    }

    @Test
    fun aClosedConnectionIsForgotten() {
        withServer { users, hub ->
            val session =
                realtimeClient().webSocketSession("/ws") {
                    header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}")
                }
            session.nextMessage()
            val userId = users.resolveBySubject("auth-1").id

            session.close()

            // The server notices when its read loop ends.
            withTimeout(WAIT_MILLIS) {
                while (hub.connectionCount(userId) > 0) {
                    kotlinx.coroutines.delay(20)
                }
            }

            assertEquals(0, hub.connectionCount(userId))
        }
    }

    @Test
    fun onePersonCanConnectFromTwoDevices() {
        withServer { users, hub ->
            val client = realtimeClient()
            val first =
                client.webSocketSession("/ws") { header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}") }
            first.nextMessage()
            val second =
                client.webSocketSession("/ws") { header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}") }
            second.nextMessage()

            assertEquals(2, hub.connectionCount(users.resolveBySubject("auth-1").id))

            first.close()
            second.close()
        }
    }

    @Test
    fun twoPeopleAreTrackedSeparately() {
        withServer { users, hub ->
            val client = realtimeClient()
            val jordan =
                client.webSocketSession("/ws") { header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}") }
            jordan.nextMessage()
            val alex =
                client.webSocketSession("/ws") { header("Authorization", "Bearer ${tokens.tokenFor("auth-2")}") }
            alex.nextMessage()

            assertEquals(1, hub.connectionCount(users.resolveBySubject("auth-1").id))
            assertEquals(1, hub.connectionCount(users.resolveBySubject("auth-2").id))

            jordan.close()
            alex.close()
        }
    }

    @Test
    fun anUnauthenticatedClientCannotConnect() {
        withServer { users, hub ->
            // The handshake itself fails: there is no session to hand back.
            val failure =
                assertFails {
                    realtimeClient().webSocketSession("/ws").close()
                }

            // The client reports a failed handshake; what matters is that no session
            // exists and the hub registered nothing.
            assertTrue(failure.message.orEmpty().isNotEmpty())
            assertEquals(0, hub.connectionCount(users.resolveBySubject("auth-1").id))
        }
    }

    @Test
    fun aForgedTokenCannotConnect() {
        withServer { users, hub ->
            val failure =
                assertFails {
                    realtimeClient()
                        .webSocketSession("/ws") {
                            header("Authorization", "Bearer ${tokens.tokenFromAnotherKey("auth-1")}")
                        }.close()
                }

            // The client reports a failed handshake; what matters is that no session
            // exists and the hub registered nothing.
            assertTrue(failure.message.orEmpty().isNotEmpty())
            assertEquals(0, hub.connectionCount(users.resolveBySubject("auth-1").id))
        }
    }

    @Test
    fun connectingCreatesTheUserLikeAnyAuthenticatedRequest() {
        withServer { users, _ ->
            val session =
                realtimeClient().webSocketSession("/ws") {
                    header("Authorization", "Bearer ${tokens.tokenFor("auth-new")}")
                }
            session.nextMessage()

            assertEquals("auth-new", users.resolveBySubject("auth-new").authSubject)

            session.close()
        }
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
