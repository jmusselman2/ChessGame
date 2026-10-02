package com.jmussel.chessgame.local

import com.jmussel.chessgame.core.chess.ChessGame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The view model's way to the local-game [store] (`D084`): the game on screen, kept in
 * step with its record (`M21.2`, and `M21.6` against the computer), and the finished
 * games (`M21.3`).
 *
 * The view model shows the game; this keeps the store in step with it. Every store call
 * runs on [io], one at a time and in the order it was asked for, so a takeback is never
 * written before the move it takes back, and loading a game waits for the writes before it.
 *
 * Saves run in a scope of their own rather than the view model's. A move saved as the app
 * closes still reaches the store: the view model's scope is cancelled with it, and a
 * cancelled save would lose the move.
 */
class LocalGameSession(
    private val store: LocalGameStore,
    private val io: CoroutineDispatcher,
) {
    private val saves = CoroutineScope(SupervisorJob() + io)
    private val lock = Mutex()

    /**
     * The unfinished pass-and-play game, or a new one when there is none: what opening the
     * local game shows, whatever the game against the computer is doing (`D090`).
     */
    suspend fun resume(): StoredLocalGame = serial { store.activeGame(LocalGameKind.PASS_AND_PLAY) ?: store.startGame() }

    /** The unfinished game against the computer, or `null` when there is none (`M21.6`). */
    suspend fun resumeComputer(): StoredLocalGame? = serial { store.activeGame(LocalGameKind.COMPUTER) }

    /**
     * A new local game: against [computer], or pass-and-play when it is `null`. The
     * unfinished game of the same kind, if there is one, is deleted: the screen has already
     * asked the player (`D084`). The other kind's game is left alone (`D090`).
     */
    suspend fun startNew(computer: ComputerOpponent? = null): StoredLocalGame = serial { store.startGame(computer) }

    /** Every finished local game, newest first, once the saves asked for before are done. */
    suspend fun completedGames(): List<LocalGameSummary> = serial { store.completedGames() }

    /** The local game with [id], or `null` when there is none. */
    suspend fun game(id: Long): StoredLocalGame? = serial { store.game(id) }

    /**
     * Saves the change from [before] to [after] in the stored game [id], if the game changed.
     *
     * Returns at once; the write follows in order. If it fails, the store still holds the
     * game as it was, and [onFailure] is called, off the main thread, so the caller can show
     * that instead.
     */
    fun save(
        id: Long,
        before: ChessGame,
        after: ChessGame,
        onFailure: () -> Unit,
    ) {
        val change = LocalGameChange.between(before, after) ?: return
        // Undispatched, so the save takes its place in the lock's queue before this returns.
        saves.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                serial {
                    when (change) {
                        LocalGameChange.Move -> store.recordMove(id, after)
                        is LocalGameChange.TakeBack -> store.takeBack(id, change.plies)
                        LocalGameChange.Result -> store.recordResult(id, after)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                onFailure()
            }
        }
    }

    private suspend fun <T> serial(block: () -> T): T = lock.withLock { withContext(io) { block() } }
}
