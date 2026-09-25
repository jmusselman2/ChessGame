package com.jmussel.chessgame.app

import com.jmussel.chessgame.api.CurrentUserDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the launch splash gives way to the app.
 *
 * The splash can say nothing, so it must never stand in front of a screen that has
 * something to say: a server being woken, a failure, or the app itself.
 */
class LaunchSplashTest {
    @Test
    fun `the splash stays while startup is still loading`() {
        assertTrue(LaunchSplash.keepsOnScreen(StartupState.Loading, shownForMillis = 0))
        assertTrue(LaunchSplash.keepsOnScreen(StartupState.Loading, shownForMillis = LaunchSplash.LIMIT_MILLIS - 1))
    }

    @Test
    fun `a slow device's ordinary start is still covered`() {
        // A Fire HD 8 took about 2.4 s to start; it should not flash "Starting…" on the way.
        assertTrue(LaunchSplash.keepsOnScreen(StartupState.Loading, shownForMillis = 2_500))
    }

    @Test
    fun `a slow start is handed to the startup screen at the limit`() {
        assertFalse(LaunchSplash.keepsOnScreen(StartupState.Loading, shownForMillis = LaunchSplash.LIMIT_MILLIS))
        assertFalse(LaunchSplash.keepsOnScreen(StartupState.Loading, shownForMillis = 65_000))
    }

    @Test
    fun `a ready player leaves the splash at once`() {
        val ready = StartupState.Ready(CurrentUserDto(userId = "user-1", username = "carla"))

        assertFalse(LaunchSplash.keepsOnScreen(ready, shownForMillis = 0))
    }

    @Test
    fun `a waking server is shown rather than hidden behind the splash`() {
        assertFalse(LaunchSplash.keepsOnScreen(StartupState.Waking(failures = 1, elapsedMillis = 300), shownForMillis = 300))
    }

    @Test
    fun `a failure is shown rather than hidden behind the splash, retryable or not`() {
        assertFalse(LaunchSplash.keepsOnScreen(StartupState.Failed("unreachable", canRetry = true), shownForMillis = 0))
        assertFalse(LaunchSplash.keepsOnScreen(StartupState.Failed("no key", canRetry = false), shownForMillis = 0))
    }
}
