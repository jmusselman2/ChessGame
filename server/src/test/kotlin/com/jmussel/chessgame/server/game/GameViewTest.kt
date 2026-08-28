@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.game

import com.jmussel.chessgame.server.api.GameView
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.series.seriesService
import com.jmussel.chessgame.server.testModule
import com.jmussel.chessgame.server.user.Username
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * What `GET /games/{gameId}` tells a player about their game.
 *
 * A screen rebuilt after the process was recreated has only a game id to go on, so
 * everything it needs to draw the game has to be in this one answer: who the opponent is,
 * which side the viewer plays, and which move was last played. Skipped when this machine
 * has no test database (see [DatabaseTestSupport]).
 */
class GameViewTest {
    private val tokens = TestTokens()
    private val json = Json { ignoreUnknownKeys = true }

    private class Players(
        val jordan: Uuid,
        val alex: Uuid,
        val gameId: Uuid,
    )

    private fun withGame(block: suspend ApplicationTestBuilder.(Players) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val users = UserRepository(database)

            fun named(
                subject: String,
                username: String,
            ): Uuid {
                val user = users.resolveBySubject(subject)
                users.claimUsername(user.id, Username.of(username))
                return user.id
            }

            val jordan = named("auth-1", "Jordan")
            val alex = named("auth-2", "Alex")
            FriendshipRepository(database).add(jordan, alex)

            val opened = seriesService(database).openWithGame(jordan, alex)
            val gameId = assertNotNull(opened.series.currentGameId)

            testApplication {
                application { testModule(tokens.verifier(), database) }
                block(Players(jordan, alex, gameId))
            }

            GameRepository(database)
        }

    private suspend fun ApplicationTestBuilder.read(
        subject: String,
        gameId: Uuid,
    ): GameView {
        val response = client.get("/games/$gameId") { header("Authorization", "Bearer ${tokens.tokenFor(subject)}") }

        assertEquals(HttpStatusCode.OK, response.status)
        return json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.move(
        subject: String,
        gameId: Uuid,
        expectedVersion: Long,
        from: String,
        to: String,
    ) = client.post("/games/$gameId/moves") {
        header("Authorization", "Bearer ${tokens.tokenFor(subject)}")
        contentType(ContentType.Application.Json)
        setBody("""{"expectedVersion":$expectedVersion,"from":"$from","to":"$to"}""")
    }

    @Test
    fun eachPlayerIsToldWhoTheirOpponentIs() {
        withGame { players ->
            assertEquals("Alex", read("auth-1", players.gameId).opponent.username)
            assertEquals("Jordan", read("auth-2", players.gameId).opponent.username)
        }
    }

    @Test
    fun theOpponentCarriesTheIdTheRestOfTheApiUses() {
        withGame { players ->
            assertEquals(players.alex.toString(), read("auth-1", players.gameId).opponent.userId)
        }
    }

    @Test
    fun eachPlayerIsToldWhichSideTheyPlay() {
        withGame { players ->
            val jordan = read("auth-1", players.gameId)
            val alex = read("auth-2", players.gameId)

            assertEquals(setOf("WHITE", "BLACK"), setOf(jordan.yourSide, alex.yourSide))
            assertEquals("WHITE", jordan.sideToMove)
            assertEquals("WHITE", alex.sideToMove)
        }
    }

    @Test
    fun aGameWithNoMovesYetHasNoLastMove() {
        withGame { players ->
            assertNull(read("auth-1", players.gameId).lastMove)
        }
    }

    @Test
    fun theLastMoveIsReportedInSquaresBothPlayersCanDraw() {
        withGame { players ->
            val white = read("auth-1", players.gameId)
            val whiteSubject = if (white.yourSide == "WHITE") "auth-1" else "auth-2"

            move(whiteSubject, players.gameId, white.version, "e2", "e4")

            listOf("auth-1", "auth-2").forEach { subject ->
                val view = read(subject, players.gameId)
                val lastMove = assertNotNull(view.lastMove, "the move just played should be reported to $subject")

                assertEquals("e2", lastMove.from)
                assertEquals("e4", lastMove.to)
                assertNull(lastMove.promotion)
            }
        }
    }

    @Test
    fun theLastMoveIsTheLatestOneNotTheFirst() {
        withGame { players ->
            val first = read("auth-1", players.gameId)
            val whiteSubject = if (first.yourSide == "WHITE") "auth-1" else "auth-2"
            val blackSubject = if (whiteSubject == "auth-1") "auth-2" else "auth-1"

            move(whiteSubject, players.gameId, first.version, "e2", "e4")
            move(blackSubject, players.gameId, first.version + 1, "e7", "e5")

            val view = read(whiteSubject, players.gameId)

            assertEquals("e7", assertNotNull(view.lastMove).from)
            assertEquals(listOf("e2e4", "e7e5"), view.moves)
        }
    }

    @Test
    fun aStrangerIsNotToldAnythingAboutTheGame() {
        withGame { players ->
            val response =
                client.get("/games/${players.gameId}") {
                    header("Authorization", "Bearer ${tokens.tokenFor("auth-3")}")
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
    }

    @Test
    fun aGameThatDoesNotExistIsNotFound() {
        withGame { _ ->
            val response =
                client.get("/games/${Uuid.random()}") {
                    header("Authorization", "Bearer ${tokens.tokenFor("auth-1")}")
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }
}
