package com.jmussel.chessgame.computer

import com.jmussel.chessgame.ai.ChessEngine
import com.jmussel.chessgame.ai.Difficulty
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.DrawClaim
import com.jmussel.chessgame.core.chess.GameResult
import com.jmussel.chessgame.core.chess.GameState
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.local.ComputerOpponent
import com.jmussel.chessgame.local.LocalGameKind
import com.jmussel.chessgame.local.LocalGameSession
import com.jmussel.chessgame.local.inMemoryLocalGameStore
import com.jmussel.chessgame.ui.board.BoardInteraction
import com.jmussel.chessgame.ui.board.BoardUiState
import com.jmussel.chessgame.ui.board.GameControls
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A game against the computer below the screen (`M21.6`, `D086`), with deterministic fake
 * engines and a real local-game store.
 *
 * Everything runs on one test dispatcher, so "the computer is thinking" is simply a search
 * that has been started and not yet run.
 */
class ComputerGameTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = inMemoryLocalGameStore()
    private val session = LocalGameSession(store, dispatcher)

    /** Plays [replies] in order, then the first legal move in coordinate order; counts its calls. */
    private class ScriptedEngine(
        vararg replies: String,
    ) : ChessEngine {
        private val script = ArrayDeque(replies.map { Move.of(it.substring(0, 2), it.substring(2, 4)) })
        var calls = 0

        /** Run inside the search, before it answers: a chance to change the game under it. */
        var whileThinking: () -> Unit = {}

        override fun chooseMove(
            state: GameState,
            difficulty: Difficulty,
            isCancelled: () -> Boolean,
        ): Move? {
            calls++
            whileThinking()
            return script.removeFirstOrNull() ?: ChessRules.legalMoves(state).minBy { it.toString() }
        }
    }

    private fun TestScope.computerGame(
        engine: ChessEngine,
        side: Side = Side.WHITE,
    ): ComputerGame = ComputerGame(session, engine, this, dispatcher, chooseSide = { side })

    /** The human's tap on [squares], as the board works them out. */
    private fun ComputerGame.tap(vararg squares: String) {
        squares.forEach { square -> update(BoardInteraction.onSquareTapped(state!!.boardState, Square.parse(square))) }
    }

    private val ComputerGame.moves: List<Move>
        get() = state!!.game.moves

    private fun TestScope.started(
        engine: ChessEngine,
        humanSide: Side = Side.WHITE,
        difficulty: Int = 2,
    ): ComputerGame =
        computerGame(engine).also {
            it.start(ComputerOpponent(humanSide, difficulty))
            advanceUntilIdle()
        }

    // --- The computer's turn ----------------------------------------------------------

    @Test
    fun aHumanMoveTriggersExactlyOneReply() =
        runTest(dispatcher) {
            val engine = ScriptedEngine("e7e5")
            val game = started(engine)

            game.tap("e2", "e4")
            assertTrue(game.state!!.thinking)
            advanceUntilIdle()

            assertEquals(1, engine.calls)
            assertEquals(listOf(Move.of("e2", "e4"), Move.of("e7", "e5")), game.moves)
            assertFalse(game.state!!.thinking)
            assertTrue(game.state!!.humansTurn)
            assertEquals(game.state!!.game, store.activeGame()!!.game)
        }

    @Test
    fun theComputerMovesFirstWhenTheHumanIsBlack() =
        runTest(dispatcher) {
            val engine = ScriptedEngine("d2d4")
            val game = started(engine, humanSide = Side.BLACK)

            assertEquals(1, engine.calls)
            assertEquals(listOf(Move.of("d2", "d4")), game.moves)
            assertEquals(Side.BLACK, game.state!!.boardState.orientation)
        }

    @Test
    fun aGameEndingHumanMoveTriggersNoEngineCall() =
        runTest(dispatcher) {
            val engine = ScriptedEngine("f2f3", "g2g4")
            val game = started(engine, humanSide = Side.BLACK)
            game.tap("e7", "e5")
            advanceUntilIdle()

            game.tap("d8", "h4")
            advanceUntilIdle()

            assertEquals(GameResult.checkmate(Side.WHITE), game.state!!.game.result)
            assertEquals(2, engine.calls)
            assertFalse(game.state!!.thinking)
        }

    @Test
    fun aGameEndingComputerMoveCompletesTheGameAndItIsKept() =
        runTest(dispatcher) {
            val engine = ScriptedEngine("e7e5", "d8h4")
            val game = started(engine, difficulty = 3)
            val id = game.state!!.id

            game.tap("f2", "f3")
            advanceUntilIdle()
            game.tap("g2", "g4")
            advanceUntilIdle()

            assertEquals(GameResult.checkmate(Side.WHITE), game.state!!.game.result)
            assertNull(store.activeGame())
            val kept = store.completedGames().single()
            assertEquals(id, kept.id)
            assertEquals(LocalGameKind.COMPUTER, kept.kind)
            assertEquals(ComputerOpponent(Side.WHITE, 3), kept.computer)
            assertFalse(game.state!!.canTakeBack)
        }

    @Test
    fun theBoardIgnoresTheHumanOnTheComputersTurn() =
        runTest(dispatcher) {
            val engine = ScriptedEngine("e7e5")
            val game = started(engine)
            game.tap("e2", "e4")
            val thinking = game.state!!

            // Selecting one of the computer's pieces, or one's own, changes nothing.
            game.update(BoardInteraction.onSquareTapped(thinking.boardState, Square.parse("d7")))
            game.update(thinking.boardState.copy(selectedSquare = Square.parse("d2")))
            // Nor does a move played for either side.
            game.update(BoardUiState(ChessRules.applyMove(thinking.game, Move.of("d7", "d5"))))
            assertEquals(thinking, game.state)
            assertFalse(thinking.humansTurn)

            advanceUntilIdle()
            assertEquals(listOf(Move.of("e2", "e4"), Move.of("e7", "e5")), game.moves)
        }

    @Test
    fun leavingStopsTheSearchAndReopeningOnTheComputersTurnStartsExactlyOne() =
        runTest(dispatcher) {
            val engine = ScriptedEngine("e7e5")
            val game = started(engine)
            game.tap("e2", "e4")
            game.leave()
            advanceUntilIdle()
            assertEquals(0, engine.calls)
            assertEquals(listOf(Move.of("e2", "e4")), store.activeGame()!!.game.moves)

            // Opened twice in quick succession, as a recreated screen might.
            val reopened = computerGame(engine)
            reopened.resume()
            reopened.resume()
            advanceUntilIdle()

            assertEquals(1, engine.calls)
            assertEquals(listOf(Move.of("e2", "e4"), Move.of("e7", "e5")), reopened.moves)
        }

    // --- Takeback ---------------------------------------------------------------------

    @Test
    fun afterAReplyTakebackRemovesItAndTheHumanMoveBeforeIt() =
        runTest(dispatcher) {
            val engine = ScriptedEngine("e7e5", "b8c6")
            val game = started(engine)
            game.tap("e2", "e4")
            advanceUntilIdle()
            game.tap("g1", "f3")
            advanceUntilIdle()
            assertEquals(4, game.moves.size)

            game.takeBack()
            advanceUntilIdle()

            assertEquals(listOf(Move.of("e2", "e4"), Move.of("e7", "e5")), game.moves)
            assertTrue(game.state!!.humansTurn)
            assertEquals(game.state!!.game, store.activeGame()!!.game)
            assertEquals(2, engine.calls)
        }

    @Test
    fun takebackCanBeRepeatedToTheHumansFirstMoveAndNoFurther() =
        runTest(dispatcher) {
            val game = started(ScriptedEngine("e2e4", "d2d4"), humanSide = Side.BLACK)
            game.tap("e7", "e5")
            advanceUntilIdle()
            assertEquals(3, game.moves.size)

            game.takeBack()
            advanceUntilIdle()
            // Back to the human's first turn, after the computer's opening move.
            assertEquals(listOf(Move.of("e2", "e4")), game.moves)
            assertFalse(game.state!!.canTakeBack)

            game.takeBack()
            advanceUntilIdle()
            assertEquals(listOf(Move.of("e2", "e4")), game.moves)
            assertEquals(game.state!!.game, store.activeGame()!!.game)
        }

    @Test
    fun whileTheComputerIsThinkingTakebackRemovesTheHumanMoveAlone() =
        runTest(dispatcher) {
            val engine = ScriptedEngine("e7e5")
            val game = started(engine)
            game.tap("e2", "e4")
            assertTrue(game.state!!.thinking)

            game.takeBack()
            advanceUntilIdle()

            assertEquals(ChessGame.newGame(), game.state!!.game)
            assertFalse(game.state!!.thinking)
            assertEquals(0, engine.calls)
            assertEquals(ChessGame.newGame(), store.activeGame()!!.game)
        }

    @Test
    fun aSearchThatFinishesAsItIsCancelledCannotApplyItsResult() =
        runTest(dispatcher) {
            val engine = ScriptedEngine("e7e5")
            val game = started(engine)
            // The search completes and answers even though the human took the move back while
            // it ran, and the engine never noticed.
            engine.whileThinking = { game.takeBack() }
            game.tap("e2", "e4")
            advanceUntilIdle()

            assertEquals(1, engine.calls)
            assertEquals(ChessGame.newGame(), game.state!!.game)
            assertEquals(ChessGame.newGame(), store.activeGame()!!.game)
        }

    @Test
    fun replacingTheGameStopsAnOldSearchFromChangingTheNewOne() =
        runTest(dispatcher) {
            val engine = ScriptedEngine("e7e5")
            val game = started(engine)
            engine.whileThinking = {
                engine.whileThinking = {}
                game.start(ComputerOpponent(Side.WHITE, 1))
            }
            game.tap("e2", "e4")
            advanceUntilIdle()

            val replacement = game.state!!
            assertEquals(ChessGame.newGame(), replacement.game)
            assertEquals(1, replacement.opponent.difficulty)
            assertEquals(replacement.id, store.activeGame()!!.id)
            assertEquals(ChessGame.newGame(), store.activeGame()!!.game)
            assertEquals(emptyList<Any>(), store.completedGames())
        }

    @Test
    fun takebackIsUnavailableOnceTheGameHasEnded() =
        runTest(dispatcher) {
            val game = started(ScriptedEngine("e7e5"))
            game.tap("e2", "e4")
            advanceUntilIdle()
            game.update(GameControls.resign(game.state!!.boardState, Side.WHITE))
            advanceUntilIdle()
            val ended = game.state!!.game

            assertFalse(game.state!!.canTakeBack)
            game.takeBack()
            advanceUntilIdle()
            assertEquals(ended, game.state!!.game)
            assertEquals(ended, store.completedGames().single().let { store.game(it.id)!!.game })
        }

    // --- Draws and resignation --------------------------------------------------------

    @Test
    fun theHumanMayResignWhileTheComputerIsThinking() =
        runTest(dispatcher) {
            val engine = ScriptedEngine("e7e5")
            val game = started(engine)
            game.tap("e2", "e4")

            game.update(GameControls.resign(game.state!!.boardState, Side.WHITE))
            advanceUntilIdle()

            assertEquals(GameResult.resignation(Side.WHITE), game.state!!.game.result)
            assertEquals(0, engine.calls)
            assertEquals(1, store.completedGames().size)
        }

    @Test
    fun theHumanCannotResignOrClaimForTheComputer() =
        runTest(dispatcher) {
            val game = started(ScriptedEngine("e7e5"))
            game.tap("e2", "e4")
            val thinking = game.state!!

            game.update(GameControls.resign(thinking.boardState, Side.BLACK))
            assertEquals(thinking, game.state)
        }

    @Test
    fun theHumanMayClaimADrawTheComputerNeverDoes() =
        runTest(dispatcher) {
            // The computer shuffles its knight back and forth, and never claims the repetition.
            val engine = ScriptedEngine("g8f6", "f6g8", "g8f6", "f6g8")
            val game = started(engine)
            repeat(2) {
                game.tap("g1", "f3")
                advanceUntilIdle()
                game.tap("f3", "g1")
                advanceUntilIdle()
            }
            assertNull(game.state!!.game.result)
            assertEquals(setOf(DrawClaim.THREEFOLD_REPETITION), ChessRules.availableDrawClaims(game.state!!.game))

            game.update(GameControls.claimDraw(game.state!!.boardState, DrawClaim.THREEFOLD_REPETITION))
            advanceUntilIdle()

            assertTrue(game.state!!.game.isOver)
            assertNull(store.activeGame())
        }

    // --- Play the computer: entry, level, Play again (M21.7) --------------------------

    @Test
    fun withNothingUnfinishedTheEntryAsksForALevelAndTheColourIsChosenForTheFirstGame() =
        runTest(dispatcher) {
            val game = computerGame(ScriptedEngine("e2e4"), side = Side.BLACK)

            game.open()
            advanceUntilIdle()
            assertEquals(ComputerSetup.ChooseDifficulty, game.setup)
            assertNull(game.state)

            game.choose(Difficulty.HARD)
            advanceUntilIdle()

            assertNull(game.setup)
            assertEquals(ComputerOpponent(Side.BLACK, Difficulty.HARD.level), game.state!!.opponent)
            assertEquals(ComputerOpponent(Side.BLACK, 3), store.activeGame()!!.computer)
            // The computer had White, so it has opened.
            assertEquals(1, game.moves.size)
        }

    @Test
    fun theEntryResumesAnUnfinishedGameAgainstTheComputer() =
        runTest(dispatcher) {
            val first = started(ScriptedEngine("e7e5"), difficulty = 1)
            first.tap("e2", "e4")
            advanceUntilIdle()

            val reopened = computerGame(ScriptedEngine())
            reopened.open()
            advanceUntilIdle()

            assertNull(reopened.setup)
            assertEquals(first.state!!.id, reopened.state!!.id)
            assertEquals(listOf(Move.of("e2", "e4"), Move.of("e7", "e5")), reopened.moves)
        }

    @Test
    fun anUnfinishedPassAndPlayGameIsOnlyReplacedOnceThePlayerAgrees() =
        runTest(dispatcher) {
            val passAndPlay = store.startGame()
            store.recordMove(passAndPlay.id, ChessRules.applyMove(passAndPlay.game, Move.of("e2", "e4")))
            val game = computerGame(ScriptedEngine())

            game.open()
            advanceUntilIdle()
            assertEquals(ComputerSetup.ConfirmReplacing, game.setup)
            // A level cannot be chosen before the question is answered.
            game.choose(Difficulty.EASY)
            advanceUntilIdle()
            assertEquals(passAndPlay.id, store.activeGame()!!.id)

            game.confirmReplacing()
            assertEquals(ComputerSetup.ChooseDifficulty, game.setup)
            game.choose(Difficulty.EASY)
            advanceUntilIdle()

            assertNull(store.game(passAndPlay.id))
            assertEquals(emptyList<Any>(), store.completedGames())
            assertEquals(LocalGameKind.COMPUTER, store.activeGame()!!.kind)
        }

    @Test
    fun playAgainKeepsThePreviousGameAndTheLevelAndSwapsColours() =
        runTest(dispatcher) {
            val game = started(ScriptedEngine("e7e5", "d2d4"), difficulty = 2)
            game.tap("e2", "e4")
            advanceUntilIdle()
            // Play again is offered only once the game has ended.
            game.playAgain()
            advanceUntilIdle()
            assertEquals(2, game.moves.size)

            game.update(GameControls.resign(game.state!!.boardState, Side.WHITE))
            advanceUntilIdle()
            val finished = game.state!!

            game.playAgain()
            advanceUntilIdle()

            val again = game.state!!
            assertTrue(again.id != finished.id)
            assertEquals(ComputerOpponent(Side.BLACK, 2), again.opponent)
            // The computer now has White and has opened; the new game owes nothing to the old.
            assertEquals(listOf(Move.of("d2", "d4")), again.game.moves)
            val kept = store.game(finished.id)!!
            assertEquals(finished.game, kept.game)
            assertEquals(ComputerOpponent(Side.WHITE, 2), kept.computer)
            assertEquals(listOf(finished.id), store.completedGames().map { it.id })
        }

    // --- New game on the game screen (M21.10) -----------------------------------------

    @Test
    fun newGameOnAnUnfinishedGameAsksAndKeepingItChangesNothing() =
        runTest(dispatcher) {
            val engine = ScriptedEngine("e7e5")
            val game = started(engine)
            game.tap("e2", "e4")
            val thinking = game.state!!
            assertTrue(thinking.thinking)

            game.newGame()
            assertEquals(ComputerSetup.ConfirmNewGame, game.setup)
            // A level cannot be chosen before the question is answered.
            game.choose(Difficulty.HARD)
            assertEquals(thinking, game.state)

            assertTrue(game.keepGame())
            assertNull(game.setup)
            assertEquals(thinking, game.state)

            // The search in progress carried on.
            advanceUntilIdle()
            assertEquals(1, engine.calls)
            assertEquals(listOf(Move.of("e2", "e4"), Move.of("e7", "e5")), game.moves)
            assertEquals(thinking.id, store.activeGame()!!.id)
            assertEquals(game.state!!.game, store.activeGame()!!.game)
        }

    @Test
    fun agreeingToNewGameAndChoosingALevelReplacesTheGameAndTheOldOneIsNotKept() =
        runTest(dispatcher) {
            val game = started(ScriptedEngine("e7e5", "d2d4"), difficulty = 1)
            game.tap("e2", "e4")
            advanceUntilIdle()
            val old = game.state!!

            game.newGame()
            game.confirmReplacing()
            assertEquals(ComputerSetup.ChooseDifficulty, game.setup)
            // Nothing is deleted until a level is chosen.
            assertEquals(old, game.state)
            assertEquals(old.id, store.activeGame()!!.id)

            game.choose(Difficulty.HARD)
            advanceUntilIdle()

            val replacement = game.state!!
            assertNull(game.setup)
            assertTrue(replacement.id != old.id)
            assertEquals(ComputerOpponent(Side.WHITE, Difficulty.HARD.level), replacement.opponent)
            assertEquals(ChessGame.newGame(), replacement.game)
            assertNull(store.game(old.id))
            assertEquals(emptyList<Any>(), store.completedGames())
            assertEquals(replacement.id, store.activeGame()!!.id)
        }

    @Test
    fun newGamesColourIsChosenAsFromTheEntry() =
        runTest(dispatcher) {
            val game = computerGame(ScriptedEngine("d2d4"), side = Side.BLACK)
            game.start(ComputerOpponent(Side.WHITE, 2))
            advanceUntilIdle()

            game.newGame()
            game.confirmReplacing()
            game.choose(Difficulty.EASY)
            advanceUntilIdle()

            assertEquals(ComputerOpponent(Side.BLACK, Difficulty.EASY.level), game.state!!.opponent)
            // The computer has White, so it has opened.
            assertEquals(1, game.moves.size)
        }

    @Test
    fun backingOutOfNewGamesLevelChoiceKeepsTheGame() =
        runTest(dispatcher) {
            val engine = ScriptedEngine("e7e5")
            val game = started(engine)
            game.tap("e2", "e4")
            advanceUntilIdle()
            val kept = game.state!!

            game.newGame()
            game.confirmReplacing()
            assertTrue(game.keepGame())
            advanceUntilIdle()

            assertNull(game.setup)
            assertEquals(kept, game.state)
            assertEquals(kept.id, store.activeGame()!!.id)
            assertEquals(kept.game, store.activeGame()!!.game)
            // With the choice gone, no level can start a game.
            game.choose(Difficulty.EASY)
            advanceUntilIdle()
            assertEquals(kept, game.state)
            assertEquals(1, engine.calls)
        }

    @Test
    fun newGameOnAFinishedGameKeepsItAndStartsTheChosenLevel() =
        runTest(dispatcher) {
            val game = started(ScriptedEngine("e7e5"), difficulty = 1)
            game.tap("e2", "e4")
            advanceUntilIdle()
            game.update(GameControls.resign(game.state!!.boardState, Side.WHITE))
            advanceUntilIdle()
            val finished = game.state!!

            game.newGame()
            // A finished game is kept, so nothing is asked.
            assertEquals(ComputerSetup.ChooseDifficulty, game.setup)
            game.choose(Difficulty.MEDIUM)
            advanceUntilIdle()

            val next = game.state!!
            assertTrue(next.id != finished.id)
            assertEquals(ComputerOpponent(Side.WHITE, Difficulty.MEDIUM.level), next.opponent)
            assertEquals(finished.game, store.game(finished.id)!!.game)
            assertEquals(listOf(finished.id), store.completedGames().map { it.id })
            assertEquals(next.id, store.activeGame()!!.id)
        }

    @Test
    fun withNoGameBehindTheLevelChoiceKeepGameLeavesBackToTheScreen() =
        runTest(dispatcher) {
            val game = computerGame(ScriptedEngine())
            game.open()
            advanceUntilIdle()

            assertEquals(ComputerSetup.ChooseDifficulty, game.setup)
            assertFalse(game.keepGame())
            assertEquals(ComputerSetup.ChooseDifficulty, game.setup)
        }

    @Test
    fun aSearchRunningWhenTheNewGameStartsCannotChangeIt() =
        runTest(dispatcher) {
            val engine = ScriptedEngine("e7e5")
            val game = started(engine)
            // The level is chosen while the computer is still thinking about the old game, and
            // the search answers anyway.
            engine.whileThinking = {
                engine.whileThinking = {}
                game.newGame()
                game.confirmReplacing()
                game.choose(Difficulty.EASY)
            }
            game.tap("e2", "e4")
            advanceUntilIdle()

            assertEquals(1, engine.calls)
            val replacement = game.state!!
            assertEquals(ComputerOpponent(Side.WHITE, Difficulty.EASY.level), replacement.opponent)
            assertEquals(ChessGame.newGame(), replacement.game)
            assertFalse(replacement.thinking)
            assertEquals(replacement.id, store.activeGame()!!.id)
            assertEquals(ChessGame.newGame(), store.activeGame()!!.game)
            assertEquals(emptyList<Any>(), store.completedGames())
        }
}
