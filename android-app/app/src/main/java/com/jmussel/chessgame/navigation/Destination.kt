package com.jmussel.chessgame.navigation

/**
 * A screen the application can be showing.
 *
 * These are the top-level destinations of the MVP and nothing else: the app is small
 * enough that the whole set fits in one file, and a route/deep-link framework would be
 * machinery with nothing to do (`docs/ARCHITECTURE.md`).
 */
sealed interface Destination {
    /** Restoring or creating the anonymous session before anything else can be shown. */
    data object Startup : Destination

    /** Claiming a username, which a new anonymous account has to do before playing. */
    data object UsernameOnboarding : Destination

    /** The home screen a returning player lands on (`docs/PRODUCT.md`). */
    data object Dashboard : Destination

    /** Adding and removing friends, who are the only people there is anything to do with. */
    data object Friends : Destination

    /** Games that are over. */
    data object History : Destination

    /** Pass-and-play on one device, which needs no account and no server. */
    data object LocalGame : Destination

    /** One server-owned game, identified the way the server identifies it. */
    data class OnlineGame(
        val gameId: String,
    ) : Destination {
        init {
            require(gameId.isNotBlank()) { "A game needs an id" }
        }
    }
}

/**
 * Which screen is showing and what is behind it.
 *
 * Immutable, and free of Compose and Android, so every transition is decided in one place
 * and tested without a screen. The stack is never empty: [current] is always the last
 * thing pushed.
 *
 * Nothing here is persisted. After the process is recreated the app starts at
 * [Destination.Startup] again and reaches the dashboard through the stored session, which
 * is the only thing that has to survive (`D006`).
 */
data class AppNavigation(
    val stack: List<Destination> = listOf(Destination.Startup),
) {
    init {
        require(stack.isNotEmpty()) { "There is always a screen showing" }
    }

    /** The screen showing now. */
    val current: Destination
        get() = stack.last()

    /** Whether [back] has somewhere to go rather than leaving the app. */
    val canGoBack: Boolean
        get() = stack.size > 1

    /**
     * [destination] in front of the current screen, which stays behind it.
     *
     * Opening the screen already showing changes nothing, so a double tap cannot stack a
     * screen on top of itself.
     */
    fun open(destination: Destination): AppNavigation = if (destination == current) this else copy(stack = stack + destination)

    /**
     * [destination] as the only screen, with nothing behind it.
     *
     * This is how startup and onboarding hand over: neither is somewhere to go back to —
     * the session is already restored, the username is already claimed — so the dashboard
     * becomes the screen the player leaves the app from.
     */
    fun restartAt(destination: Destination): AppNavigation = AppNavigation(listOf(destination))

    /**
     * The screen behind this one, or `null` when there is none.
     *
     * `null` means the back press belongs to the system: there is nothing left to go back
     * to and the app closes.
     */
    fun back(): AppNavigation? = if (canGoBack) copy(stack = stack.dropLast(1)) else null
}
