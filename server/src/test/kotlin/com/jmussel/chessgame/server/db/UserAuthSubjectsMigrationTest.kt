package com.jmussel.chessgame.server.db

import org.flywaydb.core.Flyway
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * `V11` (`M20.4`, `D082`): every installation a user had is carried to `user_auth_subjects`,
 * `users.auth_subject` goes, and the mapping keeps a subject to one user while letting a
 * user have many.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class UserAuthSubjectsMigrationTest {
    @Test
    fun everyExistingSubjectIsCarriedOverToTheSameUser() {
        DatabaseTestSupport.withEmptyDatabase { dataSource ->
            migrateTo(dataSource, "10")
            val named = insertBeforeV11(dataSource, "subject-named", "'Jordan'")
            val nameless = insertBeforeV11(dataSource, "subject-nameless", "null")

            Migrations.migrate(dataSource)

            assertEquals(
                listOf("subject-named $named", "subject-nameless $nameless").sorted(),
                rows(dataSource, "select auth_subject || ' ' || user_id from user_auth_subjects").sorted(),
            )
            assertEquals(
                2,
                count(
                    dataSource,
                    "select count(*) from user_auth_subjects s join users u on u.id = s.user_id where s.created_at = u.created_at",
                ),
                "each mapping is as old as the user it came from",
            )
            assertEquals("Jordan", rows(dataSource, "select username from users where id = '$named'").single())
            assertFalse(
                DatabaseTestSupport.count(
                    dataSource,
                    "select count(*) from information_schema.columns where table_name = 'users' and column_name = 'auth_subject'",
                ) > 0,
                "users.auth_subject is gone, so the mapping is the one source of truth",
            )
        }
    }

    @Test
    fun aSubjectMapsToOneUserAndAUserToManySubjects() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val user = rows(dataSource, "insert into users default values returning id::text").single()
            val other = rows(dataSource, "insert into users default values returning id::text").single()

            execute(dataSource, "insert into user_auth_subjects (auth_subject, user_id) values ('phone', '$user')")
            execute(dataSource, "insert into user_auth_subjects (auth_subject, user_id) values ('tablet', '$user')")

            assertFailsWith<SQLException>("one subject, two users") {
                execute(dataSource, "insert into user_auth_subjects (auth_subject, user_id) values ('phone', '$other')")
            }
            assertFailsWith<SQLException>("a subject for nobody") {
                execute(
                    dataSource,
                    "insert into user_auth_subjects (auth_subject, user_id) values ('ghost', '00000000-0000-0000-0000-000000000000')",
                )
            }
            assertFailsWith<SQLException>("a user who still has an installation cannot be deleted") {
                execute(dataSource, "delete from users where id = '$user'")
            }
        }
    }

    /** A user as the schema before `V11` stored one, with its subject in the row. */
    private fun insertBeforeV11(
        dataSource: DataSource,
        subject: String,
        usernameSql: String,
    ): String =
        rows(
            dataSource,
            "insert into users (auth_subject, username, username_normalized) " +
                "values ('$subject', $usernameSql, lower($usernameSql)) returning id::text",
        ).single()

    private fun migrateTo(
        dataSource: DataSource,
        version: String,
    ) {
        Flyway
            .configure()
            .dataSource(dataSource)
            .locations(Migrations.LOCATION)
            .table(Migrations.HISTORY_TABLE)
            .target(version)
            .load()
            .migrate()
    }

    private fun execute(
        dataSource: DataSource,
        statement: String,
    ) {
        dataSource.connection.use { connection ->
            connection.createStatement().use { it.execute(statement) }
            connection.commit()
        }
    }

    private fun count(
        dataSource: DataSource,
        query: String,
    ): Int = DatabaseTestSupport.count(dataSource, query)

    /** Every value of the first column [query] returns, committed if it wrote anything. */
    private fun rows(
        dataSource: DataSource,
        query: String,
    ): List<String> =
        dataSource.connection.use { connection ->
            connection
                .createStatement()
                .use { statement ->
                    statement.executeQuery(query).use { result ->
                        buildList { while (result.next()) add(result.getString(1)) }
                    }
                }.also { connection.commit() }
        }
}
