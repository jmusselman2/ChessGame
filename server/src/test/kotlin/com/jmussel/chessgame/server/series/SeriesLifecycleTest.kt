@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.series

import com.jmussel.chessgame.server.db.ACTIVE_SERIES
import com.jmussel.chessgame.server.db.CLOSED_SERIES
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GameSeriesRepository
import com.jmussel.chessgame.server.db.GameTypes
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
 * A series' lifecycle: active, then closed, each step safe to repeat.
 *
 * Until `M19.5` there was a step between the two: marked to close after the current game,
 * which removing a friend set (`D013`). `D053` superseded that, and the mark and the tests of
 * it went with it. A series now ends only by being closed — which `M19.8` makes a player's
 * explicit action — and removing a friend leaves it active.
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
            return series.openOrCreate(GameTypes.CHESS, listOf(jordan, alex)).series.id
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
    fun aNewSeriesIsActive() {
        withFixture { fixture ->
            val series = assertNotNull(fixture.series.find(fixture.openSeries()))

            assertEquals(ACTIVE_SERIES, series.status)
            assertTrue(series.isActive)
            assertNull(series.closedAt)
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
    fun aClosedSeriesIsNoLongerTheActiveOne() {
        withFixture { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            fixture.friendships.add(jordan, alex)
            val opened =
                fixture.series
                    .openOrCreate(GameTypes.CHESS, listOf(jordan, alex))
                    .series
            val id = opened.id
            fixture.series.close(id)

            assertTrue(fixture.series.activeAt(opened.tableId).isEmpty())
            assertNotNull(fixture.series.find(id), "it stays available for history")
        }
    }

    @Test
    fun removingAFriendLeavesTheSeriesActive() {
        withFixture { fixture ->
            val jordan = fixture.named("auth-1", "Jordan")
            val alex = fixture.named("auth-2", "Alex")
            fixture.friendships.add(jordan, alex)
            val id =
                fixture.series
                    .openOrCreate(GameTypes.CHESS, listOf(jordan, alex))
                    .series.id

            fixture.friendships.remove(jordan, alex)

            // Until `M19.5` this asserted the series was marked to close (`D013`). `D053`
            // superseded that: unfriending affects the friends list only.
            val series = assertNotNull(fixture.series.find(id))
            assertEquals(ACTIVE_SERIES, series.status)
            assertNull(series.closedAt)
        }
    }

    @Test
    fun closingAnUnknownSeriesDoesNothing() {
        withFixture { fixture ->
            assertFalse(fixture.series.close(Uuid.random()))
        }
    }
}
