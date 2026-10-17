package com.jmussel.chessgame.ui.game

import com.jmussel.chessgame.api.ChessCommandRefusedException
import com.jmussel.chessgame.api.CommandRejectionDto
import com.jmussel.chessgame.api.GameViewDto
import com.jmussel.chessgame.api.MoveDto
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.api.playedMoves
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.ui.board.PendingPromotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a tap on an online board comes to.
 *
 * Selecting a piece and choosing a promotion happen on the screen; a move is a request the
 * server has to answer, and nothing on the board moves until it does (`D004`). The legal
 * destinations shown are a preview, replayed from the moves the server listed.
 */
class OnlineMoveTest {
    /** The game as it stands after [moves], described the way the server would describe it. */
    private fun game(
        moves: List<MoveDto> = emptyList(),
        yourSide: String = "WHITE",
        yourTurn: Boolean? = null,
        result: String? = null,
    ): GameViewDto {
        val replayed =
            moves.fold(ChessGame.newGame()) { position, played ->
                val move = Move(Square.parse(played.from), Square.parse(played.to), played.promotion?.let(PieceType::valueOf))
                ChessRules.applyMove(position, move)
            }

        return GameViewDto(
            gameId = "game-1",
            seriesId = "series-1",
            opponent = UserSummaryDto(userId = "user-1", username = "Alex"),
            version = moves.size.toLong() + 1,
            yourSide = yourSide,
            sideToMove = replayed.state.sideToMove.name,
            yourTurn = yourTurn ?: (replayed.state.sideToMove.name == yourSide),
            board =
                replayed.state.board
                    .toString()
                    .lines(),
            moves = moves,
            moveNumber = replayed.state.fullmoveNumber,
            result = result,
        )
    }

    private fun moveOf(text: String): Move =
        Move(
            from = Square.parse(text.take(2)),
            to = Square.parse(text.drop(2).take(2)),
            promotion =
                text.drop(4).firstOrNull()?.let { letter ->
                    PieceType.PROMOTION_CHOICES.first { it.letter.lowercaseChar() == letter }
                },
        )

    private fun ready(
        moves: List<MoveDto> = emptyList(),
        yourSide: String = "WHITE",
        yourTurn: Boolean? = null,
        result: String? = null,
        selected: Square? = null,
        submitting: Boolean = false,
    ) = OnlineGameState.Ready(
        game = game(moves = moves, yourSide = yourSide, yourTurn = yourTurn, result = result),
        selected = selected,
        submitting = submitting,
    )

    @Test
    fun theMovesTheServerListedReplayIntoThePositionItSent() {
        val game = game(moves = playedMoves("e2e4", "e7e5", "g1f3"))

        val replayed = OnlineGame.replayOf(game)

        assertEquals(OnlineGame.boardFrom(game.board), replayed?.state?.board)
    }

    @Test
    fun movesThatCannotBeReplayedCostThePreviewAndNothingElse() {
        val nonsense = game().copy(moves = listOf(MoveDto(from = "not", to = "a-move", san = "?")))

        assertNull(OnlineGame.replayOf(nonsense))
        assertTrue(OnlineGame.legalDestinations(OnlineGameState.Ready(nonsense, selected = Square.parse("e2"))).isEmpty())
    }

    @Test
    fun tappingYourOwnPieceSelectsItAndShowsWhereItCanGo() {
        val tap = OnlineGame.onSquareTapped(ready(), Square.parse("e2"))

        assertTrue(tap is BoardTap.Showing)
        assertEquals(Square.parse("e2"), tap.state.selected)
        assertEquals(
            setOf(Square.parse("e3"), Square.parse("e4")),
            OnlineGame.legalDestinations(tap.state),
        )
    }

    @Test
    fun tappingALegalDestinationAsksTheServerForThatMove() {
        val tap = OnlineGame.onSquareTapped(ready(selected = Square.parse("e2")), Square.parse("e4"))

        assertTrue(tap is BoardTap.Submit)
        assertEquals(Move.of("e2", "e4"), (tap as BoardTap.Submit).move)
        assertTrue("input is closed while the server is deciding", tap.state.submitting)
        assertNull(tap.state.selected)
    }

    @Test
    fun tappingTheSelectedSquareAgainClearsIt() {
        val tap = OnlineGame.onSquareTapped(ready(selected = Square.parse("e2")), Square.parse("e2"))

        assertTrue(tap is BoardTap.Showing)
        assertNull(tap.state.selected)
    }

    @Test
    fun nothingHappensWhenItIsNotYourMove() {
        val tap = OnlineGame.onSquareTapped(ready(moves = playedMoves("e2e4")), Square.parse("e7"))

        assertTrue(tap is BoardTap.Showing)
        assertNull(tap.state.selected)
    }

    @Test
    fun nothingHappensWhileAMoveIsInFlight() {
        val tap = OnlineGame.onSquareTapped(ready(submitting = true), Square.parse("e2"))

        assertTrue(tap is BoardTap.Showing)
        assertNull(tap.state.selected)
    }

    @Test
    fun nothingHappensInAGameThatIsOver() {
        val tap = OnlineGame.onSquareTapped(ready(result = "WHITE_WINS"), Square.parse("e2"))

        assertTrue(tap is BoardTap.Showing)
        assertNull(tap.state.selected)
    }

    @Test
    fun aPawnReachingTheLastRankAsksWhichPieceItBecomes() {
        // A White pawn has captured its way to h7; taking the knight on g8 promotes it.
        val moves = playedMoves("g2g4", "e7e5", "g4g5", "e5e4", "g5g6", "e4e3", "g6h7", "e3d2", "c1d2", "a7a6")
        val state = ready(moves = moves, selected = Square.parse("h7"))

        val tap = OnlineGame.onSquareTapped(state, Square.parse("g8"))

        assertTrue(tap is BoardTap.Showing)
        assertEquals(Square.parse("h7"), tap.state.pendingPromotion?.from)
        assertEquals(Square.parse("g8"), tap.state.pendingPromotion?.to)
        assertFalse("nothing is sent until the piece is chosen", tap.state.submitting)
    }

    @Test
    fun choosingThePromotionPieceSendsTheMoveWithIt() {
        val moves = playedMoves("g2g4", "e7e5", "g4g5", "e5e4", "g5g6", "e4e3", "g6h7", "e3d2", "c1d2", "a7a6")
        val prompted = OnlineGame.onSquareTapped(ready(moves = moves, selected = Square.parse("h7")), Square.parse("g8"))

        val tap = OnlineGame.choosePromotion(prompted.state, PieceType.QUEEN)

        assertTrue(tap is BoardTap.Submit)
        assertEquals(Move.of("h7", "g8", PieceType.QUEEN), (tap as BoardTap.Submit).move)
        assertTrue(tap.state.submitting)
        assertNull(tap.state.pendingPromotion)
    }

    @Test
    fun backingOutOfThePromotionSendsNothing() {
        val moves = playedMoves("g2g4", "e7e5", "g4g5", "e5e4", "g5g6", "e4e3", "g6h7", "e3d2", "c1d2", "a7a6")
        val prompted = OnlineGame.onSquareTapped(ready(moves = moves, selected = Square.parse("h7")), Square.parse("g8"))

        val cancelled = OnlineGame.cancelPromotion(prompted.state)

        assertNull(cancelled.pendingPromotion)
        assertFalse(cancelled.submitting)
    }

    @Test
    fun aStaleRefusalIsExplainedRatherThanRepeated() {
        val refusal =
            ChessCommandRefusedException(
                status = 409,
                rejection = CommandRejectionDto(reason = "STALE_VERSION", message = "This game is at version 5"),
            )

        assertTrue(OnlineGame.messageFor(refusal).contains("moved on"))
    }

    @Test
    fun everyRefusalTheServerNamesIsSaidInPlainWords() {
        listOf("NOT_YOUR_TURN" to "not your move", "GAME_OVER" to "finished", "ILLEGAL_MOVE" to "not legal")
            .forEach { (reason, expected) ->
                val refusal =
                    ChessCommandRefusedException(409, CommandRejectionDto(reason = reason, message = "no"))

                assertTrue(
                    "$reason should mention \"$expected\"",
                    OnlineGame.messageFor(refusal).contains(expected),
                )
            }
    }

    @Test
    fun aRefusalWithNoReasonFallsBackToWhatTheServerSaid() {
        val refusal = ChessCommandRefusedException(409, CommandRejectionDto(message = "Something else"))

        assertEquals("Something else", OnlineGame.messageFor(refusal))
    }

    @Test
    fun aLegalDropAsksForTheMoveTheTwoTapsWould() {
        val state = ready()

        val drop = OnlineGame.onPieceDropped(state, Square.parse("e2"), Square.parse("e4"))

        assertEquals(OnlineGame.onSquareTapped(state.copy(selected = Square.parse("e2")), Square.parse("e4")), drop)
        assertEquals(Move.of("e2", "e4"), (drop as BoardTap.Submit).move)
        assertTrue(drop.state.submitting)
    }

    @Test
    fun anIllegalDropSendsNothingAndChangesNothing() {
        val state = ready(selected = Square.parse("g1"))

        val drop = OnlineGame.onPieceDropped(state, Square.parse("e2"), Square.parse("e5"))

        assertEquals(BoardTap.Showing(state), drop)
    }

    @Test
    fun aDropOntoTheLastRankAsksForThePromotionFirst() {
        val moves = playedMoves("g2g4", "e7e5", "g4g5", "e5e4", "g5g6", "e4e3", "g6h7", "e3d2", "c1d2", "a7a6")

        val drop = OnlineGame.onPieceDropped(ready(moves = moves), Square.parse("h7"), Square.parse("g8"))

        assertTrue(drop is BoardTap.Showing)
        assertEquals(PendingPromotion(Square.parse("h7"), Square.parse("g8")), drop.state.pendingPromotion)
        assertFalse("nothing is sent until the piece is chosen", drop.state.submitting)
    }

    @Test
    fun onlyThePlayersOwnPiecesOnTheirOwnTurnMayBePickedUp() {
        assertEquals(setOf(Square.parse("e3"), Square.parse("e4")), OnlineGame.dragDestinations(ready(), Square.parse("e2")))
        assertNull("the opponent's piece", OnlineGame.dragDestinations(ready(), Square.parse("e7")))
        assertNull("not their turn", OnlineGame.dragDestinations(ready(yourTurn = false), Square.parse("e2")))
        assertNull("a command in flight", OnlineGame.dragDestinations(ready(submitting = true), Square.parse("e2")))
        assertNull("a finished game", OnlineGame.dragDestinations(ready(result = "BLACK_WINS"), Square.parse("e2")))

        val notYours = ready(yourTurn = false)
        assertEquals(BoardTap.Showing(notYours), OnlineGame.onPieceDropped(notYours, Square.parse("e2"), Square.parse("e4")))
    }
}
