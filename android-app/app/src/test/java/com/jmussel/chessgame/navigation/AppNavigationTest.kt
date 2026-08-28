package com.jmussel.chessgame.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the app starts, where each screen leads, and what a back press does.
 *
 * [AppNavigation] is free of Compose and Android, so every transition the shell supports
 * is checked here rather than on a device.
 */
class AppNavigationTest {
    private val game = Destination.OnlineGame("game-1")

    @Test
    fun theAppStartsAtStartup() {
        assertEquals(Destination.Startup, AppNavigation().current)
    }

    @Test
    fun startupHasNothingBehindIt() {
        val navigation = AppNavigation()

        assertFalse(navigation.canGoBack)
        assertNull(navigation.back())
    }

    @Test
    fun aNewAccountGoesFromStartupToOnboarding() {
        val navigation = AppNavigation().restartAt(Destination.UsernameOnboarding)

        assertEquals(Destination.UsernameOnboarding, navigation.current)
    }

    @Test
    fun onboardingCannotBeBackedOutOf() {
        val navigation = AppNavigation().restartAt(Destination.UsernameOnboarding)

        assertNull(navigation.back())
    }

    @Test
    fun aReturningPlayerGoesFromStartupStraightToTheDashboard() {
        val navigation = AppNavigation().restartAt(Destination.Dashboard)

        assertEquals(Destination.Dashboard, navigation.current)
    }

    @Test
    fun claimingAUsernameLeadsToTheDashboard() {
        val navigation =
            AppNavigation()
                .restartAt(Destination.UsernameOnboarding)
                .restartAt(Destination.Dashboard)

        assertEquals(Destination.Dashboard, navigation.current)
    }

    @Test
    fun theDashboardIsWhereTheAppIsLeftFrom() {
        val navigation = AppNavigation().restartAt(Destination.Dashboard)

        assertFalse(navigation.canGoBack)
        assertNull(navigation.back())
    }

    @Test
    fun startupIsNotSomewhereToGoBackTo() {
        val navigation = AppNavigation().restartAt(Destination.Dashboard)

        assertEquals(listOf(Destination.Dashboard), navigation.stack)
    }

    @Test
    fun theDashboardOpensFriends() {
        val navigation = dashboard().open(Destination.Friends)

        assertEquals(Destination.Friends, navigation.current)
        assertEquals(Destination.Dashboard, navigation.back()?.current)
    }

    @Test
    fun theDashboardOpensHistory() {
        val navigation = dashboard().open(Destination.History)

        assertEquals(Destination.History, navigation.current)
        assertEquals(Destination.Dashboard, navigation.back()?.current)
    }

    @Test
    fun theDashboardOpensALocalGame() {
        val navigation = dashboard().open(Destination.LocalGame)

        assertEquals(Destination.LocalGame, navigation.current)
        assertEquals(Destination.Dashboard, navigation.back()?.current)
    }

    @Test
    fun theDashboardOpensAnOnlineGame() {
        val navigation = dashboard().open(game)

        assertEquals(game, navigation.current)
        assertEquals(Destination.Dashboard, navigation.back()?.current)
    }

    @Test
    fun friendsOpensAnOnlineGameAndBackReturnsToFriends() {
        val navigation = dashboard().open(Destination.Friends).open(game)

        assertEquals(game, navigation.current)
        assertEquals(Destination.Friends, navigation.back()?.current)
        assertEquals(Destination.Dashboard, navigation.back()?.back()?.current)
    }

    @Test
    fun historyOpensAFinishedGameAndBackReturnsToHistory() {
        val navigation = dashboard().open(Destination.History).open(Destination.OnlineGame("game-9"))

        assertEquals(Destination.History, navigation.back()?.current)
    }

    @Test
    fun twoGamesAreTwoDestinations() {
        val navigation = dashboard().open(game).open(Destination.OnlineGame("game-2"))

        assertEquals(Destination.OnlineGame("game-2"), navigation.current)
        assertEquals(game, navigation.back()?.current)
    }

    @Test
    fun openingTheScreenAlreadyShowingChangesNothing() {
        val navigation = dashboard().open(game)

        assertEquals(navigation, navigation.open(game))
    }

    @Test
    fun goingBackLeavesTheScreensStillBehindItAlone() {
        val navigation = dashboard().open(Destination.History).open(game).back()

        assertEquals(listOf(Destination.Dashboard, Destination.History), navigation?.stack)
    }

    @Test
    fun aGameNeedsAnId() {
        assertThrows(IllegalArgumentException::class.java) { Destination.OnlineGame(" ") }
    }

    @Test
    fun thereIsAlwaysAScreenShowing() {
        assertThrows(IllegalArgumentException::class.java) { AppNavigation(emptyList()) }
    }

    @Test
    fun aScreenWithSomethingBehindItCanGoBack() {
        assertTrue(dashboard().open(Destination.Friends).canGoBack)
    }

    private fun dashboard(): AppNavigation = AppNavigation().restartAt(Destination.Dashboard)
}
