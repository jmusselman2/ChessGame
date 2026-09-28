package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SquareTest {
    @Test
    fun coversSixtyFourDistinctSquares() {
        assertEquals(64, Square.COUNT)
        assertEquals(64, Square.ALL.size)
        assertEquals(64, Square.ALL.toSet().size)
    }

    @Test
    fun exposesFileAndRankCoordinates() {
        val e4 = Square.parse("e4")
        assertEquals(4, e4.file)
        assertEquals(3, e4.rank)
        assertEquals('e', e4.fileChar)
        assertEquals(4, e4.rankNumber)
        assertEquals("e4", e4.name)
        assertEquals("e4", e4.toString())
    }

    @Test
    fun ordersSquaresFromA1ToH8() {
        assertEquals("a1", Square.ofIndex(0).name)
        assertEquals("h1", Square.ofIndex(7).name)
        assertEquals("a2", Square.ofIndex(8).name)
        assertEquals("h8", Square.ofIndex(63).name)
    }

    @Test
    fun equalSquaresAreTheSameValue() {
        assertEquals(Square.of(4, 3), Square.parse("e4"))
    }

    @Test
    fun parsesUppercaseFileLetters() {
        assertEquals(Square.parse("e4"), Square.parse("E4"))
    }

    @Test
    fun rejectsOffBoardCoordinates() {
        assertNull(Square.ofOrNull(-1, 0))
        assertNull(Square.ofOrNull(0, 8))
        assertFailsWith<IllegalArgumentException> { Square.of(8, 0) }
        assertFailsWith<IllegalArgumentException> { Square.ofIndex(64) }
    }

    @Test
    fun rejectsInvalidNames() {
        assertNull(Square.parseOrNull("j4"))
        assertNull(Square.parseOrNull("e9"))
        assertNull(Square.parseOrNull("e"))
        assertNull(Square.parseOrNull("e44"))
        assertFailsWith<IllegalArgumentException> { Square.parse("z0") }
    }
}
