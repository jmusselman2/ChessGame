package com.jmussel.chessgame.ui.board

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jmussel.chessgame.api.DrawOfferDto
import com.jmussel.chessgame.api.GameViewDto
import com.jmussel.chessgame.api.MoveDto
import com.jmussel.chessgame.api.UndoRequestDto
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.GameState
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.ui.game.OnlineGame
import com.jmussel.chessgame.ui.game.OnlineGameScreen
import com.jmussel.chessgame.ui.game.OnlineGameState
import com.jmussel.chessgame.ui.review.GameReview
import com.jmussel.chessgame.ui.review.GameReviewScreen
import com.jmussel.chessgame.ui.review.ReviewMove
import com.jmussel.chessgame.ui.review.ReviewPosition
import com.jmussel.chessgame.ui.theme.ChessGameTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The move list following the position (`D105`, `M23.2`), drawn on a device: Review shows only
 * the moves up to its ply, every live game marks its last move, a new current move scrolls into
 * view while an unrelated recomposition leaves the player's scroll alone, and scrolling the list
 * never moves the board, the captured pieces or a control. Not run by CI: see
 * `docs/DEVELOPMENT.md`.
 */
class MoveHistoryUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var viewport by mutableStateOf(PIXEL_7_PORTRAIT)
    private var fontScale by mutableFloatStateOf(1f)

    @Before
    fun keepTheScreenOn() = keepScreenOn(composeRule.activity)

    /** A long unfinished game, with captures on both sides as most long games have. */
    private val long = longGame(plies = 120)
    private val longLines = GameControls.moveListLines(long.game)

    // Review.

    @Test
    fun reviewShowsOnlyTheMovesUpToItsPlyAndMarksTheLast() {
        var review by mutableStateOf(
            reviewOf(
                ChessRules.applyMove(
                    ChessRules.applyMove(ChessRules.applyMove(ChessGame.newGame(), move("e2e4")), move("e7e5")),
                    move("g1f3"),
                ),
            ),
        )
        show { GameReviewScreen(review = review, onStep = { review = review.at(it) }, onBack = {}) }

        // Opened at the end: every move, the last current.
        composeRule.onNodeWithText("2. Nf3").assertIsDisplayed()
        assertCurrent("Nf3")

        composeRule.onNodeWithText(START).performClick()
        composeRule.onAllNodes(inHistory).assertCountEquals(0)
        currentNodes().assertCountEquals(0)

        composeRule.onNodeWithText(NEXT).performClick()
        composeRule.onNodeWithText("1. e4").assertIsDisplayed()
        // Black's reply and everything after it are after the position: not drawn, not spoken.
        composeRule.onAllNodes(hasText("e5", substring = true)).assertCountEquals(0)
        composeRule.onAllNodes(hasText("Nf3", substring = true)).assertCountEquals(0)
        assertCurrent("e4")

        composeRule.onNodeWithText(NEXT).performClick()
        composeRule.onNodeWithText("1. e4 e5").assertIsDisplayed()
        composeRule.onAllNodes(hasText("Nf3", substring = true)).assertCountEquals(0)
        assertCurrent("e5")

        composeRule.onNodeWithText(END).performClick()
        assertCurrent("Nf3")
        composeRule.onNodeWithText(PREVIOUS).performClick()
        assertCurrent("e5")
        composeRule.onAllNodes(hasText("Nf3", substring = true)).assertCountEquals(0)
    }

    @Test
    fun reviewStepsBringTheCurrentMoveIntoViewAndScrollingNeverMovesTheButtons() {
        var review by mutableStateOf(reviewOf(long.game))
        show { GameReviewScreen(review = review, onStep = { review = review.at(it) }, onBack = {}) }

        WINDOWS.forEach { window ->
            showIn(window)
            composeRule.runOnIdle { review = review.at(review.plies) }
            composeRule.waitForIdle()
            composeRule.onNodeWithText(longLines.last()).assertIsDisplayed()

            // Scroll the list back to the first move: no navigation button moves.
            val buttons = listOf(START, PREVIOUS, NEXT, END).associateWith { bounds(it) }
            composeRule.onNodeWithText(longLines.first()).performScrollTo().assertIsDisplayed()
            buttons.forEach { (label, before) -> assertEquals("$window: $label", before, bounds(label)) }

            // Previous from the end brings the new current move back into view.
            reach(PREVIOUS)
            composeRule.onNodeWithText(PREVIOUS).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText(longLines.last().substringBeforeLast(" ")).assertIsDisplayed()

            // Start clears the list; End shows the final move again.
            reach(START)
            composeRule.onNodeWithText(START).performClick()
            composeRule.onAllNodes(inHistory).assertCountEquals(0)
            reach(END)
            composeRule.onNodeWithText(END).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText(longLines.last()).assertIsDisplayed()
        }
    }

    // Live games.

    @Test
    fun scrollingTheHistoryMovesNoControlInAnyWindowOrTextSize() {
        // A move taken back, so Undo and Redo are both offered, as are both resignations and New game.
        val state = LocalGameUiState(boardState = GameControls.undo(long))
        show { LocalGameScreen(state = state, onStateChange = {}, onBack = {}) }
        val lines = GameControls.moveListLines(state.boardState.game)
        val controls =
            listOf(
                "Back",
                GameControls.statusFor(state.boardState.game),
                GameControls.undoLabelFor(state.boardState)!!,
                GameControls.REDO_LABEL,
                GameControls.resignLabelFor(Side.WHITE),
                GameControls.resignLabelFor(Side.BLACK),
                NEW_GAME,
            )

        WINDOWS.forEach { window ->
            listOf(1f, 2f).forEach { scale ->
                showIn(window, scale)
                val where = "$window at $scale"
                assertTrue("$where: the current move is in sight", composeRule.onNodeWithText(lines.last()).isDisplayedNow())
                val fixed = (
                    controls.map { bounds(it) } +
                        listOf(CHESS_BOARD_TAG, CAPTURED_TOP_TAG, CAPTURED_BOTTOM_TAG).map(composeRule::boundsOf)
                )

                // The player scrolls the history back to the first move.
                composeRule.onNodeWithText(lines.first()).performScrollTo()
                composeRule.onNodeWithText(lines.last()).assertIsNotDisplayed()

                assertEquals(
                    "$where: nothing fixed moved",
                    fixed,
                    controls.map { bounds(it) } + listOf(CHESS_BOARD_TAG, CAPTURED_TOP_TAG, CAPTURED_BOTTOM_TAG).map(composeRule::boundsOf),
                )
                // Every control is still there to use, scrolling its own region where the window
                // is too short for all of them, and that leaves the history where it was.
                val history = composeRule.onNodeWithTag(MOVE_HISTORY_TAG).fetchSemanticsNode().boundsInRoot
                val firstShown = composeRule.onNodeWithText(lines.first()).fetchSemanticsNode().boundsInRoot
                controls.forEach { label -> reach(label) }
                assertEquals(
                    "$where: the history did not move",
                    history,
                    composeRule.onNodeWithTag(MOVE_HISTORY_TAG).fetchSemanticsNode().boundsInRoot,
                )
                assertEquals(
                    "$where: nor its contents",
                    firstShown,
                    composeRule.onNodeWithText(lines.first()).fetchSemanticsNode().boundsInRoot,
                )

                if (window.liveSpec.twoPane) {
                    val side = composeRule.boundsOf(CHESS_BOARD_TAG).width / composeRule.pixelsPerDp(window)
                    assertEquals("$where: the two-pane board keeps its size", window.spec.boardSide.value, side, 1f)
                }
                // Back to the end for the next window.
                composeRule.onNodeWithText(lines.last()).performScrollTo()
            }
        }
    }

    @Test
    fun aManualScrollSurvivesARecompositionAndANewCurrentMoveScrollsIntoView() {
        var state by mutableStateOf(LocalGameUiState(boardState = long))
        show { LocalGameScreen(state = state, onStateChange = { state = it }, onBack = {}) }

        listOf(PIXEL_7_PORTRAIT, OWNER_LANDSCAPE).forEach { window ->
            composeRule.runOnIdle { state = LocalGameUiState(boardState = long) }
            showIn(window)
            composeRule.onNodeWithText(longLines.first()).performScrollTo().assertIsDisplayed()

            // Picking a piece up recomposes the screen with the same current move.
            val piece = ChessRules.legalMoves(long.game).first().from
            composeRule.runOnIdle { state = state.copy(boardState = BoardInteraction.onSquareTapped(state.boardState, piece)) }
            composeRule.waitForIdle()
            composeRule.onNodeWithText(longLines.first()).assertIsDisplayed()
            assertCurrent(
                long.game.history
                    .last()
                    .let { ChessRules.san(it.positionBefore, it.move) },
            )

            // A move is a new current move: it scrolls into view.
            val played = ChessRules.applyMove(long.game, ChessRules.legalMoves(long.game).first())
            composeRule.runOnIdle { state = LocalGameUiState(boardState = BoardUiState(game = played)) }
            composeRule.waitForIdle()
            val playedLines = GameControls.moveListLines(played)
            composeRule.onNodeWithText(playedLines.last()).assertIsDisplayed()
            composeRule.onNodeWithText(playedLines.first()).assertIsNotDisplayed()

            // Undo makes the previous move current; scrolled away from, it comes back into view.
            composeRule.onNodeWithText(playedLines.first()).performScrollTo()
            val undone = GameControls.undo(BoardUiState(game = played))
            composeRule.runOnIdle { state = LocalGameUiState(boardState = undone) }
            composeRule.waitForIdle()
            composeRule.onNodeWithText(longLines.last()).assertIsDisplayed()
            assertCurrent(GameControls.moveHistory(undone.game).current!!.san)

            // So does Redo.
            composeRule.onNodeWithText(longLines.first()).performScrollTo()
            composeRule.runOnIdle { state = LocalGameUiState(boardState = GameControls.redo(undone)) }
            composeRule.waitForIdle()
            composeRule.onNodeWithText(playedLines.last()).assertIsDisplayed()
        }
    }

    @Test
    fun anOnlineRefreshKeepsTheScrollUnlessItBringsANewMove() {
        val moves = movesOf(long.game)
        var state by mutableStateOf<OnlineGameState>(OnlineGameState.Ready(onlineGame(moves, version = 10)))
        show { OnlineGameScreen(state = state, onBack = {}) }
        val lines = OnlineGame.moveListLines(onlineGame(moves, 10))

        listOf(PIXEL_7_PORTRAIT, FIRE_HD_8_LANDSCAPE).forEach { window ->
            composeRule.runOnIdle { state = OnlineGameState.Ready(onlineGame(moves, version = 10)) }
            showIn(window)
            composeRule.onNodeWithText(lines.last()).assertIsDisplayed()
            composeRule.onNodeWithText(lines.first()).performScrollTo()

            // The same moves at a later version, as a refresh with nothing new brings.
            composeRule.runOnIdle { state = OnlineGameState.Ready(onlineGame(moves, version = 11)) }
            composeRule.waitForIdle()
            composeRule.onNodeWithText(lines.first()).assertIsDisplayed()

            // The opponent's move, learned by refresh or realtime: current, and in view.
            val reply = ChessRules.applyMove(long.game, ChessRules.legalMoves(long.game).first())
            val newer = onlineGame(movesOf(reply), version = 12)
            composeRule.runOnIdle { state = OnlineGameState.Ready(newer) }
            composeRule.waitForIdle()
            composeRule.onNodeWithText(OnlineGame.moveListLines(newer).last()).assertIsDisplayed()
            assertCurrent(newer.moves.last().san)
        }
    }

    @Test
    fun aListShowingTheCurrentMoveKeepsShowingItWhenRequestsShortenIt() {
        val moves = movesOf(long.game)
        val plain = onlineGame(moves, version = 10)
        // An undo request and a draw offer to answer: two more lines and two rows of buttons
        // above the list, which gets shorter, as on the Pixel 7 during M23.2's two-phone play.
        val asked =
            plain.copy(
                version = 11,
                undoRequest =
                    UndoRequestDto(
                        fromYou = false,
                        requesterMove = moves[moves.size - 2],
                        replyMove = moves.last(),
                    ),
                drawOffer = DrawOfferDto(fromYou = false),
            )
        var state by mutableStateOf<OnlineGameState>(OnlineGameState.Ready(plain))
        show { OnlineGameScreen(state = state, onBack = {}) }
        val lines = OnlineGame.moveListLines(plain)

        listOf(PIXEL_7_PORTRAIT, OWNER_PORTRAIT, FIRE_HD_8_LANDSCAPE).forEach { window ->
            composeRule.runOnIdle { state = OnlineGameState.Ready(plain) }
            showIn(window)
            val tall =
                composeRule
                    .onNodeWithTag(MOVE_HISTORY_TAG)
                    .fetchSemanticsNode()
                    .boundsInRoot.height

            composeRule.runOnIdle { state = OnlineGameState.Ready(asked) }
            composeRule.waitForIdle()
            val short =
                composeRule
                    .onNodeWithTag(MOVE_HISTORY_TAG)
                    .fetchSemanticsNode()
                    .boundsInRoot.height
            assertTrue("$window: the list is shorter with the requests ($short of $tall px)", short < tall)
            composeRule.onNodeWithText(lines.last()).assertIsDisplayed()
            assertCurrent(moves.last().san)

            // Scrolled back by the player, the list stays where they put it as it shortens.
            composeRule.runOnIdle { state = OnlineGameState.Ready(plain.copy(version = 12)) }
            composeRule.waitForIdle()
            composeRule.onNodeWithText(lines.first()).performScrollTo()
            composeRule.runOnIdle { state = OnlineGameState.Ready(asked.copy(version = 13)) }
            composeRule.waitForIdle()
            composeRule.onNodeWithText(lines.first()).assertIsDisplayed()
        }
    }

    @Test
    fun exactlyOneMoveIsCurrentAndItIsMarkedByMoreThanColour() {
        show { LocalGameScreen(state = LocalGameUiState(boardState = localMoves("e2", "e4", "e7", "e5")), onStateChange = {}, onBack = {}) }

        currentNodes().assertCountEquals(1)
        assertCurrent("e5")
        // Bold, as well as its highlight, and only that move.
        val text =
            composeRule
                .onNodeWithText("1. e4 e5")
                .fetchSemanticsNode()
                .config[SemanticsProperties.Text]
                .single()
        val bold = text.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }.map { text.text.substring(it.start, it.end) }
        assertEquals(listOf("e5"), bold)
        // Display only: no move can be tapped.
        composeRule.onAllNodes(inHistory and hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun aNewGameHasNoCurrentMove() {
        show { LocalGameScreen(state = LocalGameUiState(), onStateChange = {}, onBack = {}) }

        currentNodes().assertCountEquals(0)
        composeRule.onAllNodes(inHistory).assertCountEquals(0)
    }

    private val inHistory = hasAnyAncestor(hasTestTag(MOVE_HISTORY_TAG)) and hasText("", substring = true)

    private fun currentNodes() =
        composeRule.onAllNodes(
            SemanticsMatcher("is the current move") { node ->
                node.config.getOrNull(SemanticsProperties.StateDescription)?.startsWith(CURRENT_MOVE) == true
            },
        )

    private fun assertCurrent(san: String) {
        currentNodes().assertCountEquals(1)
        val node = currentNodes().fetchSemanticsNodes().single()
        assertEquals("$CURRENT_MOVE: $san", node.config[SemanticsProperties.StateDescription])
    }

    /** Shows [label]: it is in view already, or its own region scrolls to it. */
    private fun reach(label: String) {
        val node = composeRule.onNodeWithText(label)
        if (!node.isDisplayedNow()) node.performScrollTo()
        node.assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.isDisplayedNow(): Boolean = runCatching { assertIsDisplayed() }.isSuccess

    private fun bounds(label: String): Rect = composeRule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot

    private fun move(coordinates: String) =
        com.jmussel.chessgame.core.chess.Move
            .of(coordinates.take(2), coordinates.substring(2, 4))

    private fun reviewOf(game: ChessGame): GameReview {
        fun positionOf(state: GameState) = ReviewPosition(state.board, state.sideToMove, ChessRules.isInCheck(state))

        return GameReview(
            title = "Against Alex",
            moves = game.history.map { ReviewMove(it.move.from, it.move.to, ChessRules.san(it.positionBefore, it.move)) },
            positions = game.history.map { positionOf(it.positionBefore) } + positionOf(game.state),
            ending = "The game ended",
            orientation = Side.WHITE,
        )
    }

    private fun movesOf(game: ChessGame): List<MoveDto> =
        game.history.map {
            MoveDto(
                from = it.move.from.toString(),
                to = it.move.to.toString(),
                san = ChessRules.san(it.positionBefore, it.move),
            )
        }

    private fun onlineGame(
        moves: List<MoveDto>,
        version: Long,
    ) = GameViewDto(
        gameId = "game-1",
        seriesId = "series-1",
        opponent = UserSummaryDto(userId = "user-1", username = "Alex"),
        version = version,
        yourSide = "WHITE",
        sideToMove = if (moves.size % 2 == 0) "WHITE" else "BLACK",
        board = listOf("rnbqkbnr", "pppppppp", "........", "........", "........", "........", "PPPPPPPP", "RNBQKBNR"),
        moves = moves,
        lastMove = moves.lastOrNull(),
        canOfferDraw = true,
    )

    /**
     * [content] in the current window and text size. Each new window or size starts a fresh
     * screen, as rotating a device or changing its font size recreates the activity; a forced
     * size alone would keep a list scrolled for the previous one.
     */
    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { ChessGameTheme { InViewport(viewport, fontScale) { key(viewport, fontScale) { content() } } } }
    }

    private fun showIn(
        window: Viewport,
        scale: Float = 1f,
    ) {
        composeRule.runOnIdle {
            viewport = window
            fontScale = scale
        }
        composeRule.waitForIdle()
    }

    private companion object {
        val PIXEL_7_PORTRAIT = Viewport("Pixel 7 stock portrait", 411.dp, 914.dp)
        val PIXEL_7_LANDSCAPE = Viewport("Pixel 7 stock landscape", 834.dp, 383.dp)
        val OWNER_LANDSCAPE = Viewport("Pixel 7 at owner's size, landscape", 969.dp, 457.dp)
        val OWNER_PORTRAIT = Viewport("Pixel 7 at owner's size, portrait", 485.dp, 1078.dp)
        val FIRE_HD_8_PORTRAIT = Viewport("Fire HD 8 portrait", 601.dp, 914.dp)
        val FIRE_HD_8_LANDSCAPE = Viewport("Fire HD 8 landscape", 962.dp, 553.dp)
        val NARROW_PHONE = Viewport("small phone portrait", 360.dp, 640.dp)
        val SHORT_LANDSCAPE = Viewport("small phone landscape after insets", 616.dp, 332.dp)

        val WINDOWS =
            listOf(
                PIXEL_7_PORTRAIT,
                PIXEL_7_LANDSCAPE,
                OWNER_LANDSCAPE,
                FIRE_HD_8_PORTRAIT,
                FIRE_HD_8_LANDSCAPE,
                NARROW_PHONE,
                SHORT_LANDSCAPE,
            )

        const val START = "Start"
        const val PREVIOUS = "Previous"
        const val NEXT = "Next"
        const val END = "End"
        const val NEW_GAME = "New game"
    }
}
