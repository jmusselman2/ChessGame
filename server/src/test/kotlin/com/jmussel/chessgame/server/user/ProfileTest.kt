@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.user

import com.jmussel.chessgame.server.api.ProfileView
import com.jmussel.chessgame.server.api.RecordView
import com.jmussel.chessgame.server.api.SharedGroupView
import com.jmussel.chessgame.server.auth.TestTokens
import com.jmussel.chessgame.server.db.AddGroupMemberResult
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.GroupRepository
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.game.CommandResult
import com.jmussel.chessgame.server.game.GameCommandService
import com.jmussel.chessgame.server.series.seriesService
import com.jmussel.chessgame.server.testModule
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Relationship profiles (`D099`, `M22.5`): `GET /profiles/{userId}` for a friend or a current
 * shared-group member, with exactly username, UTC join month and year, the viewer's record
 * and the shared groups; `404` for everyone else.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class ProfileTest {
    private val tokens = TestTokens()
    private val json = Json { ignoreUnknownKeys = true }

    private class People(
        val database: Database,
    ) {
        val users = UserRepository(database)
        val friendships = FriendshipRepository(database)
        val groups = GroupRepository(database, friendships)

        fun named(
            subject: String,
            username: String,
        ): Uuid {
            val user = users.resolveBySubject(subject)
            users.claimUsername(user.id, Username.of(username))
            return user.id
        }

        fun joinedAt(
            user: Uuid,
            timestamp: String,
        ) = transaction(database) { exec("update users set created_at = '$timestamp'::timestamptz where id = '$user'") }
    }

    private fun withPeople(block: suspend ApplicationTestBuilder.(People) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            testApplication {
                application { testModule(tokens.verifier(), database) }
                block(People(database))
            }
        }

    private suspend fun ApplicationTestBuilder.profile(
        viewerSubject: String,
        subject: Uuid,
    ): HttpResponse = profile(viewerSubject, subject.toString())

    private suspend fun ApplicationTestBuilder.profile(
        viewerSubject: String,
        subject: String,
    ): HttpResponse = client.get("/profiles/$subject") { header("Authorization", "Bearer ${tokens.tokenFor(viewerSubject)}") }

    @Test
    fun aFriendSeesExactlyTheProfileFieldsByImmutableId() {
        withPeople { people ->
            val jordan = people.named("auth-1", "Jordan")
            val alex = people.named("auth-2", "Alex")
            people.friendships.add(jordan, alex)
            people.joinedAt(alex, "2026-09-14T10:00:00Z")

            val response = profile("auth-1", alex)

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertEquals(
                setOf("userId", "username", "joinedYear", "joinedMonth", "record", "sharedGroups"),
                json.parseToJsonElement(body).jsonObject.keys,
                "nothing else is sent: $body",
            )
            val view = json.decodeFromString<ProfileView>(body)
            assertEquals(alex.toString(), view.userId)
            assertEquals("Alex", view.username)
            assertEquals(2026, view.joinedYear)
            assertEquals(9, view.joinedMonth)
            assertEquals(RecordView(0, 0, 0), view.record)
            assertEquals(emptyList(), view.sharedGroups)
        }
    }

    @Test
    fun theJoinMonthIsTheUtcMonth() {
        withPeople { people ->
            val jordan = people.named("auth-1", "Jordan")
            val alex = people.named("auth-2", "Alex")
            people.friendships.add(jordan, alex)
            // 31 January in New York, 1 February in UTC.
            people.joinedAt(alex, "2026-01-31T23:30:00-05:00")

            val view = json.decodeFromString<ProfileView>(profile("auth-1", alex).bodyAsText())

            assertEquals(2026, view.joinedYear)
            assertEquals(2, view.joinedMonth)
        }
    }

    @Test
    fun theRecordIsTheViewersAgainstTheSubject() {
        withPeople { people ->
            val jordan = people.named("auth-1", "Jordan")
            val alex = people.named("auth-2", "Alex")
            people.friendships.add(jordan, alex)
            val games = GameRepository(people.database)
            val series = seriesService(people.database)
            val commands = GameCommandService(people.database, games, series, people.users)
            val gameId = assertNotNull(series.play(jordan, alex).series.currentGameId)
            assertIs<CommandResult.Applied>(commands.resign(alex, gameId, 0))

            assertEquals(RecordView(1, 0, 0), json.decodeFromString<ProfileView>(profile("auth-1", alex).bodyAsText()).record)
            assertEquals(RecordView(0, 1, 0), json.decodeFromString<ProfileView>(profile("auth-2", jordan).bodyAsText()).record)
        }
    }

    @Test
    fun aSharedGroupMemberWhoIsNotAFriendSeesOnlyTheSharedGroups() {
        withPeople { people ->
            val jordan = people.named("auth-1", "Jordan")
            val alex = people.named("auth-2", "Alex")
            val robin = people.named("auth-3", "Robin")
            people.friendships.add(jordan, alex)
            people.friendships.add(alex, robin)
            val tuesday = people.groups.create(jordan, "Tuesday")
            assertIs<AddGroupMemberResult.Added>(people.groups.addMember(tuesday.id, jordan, alex))
            assertIs<AddGroupMemberResult.Added>(people.groups.addMember(tuesday.id, alex, robin))
            // A group of Robin's that Jordan is not in, which Jordan must never see.
            people.groups.create(robin, "Robin's own")

            val view = json.decodeFromString<ProfileView>(profile("auth-1", robin).bodyAsText())

            assertEquals("Robin", view.username)
            assertEquals(listOf(SharedGroupView(tuesday.id.toString(), "Tuesday")), view.sharedGroups)
        }
    }

    @Test
    fun anUnrelatedCallerAnUnknownIdAndYourselfAllGetTheSame404() {
        withPeople { people ->
            val jordan = people.named("auth-1", "Jordan")
            val sam = people.named("auth-4", "Sam")

            val unrelated = profile("auth-1", sam)
            val unknown = profile("auth-1", Uuid.random())
            val yourself = profile("auth-1", jordan)
            val notAnId = profile("auth-1", "Sam")

            listOf(unrelated, unknown, yourself, notAnId).forEach { assertEquals(HttpStatusCode.NotFound, it.status) }
            assertEquals(unrelated.bodyAsText(), unknown.bodyAsText(), "an existing user is not confirmed")
        }
    }

    @Test
    fun aRemovedFriendOrALeftGroupRemovesAccessAtOnce() {
        withPeople { people ->
            val jordan = people.named("auth-1", "Jordan")
            val alex = people.named("auth-2", "Alex")
            val robin = people.named("auth-3", "Robin")
            people.friendships.add(jordan, alex)
            people.friendships.add(alex, robin)
            val tuesday = people.groups.create(jordan, "Tuesday")
            people.groups.addMember(tuesday.id, jordan, alex)
            people.groups.addMember(tuesday.id, alex, robin)
            assertEquals(HttpStatusCode.OK, profile("auth-1", robin).status)

            people.groups.leave(tuesday.id, robin)
            assertEquals(HttpStatusCode.NotFound, profile("auth-1", robin).status, "Robin left the only shared group")

            people.groups.leave(tuesday.id, alex)
            assertEquals(HttpStatusCode.OK, profile("auth-1", alex).status, "still friends")
            people.friendships.remove(jordan, alex)
            assertEquals(HttpStatusCode.NotFound, profile("auth-1", alex).status, "no longer friends or in a group")
        }
    }
}
