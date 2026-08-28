package com.jmussel.chessgame.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jmussel.chessgame.api.ChessApiClient
import com.jmussel.chessgame.api.ChessApiException
import com.jmussel.chessgame.api.CurrentUserDto
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.navigation.AppNavigation
import com.jmussel.chessgame.navigation.Destination
import com.jmussel.chessgame.ui.friends.Friends
import com.jmussel.chessgame.ui.friends.FriendsUiState
import com.jmussel.chessgame.ui.onboarding.UsernameClaim
import com.jmussel.chessgame.ui.onboarding.UsernameOnboarding
import kotlinx.coroutines.CancellationException
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

    /** How far getting a session and an identity has got. */
    var startup: StartupState by mutableStateOf(StartupState.Loading)
        private set

    /** Who the server says the player is, once startup has asked it. */
    var currentUser: CurrentUserDto? by mutableStateOf(null)
        private set

    /** How far claiming a username has got. */
    var usernameClaim: UsernameClaim by mutableStateOf(UsernameClaim.Idle)
        private set

    /** The friends screen: who they are, and what is happening on it. */
    var friends: FriendsUiState by mutableStateOf(FriendsUiState())
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

    /** The claim in flight, if any. Internal for the same reason as [startupJob]. */
    internal var usernameClaimJob: Job? = null
        private set

    /** Whatever the friends screen has asked for, if anything. Internal for the same reason. */
    internal var friendsJob: Job? = null
        private set

    /**
     * Restores or creates the anonymous session, asks the server who it belongs to, and
     * goes wherever that answer says.
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

                if (result is StartupState.Ready) arriveAs(result.user)
            }
    }

    /**
     * Claims [requested] as this account's username and goes on to the dashboard.
     *
     * Whether the name is allowed and whether it is still free are the server's answers
     * (`D007`), so a refusal is shown in the server's own words and the player can try
     * another. An empty box is not sent at all. A claim already in flight is left alone,
     * so a second tap cannot claim twice.
     */
    fun claimUsername(requested: String) {
        if (usernameClaimJob?.isActive == true) return
        if (!UsernameOnboarding.isSendable(requested)) return

        usernameClaimJob =
            viewModelScope.launch {
                usernameClaim = UsernameClaim.Claiming

                try {
                    val claimed = dependencies.chessApi.claimUsername(UsernameOnboarding.cleaned(requested))
                    usernameClaim = UsernameClaim.Idle
                    arriveAs(CurrentUserDto(userId = currentUser?.userId.orEmpty(), username = claimed))
                } catch (refused: ChessApiException) {
                    usernameClaim = UsernameClaim.Rejected(UsernameOnboarding.messageFor(refused))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (unreachable: Exception) {
                    usernameClaim = UsernameClaim.Rejected(UsernameOnboarding.unreachableMessage())
                }
            }
    }

    /**
     * Settles on the screen [user] belongs on.
     *
     * A player with a name goes to the dashboard; one without goes to onboarding, which is
     * the only thing a new account can do. Neither has anything behind it.
     */
    private fun arriveAs(user: CurrentUserDto) {
        currentUser = user
        restartAt(if (user.username == null) Destination.UsernameOnboarding else Destination.Dashboard)
    }

    /**
     * Opens the friends screen and loads it.
     *
     * The list is fetched every time it is opened rather than cached, because the other
     * side of a friendship can change it while the app is elsewhere.
     */
    fun openFriends() {
        open(Destination.Friends)
        loadFriends()
    }

    /** Fetches the friends list, keeping whatever is on screen until the new one arrives. */
    fun loadFriends() {
        if (friendsJob?.isActive == true) return

        friendsJob = viewModelScope.launch { fetchFriends() }
    }

    /**
     * Looks [requested] up by exact username, so the player sees who they are about to add.
     *
     * A name that belongs to nobody is the server's answer, in the server's words (`D009`).
     */
    fun findUser(requested: String) {
        if (!Friends.isSendable(requested)) return

        runOnFriends { api ->
            friends = friends.copy(found = api.lookUpUser(Friends.cleaned(requested)), message = null)
        }
    }

    /** Forgets the person a lookup found, without adding them. */
    fun dismissFoundUser() {
        if (friends.found == null) return
        friends = friends.copy(found = null)
    }

    /**
     * Becomes friends with [username], which is mutual immediately (`D009`).
     *
     * Adding yourself, or someone who is already a friend, is refused by the server and
     * reported in its own words. The list is reloaded afterwards rather than guessed at.
     */
    fun addFriend(username: String) {
        if (!Friends.isSendable(username)) return

        runOnFriends { api ->
            val added = api.addFriend(Friends.cleaned(username))
            friends = friends.copy(found = null, message = "Added $added.")
            fetchFriends()
        }
    }

    /** Asks whether [friend] really should be removed, and what that will do. */
    fun askToRemoveFriend(friend: UserSummaryDto) {
        friends = friends.copy(removing = friend, message = null)
    }

    /** Leaves the removal unmade. */
    fun cancelRemoveFriend() {
        friends = friends.copy(removing = null)
    }

    /**
     * Removes [friend], after the confirmation has been given.
     *
     * What that does to a game under way is the server's to say (`D013`), so its sentence
     * is what the player reads. The list is reloaded rather than guessed at.
     */
    fun removeFriend(friend: UserSummaryDto) {
        friends = friends.copy(removing = null)

        runOnFriends { api ->
            friends = friends.copy(message = api.removeFriend(friend.username))
            fetchFriends()
        }
    }

    /**
     * Opens the game with [friend], asking the server for the series it belongs to.
     *
     * Whether that opens the series already running or starts one is the server's business,
     * not the app's (`D011`). A series between games has nothing to open yet, and says so.
     */
    fun playFriend(friend: UserSummaryDto) {
        runOnFriends { api ->
            val gameId = api.openSeries(friend.username).currentGameId

            if (gameId == null) {
                friends = friends.copy(message = "No game with ${friend.username} to open yet.")
            } else {
                friends = friends.copy(message = null)
                open(Destination.OnlineGame(gameId))
            }
        }
    }

    /** The friends list as the server has it now. */
    private suspend fun fetchFriends() {
        friends = friends.copy(loading = true)

        friends =
            try {
                friends.copy(friends = dependencies.chessApi.friends(), loading = false, loaded = true)
            } catch (refused: ChessApiException) {
                friends.copy(loading = false, message = Friends.messageFor(refused))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (unreachable: Exception) {
                friends.copy(loading = false, message = Friends.unreachableMessage())
            }
    }

    /**
     * Runs one thing the friends screen asked for, with nothing else running at the time.
     *
     * Every one of them is a single request whose refusal is the server's to explain, so
     * they share the same guard, the same busy flag, and the same two failure messages.
     */
    private fun runOnFriends(action: suspend (ChessApiClient) -> Unit) {
        if (friendsJob?.isActive == true) return

        friendsJob =
            viewModelScope.launch {
                friends = friends.copy(busy = true)

                try {
                    action(dependencies.chessApi)
                } catch (refused: ChessApiException) {
                    friends = friends.copy(message = Friends.messageFor(refused))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (unreachable: Exception) {
                    friends = friends.copy(message = Friends.unreachableMessage())
                } finally {
                    friends = friends.copy(busy = false)
                }
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
