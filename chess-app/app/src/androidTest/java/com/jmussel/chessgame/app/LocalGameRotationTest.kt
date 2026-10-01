package com.jmussel.chessgame.app

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.jmussel.chessgame.BuildConfig
import com.jmussel.chessgame.MainActivity
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.local.LOCAL_GAME_DATABASE_NAME
import com.jmussel.chessgame.ui.board.squareTag
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * A local game in progress survives the activity being recreated, as a rotation does
 * (`D073`), and Back (`D084`), in the real app.
 *
 * The local game is reached from the dashboard, so this starts the app for real: it signs
 * in through Supabase and wakes the beta server, which can take a minute. It uses whatever
 * account the device has, and claims a throwaway username if the app is new. It is skipped
 * unless the build was pointed at an HTTPS server with a Supabase key, which is how
 * `docs/DEVELOPMENT.md` says to run it. Not run by CI.
 *
 * It deletes the device's local games first, so the game it opens is a new one.
 */
class LocalGameRotationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun needsTheLiveServer() {
        assumeTrue(
            "Build with -PchessServerUrl=https://… and the Supabase key to run this test",
            BuildConfig.CHESS_SERVER_URL.startsWith("https://") && BuildConfig.SUPABASE_ANON_KEY.isNotBlank(),
        )
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(LOCAL_GAME_DATABASE_NAME)
    }

    @Test
    fun aLocalGameInProgressSurvivesRecreationAndBack() {
        reachTheDashboard()
        openTheLocalGame()

        listOf("e2", "e4", "e7", "e5").forEach { square -> composeRule.onNodeWithTag(squareTag(Square.parse(square))).performClick() }
        composeRule.onNodeWithText(MOVES).assertExists()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithText(MOVES).assertExists()
        composeRule.onNodeWithText("WHITE to move").assertExists()
        Square.ALL.forEach { square -> composeRule.onNodeWithTag(squareTag(square)).assertIsNotSelected() }

        // Leaving with Back keeps it saved, and the local game resumes it (`D084`).
        composeRule.onNodeWithText("Back").performClick()
        openTheLocalGame()
        composeRule.onNodeWithText(MOVES).assertExists()
        composeRule.onNodeWithText("WHITE to move").assertExists()
    }

    /** Opens the local game and waits while it is read from the device. */
    private fun openTheLocalGame() {
        composeRule.onNodeWithText(LOCAL_GAME).performClick()
        composeRule.waitUntil(LOAD_MILLIS) { shows("to move") }
    }

    /** Waits out startup, and claims a throwaway username if this install has none yet. */
    private fun reachTheDashboard() {
        composeRule.waitUntil(STARTUP_MILLIS) { shows(LOCAL_GAME) || shows(CHOOSE_USERNAME) }

        if (shows(CHOOSE_USERNAME)) {
            composeRule.onNode(hasSetTextAction()).performTextInput("m177_${System.currentTimeMillis() % 1_000_000_000}")
            composeRule.onNodeWithText("Claim").performClick()
            composeRule.waitUntil(STARTUP_MILLIS) { shows(LOCAL_GAME) }
        }
    }

    private fun shows(text: String): Boolean = composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        /** The server's own wake deadline is 150 s (`D037`); allow for signing in as well. */
        const val STARTUP_MILLIS = 240_000L
        const val LOAD_MILLIS = 5_000L
        const val LOCAL_GAME = "Local game"
        const val CHOOSE_USERNAME = "Choose a username"
        const val MOVES = "1. e2e4 e7e5"
    }
}
