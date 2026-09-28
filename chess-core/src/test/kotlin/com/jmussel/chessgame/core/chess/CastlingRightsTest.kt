package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CastlingRightsTest {
    @Test
    fun allRightsAreAvailableInitially() {
        Side.entries.forEach { side ->
            CastlingSide.entries.forEach { castlingSide ->
                assertTrue(CastlingRights.ALL.has(side, castlingSide))
            }
            assertTrue(CastlingRights.ALL.hasAny(side))
        }
    }

    @Test
    fun noRightsAreAvailableWhenNone() {
        Side.entries.forEach { side ->
            CastlingSide.entries.forEach { castlingSide ->
                assertFalse(CastlingRights.NONE.has(side, castlingSide))
            }
            assertFalse(CastlingRights.NONE.hasAny(side))
        }
    }

    @Test
    fun removingOneRightLeavesTheOthers() {
        val rights = CastlingRights.ALL.without(Side.WHITE, CastlingSide.QUEEN_SIDE)

        assertFalse(rights.has(Side.WHITE, CastlingSide.QUEEN_SIDE))
        assertTrue(rights.has(Side.WHITE, CastlingSide.KING_SIDE))
        assertTrue(rights.has(Side.BLACK, CastlingSide.QUEEN_SIDE))
        assertTrue(rights.has(Side.BLACK, CastlingSide.KING_SIDE))
    }

    @Test
    fun removingBothRightsForASideLeavesTheOpponentUntouched() {
        val rights = CastlingRights.ALL.without(Side.BLACK)

        assertFalse(rights.hasAny(Side.BLACK))
        assertTrue(rights.has(Side.WHITE, CastlingSide.KING_SIDE))
        assertTrue(rights.has(Side.WHITE, CastlingSide.QUEEN_SIDE))
    }

    @Test
    fun rightsAreImmutable() {
        val original = CastlingRights.ALL
        original.without(Side.WHITE)

        assertTrue(original.hasAny(Side.WHITE))
    }

    @Test
    fun rendersInFenOrder() {
        assertEquals("KQkq", CastlingRights.ALL.toString())
        assertEquals("-", CastlingRights.NONE.toString())
        assertEquals(
            "Kq",
            CastlingRights.ALL
                .without(Side.WHITE, CastlingSide.QUEEN_SIDE)
                .without(Side.BLACK, CastlingSide.KING_SIDE)
                .toString(),
        )
    }
}
