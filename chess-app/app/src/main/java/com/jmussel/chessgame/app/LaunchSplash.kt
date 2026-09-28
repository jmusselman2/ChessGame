package com.jmussel.chessgame.app

/**
 * How long the launch splash stays in front of the app.
 *
 * The splash can show an icon and nothing else, so it covers only the stretch where there
 * is nothing to say: while startup is still [StartupState.Loading]. The moment startup
 * knows something — it is [StartupState.Ready], or it is [StartupState.Waking] the server,
 * or it has [StartupState.Failed] — the app's own screen has to be visible, because that
 * screen is what explains the wait and offers the retry.
 *
 * [LIMIT_MILLIS] bounds it even while still loading. A sleeping server's first request can
 * take a long time to fail — that failure is what reports [StartupState.Waking] — and a
 * logo that sits there for it reads as a frozen app. Past the limit the startup screen says
 * "Starting…" instead, which is the same wait with words on it.
 */
object LaunchSplash {
    /**
     * Long enough that a returning player with a stored session and an awake server goes
     * straight from the splash to the app on a slow device too: startup measured about
     * 1.2 s on a Pixel 7 and about 2.4 s on a Fire HD 8 (API 22), and 1.5 s left the Fire
     * flashing "Starting…" on every cold launch. Short enough that a stuck start soon says
     * so, and well inside the 5 s at which Android vitals calls a cold start slow.
     */
    const val LIMIT_MILLIS: Long = 3_000L

    /** Whether the splash should still cover the app after [shownForMillis]. */
    fun keepsOnScreen(
        startup: StartupState,
        shownForMillis: Long,
    ): Boolean = startup is StartupState.Loading && shownForMillis < LIMIT_MILLIS
}
