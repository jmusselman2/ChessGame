package com.jmussel.chessgame.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/**
 * Where the anonymous session is kept between app launches.
 *
 * The session *is* the account for an anonymous user (`D006`, `D008`), so losing it loses
 * the username with it — it has to outlive the process.
 */
interface SessionStore {
    suspend fun read(): AnonymousSession?

    suspend fun write(session: AnonymousSession)

    suspend fun clear()
}

/** A store that forgets everything when the process does. Used by tests. */
class InMemorySessionStore(
    private var session: AnonymousSession? = null,
) : SessionStore {
    override suspend fun read(): AnonymousSession? = session

    override suspend fun write(session: AnonymousSession) {
        this.session = session
    }

    override suspend fun clear() {
        session = null
    }
}

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "anonymous_session")

/**
 * The real store: app-private DataStore.
 *
 * App-private storage is the platform's protection here. Nothing more elaborate is used
 * because the token is only as valuable as the anonymous account it belongs to, and the
 * server treats every client as untrusted anyway (`D004`).
 */
class DataStoreSessionStore(
    private val context: Context,
) : SessionStore {
    override suspend fun read(): AnonymousSession? {
        val preferences = context.sessionDataStore.data.first()
        val accessToken = preferences[ACCESS_TOKEN] ?: return null
        val refreshToken = preferences[REFRESH_TOKEN] ?: return null
        val userId = preferences[USER_ID] ?: return null
        val expiresAt = preferences[EXPIRES_AT] ?: return null

        return AnonymousSession(accessToken, refreshToken, userId, expiresAt)
    }

    override suspend fun write(session: AnonymousSession) {
        context.sessionDataStore.edit { preferences ->
            preferences[ACCESS_TOKEN] = session.accessToken
            preferences[REFRESH_TOKEN] = session.refreshToken
            preferences[USER_ID] = session.userId
            preferences[EXPIRES_AT] = session.expiresAtEpochSeconds
        }
    }

    override suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }

    private companion object {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val EXPIRES_AT = longPreferencesKey("expires_at")
    }
}
