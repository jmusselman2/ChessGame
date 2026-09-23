package com.jmussel.chessgame.app

import com.jmussel.chessgame.navigation.AppNavigation
import com.jmussel.chessgame.navigation.Destination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where the player's own username appears in the top row (`M17.3`): on the dashboard's
 * row, and nowhere else.
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
