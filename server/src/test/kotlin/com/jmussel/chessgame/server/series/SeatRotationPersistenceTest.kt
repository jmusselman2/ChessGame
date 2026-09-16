@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.series

import com.jmussel.chessgame.server.db.GameSeriesRepository
import com.jmussel.chessgame.server.db.GameSeriesTable
import com.jmussel.chessgame.server.db.TableRepository
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi

/**
 * A series remembers where it is in its seat rotation (`D050`, `M19.6`).
 *
 * The rotation is kept per series: its cycle length, the current cycle's base order, which game
 * of the cycle was played last, and the previous cycle's base order. Chess plays it as the
 * N = 2 case, so these tests also check that what is stored is what the games actually did.
 *
 * Skipped when this machine has no test database (see `DatabaseTestSupport`).
 */
class SeatRotationPersistenceTest {
    @Test
    fun theFirstGameOpensTheFirstCycle() {
        withSeries { fixture ->
            val rotation = assertNotNull(fixture.series().seatRotation)
            val first = fixture.game(fixture.firstGameId)

            assertEquals(2, rotation.cycleLength)
            assertEquals(0, rotation.gameInCycle)
            assertEquals(first.participants, rotation.baseOrder, "the base order is the first game's turn order")
            assertNull(rotation.previousBaseOrder)
        }
    }

    @Test
    fun eachRematchMovesTheRotationOnAndCrossesIntoTheNextCycle() {
        withSeries { fixture ->
            val firstOrder = fixture.game(fixture.firstGameId).participants
            fixture.playFoolsMate(fixture.firstGameId)

            val second = fixture.series()
            assertEquals(1, assertNotNull(second.seatRotation).gameInCycle)
            assertEquals(firstOrder.reversed(), fixture.currentGame().participants)

            fixture.playFoolsMate(fixture.currentGame().id)

            val third = assertNotNull(fixture.series().seatRotation)
            assertEquals(0, third.gameInCycle, "a new cycle")
            assertEquals(firstOrder, third.previousBaseOrder, "which remembers the one before")
            assertEquals(firstOrder, fixture.currentGame().participants, "and at two seats the colours keep alternating")
        }
    }

    /** A series from before `V8` has no rotation recorded; its next rematch still reverses the colours. */
    @Test
    fun aSeriesWithNoRecordedRotationPicksItUpFromTheGameThatEnded() {
        withSeries { fixture ->
            transaction(fixture.database) {
                GameSeriesTable.update({ GameSeriesTable.id eq fixture.seriesId }) { row ->
                    row[GameSeriesTable.seatRotation] = null
                }
            }
            val firstOrder = fixture.game(fixture.firstGameId).participants

            fixture.playFoolsMate(fixture.firstGameId)

            assertEquals(firstOrder.reversed(), fixture.currentGame().participants)
            val rotation = assertNotNull(fixture.series().seatRotation)
            assertEquals(firstOrder, rotation.baseOrder)
            assertEquals(1, rotation.gameInCycle)
        }
    }

    /** The storage is not two-seat shaped: a four-seat cycle with a previous cycle round-trips. */
    @Test
    fun aLongerCycleRoundTrips() {
        withSeries { fixture ->
            val c = fixture.named("auth-cat", "Cat")
            val d = fixture.named("auth-dev", "Dev")
            transaction(fixture.database) { exec("insert into game_types values ('TEST_TWO_TO_FOUR', 2, 4)") }
            val seats = listOf(fixture.white, fixture.black, c, d)
            val series = GameSeriesRepository(fixture.database, TableRepository(fixture.database))
            val opened = series.openOrCreate("TEST_TWO_TO_FOUR", seats).series
            val cycle = SeatCycle(baseOrder = listOf(d, c, fixture.black, fixture.white), gameInCycle = 3, previousBaseOrder = seats)

            series.saveSeatRotation(opened.id, cycle)

            assertEquals(cycle, series.find(opened.id)?.seatRotation)
        }
    }
}
