package com.jmussel.chessgame.server.series

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `D050`'s required property test: seat rotation by cycle, over N ∈ {2, 3, 4}.
 *
 * 1. Within any single cycle, every seat is first exactly once and last exactly once.
 * 2. For N ≥ 3, consecutive cycles' base orders are not rotations of each other.
 * 3. For N ≥ 3, a cycle's base order *may* be a rotation of the cycle two before it — only the
 *    immediately preceding cycle is excluded.
 * 4. An enemy participant is absent from every base order and takes the final turn of every
 *    game.
 *
 * Each property is checked over many cycles under many seeds rather than one hand-picked run,
 * and the N = 2 case is also checked against `D014`: it is chess's per-game alternation.
 *
 * Pure; no database.
 */
class SeatRotationTest {
    private val sizes = listOf(2, 3, 4)
    private val seeds = 0L until 25L

    /** [cycles] whole cycles of games, each game's order, grouped by cycle. */
    private fun play(
        n: Int,
        seed: Long,
        cycles: Int,
    ): List<Pair<SeatCycle<String>, List<List<String>>>> {
        val random = Random(seed)
        var cycle = SeatRotation.firstCycle(seatsOf(n), random)
        val played = mutableListOf<Pair<SeatCycle<String>, List<List<String>>>>()

        repeat(cycles) {
            val start = cycle
            val games = mutableListOf<List<String>>()
            repeat(n) {
                games += SeatRotation.turnOrder(cycle)
                cycle = SeatRotation.next(cycle, random)
            }
            played += start to games
        }

        return played
    }

    private fun seatsOf(n: Int): List<String> = (0 until n).map { "P$it" }

    @Test
    fun withinACycleEverySeatIsFirstOnceAndLastOnce() {
        for (n in sizes) {
            for (seed in seeds) {
                play(n, seed, cycles = 12).forEachIndexed { index, (_, games) ->
                    assertEquals(n, games.size)
                    assertEquals(seatsOf(n).toSet(), games.map { it.first() }.toSet(), "N=$n seed=$seed cycle=$index: first")
                    assertEquals(seatsOf(n).toSet(), games.map { it.last() }.toSet(), "N=$n seed=$seed cycle=$index: last")
                }
            }
        }
    }

    @Test
    fun consecutiveCyclesAreNotRotationsOfEachOther() {
        for (n in sizes.filter { it >= 3 }) {
            for (seed in seeds) {
                play(n, seed, cycles = 12).zipWithNext().forEach { (before, after) ->
                    assertFalse(
                        SeatRotation.isRotationOf(after.first.baseOrder, before.first.baseOrder),
                        "N=$n seed=$seed: ${after.first.baseOrder} rotates ${before.first.baseOrder}",
                    )
                    assertEquals(before.first.baseOrder, after.first.previousBaseOrder, "each cycle remembers the one before")
                }
            }
        }
    }

    @Test
    fun aCycleMayRepeatTheFamilyOfTheCycleTwoBefore() {
        for (n in sizes.filter { it >= 3 }) {
            // As a rule: nothing about the cycle two before is excluded from the candidates.
            val twoBefore = seatsOf(n)
            val before = SeatRotation.nextBaseOrderCandidates(twoBefore).first()
            val candidates = SeatRotation.nextBaseOrderCandidates(before)

            assertTrue(
                (0 until n).map { SeatRotation.rotate(twoBefore, it) }.all { it in candidates },
                "N=$n: every rotation of the cycle two before is still allowed",
            )

            // And in practice: it happens.
            val repeatsSeen =
                seeds.sumOf { seed ->
                    play(n, seed, cycles = 12).windowed(3).count { (twoAgo, _, now) ->
                        SeatRotation.isRotationOf(now.first.baseOrder, twoAgo.first.baseOrder)
                    }
                }
            assertTrue(repeatsSeen > 0, "N=$n: a cycle took the family of the cycle two before at least once")
        }

        // N = 3 has two families, so cycles must strictly alternate between them.
        seeds.forEach { seed ->
            play(3, seed, cycles = 8).windowed(3).forEach { (twoAgo, _, now) ->
                assertTrue(SeatRotation.isRotationOf(now.first.baseOrder, twoAgo.first.baseOrder), "N=3 seed=$seed")
            }
        }
    }

    @Test
    fun theEnemyIsNeverRotatedAndAlwaysMovesLast() {
        for (n in sizes) {
            for (seed in seeds) {
                val random = Random(seed)
                var cycle = SeatRotation.firstCycle(seatsOf(n), random)

                repeat(n * 10) {
                    assertFalse(ENEMY in cycle.baseOrder, "N=$n seed=$seed: the enemy is not in a base order")
                    val order = SeatRotation.turnOrder(cycle, finalTurn = listOf(ENEMY))
                    assertEquals(ENEMY, order.last(), "N=$n seed=$seed: the enemy takes the final turn")
                    assertEquals(n + 1, order.size)
                    cycle = SeatRotation.next(cycle, random)
                }
            }
        }
    }

    @Test
    fun aFinalTurnParticipantCannotAlsoRotate() {
        val cycle = SeatRotation.firstCycle(listOf("A", "B"), Random(1))

        runCatching { SeatRotation.turnOrder(cycle, finalTurn = listOf("A")) }
            .let { assertTrue(it.isFailure, "someone who rotates cannot also move last") }
    }

    /** `D014` is the N = 2 case: every game reverses the one before, across cycle boundaries too. */
    @Test
    fun twoSeatsAlternateEveryGame() {
        for (seed in seeds) {
            val games = play(2, seed, cycles = 10).flatMap { it.second }

            games.zipWithNext().forEach { (before, after) ->
                assertEquals(before.reversed(), after, "seed=$seed: the colours reverse every game")
            }
        }
    }

    @Test
    fun oneSeatRotatesNothing() {
        var cycle = SeatRotation.firstCycle(listOf("Solo"), Random(3))

        repeat(5) {
            assertEquals(listOf("Solo"), SeatRotation.turnOrder(cycle))
            cycle = SeatRotation.next(cycle, Random(it))
        }
    }

    @Test
    fun theFirstCycleIsDrawnFromEveryOrder() {
        for (n in sizes) {
            val drawn = (0L until 400L).map { SeatRotation.firstCycle(seatsOf(n), Random(it)).baseOrder }.toSet()

            assertEquals((1..n).fold(1) { acc, k -> acc * k }, drawn.size, "N=$n: any order can open the first cycle")
        }
    }

    @Test
    fun rotatingMovesEveryoneOneSeatEarlier() {
        assertEquals(listOf("B", "C", "D", "A"), SeatRotation.rotate(listOf("A", "B", "C", "D"), 1))
        assertEquals(listOf("D", "A", "B", "C"), SeatRotation.rotate(listOf("A", "B", "C", "D"), 3))
        assertEquals(listOf("A", "B", "C", "D"), SeatRotation.rotate(listOf("A", "B", "C", "D"), 4))
        assertEquals(5, SeatRotation.nextBaseOrderCandidates(listOf("A", "B", "C", "D")).size / 4, "`D050`: five families remain at N = 4")
        assertEquals(20, SeatRotation.nextBaseOrderCandidates(listOf("A", "B", "C", "D")).size)
        assertEquals(3, SeatRotation.nextBaseOrderCandidates(listOf("A", "B", "C")).size, "one other family at N = 3")
        assertTrue(SeatRotation.nextBaseOrderCandidates(listOf("A", "B")).isEmpty(), "none at N = 2")
    }

    private companion object {
        const val ENEMY = "ENEMY"
    }
}
