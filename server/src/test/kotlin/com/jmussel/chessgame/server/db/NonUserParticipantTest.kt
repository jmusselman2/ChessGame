@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.server.user.Username
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Participants that are not people (`D051`, `M19.7`).
 *
 * A seat names either a `users` row or a non-user participant, by kind, and a non-user
 * participant is never a person anywhere a person is shown or counted: not a friend, not on a
 * dashboard, not a `last_seen_at`, not a username. It keeps its own state across reloads.
 *
 * A test-only game type seats the mixed tables. No deck-builder type, rule, or state shape is
 * registered or assumed (`D063`); the state here is an opaque document.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class NonUserParticipantTest {
    private class Fixture(
        val dataSource: DataSource,
        val database: Database,
    ) {
        val users = UserRepository(database)
        val friendships = FriendshipRepository(database)
        val nonUsers = NonUserParticipantRepository(database)
        val tables = TableRepository(database)
        val series = GameSeriesRepository(database, tables)
        val games = GameRepository(database)

        fun named(username: String): Uuid {
            val user = users.resolveBySubject("auth-$username")
            users.claimUsername(user.id, Username.of(username))
            return user.id
        }

        fun registerTestType() = execute("insert into game_types values ('$TEST_TYPE', 2, 4)")

        fun execute(sql: String) {
            dataSource.connection.use { connection ->
                connection.createStatement().use { it.execute(sql) }
                connection.commit()
            }
        }

        fun userCount(): Int = transaction(database) { UsersTable.selectAll().count().toInt() }
    }

    private fun withParticipants(block: (Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            block(Fixture(dataSource, Databases.connect(dataSource)))
        }

    @Test
    fun aTableSeatsUsersAndNonUsersByKind() {
        withParticipants { fixture ->
            fixture.registerTestType()
            val ana = fixture.named("Ana")
            val ben = fixture.named("Ben")
            val scripted = fixture.nonUsers.create(ParticipantKind.SCRIPTED).participant
            val computer = fixture.nonUsers.create(ParticipantKind.COMPUTER).participant

            val table =
                fixture.tables.findOrCreate(
                    TEST_TYPE,
                    listOf(Participant.user(ana), scripted, Participant.user(ben), computer),
                )
            val again = fixture.tables.findOrCreate(TEST_TYPE, listOf(computer, Participant.user(ben), scripted, Participant.user(ana)))

            assertEquals(table.id, again.id, "the same set, whatever it is made of, is the same table")
            assertEquals(
                setOf(Participant.user(ana), Participant.user(ben), scripted, computer),
                assertNotNull(fixture.tables.find(table.id)).participants.toSet(),
                "each seat comes back as the kind it was written as",
            )
            assertEquals(listOf(ana, ben).sorted(), table.participants.userIds.sorted())
        }
    }

    @Test
    fun aGameSeatsThemInTurnOrder() {
        withParticipants { fixture ->
            fixture.registerTestType()
            val ana = Participant.user(fixture.named("Ana"))
            val scripted = fixture.nonUsers.create(ParticipantKind.SCRIPTED).participant
            val series = fixture.series.openOrCreate(TEST_TYPE, listOf(ana, scripted)).series

            val game = fixture.games.create(series.id, 1, listOf(ana, scripted), ChessGame.newGame())

            assertEquals(listOf(ana, scripted), fixture.games.load(game)?.participants)
        }
    }

    @Test
    fun aSeatNamesExactlyTheReferenceItsKindCallsFor() {
        withParticipants { fixture ->
            val ana = fixture.named("Ana")
            val table = fixture.tables.findOrCreate(GameTypes.CHESS, listOf(ana, fixture.named("Ben")))
            val scripted = fixture.nonUsers.create(ParticipantKind.SCRIPTED)

            // A USER seat with a non-user reference, a non-user seat with a user reference, a
            // seat naming both, and a seat whose kind is not its participant's kind.
            listOf(
                "insert into table_participants (table_id, seat_index, kind, non_user_participant_id) values ('${table.id}', 5, 'USER', '${scripted.id}')",
                "insert into table_participants (table_id, seat_index, kind, user_id) values ('${table.id}', 5, 'SCRIPTED', '$ana')",
                "insert into table_participants (table_id, seat_index, kind, user_id, non_user_participant_id) " +
                    "values ('${table.id}', 5, 'SCRIPTED', '$ana', '${scripted.id}')",
                "insert into table_participants (table_id, seat_index, kind, non_user_participant_id) values ('${table.id}', 5, 'COMPUTER', '${scripted.id}')",
                "insert into non_user_participants (kind) values ('USER')",
            ).forEach { sql ->
                assertFailsWith<SQLException>(sql) { fixture.execute(sql) }
            }

            // And a non-user participant takes one seat per table, like anyone.
            fixture.execute(
                "insert into table_participants (table_id, seat_index, kind, non_user_participant_id) values ('${table.id}', 5, 'SCRIPTED', '${scripted.id}')",
            )
            assertFailsWith<SQLException> {
                fixture.execute(
                    "insert into table_participants (table_id, seat_index, kind, non_user_participant_id) " +
                        "values ('${table.id}', 6, 'SCRIPTED', '${scripted.id}')",
                )
            }
        }
    }

    @Test
    fun aNonUserParticipantIsNeverAPerson() {
        withParticipants { fixture ->
            fixture.registerTestType()
            val ana = fixture.named("Ana")
            val ben = fixture.named("Ben")
            fixture.friendships.add(ana, ben)
            val usersBefore = fixture.userCount()

            val scripted = fixture.nonUsers.create(ParticipantKind.SCRIPTED)
            val computer = fixture.nonUsers.create(ParticipantKind.COMPUTER)
            val seats = listOf(Participant.user(ana), Participant.user(ben), scripted.participant, computer.participant)
            val series = fixture.series.openOrCreate(TEST_TYPE, seats).series
            fixture.games.create(series.id, 1, seats, ChessGame.newGame()).also { fixture.series.attachCurrentGame(series.id, it) }

            // Not a users row, so not a username, not a last_seen_at, and not findable.
            assertEquals(usersBefore, fixture.userCount())
            assertNull(fixture.users.find(scripted.id))
            assertNull(fixture.users.find(computer.id))

            // Not a friend.
            assertEquals(listOf(ben), fixture.friendships.friendsOf(ana))

            // Not an opponent on anyone's dashboard or in anyone's history.
            val dashboard = DashboardQueries(fixture.database).activeSeriesFor(ana)
            assertEquals(listOf(ben), dashboard.map { it.opponent.id }, "the only person at the table is the opponent")
            assertEquals(
                listOf(ben),
                fixture.series
                    .find(series.id)
                    ?.participants
                    ?.userIds
                    ?.filter { it != ana },
            )
        }
    }

    @Test
    fun itsStateSurvivesAReload() {
        withParticipants { fixture ->
            val created = fixture.nonUsers.create(ParticipantKind.SCRIPTED, JsonObject(mapOf("turnsTaken" to JsonPrimitive(0))))

            assertTrue(fixture.nonUsers.saveState(created.id, JsonObject(mapOf("turnsTaken" to JsonPrimitive(3)))))

            val reloaded = assertNotNull(NonUserParticipantRepository(Databases.connect(fixture.dataSource)).find(created.id))
            assertEquals(ParticipantKind.SCRIPTED, reloaded.kind)
            assertEquals(JsonPrimitive(3), reloaded.state["turnsTaken"])
        }
    }

    @Test
    fun aPersonCannotBeMadeANonUserParticipant() {
        withParticipants { fixture ->
            assertFailsWith<IllegalArgumentException> { fixture.nonUsers.create(ParticipantKind.USER) }
        }
    }

    private companion object {
        const val TEST_TYPE = "TEST_TWO_TO_FOUR"
    }
}
