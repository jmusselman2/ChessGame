package com.jmussel.chessgame.ui.board

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.DrawClaim
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dropping a dragged piece (`M22.12`): a legal drop is the same as the two taps it stands for,
 * through [BoardInteraction] and `chess-core`; any other drop changes nothing at all.
 */
class PieceDropTest {
    private val newGame = BoardUiState.newGame()

    private fun tap(
        state: BoardUiState,
        vararg squares: String,
    ): BoardUiState = squares.fold(state) { at, square -> BoardInteraction.onSquareTapped(at, Square.parse(square)) }

    private fun drop(
        state: BoardUiState,
        from: String,
        to: String,
    ): BoardUiState = BoardInteraction.onPieceDropped(state, Square.parse(from), Square.parse(to))

    private fun destinations(
        state: BoardUiState,
        square: String,
    ): Set<Square>? = BoardInteraction.dragDestinations(state, Square.parse(square))

    private fun squares(vararg names: String): Set<Square> = names.mapTo(mutableSetOf(), Square::parse)

    @Test
    fun aLegalDropPlaysTheMoveTheTwoTapsWould() {
        val dropped = drop(newGame, "e2", "e4")

        assertEquals(tap(newGame, "e2", "e4"), dropped)
        assertEquals(Move.of("e2", "e4"), dropped.game.lastMove)
        assertNull(dropped.selectedSquare)
    }

    @Test
    fun anIllegalDropChangesNothingNotEvenTheSelection() {
        val selected = tap(newGame, "g1")

        assertSame(selected, drop(selected, "e2", "e5"))
        assertSame(newGame, drop(newGame, "e2", "d3"))
    }

    @Test
    fun droppingAPieceBackOnItsOwnSquareChangesNothing() {
        assertSame(newGame, drop(newGame, "e2", "e2"))
    }

    @Test
    fun onlyTheMovingSidesPiecesMayBePickedUp() {
        assertEquals(squares("e3", "e4"), destinations(newGame, "e2"))
        assertEquals("a pinned-in piece is picked up and goes back", emptySet<Square>(), destinations(newGame, "a1"))
        assertNull("the other side's piece", destinations(newGame, "e7"))
        assertNull("an empty square", destinations(newGame, "e4"))

        assertSame(newGame, drop(newGame, "e7", "e5"))
        assertSame(newGame, drop(newGame, "e4", "e5"))
    }

    @Test
    fun theDestinationsDoNotDependOnWhatIsSelected() {
        assertEquals(destinations(newGame, "g1"), destinations(tap(newGame, "e2"), "g1"))
        assertEquals(squares("f3", "h3"), destinations(tap(newGame, "e2"), "g1"))
    }

    @Test
    fun aDropOntoTheLastRankAsksForThePromotionFirst() {
        // `1. h4 g5 2. hxg5 h6 3. gxh6 Bg7 4. hxg7 a6`: White's pawn on g7 takes on h8.
        val ready = tap(newGame, "h2", "h4", "g7", "g5", "h4", "g5", "h7", "h6", "g5", "h6", "f8", "g7", "h6", "g7", "a7", "a6")

        val dropped = drop(ready, "g7", "h8")

        assertEquals(tap(ready, "g7", "h8"), dropped)
        assertEquals(PendingPromotion(Square.parse("g7"), Square.parse("h8")), dropped.pendingPromotion)
        assertEquals("nothing is played until the piece is chosen", ready.game, dropped.game)
    }

    @Test
    fun aDropThatWouldEntitleADrawIsDeclaredFirst() {
        val shuffle = arrayOf("g1", "f3", "g8", "f6", "f3", "g1", "f6", "g8")
        val beforeThird = tap(newGame, *shuffle, "g1", "f3", "g8", "f6", "f3", "g1")

        val dropped = drop(beforeThird, "f6", "g8")

        assertEquals(tap(beforeThird, "f6", "g8"), dropped)
        assertEquals(setOf(DrawClaim.THREEFOLD_REPETITION), dropped.declaredMove?.claims)
        assertEquals(beforeThird.game, dropped.game)
    }

    @Test
    fun nothingMayBePickedUpWhileAPromptWaitsOrOnceTheGameIsOver() {
        val prompted =
            tap(newGame, "h2", "h4", "g7", "g5", "h4", "g5", "h7", "h6", "g5", "h6", "f8", "g7", "h6", "g7", "a7", "a6", "g7", "h8")
        assertNotNull(prompted.pendingPromotion)
        assertNull(destinations(prompted, "e2"))
        assertSame(prompted, drop(prompted, "e2", "e4"))

        val mated = tap(newGame, "f2", "f3", "e7", "e5", "g2", "g4", "d8", "h4")
        assertTrue(mated.game.isOver)
        assertNull(destinations(mated, "e1"))
        assertSame(mated, drop(mated, "a2", "a3"))
    }

    @Test
    fun aDropThatRepeatsTheRetainedMoveAdvancesTheContinuationAsATapDoes() {
        val played =
            listOf("e2e4", "e7e5", "g1f3").fold(ChessGame.newGame()) { game, move ->
                ChessRules.applyMove(game, Move.of(move.take(2), move.drop(2)))
            }
        val undoOne = { game: ChessGame -> ChessRules.undo(game, requireNotNull(ChessRules.undoableSide(game))) }
        val undone = BoardUiState(undoOne(undoOne(played)))
        assertEquals(Move.of("e7", "e5"), ChessRules.retainedMove(undone.game))

        val dropped = drop(undone, "e7", "e5")

        assertEquals(tap(undone, "e7", "e5"), dropped)
        assertEquals(Move.of("g1", "f3"), ChessRules.retainedMove(dropped.game))
        assertEquals(played, ChessRules.redo(dropped.game))
    }

    @Test
    fun aBoardTurnedForBlackStillDropsOnTheTrueSquares() {
        val turned = newGame.copy(orientation = Side.BLACK)

        assertEquals(tap(turned, "e2", "e4"), drop(turned, "e2", "e4"))
        assertEquals(Side.BLACK, drop(turned, "e2", "e4").orientation)
    }
}
