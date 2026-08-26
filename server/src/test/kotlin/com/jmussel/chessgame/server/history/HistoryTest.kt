@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.history

import com.jmussel.chessgame.server.api.SeriesHistoryEntry
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.CLOSED_SERIES
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.HistoryQueries
import com.jmussel.chessgame.server.game.CommandResult
import com.jmussel.chessgame.server.series.SeriesEndFixture
import com.jmussel.chessgame.server.series.withSeries
import com.jmussel.chessgame.server.testModule
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * What a player can still read after a game is over.
 *
 * A finished game and a closed series stay available (`D012`), and they are read-only by
 * construction rather than by a rule stated in the history layer: a finished game refuses
 * every command (`D017`) and a closed series never gets another one (`D013`).
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class HistoryTest {
    private val tokens = TestTokens()
    private val json = Json { ignoreUnknownKeys = true }

    private fun SeriesEndFixture.history(userId: Uuid) = HistoryQueries(database).historyFor(userId)

    @Test
    fun aFinishedGameIsInHistory() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)

            val series = fixture.history(fixture.white).single()
            val game = series.games.single()

            assertEquals(fixture.firstGameId, game.gameId)
            assertEquals(1, game.sequenceNumber)
            assertEquals("BLACK_WINS", game.result)
            assertEquals("CHECKMATE", game.terminationReason)
            assertNotNull(game.endedAt)
        }
    }

    @Test
    fun theGameInProgressIsNotHistory() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)

            // The rematch has begun and is not over, so history holds only the first game.
            val series = fixture.history(fixture.white).single()

            assertEquals(listOf(1), series.games.map { it.sequenceNumber })
            assertEquals(2, fixture.gamesInSeries().size)
        }
    }

    @Test
    fun aSeriesWithNothingFinishedIsNotListed() {
        withSeries { fixture ->
            assertTrue(fixture.history(fixture.white).isEmpty(), "nothing has been played out yet")
        }
    }

    @Test
    fun eachPlayerSeesTheSideTheyPlayed() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)

            val whitesHistory = fixture.history(fixture.white).single()
            val blacksHistory = fixture.history(fixture.black).single()
            val asWhite = whitesHistory.games.single()
            val asBlack = blacksHistory.games.single()

            assertEquals("WHITE", asWhite.yourSide)
            assertEquals("BLACK", asBlack.yourSide)
            assertEquals(asWhite.gameId, asBlack.gameId, "one game, two points of view")
        }
    }

    @Test
    fun theGamesOfASeriesAreInTheOrderTheyWerePlayed() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)
            fixture.playFoolsMate(fixture.currentGame().id)
            fixture.playFoolsMate(fixture.currentGame().id)

            val series = fixture.history(fixture.white).single()

            assertEquals(listOf(1, 2, 3), series.games.map { it.sequenceNumber })
        }
    }

    @Test
    fun aClosedSeriesStaysReadable() {
        withSeries { fixture ->
            fixture.seriesRepository.markCloseAfterCurrentGame(fixture.seriesId)
            fixture.playFoolsMate(fixture.firstGameId)

            val series = fixture.history(fixture.white).single()

            assertEquals(CLOSED_SERIES, series.status)
            assertNotNull(series.closedAt)
            assertEquals(1, series.games.size)
            assertEquals(fixture.seriesId, series.seriesId)
        }
    }

    @Test
    fun aStrangersSeriesIsNotInYourHistory() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)
            val stranger = fixture.named("auth-stranger", "Stranger")

            assertTrue(fixture.history(stranger).isEmpty())
        }
    }

    @Test
    fun theOpponentIsNamed() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)

            val whitesView = fixture.history(fixture.white).single()
            val blacksView = fixture.history(fixture.black).single()

            assertEquals(fixture.black, whitesView.opponent.id)
            assertEquals(fixture.white, blacksView.opponent.id)
        }
    }

    @Test
    fun historyIsServedOverHttp() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)

            testApplication {
                application { testModule(tokens.verifier(), database) }

                fun authorized(subject: String) = "Bearer ${tokens.tokenFor(subject)}"

                client.post("/username") {
                    header("Authorization", authorized("auth-jordan"))
                    setBody("Jordan")
                }
                client.post("/username") {
                    header("Authorization", authorized("auth-alex"))
                    setBody("Alex")
                }
                client.post("/friends") {
                    header("Authorization", authorized("auth-jordan"))
                    setBody("Alex")
                }

                val series =
                    json.decodeFromString<com.jmussel.chessgame.server.api.SeriesSummary>(
                        client
                            .post("/series") {
                                header("Authorization", authorized("auth-jordan"))
                                setBody("Alex")
                            }.bodyAsText(),
                    )
                val gameId = assertNotNull(series.currentGameId)

                // Jordan gives up, which finishes the game whichever colour they drew.
                val resigned =
                    client.post("/games/$gameId/resignation") {
                        header("Authorization", authorized("auth-jordan"))
                        contentType(ContentType.Application.Json)
                        setBody("""{"expectedVersion":0}""")
                    }

                assertEquals(HttpStatusCode.OK, resigned.status)

                val history =
                    json.decodeFromString<List<SeriesHistoryEntry>>(
                        client.get("/history") { header("Authorization", authorized("auth-alex")) }.bodyAsText(),
                    )
                val entry = history.single()

                assertEquals("Jordan", entry.opponent.username)
                assertEquals("RESIGNATION", entry.games.single().terminationReason)
                assertEquals(gameId, entry.games.single().gameId)
            }
        }
    }

    @Test
    fun anEmptyHistoryIsAnEmptyList() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)

            testApplication {
                application { testModule(tokens.verifier(), database) }

                val response =
                    client.get("/history") {
                        header("Authorization", "Bearer ${tokens.tokenFor("auth-newcomer")}")
                    }

                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("[]", response.bodyAsText())
            }
        }
    }

    @Test
    fun historyNeedsAToken() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)

            testApplication {
                application { testModule(tokens.verifier(), database) }

                assertEquals(HttpStatusCode.Unauthorized, client.get("/history").status)
            }
        }
    }

    @Test
    fun aHistoricalGameCanStillBeReadInFullButNotPlayed() {
        withSeries { fixture ->
            fixture.playFoolsMate(fixture.firstGameId)

            val finished = fixture.game(fixture.firstGameId)

            // Read in full, and refusing everything: history is read-only because a
            // finished game is final, not because a separate rule says so.
            assertEquals(4, finished.game.history.size)

            val refused = fixture.commands.resign(fixture.white, fixture.firstGameId, finished.version)

            assertTrue(refused is CommandResult.GameOver)
            assertEquals(finished.version, fixture.game(fixture.firstGameId).version)
            assertEquals(finished.endedAt, fixture.game(fixture.firstGameId).endedAt)
        }
    }
}
