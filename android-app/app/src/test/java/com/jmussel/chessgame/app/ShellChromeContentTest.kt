package com.jmussel.chessgame.app

import com.jmussel.chessgame.navigation.AppNavigation
import com.jmussel.chessgame.navigation.Destination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which screens have the top row, and where the player's own username appears in it
 * (`M17.3`): on the dashboard's row, and nowhere else. The game screens have none
 * (`D073`).
 */
class ShellChromeContentTest {
    private val dashboard = AppNavigation(listOf(Destination.Dashboard))

    @Test
    fun theDashboardRowNamesThePlayer() {
        assertEquals("Taylor", ShellChromeContent.ownUsername(dashboard, "Taylor"))
    }

    @Test
    fun aScreenWithBackDoesNotNameThePlayer() {
        listOf(Destination.Friends, Destination.History, Destination.LocalGame, Destination.OnlineGame("game-1"))
            .forEach { destination ->
                assertNull(destination.toString(), ShellChromeContent.ownUsername(dashboard.open(destination), "Taylor"))
            }
    }

    @Test
    fun theGameScreensHaveNoTopRowBecauseTheyDrawTheirOwnBack() {
        listOf(Destination.LocalGame, Destination.OnlineGame("game-1")).forEach { destination ->
            assertFalse(destination.toString(), ShellChromeContent.hasChrome(dashboard.open(destination)))
        }
    }

    @Test
    fun everyOtherScreenAfterStartupHasTheTopRow() {
        assertTrue(ShellChromeContent.hasChrome(dashboard))
        listOf(Destination.Friends, Destination.AllUsers, Destination.History).forEach { destination ->
            assertTrue(destination.toString(), ShellChromeContent.hasChrome(dashboard.open(destination)))
        }
    }

    @Test
    fun startupAndOnboardingHaveNoRowToNameThePlayerIn() {
        assertNull(ShellChromeContent.ownUsername(AppNavigation(), "Taylor"))
        assertNull(ShellChromeContent.ownUsername(AppNavigation().restartAt(Destination.UsernameOnboarding), "Taylor"))
    }

    @Test
    fun anAccountWithoutAUsernameShowsNothing() {
        assertNull(ShellChromeContent.ownUsername(dashboard, null))
        assertNull(ShellChromeContent.ownUsername(dashboard, ""))
        assertNull(ShellChromeContent.ownUsername(dashboard, "   "))
    }
}
