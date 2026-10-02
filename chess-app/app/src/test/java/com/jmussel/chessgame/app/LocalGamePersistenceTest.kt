package com.jmussel.chessgame.app

import com.jmussel.chessgame.ai.ChessEngine
import com.jmussel.chessgame.ai.Difficulty
import com.jmussel.chessgame.api.ChessServerConfig
import com.jmussel.chessgame.auth.InMemorySessionStore
import com.jmussel.chessgame.auth.SupabaseConfig
import com.jmussel.chessgame.computer.ComputerSetup
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.DrawClaim
import com.jmussel.chessgame.core.chess.GameResult
import com.jmussel.chessgame.core.chess.GameState
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.core.chess.TerminationReason
import com.jmussel.chessgame.local.ComputerOpponent
import com.jmussel.chessgame.local.LocalGameKind
import com.jmussel.chessgame.local.LocalGameStore
import com.jmussel.chessgame.local.inMemoryLocalGameStore
import com.jmussel.chessgame.navigation.Destination
import com.jmussel.chessgame.ui.board.BoardInteraction
import com.jmussel.chessgame.ui.board.BoardUiState
import com.jmussel.chessgame.ui.board.GameControls
import com.jmussel.chessgame.ui.board.LocalGameUiState
import com.jmussel.chessgame.ui.localhistory.PastLocalGames
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pass-and-play is saved on the device and resumes (`M21.2`, `D084`): through the view
 * model, as the screen drives it, against a real local-game store.
 *
 * The store is SQLite in memory. Handing the same store to a second view model is the app
 * after its process was lost: nothing but the store survives.
 */
class LocalGamePersistenceTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = inMemoryLocalGameStore()

    @Before
    fun useTheTestDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun releaseTheTestDispatcher() {
        Dispatchers.resetMain()
    }

    /** A model with no server: nothing here needs one, and any request would fail. */
    private fun viewModel(localGameStore: LocalGameStore = store): ChessAppViewModel =
        ChessAppViewModel(
            ChessAppDependencies(
                serverConfig = ChessServerConfig("https://chess.example"),
                supabaseConfig = SupabaseConfig(url = "https://supabase.example", anonKey = "publishable-key"),
                httpClient = HttpClient(MockEngine { respondError(HttpStatusCode.InternalServerError) }),
                sessionStore = InMemorySessionStore(),
                localGameStore = localGameStore,
                localGameDispatcher = dispatcher,
                chessEngine = FirstLegalMove,
                engineDispatcher = dispatcher,
            ),
        )

    /** A computer that plays its first legal move in coordinate order, on the test's thread. */
    private object FirstLegalMove : ChessEngine {
        override fun chooseMove(
            state: GameState,
            difficulty: Difficulty,
            isCancelled: () -> Boolean,
        ): Move = ChessRules.legalMoves(state).minBy { it.toString() }
    }

    /** The human's move in the game against the computer, if it is their turn: their first legal one. */
    private fun TestScope.playTheComputer(viewModel: ChessAppViewModel) {
        val state = viewModel.computerGame.state!!
        if (state.humansTurn) {
            val move = ChessRules.legalMoves(state.game).minBy { it.toString() }
            viewModel.computerGame.update(BoardUiState(ChessRules.applyMove(state.game, move), orientation = state.humanSide))
        }
        advanceUntilIdle()
    }

    /** The model on the dashboard with the local game opened and read. */
    private fun TestScope.openLocalGame(viewModel: ChessAppViewModel = viewModel()): ChessAppViewModel {
        if (viewModel.navigation.current != Destination.Dashboard) viewModel.restartAt(Destination.Dashboard)
        viewModel.open(Destination.LocalGame)
        advanceUntilIdle()
        assertFalse(viewModel.localGame.loading)
        return viewModel
    }

    /** Taps [squares] on the local game's board, one tap at a time, as the screen does. */
    private fun TestScope.tap(
        viewModel: ChessAppViewModel,
        vararg squares: String,
    ) {
        squares.forEach { square ->
            val state = viewModel.localGame
            viewModel.updateLocalGame(state.copy(boardState = BoardInteraction.onSquareTapped(state.boardState, Square.parse(square))))
        }
        advanceUntilIdle()
    }

    private fun TestScope.change(
        viewModel: ChessAppViewModel,
        transform: (BoardUiState) -> BoardUiState,
    ) {
        viewModel.updateLocalGame(LocalGameUiState(boardState = transform(viewModel.localGame.boardState)))
        advanceUntilIdle()
    }

    private val savedGame: ChessGame?
        get() = store.activeGame(LocalGameKind.PASS_AND_PLAY)?.game

    // --- Resuming ---------------------------------------------------------------------

    @Test
    fun openingTheLocalGameWithNothingUnfinishedStartsAndSavesOne() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.restartAt(Destination.Dashboard)

            viewModel.open(Destination.LocalGame)
            // Nothing to tap until the game has been read.
            assertTrue(viewModel.localGame.loading)
            advanceUntilIdle()

            assertEquals(ChessGame.newGame(), viewModel.localGame.boardState.game)
            assertEquals(LocalGameKind.PASS_AND_PLAY, store.activeGame(LocalGameKind.PASS_AND_PLAY)!!.kind)
        }

    @Test
    fun movesAreSavedAsTheyArePlayed() =
        runTest(dispatcher) {
            val viewModel = openLocalGame()

            tap(viewModel, "e2", "e4", "e7", "e5")

            assertEquals(listOf(Move.of("e2", "e4"), Move.of("e7", "e5")), savedGame!!.moves)
            assertEquals(viewModel.localGame.boardState.game, savedGame)
        }

    @Test
    fun backDoesNotDiscardTheGameAndLocalGameResumesIt() =
        runTest(dispatcher) {
            val viewModel = openLocalGame()
            tap(viewModel, "e2", "e4", "e7", "e5")
            val played = viewModel.localGame.boardState.game

            assertTrue(viewModel.back())
            assertEquals(Destination.Dashboard, viewModel.navigation.current)
            advanceUntilIdle()
            assertEquals(played, savedGame)

            openLocalGame(viewModel)
            assertEquals(played, viewModel.localGame.boardState.game)
            assertEquals(listOf("1. e2e4 e7e5"), GameControls.moveListLines(viewModel.localGame.boardState.game))
        }

    @Test
    fun theGameResumesAfterTheProcessIsLost() =
        runTest(dispatcher) {
            val first = openLocalGame()
            tap(first, "e2", "e4", "e7", "e5", "g1", "f3")
            val played = first.localGame.boardState.game

            // A new model on the same store: all that survives the process.
            val restarted = openLocalGame(viewModel())

            assertEquals(played, restarted.localGame.boardState.game)
            assertEquals(Side.BLACK, restarted.localGame.boardState.game.sideToMove)
        }

    @Test
    fun reopeningWhileTheGameIsShowingChangesNothing() =
        runTest(dispatcher) {
            val viewModel = openLocalGame()
            tap(viewModel, "e2")
            val selected = viewModel.localGame

            // A rotation recreates the screen, which reads the game back from the model.
            viewModel.open(Destination.LocalGame)
            advanceUntilIdle()

            assertEquals(selected, viewModel.localGame)
            assertEquals(Square.parse("e2"), viewModel.localGame.boardState.selectedSquare)
        }

    @Test
    fun undoIsSavedAndSurvivesRestarting() =
        runTest(dispatcher) {
            val viewModel = openLocalGame()
            tap(viewModel, "e2", "e4", "e7", "e5")

            change(viewModel, GameControls::undo)

            assertEquals(listOf(Move.of("e2", "e4")), savedGame!!.moves)
            assertEquals(viewModel.localGame.boardState.game, openLocalGame(viewModel()).localGame.boardState.game)
        }

    // --- A new game -------------------------------------------------------------------

    @Test
    fun aNewGameAsksBeforeReplacingAnUnfinishedOne() {
        val unfinished = BoardUiState(ChessRules.applyMove(ChessGame.newGame(), Move.of("e2", "e4")))
        val finished = BoardUiState(ChessRules.resign(unfinished.game, Side.WHITE))

        assertTrue(GameControls.canStartNewGame(unfinished))
        assertTrue(GameControls.newGameNeedsConfirmation(unfinished))
        assertTrue(GameControls.canStartNewGame(finished))
        assertFalse(GameControls.newGameNeedsConfirmation(finished))
        // A game nobody has moved in is already new: there is nothing to replace.
        assertFalse(GameControls.canStartNewGame(BoardUiState.newGame()))
    }

    @Test
    fun aConfirmedNewGameDeletesTheUnfinishedOneAndDoesNotKeepIt() =
        runTest(dispatcher) {
            val viewModel = openLocalGame()
            tap(viewModel, "e2", "e4")
            val replaced = store.activeGame(LocalGameKind.PASS_AND_PLAY)!!.id

            viewModel.updateLocalGame(viewModel.localGame.copy(confirmingNewGame = true))
            viewModel.startNewLocalGame()
            advanceUntilIdle()

            assertEquals(ChessGame.newGame(), viewModel.localGame.boardState.game)
            assertFalse(viewModel.localGame.confirmingNewGame)
            assertNull(store.game(replaced))
            assertNotEquals(replaced, store.activeGame(LocalGameKind.PASS_AND_PLAY)!!.id)
            assertEquals(emptyList<Any>(), store.completedGames())
        }

    @Test
    fun cancellingTheQuestionKeepsTheGame() =
        runTest(dispatcher) {
            val viewModel = openLocalGame()
            tap(viewModel, "e2", "e4")
            val played = viewModel.localGame.boardState.game

            viewModel.updateLocalGame(viewModel.localGame.copy(confirmingNewGame = true))
            viewModel.updateLocalGame(viewModel.localGame.copy(confirmingNewGame = false))
            advanceUntilIdle()

            assertEquals(played, viewModel.localGame.boardState.game)
            assertEquals(played, savedGame)
        }

    // --- The end of a game ------------------------------------------------------------

    @Test
    fun aGameEndingMoveSavesTheGameAsCompleted() =
        runTest(dispatcher) {
            val viewModel = openLocalGame()
            val id = store.activeGame(LocalGameKind.PASS_AND_PLAY)!!.id

            tap(viewModel, "f2", "f3", "e7", "e5", "g2", "g4", "d8", "h4")

            assertNull(store.activeGame(LocalGameKind.PASS_AND_PLAY))
            val kept = store.game(id)!!
            assertFalse(kept.isActive)
            assertEquals(GameResult.checkmate(Side.WHITE), kept.game.result)
            assertEquals(listOf(id), store.completedGames().map { it.id })
        }

    @Test
    fun resignationSavesTheGameAsCompleted() =
        runTest(dispatcher) {
            val viewModel = openLocalGame()
            val id = store.activeGame(LocalGameKind.PASS_AND_PLAY)!!.id
            tap(viewModel, "e2", "e4")

            change(viewModel) { GameControls.resign(it, Side.BLACK) }

            assertEquals(GameResult.resignation(Side.BLACK), store.game(id)!!.game.result)
            assertEquals(listOf(id), store.completedGames().map { it.id })
        }

    @Test
    fun aDrawClaimSavesTheGameAsCompleted() =
        runTest(dispatcher) {
            val viewModel = openLocalGame()
            val id = store.activeGame(LocalGameKind.PASS_AND_PLAY)!!.id
            repeat(2) { tap(viewModel, "g1", "f3", "g8", "f6", "f3", "g1", "f6", "g8") }
            // The last of those moves would repeat the position a third time, so it is
            // declared rather than played, and the claim is made on it (`D041`).
            assertEquals(
                Move.of("f6", "g8"),
                viewModel.localGame.boardState.declaredMove!!
                    .move,
            )

            change(viewModel) { GameControls.claimDeclaredDraw(it, DrawClaim.THREEFOLD_REPETITION) }

            val drawn = store.game(id)!!.game
            assertEquals(TerminationReason.THREEFOLD_REPETITION_CLAIM, drawn.result!!.reason)
            assertEquals(7, drawn.history.size)
            assertNull(store.activeGame(LocalGameKind.PASS_AND_PLAY))
        }

    @Test
    fun aFinishedGameOffersANewGameWithoutAskingAndIsKept() =
        runTest(dispatcher) {
            val viewModel = openLocalGame()
            val finished = store.activeGame(LocalGameKind.PASS_AND_PLAY)!!.id
            tap(viewModel, "f2", "f3", "e7", "e5", "g2", "g4", "d8", "h4")
            assertFalse(GameControls.newGameNeedsConfirmation(viewModel.localGame.boardState))

            viewModel.startNewLocalGame()
            advanceUntilIdle()

            assertEquals(ChessGame.newGame(), viewModel.localGame.boardState.game)
            assertEquals(listOf(finished), store.completedGames().map { it.id })
        }

    @Test
    fun aFinishedGameIsNotResumedByTheLocalGameEntry() =
        runTest(dispatcher) {
            val viewModel = openLocalGame()
            tap(viewModel, "f2", "f3", "e7", "e5", "g2", "g4", "d8", "h4")
            viewModel.back()

            openLocalGame(viewModel)

            assertEquals(ChessGame.newGame(), viewModel.localGame.boardState.game)
            assertEquals(1, store.completedGames().size)
        }

    // --- Past local games (M21.3) -----------------------------------------------------

    /** Plays fool's mate in the open local game, which finishes it, and returns its id. */
    private fun TestScope.finishAGame(viewModel: ChessAppViewModel): Long {
        if (viewModel.navigation.current != Destination.LocalGame) openLocalGame(viewModel)
        viewModel.startNewLocalGame()
        advanceUntilIdle()
        val id = store.activeGame(LocalGameKind.PASS_AND_PLAY)!!.id
        tap(viewModel, "f2", "f3", "e7", "e5", "g2", "g4", "d8", "h4")
        return id
    }

    @Test
    fun finishedGamesAreListedNewestFirst() =
        runTest(dispatcher) {
            val viewModel = openLocalGame()
            val finished = (1..3).map { finishAGame(viewModel) }
            // An unfinished game is not a past game.
            viewModel.startNewLocalGame()
            advanceUntilIdle()
            tap(viewModel, "e2", "e4")
            viewModel.back()

            viewModel.restartAt(Destination.History)
            viewModel.openPastLocalGames()
            assertTrue(viewModel.pastLocalGames.loading)
            advanceUntilIdle()

            assertEquals(Destination.PastLocalGames, viewModel.navigation.current)
            assertFalse(viewModel.pastLocalGames.loading)
            assertEquals(finished.reversed(), viewModel.pastLocalGames.games.map { it.id })
            assertTrue(viewModel.pastLocalGames.games.all { it.kind == LocalGameKind.PASS_AND_PLAY })
        }

    @Test
    fun aGameJustFinishedAppearsInPastLocalGames() =
        runTest(dispatcher) {
            val viewModel = openLocalGame()
            // The list is read straight after the game-ending move, before anything else runs.
            viewModel.startNewLocalGame()
            advanceUntilIdle()
            val id = store.activeGame(LocalGameKind.PASS_AND_PLAY)!!.id
            listOf("f2", "f3", "e7", "e5", "g2", "g4", "d8", "h4").forEach { square ->
                val state = viewModel.localGame
                viewModel.updateLocalGame(state.copy(boardState = BoardInteraction.onSquareTapped(state.boardState, Square.parse(square))))
            }
            viewModel.openPastLocalGames()
            advanceUntilIdle()

            assertEquals(listOf(id), viewModel.pastLocalGames.games.map { it.id })
            assertEquals(
                GameResult.checkmate(Side.WHITE),
                viewModel.pastLocalGames.games
                    .single()
                    .result,
            )
        }

    @Test
    fun aFinishedGameOpensReadOnlyAtItsEndAndStepsThroughItsMoves() =
        runTest(dispatcher) {
            val viewModel = openLocalGame()
            val id = finishAGame(viewModel)
            val kept = store.game(id)!!
            viewModel.restartAt(Destination.History)
            viewModel.openPastLocalGames()
            advanceUntilIdle()

            viewModel.openPastLocalGame(id)
            assertNull(viewModel.localReview)
            advanceUntilIdle()

            val review = viewModel.localReview!!
            assertEquals(Destination.PastLocalGame(id), viewModel.navigation.current)
            assertEquals(kept.game, review.game)
            assertEquals(4, review.ply)
            assertTrue(review.faceToFace)

            viewModel.stepLocalReview(1)
            assertEquals(listOf(Move.of("f2", "f3")), viewModel.localReview!!.game.moves)
            viewModel.stepLocalReview(0)
            assertEquals(ChessGame.newGame(), viewModel.localReview!!.game)

            // Looking back changes nothing that was kept.
            assertEquals(kept, store.game(id))
            assertTrue(viewModel.back())
            assertEquals(Destination.PastLocalGames, viewModel.navigation.current)
        }

    @Test
    fun anUnfinishedGameIsNotReviewed() =
        runTest(dispatcher) {
            val viewModel = openLocalGame()
            tap(viewModel, "e2", "e4")
            val unfinished = store.activeGame(LocalGameKind.PASS_AND_PLAY)!!.id

            viewModel.openPastLocalGame(unfinished)
            advanceUntilIdle()

            assertNull(viewModel.localReview)
        }

    // --- One unfinished game of each kind (M21.13, D090) ------------------------------

    @Test
    fun theLocalGameEntryLeavesAnUnfinishedComputerGameAlone() =
        runTest(dispatcher) {
            val computer = store.startGame(ComputerOpponent(Side.WHITE, 2))
            val played = ChessRules.applyMove(computer.game, Move.of("e2", "e4"))
            store.recordMove(computer.id, played)
            val viewModel = viewModel()
            viewModel.restartAt(Destination.Dashboard)

            viewModel.open(Destination.LocalGame)
            advanceUntilIdle()

            assertEquals(ChessGame.newGame(), viewModel.localGame.boardState.game)
            assertEquals(LocalGameKind.PASS_AND_PLAY, store.activeGame(LocalGameKind.PASS_AND_PLAY)!!.kind)
            assertEquals(computer.id, store.activeGame(LocalGameKind.COMPUTER)!!.id)
            assertEquals(played, store.activeGame(LocalGameKind.COMPUTER)!!.game)
        }

    @Test
    fun playTheComputerGoesStraightToTheLevelChoiceAndKeepsPassAndPlay() =
        runTest(dispatcher) {
            val viewModel = openLocalGame()
            tap(viewModel, "e2", "e4")
            val passAndPlay = store.activeGame(LocalGameKind.PASS_AND_PLAY)!!
            viewModel.back()

            viewModel.openComputerGame()
            advanceUntilIdle()
            assertEquals(Destination.ComputerGame, viewModel.navigation.current)
            assertEquals(ComputerSetup.ChooseDifficulty, viewModel.computerGame.setup)

            viewModel.computerGame.choose(Difficulty.EASY)
            advanceUntilIdle()

            assertEquals(passAndPlay, store.activeGame(LocalGameKind.PASS_AND_PLAY))
            assertEquals(ComputerOpponent(viewModel.computerGame.state!!.humanSide, 2), store.activeGame(LocalGameKind.COMPUTER)!!.computer)
        }

    @Test
    fun switchingBetweenTheTwoEntriesResumesBothGamesWhereTheyWere() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.restartAt(Destination.Dashboard)

            // A game against the computer, with a move each.
            viewModel.openComputerGame()
            advanceUntilIdle()
            viewModel.computerGame.choose(Difficulty.MEDIUM)
            advanceUntilIdle()
            playTheComputer(viewModel)
            val computerGame = viewModel.computerGame.state!!.game
            assertTrue(computerGame.moves.size >= 2)
            viewModel.back()

            // Pass-and-play, with a move.
            viewModel.open(Destination.LocalGame)
            advanceUntilIdle()
            tap(viewModel, "e2", "e4")
            viewModel.back()

            viewModel.openComputerGame()
            advanceUntilIdle()
            assertNull(viewModel.computerGame.setup)
            assertEquals(computerGame, viewModel.computerGame.state!!.game)
            viewModel.back()

            viewModel.open(Destination.LocalGame)
            advanceUntilIdle()
            assertEquals(listOf(Move.of("e2", "e4")), viewModel.localGame.boardState.game.moves)
        }

    @Test
    fun newGameOfOneKindDeletesOnlyThatKindsGame() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.restartAt(Destination.Dashboard)
            viewModel.openComputerGame()
            advanceUntilIdle()
            viewModel.computerGame.choose(Difficulty.EASY)
            advanceUntilIdle()
            playTheComputer(viewModel)
            val firstComputerGame = store.activeGame(LocalGameKind.COMPUTER)!!
            viewModel.back()
            openLocalGame(viewModel)
            tap(viewModel, "e2", "e4")
            val firstPassAndPlay = store.activeGame(LocalGameKind.PASS_AND_PLAY)!!

            // New game on pass-and-play leaves the computer game.
            viewModel.startNewLocalGame()
            advanceUntilIdle()
            assertNull(store.game(firstPassAndPlay.id))
            assertEquals(ChessGame.newGame(), store.activeGame(LocalGameKind.PASS_AND_PLAY)!!.game)
            assertEquals(firstComputerGame, store.activeGame(LocalGameKind.COMPUTER))
            tap(viewModel, "d2", "d4")
            val secondPassAndPlay = store.activeGame(LocalGameKind.PASS_AND_PLAY)!!
            viewModel.back()

            // New game against the computer leaves pass-and-play.
            viewModel.openComputerGame()
            advanceUntilIdle()
            viewModel.computerGame.newGame()
            viewModel.computerGame.confirmNewGame()
            viewModel.computerGame.choose(Difficulty.HARD)
            advanceUntilIdle()
            assertNull(store.game(firstComputerGame.id))
            assertEquals(4, store.activeGame(LocalGameKind.COMPUTER)!!.computer!!.difficulty)
            assertEquals(secondPassAndPlay, store.activeGame(LocalGameKind.PASS_AND_PLAY))
            assertEquals(emptyList<Any>(), store.completedGames())
        }

    @Test
    fun backFromTheComputersNewGameReturnsToTheGameAndOnlyThenLeavesIt() =
        runTest(dispatcher) {
            val game = store.startGame(ComputerOpponent(Side.WHITE, 1))
            val viewModel = viewModel()
            viewModel.restartAt(Destination.Dashboard)
            viewModel.openComputerGame()
            advanceUntilIdle()

            viewModel.computerGame.newGame()
            assertEquals(ComputerSetup.ConfirmNewGame, viewModel.computerGame.setup)
            assertTrue(viewModel.back())
            assertNull(viewModel.computerGame.setup)

            viewModel.computerGame.newGame()
            viewModel.computerGame.confirmNewGame()
            assertEquals(ComputerSetup.ChooseDifficulty, viewModel.computerGame.setup)
            assertTrue(viewModel.back())
            assertNull(viewModel.computerGame.setup)
            assertEquals(Destination.ComputerGame, viewModel.navigation.current)
            assertEquals(game.id, viewModel.computerGame.state!!.id)

            assertTrue(viewModel.back())
            assertEquals(Destination.Dashboard, viewModel.navigation.current)
            assertEquals(game.id, store.activeGame(LocalGameKind.COMPUTER)!!.id)
        }

    @Test
    fun aFinishedComputerGameShowsItsLevelAndColourInPastLocalGames() =
        runTest(dispatcher) {
            val game = store.startGame(ComputerOpponent(Side.BLACK, 4))
            store.recordResult(game.id, ChessRules.resign(game.game, Side.WHITE))
            val viewModel = viewModel()
            viewModel.restartAt(Destination.History)

            viewModel.openPastLocalGames()
            advanceUntilIdle()

            val listed = viewModel.pastLocalGames.games.single()
            assertEquals(ComputerOpponent(Side.BLACK, 4), listed.computer)
            assertEquals(
                "Computer (Hard) • You played Black • today • Black won by resignation",
                PastLocalGames.summaryFor(listed) { "today" },
            )

            viewModel.openPastLocalGame(game.id)
            advanceUntilIdle()
            assertFalse(viewModel.localReview!!.faceToFace)
            assertEquals(Side.BLACK, viewModel.localReview!!.orientation)
        }
}
