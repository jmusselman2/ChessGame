@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi

/** Evaluator regression for M19-01; production code is intentionally unchanged. */
class M19ParticipantIdentityRegressionTest {
    @Test
    fun participantKindIsPartOfExactSetIdentity() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val user = UserRepository(database).resolveBySubject("m19-evaluator-kind-identity")

            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("insert into game_types values ('EVALUATOR_TWO', 2, 2)")
                    statement.execute(
                        "insert into non_user_participants (id, kind) values ('${user.id}', 'SCRIPTED')",
                    )
                }
                connection.commit()
            }

            val person = Participant.user(user.id)
            val scripted = Participant(ParticipantKind.SCRIPTED, user.id)
            val tables = TableRepository(database)

            val first = tables.findOrCreate("EVALUATOR_TWO", listOf(person, scripted))
            val reverse = tables.findOrCreate("EVALUATOR_TWO", listOf(scripted, person))

            assertEquals(first.id, reverse.id, "one exact participant set must have one table in either input order")
            assertEquals(setOf(person, scripted), first.participants.toSet(), "kind and ref together identify a participant")
        }
    }
}
