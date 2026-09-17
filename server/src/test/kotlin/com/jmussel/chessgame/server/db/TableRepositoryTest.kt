@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.server.user.Username
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.SQLException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Tables and the participants relation (`D048`, `D063`, `M19.3`).
 *
 * What is being pinned: a table's size is checked against *its game type's* registered range,
 * read from `game_types` rather than assumed; chess is registered as exactly 2 and is the only
 * type; nothing in the schema caps a table at 2; and a table — and so a series — is identified
 * by its exact participant set.
 *
 * The ranges other than chess's belong to a test-only game type inserted into the disposable
 * database by the test that needs it. No deck-builder type is registered anywhere (`D063`).
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class TableRepositoryTest {
    private class Fixture(
        val dataSource: DataSource,
        val database: Database,
    ) {
        val users = UserRepository(database)
        val tables = TableRepository(database)
        val series = GameSeriesRepository(database, tables)

        fun named(username: String): Uuid {
            val user = users.resolveBySubject("auth-$username")
            users.claimUsername(user.id, Username.of(username))
            return user.id
        }

        /** Registers a game type that exists only in this test's disposable database. */
        fun registerTestType(
            min: Int,
            max: Int,
        ) = execute("insert into game_types (id, min_participants, max_participants) values ('$TEST_TYPE', $min, $max)")

        fun execute(sql: String) {
            dataSource.connection.use { connection ->
                connection.createStatement().use { it.execute(sql) }
                connection.commit()
            }
        }

        fun tableCount(): Int = transaction(database) { TablesTable.selectAll().count().toInt() }

        fun seatCount(): Int = transaction(database) { TableParticipantsTable.selectAll().count().toInt() }
    }

    private fun withTables(block: (Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            block(Fixture(dataSource, Databases.connect(dataSource)))
        }

    @Test
    fun chessIsTheOnlyRegisteredGameTypeAndSeatsExactlyTwo() {
        withTables { fixture ->
            val registered =
                transaction(fixture.database) {
                    GameTypesTable.selectAll().map { it[GameTypesTable.id] }
                }

            assertEquals(listOf(GameTypes.CHESS), registered)
            assertEquals(2..2, fixture.tables.participantRange(GameTypes.CHESS))
        }
    }

    @Test
    fun aChessTableRefusesEveryOtherSizeAndWritesNothing() {
        withTables { fixture ->
            val (jordan, alex, sam) = listOf("Jordan", "Alex", "Sam").map(fixture::named)

            val tooMany =
                assertFailsWith<TableSizeOutOfRangeException> {
                    fixture.tables.findOrCreate(GameTypes.CHESS, listOf(jordan, alex, sam))
                }
            assertEquals(2..2, tooMany.allowed)
            assertEquals(3, tooMany.size)

            assertFailsWith<TableSizeOutOfRangeException> {
                fixture.tables.findOrCreate(GameTypes.CHESS, listOf(jordan))
            }

            assertEquals(0, fixture.tableCount())
            assertEquals(0, fixture.seatCount())
        }
    }

    /**
     * The acceptance criterion that the check is not a disguised 2: a test-only type that seats
     * 2–4 accepts three and four, refuses five, and chess in the same database still refuses
     * three. Narrowing the registered range narrows what is accepted, with no code change —
     * the range is read, not remembered.
     */
    @Test
    fun theSizeCheckReadsTheGameTypesRegisteredRange() {
        withTables { fixture ->
            val (a, b, c, d, e) = listOf("Ana", "Ben", "Cat", "Dev", "Eli").map(fixture::named)
            fixture.registerTestType(min = 2, max = 4)

            assertEquals(
                3,
                fixture.tables
                    .findOrCreate(TEST_TYPE, listOf(a, b, c))
                    .participants.size,
            )
            assertEquals(
                4,
                fixture.tables
                    .findOrCreate(TEST_TYPE, listOf(a, b, c, d))
                    .participants.size,
            )
            assertFailsWith<TableSizeOutOfRangeException> {
                fixture.tables.findOrCreate(TEST_TYPE, listOf(a, b, c, d, e))
            }
            assertFailsWith<TableSizeOutOfRangeException> {
                fixture.tables.findOrCreate(GameTypes.CHESS, listOf(a, b, c))
            }

            fixture.execute("update game_types set max_participants = 3 where id = '$TEST_TYPE'")

            val narrowed =
                assertFailsWith<TableSizeOutOfRangeException> {
                    fixture.tables.findOrCreate(TEST_TYPE, listOf(b, c, d, e))
                }
            assertEquals(2..3, narrowed.allowed)
        }
    }

    @Test
    fun anUnregisteredGameTypeIsRefused() {
        withTables { fixture ->
            val (jordan, alex) = listOf("Jordan", "Alex").map(fixture::named)

            assertFailsWith<UnknownGameTypeException> {
                fixture.tables.findOrCreate("NOT_REGISTERED", listOf(jordan, alex))
            }
            assertEquals(0, fixture.tableCount())
        }
    }

    @Test
    fun nobodyIsSeatedTwice() {
        withTables { fixture ->
            val jordan = fixture.named("Jordan")

            assertFailsWith<IllegalArgumentException> {
                fixture.tables.findOrCreate(GameTypes.CHESS, listOf(jordan, jordan))
            }
            assertEquals(0, fixture.tableCount())
        }
    }

    @Test
    fun participantsWhoShareAnIdButNotAKindHaveOneKeyInEitherOrder() {
        // `M19-01`: the key ordered by ref alone, so a tie left it to input order.
        val id = Uuid.parse("00000000-0000-0000-0000-000000000001")
        val person = Participant.user(id)
        val scripted = Participant(ParticipantKind.SCRIPTED, id)

        val key = TableRepository.participantSetOf(listOf(person, scripted))

        assertEquals(key, TableRepository.participantSetOf(listOf(scripted, person)))
        assertEquals("SCRIPTED:$id,USER:$id", key)
    }

    @Test
    fun aSetOfUsersKeepsTheKeyTheMigrationWrote() {
        // V5 wrote 'USER:' || user_a_id || ',USER:' || user_b_id with user_a_id < user_b_id.
        val lower = Uuid.parse("00000000-0000-0000-0000-00000000000a")
        val higher = Uuid.parse("00000000-0000-0000-0000-00000000000b")

        assertEquals(
            "USER:$lower,USER:$higher",
            TableRepository.participantSetOf(listOf(Participant.user(higher), Participant.user(lower))),
        )
    }

    @Test
    fun theSameSetFindsTheSameTableWhateverOrderItIsNamedIn() {
        withTables { fixture ->
            val (jordan, alex) = listOf("Jordan", "Alex").map(fixture::named)

            val first = fixture.tables.findOrCreate(GameTypes.CHESS, listOf(jordan, alex))
            val again = fixture.tables.findOrCreate(GameTypes.CHESS, listOf(alex, jordan))

            assertEquals(first, again)
            assertEquals(listOf(jordan, alex).sorted(), first.participants.userIds, "seats are in id order")
            assertEquals(1, fixture.tableCount())
        }
    }

    @Test
    fun aDifferentSetIsADifferentTable() {
        withTables { fixture ->
            val (a, b, c) = listOf("Ana", "Ben", "Cat").map(fixture::named)
            fixture.registerTestType(min = 2, max = 4)

            val pair = fixture.tables.findOrCreate(TEST_TYPE, listOf(a, b))
            val otherPair = fixture.tables.findOrCreate(TEST_TYPE, listOf(a, c))
            val superset = fixture.tables.findOrCreate(TEST_TYPE, listOf(a, b, c))
            val sameSetOtherGame = fixture.tables.findOrCreate(GameTypes.CHESS, listOf(a, b))

            assertEquals(4, listOf(pair, otherPair, superset, sameSetOtherGame).map { it.id }.toSet().size)
        }
    }

    /** `D048`: series identity is an exact-set match — no overlap rule, no substitution. */
    @Test
    fun seriesIdentityIsAnExactSetMatch() {
        withTables { fixture ->
            val (a, b, c) = listOf("Ana", "Ben", "Cat").map(fixture::named)
            fixture.registerTestType(min = 2, max = 4)

            val threeOfUs = fixture.series.openOrCreate(TEST_TYPE, listOf(a, b, c))
            val sameThree = fixture.series.openOrCreate(TEST_TYPE, listOf(c, a, b))
            val twoOfUs = fixture.series.openOrCreate(TEST_TYPE, listOf(a, b))

            assertTrue(threeOfUs.created)
            assertEquals(threeOfUs.series.id, sameThree.series.id)
            assertEquals(false, sameThree.created)
            assertNotEquals(threeOfUs.series.id, twoOfUs.series.id)
            assertEquals(listOf(a, b, c).sorted(), threeOfUs.series.participants.userIds)
        }
    }

    @Test
    fun twoRequestsForTheSameNewTableAgreeOnOne() {
        withTables { fixture ->
            val (jordan, alex) = listOf("Jordan", "Alex").map(fixture::named)
            val requests = 8
            val start = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(requests)

            try {
                val results =
                    (1..requests)
                        .map { index ->
                            pool.submit<StoredTable> {
                                start.await()
                                val order = if (index % 2 == 0) listOf(jordan, alex) else listOf(alex, jordan)
                                fixture.tables.findOrCreate(GameTypes.CHESS, order)
                            }
                        }.also { start.countDown() }
                        .map { it.get(30, TimeUnit.SECONDS) }

                assertEquals(1, results.map { it.id }.toSet().size)
                assertEquals(1, fixture.tableCount())
                assertEquals(2, fixture.seatCount())
            } finally {
                pool.shutdownNow()
            }
        }
    }

    @Test
    fun theSchemaSeatsMoreThanTwo() {
        withTables { fixture ->
            val (a, b, c, d) = listOf("Ana", "Ben", "Cat", "Dev").map(fixture::named)
            fixture.registerTestType(min = 2, max = 4)
            val table = fixture.tables.findOrCreate(TEST_TYPE, listOf(a, b, c, d))
            val series = fixture.series.openOrCreate(TEST_TYPE, table.participants).series

            val game =
                GameRepository(fixture.database).create(
                    seriesId = series.id,
                    sequenceNumber = 1,
                    users = listOf(c, a, d, b),
                    game = ChessGame.newGame(),
                )

            assertEquals(listOf(c, a, d, b), GameRepository(fixture.database).load(game)?.participants?.userIds)
        }
    }

    @Test
    fun theSchemaRefusesWhatARegistrationOrASeatCannotMean() {
        withTables { fixture ->
            val jordan = fixture.named("Jordan")
            val table = fixture.tables.findOrCreate(GameTypes.CHESS, listOf(jordan, fixture.named("Alex")))

            // A table always has at least two participants (`D048`), and a range runs upwards.
            assertFailsWith<SQLException> {
                fixture.execute("insert into game_types values ('SOLO', 1, 1)")
            }
            assertFailsWith<SQLException> {
                fixture.execute("insert into game_types values ('BACKWARDS', 4, 2)")
            }
            // Every participant is a user until M19.7, and a user sits at a table once.
            assertFailsWith<SQLException> {
                fixture.execute(
                    "insert into table_participants (table_id, seat_index, kind, user_id) " +
                        "values ('${table.id}', 2, 'ENEMY', null)",
                )
            }
            assertFailsWith<SQLException> {
                fixture.execute(
                    "insert into table_participants (table_id, seat_index, user_id) values ('${table.id}', 2, '$jordan')",
                )
            }
            assertEquals(2, fixture.seatCount())
        }
    }

    private companion object {
        /** Registered only inside the disposable database of the test that needs it. */
        const val TEST_TYPE = "TEST_TWO_TO_FOUR"
    }
}
