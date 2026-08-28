package com.jmussel.chessgame.app

import com.jmussel.chessgame.api.ChessApiClient
import com.jmussel.chessgame.api.ChessApiException
import com.jmussel.chessgame.api.CurrentUserDto
import com.jmussel.chessgame.auth.AnonymousAuthenticator
import com.jmussel.chessgame.auth.SupabaseAuthException
import com.jmussel.chessgame.auth.SupabaseConfig
import kotlinx.coroutines.CancellationException

/**
 * How far startup has got.
 *
 * The account is invisible (`D006`), so there is nothing for the player to do here except
 * wait — or, when something is wrong, read what it was and try again.
 */
sealed interface StartupState {
    /** Restoring or creating the session, and asking the server who it belongs to. */
    data object Loading : StartupState

    /**
     * There is a usable session, and the server has said who the caller is.
     *
     * [user] carries the server's own user id and the username, which is `null` until it is
     * claimed — the difference between a returning player and a new one. No token is kept
     * here.
     */
    data class Ready(
        val user: CurrentUserDto,
    ) : StartupState

    /**
     * Startup did not finish.
     *
     * [canRetry] is false when trying again cannot help — a build with no Supabase key
     * will fail the same way every time, and the fix is a rebuild, not another tap.
     */
    data class Failed(
        val message: String,
        val canRetry: Boolean,
    ) : StartupState
}

/**
 * The first thing the app does: find out who is playing, or explain why it could not.
 *
 * That is two questions in order — a Supabase session, then the identity the Chess server
 * keeps for it — because the server is the authority on who a session belongs to and
 * whether they have a username (`D004`). Restoring, refreshing, and creating the session
 * are [AnonymousAuthenticator]'s to decide (`D031`); this turns the outcome into something
 * the shell can show. It holds no Android or Compose types, so every path is tested on the
 * JVM.
 */
class AppStartup(
    private val supabaseConfig: SupabaseConfig,
    private val authenticator: AnonymousAuthenticator,
    private val chessApi: ChessApiClient,
) {
    /**
     * A session — restored, refreshed, or newly created — and the identity that goes with it.
     *
     * Failures are described in a way that says what to do next and never quotes a token,
     * a refresh token, or the publishable key.
     */
    suspend fun run(): StartupState {
        if (!supabaseConfig.isUsable) return StartupState.Failed(NO_KEY, canRetry = false)

        return try {
            authenticator.currentSession()
            StartupState.Ready(chessApi.me())
        } catch (refused: SupabaseAuthException) {
            StartupState.Failed(signInRefusedMessage(refused.status), canRetry = true)
        } catch (refused: ChessApiException) {
            StartupState.Failed(serverRefusedMessage(refused.status), canRetry = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (unreachable: Exception) {
            // A dead network, a proxy answering with something else, a project or server
            // that has moved: none of it is worth telling apart, and all of it is worth
            // retrying.
            StartupState.Failed(UNREACHABLE, canRetry = true)
        }
    }

    private companion object {
        const val NO_KEY =
            "This build has no Supabase key, so it cannot sign in. Rebuild with SUPABASE_ANON_KEY set " +
                "(see docs/DEVELOPMENT.md)."
        const val UNREACHABLE = "Could not reach the server. Check your connection and try again."

        /** The status is the whole of what is said about a refusal; the reply itself is not quoted. */
        fun signInRefusedMessage(status: Int): String = "Signing in was refused ($status). Try again in a moment."

        fun serverRefusedMessage(status: Int): String = "The server would not say who you are ($status). Try again."
    }
}
