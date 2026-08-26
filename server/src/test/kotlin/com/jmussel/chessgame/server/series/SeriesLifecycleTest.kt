@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.series

import com.jmussel.chessgame.server.db.ACTIVE_SERIES
import com.jmussel.chessgame.server.db.CLOSED_SERIES
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GameSeriesRepository
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.user.Username
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A series' lifecycle: active, marked to close after its current game, then closed — each
 * step safe to repeat.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class SeriesLifecycleTest {
    private class Fixture(
        val database: Database,
        val users: UserRepository,
        val friendships: FriendshipRepository,
        val series: GameSeriesRepository,
    ) {
        fun openSeries(): Uuid {
            val jordan = named("auth-1", "Jordan")
            val alex = named("auth-2", "Alex")
            friendships.add(jordan, alex)
            return series.openOrCreate(jordan, alex).series.id
        }

        fun named(
            subject: String,
            username: String,
        ): Uuid {
            val user = users.resolveBySubject(subject)
            users.claimUsername(user.id, Username.of(username))
            return user.id
        }
    }

    private fun withFixture(block: (Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            block(
                Fixture(
                    database,
                    UserRepository(database),
                    FriendshipRepository(database),
                    GameSeriesRepository(database),
                ),
            )
        }

    @Test
    fun aNewSeriesIsActiveAndNotClosing() {
        withFixture { fixture ->
            val series = assertNotNull(fixture.series.find(fixture.openSeries()))

            assertEquals(ACTIVE_SERIES, series.status)
            assertTrue(series.isActive)
            assertFalse(series.closeAfterCurrentGame)
            assertNull(series.closedAt)
        }
    }

    @Test
    fun aSeriesCanBeMarkedToCloseAfterItsCurrentGame() {
        withFixture { fixture ->
            val id = fixture.openSeries()

            assertTrue(fixture.series.markCloseAfterCurrentGame(id))

            val series = assertNotNull(fixture.series.find(id))

            assertTrue(series.closeAfterCurrentGame)
            assertEquals(ACTIVE_SERIES, series.status, "it stays active until the game ends")
        }
    }

    @Test
    fun markingTwiceChangesNothing() {
        withFixture { fixture ->
            val id = fixture.openSeries()
            fixture.series.markCloseAfterCurrentGame(id)

            assertFalse(fixture.series.markCloseAfterCurrentGame(id), "already marked")
            assertTrue(assertNotNull(fixture.series.find(id)).closeAfterCurrentGame)
        }
    }

    @Test
    fun closingASeriesRecordsWhen() {
        withFixture { fixture ->
            val id = fixture.openSeries()
            val at = Instant.parse("2026-08-26T12:00:00Z")

            assertTrue(fixture.series.close(id, at))

            val series = assertNotNull(fixture.series.find(id))

            assertEquals(CLOSED_SERIES, series.status)
            assertFalse(series.isActive)
            assertEquals(at, series.closedAt)
        }
    }

    @Test
    fun closingTwiceLeavesTheFirstCloseAlone() {
        withFixture { fixture ->
            val id = fixture.openSeries()
            val first = Instant.parse("2026-08-26T12:00:00Z")
            fixture.series.close(id, first)

            assertFalse(fixture.series.close(id, Instant.parse("2026-08-26T13:00:00Z")))
            assertEquals(first, assertNotNull(fixture.series.find(id)).closedAt)
        }
    }

    @Test
    fun aMarkedSeriesClosesWhenAsked() {
        withFixture { fixture ->
            val id = fixture.openSeries()
            fixture.series.markCloseAfterCurrentGame(id)

            assertTrue(fixture.series.closeIfMarked(id))
            assertEquals(CLOSED_SERIES, assertNotNull(fixture.series.find(id)).status)
        }
    }

    @Test
    fun anUnmarkedSeriesStaysActive() {
        withFixture { fixture ->
            val id = fixture.openSeries()

            assertFalse(fixture.series.closeIfMarked(id), "this series still wants its rematch")
            assertEquals(ACTIVE_SERIES, assertNotNull(fixture.series.find(id)).status)
        }
    }

    @Test
    fun closingAMarkedSeriesTwiceIsSafe() {
        withFixture { fixture ->
            val id = fixture.openSeries()
            fixture.series.markCloseAfterCurrentGame(id)
            val at = Instant.parse("2026-08-26T12:00:00Z")
            fixture.series.closeIfMarked(id, at)

            assertFalse(fixture.series.closeIfMarked(id, Instant.parse("2026-08-26T14:00:00Z")))
            assertEquals(at, assertNotNull(fixture.series.find(id)).closedAt)
        }
    }

    @Test
    fun aClosedSeriesCannotBeMarkedAgain() {
        withFixture { fixture ->
            val id = fixture.openSeries()
            fixture.series.close(id)

            assertFalse(fixture.series.markCloseAfterCurrentGame(id))
        }
    }

    @Test
    fun aClosedSeriesIsNoLongerTheActiveOne() {
        withFixture { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            fixture.friendships.add(jordan, alex)
            val id =
                fixture.series
                    .openOrCreate(jordan, alex)
                    .series.id
            fixture.series.close(id)

            assertNull(fixture.series.findActive(jordan, alex))
            assertNotNull(fixture.series.find(id), "it stays available for history")
        }
    }

    @Test
    fun removingAFriendMarksTheSeriesTheSameWay() {
        withFixture { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            fixture.friendships.add(jordan, alex)
            val id =
                fixture.series
                    .openOrCreate(jordan, alex)
                    .series.id

            fixture.friendships.remove(jordan, alex)

            assertTrue(assertNotNull(fixture.series.find(id)).closeAfterCurrentGame)
            assertTrue(fixture.series.closeIfMarked(id))
        }
    }

    @Test
    fun markingAnUnknownSeriesDoesNothing() {
        withFixture { fixture ->
            assertFalse(fixture.series.markCloseAfterCurrentGame(Uuid.random()))
            assertFalse(fixture.series.closeIfMarked(Uuid.random()))
            assertFalse(fixture.series.close(Uuid.random()))
        }
    }
}
