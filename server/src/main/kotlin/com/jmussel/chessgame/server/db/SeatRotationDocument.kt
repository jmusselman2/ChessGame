@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import com.jmussel.chessgame.server.series.SeatCycle
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi

/**
 * How a series' place in its seat rotation is stored (`D050`).
 *
 * A persistence DTO, like [GameStateDocument]: the rotation itself ([SeatCycle]) knows nothing
 * about JSON. [cycleLength] is stored rather than only implied by the base order, because it
 * is one of the facts `D050` requires be persisted, and a document that disagrees with itself
 * is refused on the way back in.
 *
 * Each participant is stored as `KIND:ref` (`D051`), so a scripted participant that never
 * rotates can be told from one that does; an entry with no kind is a user.
 */
@Serializable
data class SeatRotationDocument(
    val cycleLength: Int,
    val baseOrder: List<String>,
    val gameInCycle: Int,
    val previousBaseOrder: List<String>? = null,
) {
    fun toCycle(): SeatCycle<Participant> {
        check(cycleLength == baseOrder.size) { "A stored cycle of $cycleLength has a base order of ${baseOrder.size}" }

        return SeatCycle(
            baseOrder = baseOrder.map(Participant::parse),
            gameInCycle = gameInCycle,
            previousBaseOrder = previousBaseOrder?.map(Participant::parse),
        )
    }

    companion object {
        fun of(cycle: SeatCycle<Participant>): SeatRotationDocument =
            SeatRotationDocument(
                cycleLength = cycle.cycleLength,
                baseOrder = cycle.baseOrder.map(Participant::toString),
                gameInCycle = cycle.gameInCycle,
                previousBaseOrder = cycle.previousBaseOrder?.map(Participant::toString),
            )
    }
}
