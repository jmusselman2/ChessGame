package com.jmussel.chessgame.app

import com.jmussel.chessgame.api.ChessApiClient
import com.jmussel.chessgame.api.ChessApiException
import com.jmussel.chessgame.api.CurrentUserDto
import com.jmussel.chessgame.api.ServerWakePolicy
import com.jmussel.chessgame.api.isProbablyAsleep
import com.jmussel.chessgame.api.withServerWake
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
     * The first attempt did not land, and it is being tried again.
     *
     * This is not a failure and must not be shown as one. A free instance spins down after
     * about fifteen idle minutes and the next request pays a cold start — 59.0 s and 64.5 s
     * when `M15.2` measured it — so the common reason for being here is simply that nobody
     * has played for a while. [failures] and [elapsedMillis] are what a screen needs to say
     * so.
     */
    data class Waking(
        val failures: Int,
        val elapsedMillis: Long,
    ) : StartupState

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
    private val wakePolicy: ServerWakePolicy = ServerWakePolicy(),
) {
    /**
     * A session — restored, refreshed, or newly created — and the identity that goes with it.
     *
     * Both halves are safe to repeat, which is what lets them be retried at all: obtaining
     * the session is [AnonymousAuthenticator]'s business and is guarded by its own mutex so
     * a retry cannot create a second anonymous account (`D031`), and `GET /me` is a read.
     * So a transport failure is waited through rather than reported, and only a refusal the
     * server actually issued — or a deadline reached — becomes a [StartupState.Failed].
     *
     * [onWaking] is called while that is happening so the shell can say the server is being
     * woken instead of showing an error a player would report as a bug.
     *
     * Failures are described in a way that says what to do next and never quotes a token,
     * a refresh token, or the publishable key.
     */
    suspend fun run(onWaking: (StartupState.Waking) -> Unit = {}): StartupState {
        if (!supabaseConfig.isUsable) return StartupState.Failed(NO_KEY, canRetry = false)

        return try {
            withServerWake(
                policy = wakePolicy,
                isWorthRetrying = ::isWorthWaitingThrough,
                onWaking = { waking ->
                    onWaking(StartupState.Waking(failures = waking.failures, elapsedMillis = waking.elapsedMillis))
                },
            ) {
                authenticator.currentSession()
                StartupState.Ready(identify())
            }
        } catch (refused: SupabaseAuthException) {
            StartupState.Failed(signInRefusedMessage(refused.status), canRetry = true)
        } catch (refused: ChessApiException) {
            StartupState.Failed(serverRefusedMessage(refused.status), canRetry = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (unreachable: Exception) {
            // A dead network, a proxy answering with something else, a project or server
            // that has moved, or a wake that ran out of time: none of it is worth telling
            // apart by now, and all of it is worth another tap.
            StartupState.Failed(UNREACHABLE, canRetry = true)
        }
    }

    /**
     * Who the caller is, asking again with a freshly issued token if the server refuses the
     * one it was given.
     *
     * The server creates the internal user on the first request from a token that verifies
     * (`D006`), so a `401` is always about the token and never about the account being
     * missing. Nothing on the device can tell that a stored token has stopped verifying —
     * as far as the app knows it is not expired — so asking for a new one is the only way
     * out. Without this a relaunch after a signing key changed, a project moved (`D035`),
     * or a wrong device clock is stranded on a retry button that can never work.
     *
     * Exactly one further attempt is made: if the freshly issued token is refused too, that
     * is the server's answer rather than something to keep asking.
     */
    private suspend fun identify(): CurrentUserDto =
        try {
            chessApi.me()
        } catch (refused: ChessApiException) {
            if (refused.status != UNAUTHORIZED) throw refused

            authenticator.renewedSession()
            chessApi.me()
        }

    private companion object {
        /** The one status that means "not with this token", and the only one worth renewing for. */
        const val UNAUTHORIZED = 401

        /**
         * Whether [failure] is worth waiting through rather than reporting.
         *
         * Supabase is a separate always-on service, so a [SupabaseAuthException] is an
         * answer it actually gave and repeating the question would only get it again —
         * a dead refresh token needs a new account, not another second. The Chess server's
         * own refusals are already excluded by [isProbablyAsleep]. What is left is
         * transport failure, which is exactly what a sleeping free instance looks like.
         */
        fun isWorthWaitingThrough(failure: Throwable): Boolean =
            when (failure) {
                is SupabaseAuthException -> false
                else -> isProbablyAsleep(failure)
            }

        const val NO_KEY =
            "This build has no Supabase key, so it cannot sign in. Rebuild with SUPABASE_ANON_KEY set " +
                "(see docs/DEVELOPMENT.md)."
        const val UNREACHABLE = "Could not reach the server. Check your connection and try again."

        /** The status is the whole of what is said about a refusal; the reply itself is not quoted. */
        fun signInRefusedMessage(status: Int): String = "Signing in was refused ($status). Try again in a moment."

        fun serverRefusedMessage(status: Int): String = "The server would not say who you are ($status). Try again."
    }
}
