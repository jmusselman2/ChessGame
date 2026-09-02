package com.jmussel.chessgame.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jmussel.chessgame.api.ChessApiClient
import com.jmussel.chessgame.api.ChessApiException
import com.jmussel.chessgame.api.ChessCommandRefusedException
import com.jmussel.chessgame.api.CurrentUserDto
import com.jmussel.chessgame.api.GameViewDto
import com.jmussel.chessgame.api.RealtimeMessageDto
import com.jmussel.chessgame.api.ServerWakePolicy
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.api.withServerWake
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.navigation.AppNavigation
import com.jmussel.chessgame.navigation.Destination
import com.jmussel.chessgame.ui.dashboard.DashboardMessages
import com.jmussel.chessgame.ui.dashboard.DashboardRow
import com.jmussel.chessgame.ui.dashboard.DashboardUiState
import com.jmussel.chessgame.ui.dashboard.FriendRow
import com.jmussel.chessgame.ui.friends.Friends
import com.jmussel.chessgame.ui.friends.FriendsUiState
import com.jmussel.chessgame.ui.game.AfterGame
import com.jmussel.chessgame.ui.game.BoardTap
import com.jmussel.chessgame.ui.game.OnlineGame
import com.jmussel.chessgame.ui.game.OnlineGameState
import com.jmussel.chessgame.ui.history.HistoryMessages
import com.jmussel.chessgame.ui.history.HistoryUiState
import com.jmussel.chessgame.ui.onboarding.UsernameClaim
import com.jmussel.chessgame.ui.onboarding.UsernameOnboarding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    private val wakePolicy: ServerWakePolicy = ServerWakePolicy(),
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

    /** The home screen: the active series, and what is happening to them. */
    var dashboard: DashboardUiState by mutableStateOf(DashboardUiState())
        private set

    /** The friends screen: who they are, and what is happening on it. */
    var friends: FriendsUiState by mutableStateOf(FriendsUiState())
        private set

    /** The history screen: what has been played, and what is happening to the list of it. */
    var history: HistoryUiState by mutableStateOf(HistoryUiState())
        private set

    /** The game screen: the canonical state as far as it has been read, or `null` before any game has been opened. */
    var game: OnlineGameState? by mutableStateOf(null)
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

    /** Whatever the dashboard has asked for, if anything. Internal for the same reason. */
    internal var dashboardJob: Job? = null
        private set

    /** The game load in flight, if any. Internal for the same reason. */
    internal var gameJob: Job? = null
        private set

    /** The history load in flight, if any. Internal for the same reason. */
    internal var historyJob: Job? = null
        private set

    /** The move in flight, if any. Internal for the same reason. */
    internal var moveJob: Job? = null
        private set

    /** The realtime connection loop, once it has been started. Internal for the same reason. */
    internal var updatesJob: Job? = null
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
        if (startup is StartupState.Ready) return

        // A run that is only waiting out a sleeping server may be restarted by the player,
        // because the screen offers them that button and a dead button is worse than none.
        // A run genuinely in flight is left alone, so a second tap still cannot end up
        // creating two anonymous accounts (`D006`).
        if (startupJob?.isActive == true) {
            if (startup !is StartupState.Waking) return
            startupJob?.cancel()
        }

        startupJob =
            viewModelScope.launch {
                startup = StartupState.Loading

                // Reported as it happens rather than at the end: a free instance's cold
                // start takes about a minute (`M15.2`), and a screen that says nothing for
                // that long reads as broken.
                val result = dependencies.startup.run(onWaking = { waking -> startup = waking })
                startup = result

                if (result is StartupState.Ready) {
                    arriveAs(result.user)
                    watchUpdates()
                }
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

        if (user.username == null) {
            restartAt(Destination.UsernameOnboarding)
            return
        }

        restartAt(Destination.Dashboard)
        loadDashboard()
    }

    /**
     * Opens a server-owned game and loads it.
     *
     * Only the id travels: everything the screen draws — the opponent, the side, the
     * position, the move just played — comes back from the server, so a game opened from a
     * dashboard line and one reached after the process was recreated show the same thing.
     */
    fun openOnlineGame(gameId: String) {
        open(Destination.OnlineGame(gameId))
        loadGame(gameId)
    }

    /** Fetches [gameId] as the server has it now. */
    fun loadGame(gameId: String) {
        if (gameJob?.isActive == true) return

        // A different game blanks the screen, so it is never showing the one before it; the
        // game already on screen stays put while it reloads, which is both less flicker and
        // what lets a reload tell that this game has just ended.
        if ((game as? OnlineGameState.Ready)?.game?.gameId != gameId) game = OnlineGameState.Loading(gameId)

        gameJob =
            viewModelScope.launch {
                try {
                    // A reload is a read, so it is safe to repeat while the service wakes.
                    // This is the reconnect path too (`M12.3`): the socket drops when a free
                    // instance is replaced or spins down, and canonical state is reloaded
                    // over HTTPS immediately afterwards — against a server that is, by
                    // definition, only just coming back.
                    show(waitingForServer { dependencies.chessApi.game(gameId) })
                } catch (refused: ChessApiException) {
                    game =
                        OnlineGameState.Failed(
                            gameId = gameId,
                            message = OnlineGame.messageFor(refused),
                            canRetry = OnlineGame.canRetry(refused),
                        )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (unreachable: Exception) {
                    game =
                        OnlineGameState.Failed(
                            gameId = gameId,
                            message = OnlineGame.unreachableMessage(),
                            canRetry = true,
                        )
                }
            }
    }

    /** Loads the game showing now again, which is what "try again" does. */
    fun reloadGame() {
        val gameId =
            when (val showing = game) {
                is OnlineGameState.Loading -> showing.gameId
                is OnlineGameState.Ready -> showing.game.gameId
                is OnlineGameState.Failed -> showing.gameId
                null -> return
            }

        loadGame(gameId)
    }

    /**
     * Keeps a realtime connection open for as long as the app's state lives.
     *
     * Nothing on the socket is treated as state: `connected` means "you are live, so
     * whatever you have may be out of date", and a `game-updated` names a game to reload
     * over HTTPS (`D022`). A dropped connection is reconnected after a pause; the loop ends
     * only when the model is cleared.
     */
    fun watchUpdates() {
        if (updatesJob?.isActive == true) return

        updatesJob =
            viewModelScope.launch {
                while (isActive) {
                    // A dropped socket ends the flow rather than throwing; either way the
                    // answer is the same: wait a moment and connect again.
                    runCatching { dependencies.realtime.messages().collect(::onRealtimeMessage) }

                    delay(RECONNECT_PAUSE_MILLIS)
                }
            }
    }

    /**
     * What one realtime message means.
     *
     * `connected` says only that the connection is live, so everything on screen is
     * refreshed from the server; a `game-updated` for the game being looked at reloads it,
     * and one for any other game refreshes the dashboard and leaves the open game alone.
     * The version it carries is never written anywhere — it is a fact about the game, not
     * the game (`D022`), so a duplicate, a late, and an out-of-order message all come to
     * the same harmless reload.
     */
    internal fun onRealtimeMessage(message: RealtimeMessageDto) {
        when (message.type) {
            RealtimeMessageDto.CONNECTED -> {
                refreshWhatIsOnScreen()
            }

            RealtimeMessageDto.GAME_UPDATED -> {
                val showing = (game as? OnlineGameState.Ready)?.game?.gameId

                if (message.gameId != null && message.gameId == showing) {
                    loadGame(message.gameId)
                } else {
                    loadDashboard()
                }
            }

            else -> Unit
        }
    }

    /** Reloads whatever the player can see, which is what a fresh connection calls for. */
    private fun refreshWhatIsOnScreen() {
        if (currentUser?.username != null) loadDashboard()

        (game as? OnlineGameState.Ready)?.let { ready -> loadGame(ready.game.gameId) }
    }

    /**
     * Handles a tap on the online board.
     *
     * Selecting a piece and raising the promotion prompt happen on screen; a move goes to
     * the server, and the board does not change until the server answers (`D004`).
     */
    fun tapSquare(square: Square) {
        val ready = game as? OnlineGameState.Ready ?: return

        act(OnlineGame.onSquareTapped(ready, square))
    }

    /** Sends the pending promotion as the chosen piece. */
    fun choosePromotion(choice: PieceType) {
        val ready = game as? OnlineGameState.Ready ?: return

        act(OnlineGame.choosePromotion(ready, choice))
    }

    /** Backs out of the promotion prompt without playing anything. */
    fun cancelPromotion() {
        val ready = game as? OnlineGameState.Ready ?: return

        game = OnlineGame.cancelPromotion(ready)
    }

    /**
     * Shows what the tap came to, and sends the move if it was one.
     *
     * The command carries the version the move was decided against, which is what makes it
     * unique: a retry of a move already applied arrives stale and is refused with the
     * canonical state attached, so the same move cannot be played twice (`D021`). Whatever
     * the server answers — the new state, or a refusal carrying one — replaces what is on
     * screen.
     */
    private fun act(tap: BoardTap) {
        game = tap.state

        if (tap !is BoardTap.Submit) return

        val move = tap.move

        sendCommand(tap.state.game) { api, decidedAt ->
            api.makeMove(
                gameId = decidedAt.gameId,
                expectedVersion = decidedAt.version,
                from = move.from.toString(),
                to = move.to.toString(),
                promotion = move.promotion?.name,
            )
        }
    }

    /**
     * Takes back the latest move, if the server says this player may.
     *
     * Whether there is anything to take back is the canonical state's answer (`D016`) — the
     * app offers the action only when the game it was sent says so, and the server decides
     * again when it arrives. Nothing is rewritten locally either way: the board becomes
     * whatever came back.
     */
    fun undoMove() {
        val ready = game as? OnlineGameState.Ready ?: return
        if (!ready.game.canUndo || ready.submitting) return

        game = ready.copy(selected = null, pendingPromotion = null, submitting = true, message = null)

        sendCommand(ready.game) { api, decidedAt ->
            api.undoMove(gameId = decidedAt.gameId, expectedVersion = decidedAt.version)
        }
    }

    /**
     * Claims a draw the canonical state says is available (`D019`).
     *
     * The app offers only the claims the server listed and never decides that a game is
     * drawn: the game becomes whatever the server answers, which is where a claimed draw
     * gets its result and its termination reason.
     */
    fun claimDraw(claim: String) {
        val ready = game as? OnlineGameState.Ready ?: return
        if (claim !in ready.game.availableDrawClaims || ready.submitting) return

        game = ready.copy(selected = null, pendingPromotion = null, submitting = true, message = null)

        sendCommand(ready.game) { api, decidedAt ->
            api.claimDraw(gameId = decidedAt.gameId, expectedVersion = decidedAt.version, claim = claim)
        }
    }

    /** Asks whether the player really means to give the game up (`D018`). */
    fun askToResign() {
        val ready = game as? OnlineGameState.Ready ?: return
        if (ready.game.isOver || ready.submitting) return

        game = ready.copy(confirmingResignation = true, message = null)
    }

    /** Leaves the game as it was. */
    fun cancelResignation() {
        val ready = game as? OnlineGameState.Ready ?: return

        game = ready.copy(confirmingResignation = false)
    }

    /**
     * Gives the game up, once the question has been answered (`D018`).
     *
     * Available whether or not it is this player's move: a player may give up at any point
     * in a game they are losing. The result is the server's, like every other ending.
     */
    fun resign() {
        val ready = game as? OnlineGameState.Ready ?: return
        if (ready.game.isOver || ready.submitting) return

        game =
            ready.copy(
                selected = null,
                pendingPromotion = null,
                confirmingResignation = false,
                submitting = true,
                message = null,
            )

        sendCommand(ready.game) { api, decidedAt ->
            api.resign(gameId = decidedAt.gameId, expectedVersion = decidedAt.version)
        }
    }

    /**
     * Runs a **safe, repeatable** read, waiting through a service that is merely asleep.
     *
     * Only reads come through here. A mutating command must not: it carries the version it
     * was decided against, and re-sending it is the server's question to settle through the
     * version guard, not a thing a client loop should do on its own (`D021`). [sendCommand]
     * deliberately does not call this, and `commandsAreNotRetriedBlindly` locks that.
     */
    private suspend fun <T> waitingForServer(read: suspend () -> T): T = withServerWake(policy = wakePolicy, attempt = read)

    /**
     * Sends one command about the game on screen and shows whatever came back.
     *
     * Every command works the same way: it carries the version it was decided against,
     * which is what makes it unique (`D021`), and the screen is replaced by the canonical
     * state — the accepted one, or the one attached to the refusal. A retry whose first
     * reply was lost therefore sees its own effect rather than doing it twice.
     *
     * Exactly one attempt is made. A command is not safe to repeat on the client's own
     * initiative, so a transport failure is reported rather than waited through — unlike a
     * read, which goes through [waitingForServer].
     */
    private fun sendCommand(
        decidedAt: GameViewDto,
        command: suspend (ChessApiClient, GameViewDto) -> GameViewDto,
    ) {
        if (moveJob?.isActive == true) return

        moveJob =
            viewModelScope.launch {
                try {
                    show(command(dependencies.chessApi, decidedAt))
                } catch (refused: ChessCommandRefusedException) {
                    show(refused.game ?: decidedAt, OnlineGame.messageFor(refused))
                } catch (refused: ChessApiException) {
                    show(decidedAt, OnlineGame.messageFor(refused))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (unreachable: Exception) {
                    show(decidedAt, OnlineGame.unreachableMessage())
                }
            }
    }

    /**
     * Puts the canonical state on screen, and asks what follows when it has just ended.
     *
     * A game that finishes under the player — their own last move, or the opponent's
     * arriving over the socket — is the moment to find out what the series did next
     * (`D014`). A game that was already over when it was opened is not: it is being looked
     * at, not played to its end.
     */
    private fun show(
        view: GameViewDto,
        message: String? = null,
    ) {
        val showing = (game as? OnlineGameState.Ready)?.takeIf { it.game.gameId == view.gameId }
        val justEnded = view.isOver && showing != null && !showing.game.isOver

        game = OnlineGameState.Ready(game = view, message = message, after = if (justEnded) AfterGame.Looking else null)

        if (justEnded) followSeries(view)
    }

    /**
     * Asks the dashboard what the series is at now.
     *
     * The client never creates or confirms a rematch: the server made the next game when it
     * finalized this one (`D014`), so this only reads the answer — a different current game
     * to offer, or a series that has gone, which means there will not be another (`D013`).
     */
    private fun followSeries(finished: GameViewDto) {
        dashboardJob =
            viewModelScope.launch {
                fetchDashboard()

                val ready = game as? OnlineGameState.Ready ?: return@launch
                if (ready.game.gameId != finished.gameId) return@launch

                val nextGameId =
                    dashboard.entries
                        .firstOrNull { it.seriesId == finished.seriesId }
                        ?.gameId
                        ?.takeIf { it != finished.gameId }

                game = ready.copy(after = nextGameId?.let(AfterGame::NextGame) ?: AfterGame.SeriesOver)
            }
    }

    /** Opens the game the series moved on to, which the server chose (`D015`). */
    fun openNextGame() {
        val next = (game as? OnlineGameState.Ready)?.after as? AfterGame.NextGame ?: return

        openOnlineGame(next.gameId)
    }

    /** Goes back to the dashboard, which is where a player goes when a series is over. */
    fun returnToDashboard() {
        restartAt(Destination.Dashboard)
        loadDashboard()
    }

    /**
     * Loads the dashboard and the friends list together.
     *
     * They are one screen — the games waiting on the player, the games waiting on the
     * opponent, and everyone else worth playing — so they arrive together or not at all,
     * and a half-drawn dashboard is never shown.
     */
    fun loadDashboard() {
        if (dashboardJob?.isActive == true) return

        dashboardJob = viewModelScope.launch { fetchDashboard() }
    }

    /**
     * Opens the game on a dashboard line, by the id the server gave it.
     *
     * The app never works out which game that is; it opens the one named in the line it was
     * sent (`D004`).
     */
    fun openGame(row: DashboardRow) {
        openOnlineGame(row.gameId)
    }

    /**
     * "Play with this friend", from the dashboard.
     *
     * Whether that opens the series already running or starts one is the server's business
     * (`D011`), so both the friend with a game under way and the friend without go through
     * the same request. The dashboard is reloaded afterwards, because starting a series
     * changes it.
     */
    fun playFriend(row: FriendRow) {
        if (dashboardJob?.isActive == true) return

        dashboardJob =
            viewModelScope.launch {
                dashboard = dashboard.copy(busy = true)

                try {
                    val gameId = dependencies.chessApi.openSeries(row.username).currentGameId
                    fetchDashboard()

                    if (gameId == null) {
                        dashboard = dashboard.copy(message = "No game with ${row.username} to open yet.")
                    } else {
                        dashboard = dashboard.copy(message = null)
                        openOnlineGame(gameId)
                    }
                } catch (refused: ChessApiException) {
                    dashboard = dashboard.copy(message = DashboardMessages.messageFor(refused))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (unreachable: Exception) {
                    dashboard = dashboard.copy(message = DashboardMessages.unreachableMessage())
                } finally {
                    dashboard = dashboard.copy(busy = false)
                }
            }
    }

    /** The dashboard and the friends list as the server has them now. */
    private suspend fun fetchDashboard() {
        dashboard = dashboard.copy(loading = true)

        try {
            waitingForServer {
                coroutineScope {
                    val entries = async { dependencies.chessApi.dashboard() }
                    val list = async { dependencies.chessApi.friends() }
                    entries.await() to list.await()
                }
            }.let { (loadedEntries, loadedFriends) ->
                dashboard = dashboard.copy(entries = loadedEntries, loading = false, loaded = true, message = null)
                // The same list the friends screen shows; there is only one of it.
                friends = friends.copy(friends = loadedFriends, loaded = true)
            }
        } catch (refused: ChessApiException) {
            dashboard = dashboard.copy(loading = false, message = DashboardMessages.messageFor(refused))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (unreachable: Exception) {
            dashboard = dashboard.copy(loading = false, message = DashboardMessages.unreachableMessage())
        }
    }

    /**
     * Opens the history screen and loads it.
     *
     * Fetched each time it is opened: a game finished on another device belongs here as
     * soon as it is over.
     */
    fun openHistory() {
        open(Destination.History)
        loadHistory()
    }

    /** Fetches the finished games, keeping whatever is on screen until the new list arrives. */
    fun loadHistory() {
        if (historyJob?.isActive == true) return

        historyJob =
            viewModelScope.launch {
                history = history.copy(loading = true)

                history =
                    try {
                        history.copy(series = dependencies.chessApi.history(), loading = false, loaded = true, message = null)
                    } catch (refused: ChessApiException) {
                        history.copy(loading = false, message = HistoryMessages.messageFor(refused))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (unreachable: Exception) {
                        history.copy(loading = false, message = HistoryMessages.unreachableMessage())
                    }
            }
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
                openOnlineGame(gameId)
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
        /** How long to wait before opening the socket again after it has dropped. */
        private const val RECONNECT_PAUSE_MILLIS = 3_000L

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
