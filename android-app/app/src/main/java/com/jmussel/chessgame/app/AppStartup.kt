package com.jmussel.chessgame.app

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
    /** Restoring or creating the session. */
    data object Loading : StartupState

    /**
     * There is a usable session.
     *
     * [userId] is the Supabase subject, which the server maps to its own user id. It is
     * not a credential and no token is kept here.
     */
    data class Ready(
        val userId: String,
    ) : StartupState

    /**
     * Startup did not get a session.
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
 * The first thing the app does: get a session, or explain why it could not.
 *
 * Restoring, refreshing, and creating are [AnonymousAuthenticator]'s to decide (`D031`);
 * this turns the outcome into something the shell can show. It holds no Android or Compose
 * types, so every path is tested on the JVM.
 */
class AppStartup(
    private val supabaseConfig: SupabaseConfig,
    private val authenticator: AnonymousAuthenticator,
) {
    /**
     * A session, restored, refreshed, or newly created.
     *
     * Failures are described in a way that says what to do next and never quotes a token,
     * a refresh token, or the publishable key.
     */
    suspend fun run(): StartupState {
        if (!supabaseConfig.isUsable) return StartupState.Failed(NO_KEY, canRetry = false)

        return try {
            StartupState.Ready(authenticator.currentSession().userId)
        } catch (refused: SupabaseAuthException) {
            StartupState.Failed(refusedMessage(refused.status), canRetry = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (unreachable: Exception) {
            // A dead network, a proxy answering with something else, a project that has
            // moved: none of it is worth telling apart, and all of it is worth retrying.
            StartupState.Failed(UNREACHABLE, canRetry = true)
        }
    }

    private companion object {
        const val NO_KEY =
            "This build has no Supabase key, so it cannot sign in. Rebuild with SUPABASE_ANON_KEY set " +
                "(see docs/DEVELOPMENT.md)."
        const val UNREACHABLE = "Could not reach the sign-in service. Check your connection and try again."

        /** The status is the whole of what is said about a refusal; the reply itself is not quoted. */
        fun refusedMessage(status: Int): String = "Signing in was refused ($status). Try again in a moment."
    }
}
