package com.jmussel.chessgame.server.db

import java.sql.SQLException
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * What `V3__groups.sql` actually enforces (`D049`, `M19.2`).
 *
 * Only the structural half is here, because only the structural half is in the schema. The
 * two rules a column constraint cannot express — a group always contains its creator, and
 * the person added is a friend of the adder — need rows in other tables and live in
 * [GroupRepository]; `GroupMembershipTest` covers them.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class GroupSchemaTest {
    @Test
    fun theGroupTablesExist() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            listOf("groups", "group_members").forEach { table ->
                assertTrue(DatabaseTestSupport.tableExists(dataSource, table), "missing table $table")
            }
        }
    }

    @Test
    fun aGroupNeedsANameThatIsNotBlank() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val creator = insertUser(dataSource, "schema-creator", "Creator")

            assertFailsWith<SQLException> { insertGroup(dataSource, "", creator) }
            assertFailsWith<SQLException> { insertGroup(dataSource, "   ", creator) }
        }
    }

    @Test
    fun aGroupNameIsStoredTrimmedAndWithinLength() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val creator = insertUser(dataSource, "schema-creator-2", "Creator")

            // Trailing space is rejected rather than silently kept, so " Tuesday" and
            // "Tuesday" cannot both exist looking identical in a list.
            assertFailsWith<SQLException> { insertGroup(dataSource, "Tuesday ", creator) }
            assertFailsWith<SQLException> { insertGroup(dataSource, "x".repeat(49), creator) }

            insertGroup(dataSource, "x".repeat(48), creator)
            assertEquals(1, count(dataSource, "select count(*) from groups"))
        }
    }

    @Test
    fun aGroupNeedsACreatorThatExists() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            assertFailsWith<SQLException> {
                insertGroup(dataSource, "Tuesday", "00000000-0000-0000-0000-000000000000")
            }
        }
    }

    @Test
    fun someoneAppearsInAGroupAtMostOnce() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val creator = insertUser(dataSource, "schema-creator-4", "Creator")
            val group = insertGroup(dataSource, "Tuesday", creator)
            insertMembership(dataSource, group, creator, creator)

            // One row per person per group is what makes "are they in it right now" a single
            // row lookup, and what makes leaving-then-rejoining a revival.
            assertFailsWith<SQLException> { insertMembership(dataSource, group, creator, creator) }
        }
    }

    @Test
    fun aMembershipNeedsRealPeopleAndARealGroup() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val creator = insertUser(dataSource, "schema-creator-5", "Creator")
            val group = insertGroup(dataSource, "Tuesday", creator)
            val absent = "00000000-0000-0000-0000-000000000000"

            assertFailsWith<SQLException> { insertMembership(dataSource, absent, creator, creator) }
            assertFailsWith<SQLException> { insertMembership(dataSource, group, absent, creator) }
            assertFailsWith<SQLException> { insertMembership(dataSource, group, creator, absent) }
        }
    }

    @Test
    fun deletingAGroupTakesItsMembershipsWithIt() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val creator = insertUser(dataSource, "schema-creator-6", "Creator")
            val group = insertGroup(dataSource, "Tuesday", creator)
            insertMembership(dataSource, group, creator, creator)

            execute(dataSource, "delete from groups where id = ?::uuid", group)

            assertEquals(0, count(dataSource, "select count(*) from group_members"))
        }
    }

    @Test
    fun nothingInTheGroupTablesReferencesAGameOrASeries() {
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            // `D049`: a group has no presence in game state. The cheapest way to keep that
            // true is to assert it of the schema, where a future column would show up.
            val referenced =
                count(
                    dataSource,
                    """
                    select count(*)
                    from information_schema.columns
                    where table_schema = 'public'
                      and table_name in ('groups', 'group_members')
                      and (column_name like '%game%' or column_name like '%series%' or column_name like '%table%')
                    """.trimIndent(),
                )

            assertEquals(0, referenced, "a group must not know anything about games")
        }
    }

    private fun insertUser(
        dataSource: DataSource,
        subject: String,
        username: String,
    ): String =
        queryForString(
            dataSource,
            """
            insert into users (auth_subject, username, username_normalized)
            values (?, ?, lower(?))
            returning id::text
            """.trimIndent(),
            subject,
            username,
            username,
        )

    private fun insertGroup(
        dataSource: DataSource,
        name: String,
        createdBy: String,
    ): String =
        queryForString(
            dataSource,
            "insert into groups (name, created_by) values (?, ?::uuid) returning id::text",
            name,
            createdBy,
        )

    private fun insertMembership(
        dataSource: DataSource,
        groupId: String,
        userId: String,
        addedBy: String,
    ) = execute(
        dataSource,
        "insert into group_members (group_id, user_id, added_by) values (?::uuid, ?::uuid, ?::uuid)",
        groupId,
        userId,
        addedBy,
    )

    private fun execute(
        dataSource: DataSource,
        sql: String,
        vararg parameters: String?,
    ) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                parameters.forEachIndexed { index, value -> statement.setString(index + 1, value) }
                statement.execute()
            }
            connection.commit()
        }
    }

    private fun queryForString(
        dataSource: DataSource,
        sql: String,
        vararg parameters: String?,
    ): String =
        dataSource.connection.use { connection ->
            connection
                .prepareStatement(sql)
                .use { statement ->
                    parameters.forEachIndexed { index, value -> statement.setString(index + 1, value) }
                    statement.executeQuery().use { rows ->
                        check(rows.next()) { "no row returned" }
                        rows.getString(1)
                    }
                }.also { connection.commit() }
        }

    private fun count(
        dataSource: DataSource,
        sql: String,
    ): Int =
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rows ->
                    check(rows.next()) { "no row returned" }
                    rows.getInt(1)
                }
            }
        }
}
