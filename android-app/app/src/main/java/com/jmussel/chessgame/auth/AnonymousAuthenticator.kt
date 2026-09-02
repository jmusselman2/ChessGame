package com.jmussel.chessgame.auth

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps one anonymous session alive across launches.
 *
 * The user never sees a sign-in (`D006`): on first run the app creates an anonymous
 * account, and on every later run it restores the stored session, refreshing the token
 * when it is close to expiry. A refresh the server rejects means the account is gone, so a
 * new anonymous account is created rather than leaving the app unusable.
 */
class AnonymousAuthenticator(
    private val client: SupabaseAuthClient,
    private val store: SessionStore,
    private val now: () -> Long = { System.currentTimeMillis() / MILLIS_PER_SECOND },
) {
    private val mutex = Mutex()

    /**
     * A session with a usable access token: restored, refreshed, or newly created.
     *
     * Serialized, so two screens starting at once cannot create two anonymous accounts.
     */
    suspend fun currentSession(): AnonymousSession =
        mutex.withLock {
            val stored = store.read()

            when {
                stored == null -> createSession()
                !stored.needsRefresh(now()) -> stored
                else -> refreshOrCreate(stored)
            }
        }

    /**
     * A session carrying a token the server has just issued, whatever the stored one looked
     * like.
     *
     * [currentSession] trusts the stored expiry, which is all a client can normally do. Once
     * the server has actually refused a token that check cannot help: a rotated signing key,
     * a project the app was re-pointed at (`D035`), or a device clock that is wrong all leave
     * a stored session looking perfectly valid while it is worth nothing. So this asks for a
     * new token regardless of what the expiry says. A refresh the server rejects still means
     * the account is gone, and a new anonymous one is created rather than leaving the app
     * unusable — the same rule [currentSession] already follows.
     */
    suspend fun renewedSession(): AnonymousSession =
        mutex.withLock {
            when (val stored = store.read()) {
                null -> createSession()
                else -> refreshOrCreate(stored)
            }
        }

    /** The stored session as it is, without touching the network. */
    suspend fun storedSession(): AnonymousSession? = store.read()

    /** Forgets the session. The anonymous account itself is not recoverable afterwards. */
    suspend fun signOut() = mutex.withLock { store.clear() }

    private suspend fun refreshOrCreate(stored: AnonymousSession): AnonymousSession =
        try {
            store(client.refresh(stored.refreshToken))
        } catch (_: SupabaseAuthException) {
            // The refresh token is no longer good for anything; start again.
            createSession()
        }

    private suspend fun createSession(): AnonymousSession = store(client.signInAnonymously())

    private suspend fun store(session: AnonymousSession): AnonymousSession {
        store.write(session)
        return session
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
