package com.jmussel.chessgame.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jmussel.chessgame.navigation.AppNavigation
import com.jmussel.chessgame.navigation.Destination
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * What the application is showing and what it is built from.
 *
 * A `ViewModel` because the answer outlives the `Activity`: rotating the device must not
 * sign in again or drop the HTTP client, and nothing here holds an `Activity` reference,
 * so a recreated screen attaches to the state already there. When the state is finally
 * discarded — the app really is going away — [ChessAppDependencies] is closed with it.
 *
 * After the process itself is recreated there is no state to attach to and the app starts
 * at [Destination.Startup] again, which restores the stored session and lands on the
 * dashboard (`M14.6`).
 */
class ChessAppViewModel(
    private val dependencies: ChessAppDependencies,
) : ViewModel() {
    /** Which screen is showing, and what is behind it. */
    var navigation: AppNavigation by mutableStateOf(AppNavigation())
        private set

    /** How far getting a session has got. */
    var startup: StartupState by mutableStateOf(StartupState.Loading)
        private set

    /** What the screens are built from. */
    val app: ChessAppDependencies
        get() = dependencies

    /**
     * The startup run in flight, or the last one there was.
     *
     * Internal because only a test has any business waiting on it — the app watches
     * [startup], which says the same thing in the terms a screen needs.
     */
    internal var startupJob: Job? = null
        private set

    /**
     * Restores or creates the anonymous session, then goes to the dashboard.
     *
     * Safe to call again: a run already under way is left alone and a session already
     * obtained is not obtained twice, so a recreated activity cannot end up with two
     * anonymous accounts. Calling it after a failure is the retry.
     */
    fun start() {
        if (startupJob?.isActive == true || startup is StartupState.Ready) return

        startupJob =
            viewModelScope.launch {
                startup = StartupState.Loading

                val result = dependencies.startup.run()
                startup = result

                // Where a named user goes instead of the dashboard is M14.7.
                if (result is StartupState.Ready) restartAt(Destination.Dashboard)
            }
    }

    /** Shows [destination] in front of the current screen. */
    fun open(destination: Destination) {
        navigation = navigation.open(destination)
    }

    /**
     * Shows [destination] with nothing behind it.
     *
     * Startup and onboarding hand over this way: neither is somewhere to go back to.
     */
    fun restartAt(destination: Destination) {
        navigation = navigation.restartAt(destination)
    }

    /**
     * Goes back one screen.
     *
     * Returns `false` when there is nothing behind the current screen, which means the
     * back press belongs to the system and the app should close.
     */
    fun back(): Boolean {
        val previous = navigation.back() ?: return false
        navigation = previous
        return true
    }

    override fun onCleared() {
        dependencies.close()
    }

    companion object {
        /**
         * Builds the model, and its dependencies with it, only when there is not one already.
         *
         * [dependencies] is a supplier rather than an instance so that an `Activity`
         * recreated onto an existing model does not create an HTTP client that nothing
         * would ever close.
         */
        fun factory(dependencies: () -> ChessAppDependencies): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = ChessAppViewModel(dependencies()) as T
            }
    }
}
