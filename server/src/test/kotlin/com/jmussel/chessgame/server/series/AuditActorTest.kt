@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.series

import com.jmussel.chessgame.server.api.CurrentUser
import com.jmussel.chessgame.server.api.GameView
import com.jmussel.chessgame.server.api.SeriesSummary
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.GameSeriesRepository
import com.jmussel.chessgame.server.db.StoredGameEvent
import com.jmussel.chessgame.server.testModule
import io.ktor.client.request.HttpRequestBuilder
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
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Every audit event names who caused it and the series it belongs to (`D078`, `M17.13`).
 *
 * Driven through the routes, so each event is the one a real command writes: the player
 * who sent the command is its actor, the game's series is its series, and a game's end and
 * the rematch that follows it are caused by the command that ended the game.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class AuditActorTest {
    private val tokens = TestTokens()
    private val json = Json { ignoreUnknownKeys = true }

    /** Two players at one series, and where to read its audit log. */
    private class Table(
        val seriesId: Uuid,
        val firstGameId: String,
        val ids: Map<String, Uuid>,
        val events: () -> List<StoredGameEvent>,
    )

    private fun withTable(block: suspend ApplicationTestBuilder.(Table) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val repository = GameSeriesRepository(database)

            testApplication {
                application { testModule(tokens.verifier(), database) }

                listOf(JORDAN to "Jordan", ALEX to "Alex").forEach { (subject, name) ->
                    client.post("/username") {
                        authorizedAs(subject)
                        setBody(name)
                    }
                }
                client.post("/friends") {
                    authorizedAs(JORDAN)
                    setBody("Alex")
                }

                val series =
                    json.decodeFromString<SeriesSummary>(
                        client
                            .post("/series") {
                                authorizedAs(JORDAN)
                                setBody("Alex")
                            }.bodyAsText(),
                    )
                val seriesId = Uuid.parse(series.seriesId)
                val ids = listOf(JORDAN, ALEX).associateWith { subject -> Uuid.parse(me(subject).userId) }

                block(Table(seriesId, series.currentGameId!!, ids) { repository.auditEvents(seriesId) })
            }
        }

    private fun HttpRequestBuilder.authorizedAs(subject: String) = header("Authorization", "Bearer ${tokens.tokenFor(subject)}")

    private suspend fun ApplicationTestBuilder.me(subject: String): CurrentUser =
        json.decodeFromString(client.get("/me") { authorizedAs(subject) }.bodyAsText())

    private suspend fun ApplicationTestBuilder.game(
        subject: String,
        gameId: String,
    ): GameView = json.decodeFromString(client.get("/games/$gameId") { authorizedAs(subject) }.bodyAsText())

    private suspend fun ApplicationTestBuilder.command(
        subject: String,
        path: String,
        body: String,
    ) {
        val response =
            client.post(path) {
                authorizedAs(subject)
                contentType(ContentType.Application.Json)
                setBody(body)
            }

        assertEquals(HttpStatusCode.OK, response.status, "$path: ${response.bodyAsText()}")
    }

    /** Plays [moves] in [gameId] alternately, whoever is to move sending each one. */
    private suspend fun ApplicationTestBuilder.play(
        gameId: String,
        vararg moves: String,
    ) {
        moves.forEach { move ->
            val mover = listOf(JORDAN, ALEX).first { game(it, gameId).yourTurn }
            val version = game(mover, gameId).version

            command(mover, "/games/$gameId/moves", """{"expectedVersion":$version,"from":"${move.take(2)}","to":"${move.drop(2)}"}""")
        }
    }

    private suspend fun ApplicationTestBuilder.toMove(gameId: String): String = listOf(JORDAN, ALEX).first { game(it, gameId).yourTurn }

    private fun List<StoredGameEvent>.single(type: String): StoredGameEvent = filter { it.type == type }.single()

    @Test
    fun aMoveAndAnUndoNameThePlayerWhoSentThem() =
        withTable { table ->
            val mover = toMove(table.firstGameId)
            play(table.firstGameId, "e2e4")
            command(mover, "/games/${table.firstGameId}/undo", """{"expectedVersion":1}""")

            val events = table.events()

            assertEquals(table.ids.getValue(mover), events.single(MOVE_MADE).actorId)
            assertEquals(table.ids.getValue(mover), events.single(MOVE_UNDONE).actorId)
            events.forEach { assertEquals(table.seriesId, it.seriesId, "${it.type} belongs to the series") }
        }

    @Test
    fun aResignationEndsTheGameAndStartsTheRematchInTheResignersName() =
        withTable { table ->
            val resigner = toMove(table.firstGameId)
            command(resigner, "/games/${table.firstGameId}/resignation", """{"expectedVersion":0}""")

            val events = table.events()
            val actor = table.ids.getValue(resigner)

            assertEquals(listOf(PLAYER_RESIGNED, GAME_ENDED, REMATCH_CREATED), events.map { it.type })
            events.forEach { event ->
                assertEquals(actor, event.actorId, "${event.type} is the resigner's doing")
                assertEquals(table.seriesId, event.seriesId)
            }
        }

    @Test
    fun aMateEndsTheGameInTheMatingPlayersName() =
        withTable { table ->
            // Fool's mate: the side that moves first plays f3 and g4, the other e5 and Qh4#.
            val mater = listOf(JORDAN, ALEX).first { it != toMove(table.firstGameId) }
            play(table.firstGameId, "f2f3", "e7e5", "g2g4", "d8h4")

            val events = table.events()
            val actor = table.ids.getValue(mater)

            assertEquals(actor, events.single(GAME_ENDED).actorId)
            assertEquals(actor, events.single(REMATCH_CREATED).actorId)
            assertEquals(actor, events.filter { it.type == MOVE_MADE }.last().actorId)
        }

    @Test
    fun aDrawClaimEndsTheGameInTheClaimantsName() =
        withTable { table ->
            // The knights go out and back twice: the starting position occurs a third time.
            play(table.firstGameId, "g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8")
            val claimant = toMove(table.firstGameId)
            command(claimant, "/games/${table.firstGameId}/draw-claims", """{"expectedVersion":8,"claim":"THREEFOLD_REPETITION"}""")

            val events = table.events()
            val actor = table.ids.getValue(claimant)

            assertEquals(actor, events.single(DRAW_CLAIMED).actorId)
            assertEquals(actor, events.single(GAME_ENDED).actorId)
            assertEquals(actor, events.single(REMATCH_CREATED).actorId)
        }

    @Test
    fun leavingNamesThePlayerWhoLeft() =
        withTable { table ->
            val response = client.post("/series/${table.seriesId}/leave") { authorizedAs(ALEX) }
            assertEquals(HttpStatusCode.OK, response.status)

            val left = table.events().single(SERIES_LEFT)

            assertEquals(table.ids.getValue(ALEX), left.actorId)
            assertEquals(table.seriesId, left.seriesId)
        }

    @Test
    fun theSeriesLogHoldsItsGamesEventsInOrder() =
        withTable { table ->
            play(table.firstGameId, "e2e4", "e7e5")
            val resigner = toMove(table.firstGameId)
            command(resigner, "/games/${table.firstGameId}/resignation", """{"expectedVersion":2}""")

            assertEquals(
                listOf(MOVE_MADE, MOVE_MADE, PLAYER_RESIGNED, GAME_ENDED, REMATCH_CREATED),
                table.events().map { it.type },
            )
        }

    private companion object {
        const val JORDAN = "auth-jordan"
        const val ALEX = "auth-alex"

        const val MOVE_MADE = "MoveMade"
        const val MOVE_UNDONE = "MoveUndone"
        const val DRAW_CLAIMED = "DrawClaimed"
        const val PLAYER_RESIGNED = "PlayerResigned"
        const val GAME_ENDED = "GameEnded"
        const val REMATCH_CREATED = "RematchCreated"
        const val SERIES_LEFT = "SeriesLeft"
    }
}
