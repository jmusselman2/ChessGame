@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.series

import com.jmussel.chessgame.server.api.DashboardEntry
import com.jmussel.chessgame.server.api.GameView
import com.jmussel.chessgame.server.api.SeriesHistoryEntry
import com.jmussel.chessgame.server.api.SeriesOffer
import com.jmussel.chessgame.server.api.SeriesSummary
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.GameSeriesRepository
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
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * "Play with this friend", however many times it is tapped.
 *
 * Tapping Play for a friend with no series starts one, with one game. Tapping it again starts
 * nothing: the series is offered back (`D053`, which superseded `D011`'s "one active series per
 * pair" — the rule these tests were first written for), and a second series exists only when
 * the player asks for another. A finished game is followed by exactly one rematch (`D015`).
 * This is those rules held at the API boundary, where the taps actually arrive — including
 * both players tapping at once.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class SeriesIdempotencyTest {
    private val tokens = TestTokens()
    private val json = Json { ignoreUnknownKeys = true }

    private fun withFriends(block: suspend ApplicationTestBuilder.(Database) -> Unit) =
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

                block(database)
            }
        }

    private fun HttpRequestBuilder.authorizedAs(subject: String) = header("Authorization", "Bearer ${tokens.tokenFor(subject)}")

    private suspend fun ApplicationTestBuilder.tapPlay(
        subject: String,
        another: Boolean = false,
    ): HttpResponse =
        client.post(if (another) "/series?another=true" else "/series") {
            authorizedAs(subject)
            setBody(if (subject == JORDAN) "Alex" else "Jordan")
        }

    /**
     * Taps Play and lands in a series: the one it started, or — when the pair already has one
     * and it is offered (`D053`) — the newest offered, which is what choosing "Open" does.
     */
    private suspend fun ApplicationTestBuilder.play(subject: String): SeriesSummary {
        val response = tapPlay(subject)

        return if (response.status == HttpStatusCode.Conflict) {
            json.decodeFromString<SeriesOffer>(response.bodyAsText()).existing.first()
        } else {
            assertEquals(HttpStatusCode.Created, response.status)
            json.decodeFromString(response.bodyAsText())
        }
    }

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
    fun aSecondTapIsOfferedTheSeriesAndStartsNothing() {
        withFriends {
            val first = tapPlay(JORDAN)
            val second = tapPlay(JORDAN)

            assertEquals(HttpStatusCode.Created, first.status)
            assertEquals(HttpStatusCode.Conflict, second.status, "not reused silently (`D053`)")
            assertEquals(
                listOf(json.decodeFromString<SeriesSummary>(first.bodyAsText())),
                json.decodeFromString<SeriesOffer>(second.bodyAsText()).existing,
            )
            assertEquals(1, gameCount(), "the second tap started nothing")
        }
    }

    @Test
    fun choosingAnotherStartsAParallelSeriesWithItsOwnGame() {
        withFriends {
            val first = play(JORDAN)
            val another = tapPlay(ALEX, another = true)

            assertEquals(HttpStatusCode.Created, another.status)
            val second = json.decodeFromString<SeriesSummary>(another.bodyAsText())
            assertNotEquals(first.seriesId, second.seriesId)
            assertNotEquals(first.currentGameId, second.currentGameId)
            assertEquals(2, gameCount())
            assertEquals(2, dashboard(JORDAN).size, "both series are live for both players")
            assertEquals(2, dashboard(ALEX).size)
        }
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

            assertEquals(1, opened.map { it.seriesId }.distinct().size, "one series: the later tap is offered it")
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
        withFriends { database ->
            val opened = play(JORDAN)
            val firstGame = assertNotNull(opened.currentGameId)
            val closedSeries = opened.seriesId

            // Until `M19.5` this series was closed by removing the friend (`D013`). `D053`
            // superseded that, so the series is closed directly: that is what `M19.8`'s
            // explicit series exit does.
            GameSeriesRepository(database).close(Uuid.parse(closedSeries))
            resign(JORDAN, firstGame, 0)

            // "Play" starts something new rather than reviving the old.
            val reopened = play(JORDAN)

            assertNotEquals(closedSeries, reopened.seriesId, "a new series (`D012`)")
            assertNotEquals(firstGame, reopened.currentGameId)
            assertEquals(2, gameCount(), "the closed series kept its game and the new one has its own")
        }
    }

    @Test
    fun unfriendingMidGameLeavesTheSeriesToCarryOn() {
        withFriends {
            val opened = play(JORDAN)
            val firstGame = assertNotNull(opened.currentGameId)

            assertEquals(HttpStatusCode.OK, removeFriend(JORDAN, "Alex").status)
            resign(JORDAN, firstGame, 0)

            // `D053`, superseding `D013`: the rematch still follows, and Play — even with no
            // friendship between them now — is offered that same series.
            val after = play(ALEX)
            assertEquals(opened.seriesId, after.seriesId)
            assertNotEquals(firstGame, after.currentGameId, "the rematch")
            assertEquals(2, gameCount())
            assertEquals(1, dashboard(JORDAN).size)
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
