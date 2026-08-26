@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.series

import com.jmussel.chessgame.server.api.DashboardEntry
import com.jmussel.chessgame.server.api.GameView
import com.jmussel.chessgame.server.api.SeriesHistoryEntry
import com.jmussel.chessgame.server.api.SeriesSummary
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.testModule
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.uuid.ExperimentalUuidApi

/**
 * "Play with this friend", however many times it is tapped.
 *
 * One pair has one active series (`D011`) and one game to play in it, and a finished game
 * is followed by exactly one rematch (`D015`). This is those rules held at the API
 * boundary, where the taps actually arrive — including both players tapping at once.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class SeriesIdempotencyTest {
    private val tokens = TestTokens()
    private val json = Json { ignoreUnknownKeys = true }

    private fun withFriends(block: suspend ApplicationTestBuilder.() -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)

            testApplication {
                application { testModule(tokens.verifier(), database) }

                client.post("/username") {
                    authorizedAs(JORDAN)
                    setBody("Jordan")
                }
                client.post("/username") {
                    authorizedAs(ALEX)
                    setBody("Alex")
                }
                client.post("/friends") {
                    authorizedAs(JORDAN)
                    setBody("Alex")
                }

                block()
            }
        }

    private fun HttpRequestBuilder.authorizedAs(subject: String) = header("Authorization", "Bearer ${tokens.tokenFor(subject)}")

    private suspend fun ApplicationTestBuilder.play(subject: String): SeriesSummary =
        json.decodeFromString(
            client
                .post("/series") {
                    authorizedAs(subject)
                    setBody(if (subject == JORDAN) "Alex" else "Jordan")
                }.bodyAsText(),
        )

    private suspend fun ApplicationTestBuilder.readGame(
        subject: String,
        gameId: String,
    ): GameView =
        json.decodeFromString(
            client
                .get("/games/$gameId") { authorizedAs(subject) }
                .bodyAsText(),
        )

    private suspend fun ApplicationTestBuilder.dashboard(subject: String): List<DashboardEntry> =
        json.decodeFromString(
            client
                .get("/dashboard") { authorizedAs(subject) }
                .bodyAsText(),
        )

    private suspend fun ApplicationTestBuilder.history(subject: String): List<SeriesHistoryEntry> =
        json.decodeFromString(
            client
                .get("/history") { authorizedAs(subject) }
                .bodyAsText(),
        )

    private suspend fun ApplicationTestBuilder.move(
        subject: String,
        gameId: String,
        version: Long,
        from: String,
        to: String,
    ): HttpResponse =
        client.post("/games/$gameId/moves") {
            authorizedAs(subject)
            contentType(ContentType.Application.Json)
            setBody("""{"expectedVersion":$version,"from":"$from","to":"$to"}""")
        }

    private suspend fun ApplicationTestBuilder.resign(
        subject: String,
        gameId: String,
        version: Long,
    ): HttpResponse =
        client.post("/games/$gameId/resignation") {
            authorizedAs(subject)
            contentType(ContentType.Application.Json)
            setBody("""{"expectedVersion":$version}""")
        }

    /** How many games the pair has in total, across every series. */
    private suspend fun ApplicationTestBuilder.gameCount(): Int {
        val finished = history(JORDAN).sumOf { it.games.size }
        val inPlay = dashboard(JORDAN).count { it.gameId != null }

        return finished + inPlay
    }

    @Test
    fun tappingPlayTwiceOpensTheSameGame() {
        withFriends {
            val first = play(JORDAN)
            val second = play(JORDAN)

            assertEquals(first.seriesId, second.seriesId)
            assertEquals(first.currentGameId, second.currentGameId)
            assertEquals(1, gameCount(), "the second tap started nothing")
        }
    }

    @Test
    fun bothPlayersTappingPlayFindTheSameGame() {
        withFriends {
            val jordans = play(JORDAN)
            val alexs = play(ALEX)

            assertEquals(jordans.seriesId, alexs.seriesId)
            assertEquals(jordans.currentGameId, alexs.currentGameId)
            assertEquals(1, gameCount())
        }
    }

    @Test
    fun bothPlayersTappingPlayAtOnceStillGetOneGame() {
        withFriends {
            val opened =
                withContext(Dispatchers.IO) {
                    coroutineScope {
                        listOf(async { play(JORDAN) }, async { play(ALEX) }).awaitAll()
                    }
                }

            assertEquals(1, opened.map { it.seriesId }.distinct().size, "one series (`D011`)")
            assertEquals(1, opened.map { it.currentGameId }.distinct().size, "one game in it")
            assertEquals(1, gameCount())
        }
    }

    @Test
    fun tappingPlayMidGameOpensTheGameInProgress() {
        withFriends {
            val gameId = assertNotNull(play(JORDAN).currentGameId)
            val mover = if (readGame(JORDAN, gameId).yourTurn) JORDAN else ALEX
            move(mover, gameId, 0, "e2", "e4")

            val reopened = play(ALEX)

            assertEquals(gameId, reopened.currentGameId, "the game in progress, not a new one")
            assertEquals(1, readGame(ALEX, gameId).version, "and it kept the move played in it")
            assertEquals(1, gameCount())
        }
    }

    @Test
    fun tappingPlayAfterAGameEndsOpensTheRematch() {
        withFriends {
            val firstGame = assertNotNull(play(JORDAN).currentGameId)
            resign(JORDAN, firstGame, 0)

            val reopened = play(JORDAN)
            val rematch = assertNotNull(reopened.currentGameId)

            assertNotEquals(firstGame, rematch, "the finished game is not reopened")
            assertEquals(0, readGame(JORDAN, rematch).version, "the rematch is untouched")
            assertEquals(2, gameCount(), "one finished game and one rematch")
        }
    }

    @Test
    fun bothPlayersTappingPlayAfterAGameEndsStillFindOneRematch() {
        withFriends {
            val firstGame = assertNotNull(play(JORDAN).currentGameId)
            resign(JORDAN, firstGame, 0)

            val opened =
                withContext(Dispatchers.IO) {
                    coroutineScope {
                        listOf(async { play(JORDAN) }, async { play(ALEX) }).awaitAll()
                    }
                }

            assertEquals(1, opened.map { it.currentGameId }.distinct().size)
            assertNotEquals(firstGame, opened.first().currentGameId)
            assertEquals(2, gameCount(), "the rematch was created once, by the resignation")
        }
    }

    @Test
    fun tappingPlayManyTimesAcrossAFinishedGameLeavesTwoGames() {
        withFriends {
            val firstGame = assertNotNull(play(JORDAN).currentGameId)
            repeat(3) { play(JORDAN) }
            resign(ALEX, firstGame, 0)
            repeat(3) { play(ALEX) }

            assertEquals(2, gameCount())
            assertEquals(1, dashboard(JORDAN).size, "one active series with one game in it")
        }
    }

    @Test
    fun theRematchIsTheSeriesGameForBothPlayers() {
        withFriends {
            val firstGame = assertNotNull(play(JORDAN).currentGameId)
            resign(JORDAN, firstGame, 0)

            val jordans = play(JORDAN).currentGameId
            val alexs = play(ALEX).currentGameId

            assertEquals(jordans, alexs)
            assertEquals(jordans, dashboard(ALEX).single().gameId)
        }
    }

    @Test
    fun aClosedSeriesIsNotReopened() {
        withFriends {
            val firstGame = assertNotNull(play(JORDAN).currentGameId)
            val closedSeries = play(JORDAN).seriesId

            // Removing the friend marks the series to close after this game (`D013`).
            removeFriend(JORDAN, "Alex")
            resign(JORDAN, firstGame, 0)

            // They make up, and "Play" starts something new rather than reviving the old.
            client.post("/friends") {
                authorizedAs(JORDAN)
                setBody("Alex")
            }

            val reopened = play(JORDAN)

            assertNotEquals(closedSeries, reopened.seriesId, "a new series (`D012`)")
            assertNotEquals(firstGame, reopened.currentGameId)
            assertEquals(2, gameCount(), "the closed series kept its game and the new one has its own")
        }
    }

    @Test
    fun aFinishedGameStaysFinishedHoweverOftenPlayIsTapped() {
        withFriends {
            val firstGame = assertNotNull(play(JORDAN).currentGameId)
            resign(JORDAN, firstGame, 0)
            val finished = readGame(JORDAN, firstGame)

            repeat(3) { play(ALEX) }

            val stillFinished = readGame(JORDAN, firstGame)

            assertEquals(finished.version, stillFinished.version)
            assertEquals(finished.result, stillFinished.result)
            assertEquals("RESIGNATION", stillFinished.terminationReason)
        }
    }

    private suspend fun ApplicationTestBuilder.removeFriend(
        subject: String,
        username: String,
    ) = client.delete("/friends/$username") { authorizedAs(subject) }

    private companion object {
        const val JORDAN = "auth-jordan"
        const val ALEX = "auth-alex"
    }
}
