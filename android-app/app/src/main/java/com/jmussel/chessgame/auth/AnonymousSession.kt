package com.jmussel.chessgame.auth

/**
 * A signed-in anonymous Supabase session.
 *
 * The access token is what the app sends to the Chess server; the refresh token buys a new
 * one when it expires. [userId] is the Supabase auth subject, which the server maps to the
 * internal user id — it is never used as a credential or shown to anyone.
 */
data class AnonymousSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    /** When [accessToken] stops being accepted, as epoch seconds. */
    val expiresAtEpochSeconds: Long,
) {
    /**
     * Whether the token should be refreshed by [nowEpochSeconds].
     *
     * Refreshing starts [REFRESH_MARGIN_SECONDS] early so a request is not sent with a
     * token that expires while it is in flight.
     */
    fun needsRefresh(nowEpochSeconds: Long): Boolean = nowEpochSeconds >= expiresAtEpochSeconds - REFRESH_MARGIN_SECONDS

    companion object {
        /** How long before expiry a token is considered due for refresh. */
        const val REFRESH_MARGIN_SECONDS: Long = 60
    }
}
