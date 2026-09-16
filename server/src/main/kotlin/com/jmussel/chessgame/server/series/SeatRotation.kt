package com.jmussel.chessgame.server.series

import kotlin.random.Random

/**
 * Where a series is in its seat rotation (`D050`).
 *
 * A cycle is [cycleLength] consecutive games. [baseOrder] is the turn order of the cycle's
 * first game, and each later game in the cycle rotates it one seat further ([gameInCycle]
 * counts from 0). [previousBaseOrder] is the base order of the cycle before, which is what
 * the next cycle's base order must not be a rotation of.
 *
 * Only *rotating* participants are in it. A participant that always takes the final turn —
 * the scripted enemy (`D051`) — is never in a base order; it is appended after the rotation
 * ([SeatRotation.turnOrder]).
 */
data class SeatCycle<P>(
    val baseOrder: List<P>,
    val gameInCycle: Int = 0,
    val previousBaseOrder: List<P>? = null,
) {
    init {
        require(baseOrder.isNotEmpty()) { "A cycle needs someone to rotate" }
        require(baseOrder.toSet().size == baseOrder.size) { "Nobody sits twice in a base order" }
        require(gameInCycle in baseOrder.indices) { "Game $gameInCycle is not in a cycle of ${baseOrder.size}" }
        require(previousBaseOrder == null || previousBaseOrder.size == baseOrder.size) {
            "Consecutive cycles rotate the same participants"
        }
    }

    /** N: how many games the cycle lasts, which is how many participants rotate. */
    val cycleLength: Int
        get() = baseOrder.size
}

/**
 * Turn order by cycle rotation (`D050`); chess's colour alternation (`D014`) is its N = 2 case.
 *
 * - Game 1 of the first cycle takes a base order at random.
 * - Each later game in a cycle rotates the base order one seat: everyone moves one seat
 *   earlier and the first wraps to last. Over a cycle every participant is first exactly once
 *   and last exactly once.
 * - A new cycle's base order is drawn at random from the orders that are **not** a rotation of
 *   the cycle just finished. Only that one cycle is excluded, so a rotation of the cycle two
 *   before is allowed again.
 * - When nothing is left to draw from — N = 2, where both orders are rotations of each other,
 *   and N = 1 — the boundary is vacuous and the rotation simply carries on. For chess that is
 *   exactly per-game alternation: White, Black, White, … for each player.
 *
 * Pure: every choice comes from the [Random] it is given, so a test can script it. Base orders
 * are drawn by enumerating the permutations, which is what makes "not a rotation of the last
 * one" an exact rule rather than a retry loop; that is 24 orders at the four seats `D048`
 * foresees.
 */
object SeatRotation {
    /** The first cycle for [rotating], with its base order drawn at random. */
    fun <P> firstCycle(
        rotating: List<P>,
        random: Random,
    ): SeatCycle<P> = SeatCycle(baseOrder = pick(permutationsOf(rotating), random))

    /** The turn order of the cycle's current game: the base order rotated [SeatCycle.gameInCycle] seats. */
    fun <P> orderOf(cycle: SeatCycle<P>): List<P> = rotate(cycle.baseOrder, cycle.gameInCycle)

    /**
     * The whole turn order of the current game: the rotating participants in their rotated
     * order, then [finalTurn] — the participants that are never rotated and always move last.
     */
    fun <P> turnOrder(
        cycle: SeatCycle<P>,
        finalTurn: List<P> = emptyList(),
    ): List<P> {
        require(finalTurn.none { it in cycle.baseOrder }) { "A final-turn participant never rotates" }
        return orderOf(cycle) + finalTurn
    }

    /** Where the rotation stands for the next game. */
    fun <P> next(
        cycle: SeatCycle<P>,
        random: Random,
    ): SeatCycle<P> {
        if (cycle.gameInCycle + 1 < cycle.cycleLength) return cycle.copy(gameInCycle = cycle.gameInCycle + 1)

        val candidates = nextBaseOrderCandidates(cycle.baseOrder)

        // N <= 2: every order is a rotation of the one just played, so there is nothing to
        // exclude it from and the rotation carries on unbroken (`D050`, `D014`).
        val nextBase = if (candidates.isEmpty()) cycle.baseOrder else pick(candidates, random)

        return SeatCycle(baseOrder = nextBase, gameInCycle = 0, previousBaseOrder = cycle.baseOrder)
    }

    /** The base orders a cycle following one based on [finished] may take: none of its rotations. */
    fun <P> nextBaseOrderCandidates(finished: List<P>): List<List<P>> = permutationsOf(finished).filterNot { isRotationOf(it, finished) }

    /** Whether [order] is [base] rotated by some number of seats, [base] itself included. */
    fun <P> isRotationOf(
        order: List<P>,
        base: List<P>,
    ): Boolean = order.size == base.size && base.indices.any { rotate(base, it) == order }

    /** [order] with everyone moved [seats] seats earlier, the front wrapping to the back. */
    fun <P> rotate(
        order: List<P>,
        seats: Int,
    ): List<P> {
        if (order.isEmpty()) return order
        val shift = Math.floorMod(seats, order.size)
        return order.drop(shift) + order.take(shift)
    }

    private fun <P> pick(
        candidates: List<List<P>>,
        random: Random,
    ): List<P> = candidates[random.nextInt(candidates.size)]

    /** Every ordering of [items], in a fixed order that starts with [items] as given. */
    private fun <P> permutationsOf(items: List<P>): List<List<P>> {
        if (items.size <= 1) return listOf(items)

        return items.indices.flatMap { index ->
            val rest = items.take(index) + items.drop(index + 1)
            permutationsOf(rest).map { listOf(items[index]) + it }
        }
    }
}
