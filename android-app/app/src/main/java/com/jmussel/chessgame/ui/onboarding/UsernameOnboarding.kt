package com.jmussel.chessgame.ui.onboarding

import com.jmussel.chessgame.api.ChessApiException

/** How far claiming a username has got. */
sealed interface UsernameClaim {
    /** Waiting for the player to type a name, or for them to try again after [Rejected]. */
    data object Idle : UsernameClaim

    /** The name is with the server. */
    data object Claiming : UsernameClaim

    /** The name was not claimed, and [message] says why in words worth showing. */
    data class Rejected(
        val message: String,
    ) : UsernameClaim
}

/**
 * What onboarding says about a name that was not accepted.
 *
 * The rules themselves — length, characters, and who already has the name — belong to the
 * server and the database (`D007`), so the app repeats the explanation it was given rather
 * than keeping a second copy of the rules that could disagree. The only thing decided here
 * is that an empty box is not worth a request.
 *
 * Pure, so the wording is tested without a screen.
 */
object UsernameOnboarding {
    /** [requested] with the spaces around it removed, which is what is sent. */
    fun cleaned(requested: String): String = requested.trim()

    /** Whether there is anything to send at all. */
    fun isSendable(requested: String): Boolean = cleaned(requested).isNotEmpty()

    /** What to show when the server refused the name. */
    fun messageFor(refusal: ChessApiException): String = refusal.explanation.ifBlank { REFUSED }

    /** What to show when the request never reached the server. */
    fun unreachableMessage(): String = UNREACHABLE

    private const val REFUSED = "That name was not accepted. Try another."
    private const val UNREACHABLE = "Could not reach the server. Check your connection and try again."
}
