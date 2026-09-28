package com.jmussel.chessgame.ui.board

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jmussel.chessgame.ui.board.GameLayoutSpec.Companion.MAX_BOARD
import com.jmussel.chessgame.ui.board.GameLayoutSpec.Companion.MIN_BOARD
import com.jmussel.chessgame.ui.board.GameLayoutSpec.Companion.MIN_PANEL_WIDTH
import com.jmussel.chessgame.ui.board.GameLayoutSpec.Companion.MIN_TWO_PANE_BOARD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The layout rule for the game screens (`D073`), over the windows it was written for.
 *
 * Sizes are the space the game screen is given after insets, in dp. Where a window was
 * measured on a device, the comment says so.
 */
class GameLayoutSpecTest {
    /** A window, what it is, and what the rule must make of it. */
    private data class Case(
        val name: String,
        val width: Dp,
        val height: Dp,
        val twoPane: Boolean,
        val scrolls: Boolean = false,
        val narrow: Boolean = false,
    )

    private val cases =
        listOf(
            // Stock Pixel 7, density 420: 411.4 dp across. In landscape the 28 dp status bar
            // leaves at most 383.4 dp (3-button navigation, which sits at the side), and
            // gesture navigation's bottom bar leaves less.
            Case("Pixel 7 stock portrait", 411.dp, 914.dp, twoPane = false),
            Case("Pixel 7 stock landscape, 3-button", 834.dp, 383.dp, twoPane = true),
            Case("Pixel 7 stock landscape, gesture", 858.dp, 359.dp, twoPane = true),
            // The owner's Pixel 7 at density 356, measured: 969×457 in landscape.
            Case("Pixel 7 at owner's size, portrait", 485.dp, 1078.dp, twoPane = false),
            Case("Pixel 7 at owner's size, landscape", 969.dp, 457.dp, twoPane = true),
            Case("Fire HD 8 landscape", 962.dp, 553.dp, twoPane = true),
            Case("Fire HD 8 portrait", 601.dp, 914.dp, twoPane = false),
            Case("small phone portrait", 360.dp, 640.dp, twoPane = false, narrow = true),
            Case("small phone landscape", 640.dp, 360.dp, twoPane = true),
            Case("small phone landscape after insets", 616.dp, 332.dp, twoPane = false, scrolls = true),
            Case("tablet portrait", 800.dp, 1280.dp, twoPane = false),
            Case("tablet landscape", 1280.dp, 800.dp, twoPane = true),
            Case("foldable inner", 673.dp, 841.dp, twoPane = false),
            Case("foldable inner, sideways", 841.dp, 673.dp, twoPane = true),
            Case("split screen", 485.dp, 400.dp, twoPane = false, scrolls = true),
            Case("near square", 700.dp, 720.dp, twoPane = false),
            Case("square", 660.dp, 660.dp, twoPane = false),
            Case("very large", 1280.dp, 900.dp, twoPane = true),
        )

    @Test
    fun eachWindowGetsTheModeTheRuleGivesIt() {
        cases.forEach { case ->
            val spec = GameLayoutSpec.forWindow(case.width, case.height)

            assertEquals("${case.name}: two panes", case.twoPane, spec.twoPane)
            assertEquals("${case.name}: whole screen scrolls", case.scrolls, spec.scrolls)
            assertEquals("${case.name}: narrow", case.narrow, spec.narrow)
        }
    }

    @Test
    fun theBoardIsNeverWiderThanTheWindowAndNeverTallerUnlessTheScreenScrolls() {
        cases.forEach { case ->
            val spec = GameLayoutSpec.forWindow(case.width, case.height)

            assertTrue("${case.name}: board ${spec.boardSide} fits the width", spec.boardSide <= case.width)
            if (!spec.scrolls) assertTrue("${case.name}: board ${spec.boardSide} fits the height", spec.boardSide <= case.height)
        }
    }

    @Test
    fun squaresMeetTheFloorExceptInANarrowWindow() {
        cases.forEach { case ->
            val spec = GameLayoutSpec.forWindow(case.width, case.height)
            val floor = if (spec.twoPane) MIN_TWO_PANE_BOARD else MIN_BOARD

            if (!spec.narrow) assertTrue("${case.name}: board ${spec.boardSide} ≥ $floor", spec.boardSide >= floor)
        }
    }

    @Test
    fun twoPanesLeaveThePanelItsWidthAndCapTheBoard() {
        cases.map { GameLayoutSpec.forWindow(it.width, it.height) to it }.filter { (spec, _) -> spec.twoPane }.forEach { (spec, case) ->
            assertTrue("${case.name}: panel", case.width - spec.boardSide >= MIN_PANEL_WIDTH)
            assertTrue("${case.name}: cap", spec.boardSide <= MAX_BOARD)
            assertFalse("${case.name}: two panes never scroll the board", spec.scrolls)
        }
    }

    @Test
    fun aTwoPaneBoardIsAsTallAsTheWindowAllows() {
        // Pixel 7 stock landscape: the board takes the whole height, 47.9 dp squares.
        assertEquals(383.dp, GameLayoutSpec.forWindow(834.dp, 383.dp).boardSide)
        // The owner's Pixel 7: 457 dp, 57 dp squares.
        assertEquals(457.dp, GameLayoutSpec.forWindow(969.dp, 457.dp).boardSide)
        // A tablet's spare width goes to the panel, not the board.
        assertEquals(MAX_BOARD, GameLayoutSpec.forWindow(1280.dp, 800.dp).boardSide)
        // A window only just wide enough gives the panel its minimum.
        assertEquals(400.dp, GameLayoutSpec.forWindow(680.dp, 500.dp).boardSide)
    }

    @Test
    fun oneColumnKeepsItsMarginsAndLeavesRoomForTheControls() {
        // Portrait tablet: 16 dp either side.
        assertEquals(768.dp, GameLayoutSpec.forWindow(800.dp, 1280.dp).boardSide)
        // Foldable inner screen: the width decides.
        assertEquals(641.dp, GameLayoutSpec.forWindow(673.dp, 841.dp).boardSide)
        // A tall, narrowish window: the height left after the controls decides.
        assertEquals(500.dp, GameLayoutSpec.forWindow(600.dp, 660.dp).boardSide)
    }

    @Test
    fun aPhoneTooNarrowForMarginsDropsThem() {
        // Pixel 7 stock portrait: 411 − 32 < 384, so the margins go and the board is 411 dp.
        val spec = GameLayoutSpec.forWindow(411.dp, 914.dp)

        assertEquals(411.dp, spec.boardSide)
        assertFalse(spec.narrow)
    }

    @Test
    fun aWindowTooNarrowForTheFloorGetsTheLargestSquareThatFits() {
        val spec = GameLayoutSpec.forWindow(360.dp, 640.dp)

        assertEquals(360.dp, spec.boardSide)
        assertTrue(spec.narrow)
        assertFalse(spec.scrolls)
    }

    @Test
    fun aWindowTooShortKeepsAFullSizeBoardAndScrolls() {
        val spec = GameLayoutSpec.forWindow(485.dp, 400.dp)

        assertEquals(MIN_BOARD, spec.boardSide)
        assertTrue(spec.scrolls)
    }

    @Test
    fun aWindowBothTooNarrowAndTooShortIsAsWideAsItCanBeAndScrolls() {
        val spec = GameLayoutSpec.forWindow(320.dp, 360.dp)

        assertEquals(320.dp, spec.boardSide)
        assertTrue(spec.narrow)
        assertTrue(spec.scrolls)
    }

    @Test
    fun smallerTwoPaneSquaresAreUsedOnlyToAvoidScrolling() {
        // Two panes here would give 380 dp (47.5 dp squares); one column fits a 500 dp board
        // without scrolling, so it wins.
        val square = GameLayoutSpec.forWindow(660.dp, 660.dp)
        assertFalse(square.twoPane)
        assertEquals(500.dp, square.boardSide)

        // Here one column would scroll, so two panes with 45 dp squares are better.
        val landscape = GameLayoutSpec.forWindow(858.dp, 359.dp)
        assertTrue(landscape.twoPane)
        assertEquals(359.dp, landscape.boardSide)
    }

    @Test
    fun theDecisionFollowsTheWindowNotItsOrientation() {
        // As tall as it is wide: two panes still, when both fit.
        assertTrue(GameLayoutSpec.forWindow(700.dp, 700.dp).let { it.twoPane || !it.scrolls })
        // Wider than tall, but too short even for 44 dp squares: one column that scrolls.
        val short = GameLayoutSpec.forWindow(900.dp, 340.dp)
        assertFalse(short.twoPane)
        assertTrue(short.scrolls)
    }
}
