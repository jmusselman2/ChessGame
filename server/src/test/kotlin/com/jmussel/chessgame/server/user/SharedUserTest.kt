@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.user

import com.jmussel.chessgame.server.api.CurrentUser
import com.jmussel.chessgame.server.api.GroupSummary
import com.jmussel.chessgame.server.api.UserSummary
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.realtime.PlayerClient
import com.jmussel.chessgame.server.realtime.RealtimeHub
import com.jmussel.chessgame.server.realtime.RealtimeMessage
import com.jmussel.chessgame.server.realtime.nextMessage
import com.jmussel.chessgame.server.testModule
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.close
import kotlinx.serialization.json.Json
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

/**
 * Two installations that claimed one name are one user (`D082`, `M20.4`).
 *
 * They share what belongs to the user — the username, friends and groups — and both hear
 * about it in realtime. Games stay what they were: attaching an installation moves, copies
 * or creates no table, series or game, and chess's are still chess's.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class SharedUserTest {
    private val tokens = TestTokens()
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun twoInstallationsShareTheUsernameFriendsAndGroups() {
        withServer { _ ->
            val jordan = player("auth-jordan-phone")
            val alex = player("auth-alex")
            val jordansTablet = player("auth-jordan-tablet")

            jordan.claimUsername("Jordan")
            alex.claimUsername("Alex")
            jordan.addFriend("Alex")
            val group = createGroup("auth-jordan-phone", "Tuesday club")

            val claim = jordansTablet.claimUsername("jordan")

            assertEquals(HttpStatusCode.OK, claim.status)
            assertEquals("Jordan", claim.bodyAsText())
            assertEquals(me("auth-jordan-phone"), me("auth-jordan-tablet"))
            assertEquals(CurrentUser(jordan.userId().toString(), "Jordan"), me("auth-jordan-tablet"))
            assertEquals(listOf("Alex"), friendsOf("auth-jordan-tablet").map { it.username })
            assertEquals(listOf(group), groupsOf("auth-jordan-tablet"))

            // And it is one friendship, seen from the other side too.
            assertEquals(listOf("Jordan"), friendsOf("auth-alex").map { it.username })

            // A friend made on either installation is a friend on both.
            player("auth-sam").claimUsername("Sam")
            jordansTablet.addFriend("Sam")
            assertEquals(listOf("Alex", "Sam"), friendsOf("auth-jordan-phone").map { it.username }.sorted())
        }
    }

    @Test
    fun bothInstallationsHearAboutTheUsersGames() {
        withServer { _ ->
            val jordan = player("auth-jordan-phone")
            val alex = player("auth-alex")
            val jordansTablet = player("auth-jordan-tablet")
            jordan.claimUsername("Jordan")
            alex.claimUsername("Alex")
            alex.addFriend("Jordan")
            jordansTablet.claimUsername("Jordan")

            val phone = jordan.connect()
            val tablet = jordansTablet.connect()

            val gameId = alex.openSeries("Jordan").currentGameId

            listOf(phone, tablet).forEach { socket ->
                val update = socket.nextMessage()
                assertEquals(RealtimeMessage.GAME_UPDATED, update.type)
                assertEquals(gameId, update.gameId)
            }

            phone.close()
            tablet.close()
        }
    }

    @Test
    fun attachingAnInstallationLeavesGameDataWhereItWas() {
        withServer { dataSource ->
            val jordan = player("auth-jordan-phone")
            val alex = player("auth-alex")
            val jordansTablet = player("auth-jordan-tablet")
            jordan.claimUsername("Jordan")
            alex.claimUsername("Alex")
            jordan.addFriend("Alex")
            val series = jordan.openSeries("Alex")
            val gameData = gameRowCounts(dataSource)

            jordansTablet.claimUsername("Jordan")

            assertEquals(gameData, gameRowCounts(dataSource), "the claim touched no table, series, game or event")
            assertEquals(
                0,
                DatabaseTestSupport.count(dataSource, "select count(*) from tables where game_type <> 'CHESS'"),
                "every table is still a chess table",
            )
            // The tablet is the same user in the same app, so it sees the same chess series,
            // and only those: nothing was copied to it or made for it.
            assertEquals(jordan.dashboard(), jordansTablet.dashboard())
            assertEquals(listOf(series.seriesId), jordansTablet.dashboard().map { it.seriesId })
        }
    }

    private fun gameRowCounts(dataSource: DataSource): Map<String, Int> =
        listOf("tables", "table_participants", "game_series", "games", "game_participants", "moves", "game_events")
            .associateWith { DatabaseTestSupport.count(dataSource, "select count(*) from $it") }
            .also { counts -> assertTrue(counts.getValue("games") > 0, "there is a game to leave alone") }

    private lateinit var builder: ApplicationTestBuilder

    private fun player(subject: String) = PlayerClient(builder, tokens, subject)

    private suspend fun me(subject: String): CurrentUser = json.decodeFromString(get(subject, "/me"))

    private suspend fun friendsOf(subject: String): List<UserSummary> = json.decodeFromString(get(subject, "/friends"))

    private suspend fun groupsOf(subject: String): List<GroupSummary> = json.decodeFromString(get(subject, "/groups"))

    private suspend fun createGroup(
        subject: String,
        name: String,
    ): GroupSummary =
        json.decodeFromString(
            builder.client
                .post("/groups") {
                    header("Authorization", "Bearer ${tokens.tokenFor(subject)}")
                    setBody(name)
                }.bodyAsText(),
        )

    private suspend fun get(
        subject: String,
        path: String,
    ): String =
        builder.client
            .get(path) { header("Authorization", "Bearer ${tokens.tokenFor(subject)}") }
            .bodyAsText()

    private fun withServer(block: suspend ApplicationTestBuilder.(DataSource) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            testApplication {
                application { testModule(tokens.verifier(), Databases.connect(dataSource), realtime = RealtimeHub()) }
                builder = this
                block(dataSource)
            }
        }
}
