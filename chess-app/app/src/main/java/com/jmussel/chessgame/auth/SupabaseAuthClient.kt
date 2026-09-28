package com.jmussel.chessgame.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Where the Supabase auth API is and which publishable key opens it. */
data class SupabaseConfig(
    val url: String,
    val anonKey: String,
) {
    init {
        require(url.isNotBlank()) { "SUPABASE_URL is not set" }
    }

    val isUsable: Boolean
        get() = anonKey.isNotBlank()

    /** Never prints the key. */
    override fun toString(): String = "SupabaseConfig(url=$url)"
}

/** Raised when Supabase refuses a sign-in or a refresh. */
class SupabaseAuthException(
    val status: Int,
    override val message: String,
) : RuntimeException(message)

/**
 * The two Supabase auth calls this app makes: sign in anonymously, and refresh the token.
 *
 * Nothing else in Supabase is used from Android — canonical game data goes through the
 * Chess server (`D004`), so the full Supabase SDK would be almost entirely unused surface.
 */
class SupabaseAuthClient(
    private val config: SupabaseConfig,
    private val httpClient: HttpClient,
) {
    /** Creates a brand-new anonymous user and returns its session. */
    suspend fun signInAnonymously(): AnonymousSession = request("${config.url}/auth/v1/signup", EmptyBody())

    /** Exchanges [refreshToken] for a fresh session. */
    suspend fun refresh(refreshToken: String): AnonymousSession =
        request("${config.url}/auth/v1/token?grant_type=refresh_token", RefreshRequest(refreshToken))

    private suspend inline fun <reified T : Any> request(
        url: String,
        body: T,
    ): AnonymousSession {
        val response: HttpResponse =
            httpClient.post(url) {
                header("apikey", config.anonKey)
                contentType(ContentType.Application.Json)
                setBody(body)
            }

        if (!response.status.isSuccess()) {
            throw SupabaseAuthException(response.status.value, "Supabase auth failed: ${response.status}")
        }

        return response.body<SessionResponse>().toSession()
    }

    @Serializable
    private class EmptyBody

    @Serializable
    private data class RefreshRequest(
        @SerialName("refresh_token") val refreshToken: String,
    )

    @Serializable
    private data class SessionResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String,
        @SerialName("expires_at") val expiresAt: Long,
        val user: UserResponse,
    ) {
        fun toSession(): AnonymousSession =
            AnonymousSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                userId = user.id,
                expiresAtEpochSeconds = expiresAt,
            )
    }

    @Serializable
    private data class UserResponse(
        val id: String,
    )

    companion object {
        /** Lenient because Supabase returns far more fields than this app reads. */
        val Json: Json =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }

        /** An [HttpClient] configured for the Supabase auth API. */
        fun defaultHttpClient(): HttpClient =
            HttpClient {
                install(ContentNegotiation) { json(Json) }
            }
    }
}
