@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.auth

import com.jmussel.chessgame.server.db.UserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.auth.principal
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.response.respondText
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** The caller of an authenticated request. */
data class AuthenticatedUser(
    /** The internal user id everything in the database references. */
    val userId: Uuid,
    /** The Supabase auth subject the token carried. */
    val subject: String,
    val isAnonymous: Boolean,
)

/** The name of the authentication provider protecting Chess API routes. */
const val SUPABASE_AUTH: String = "supabase"

/**
 * Bearer-token authentication over Supabase-issued JWTs.
 *
 * A request is authenticated when its token verifies against the project's JWKS
 * ([SupabaseTokenVerifier]) and its subject resolves to an internal user — created on the
 * first request from a new anonymous account (`D006`). Anything else is a 401: the client
 * is untrusted, so an unverifiable token is simply not a caller (`D004`).
 */
class SupabaseAuthenticationProvider(
    config: Config,
) : AuthenticationProvider(config) {
    private val verifier = config.verifier
    private val users = config.users

    class Config internal constructor(
        name: String,
        internal val verifier: SupabaseTokenVerifier,
        internal val users: UserRepository,
    ) : AuthenticationProvider.Config(name)

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val token = context.call.request.bearerToken()

        if (token == null) {
            context.challenge(SUPABASE_AUTH, AuthenticationFailedCause.NoCredentials) { challenge, call ->
                call.respondText("Missing bearer token", status = HttpStatusCode.Unauthorized)
                challenge.complete()
            }
            return
        }

        val identity =
            try {
                verifier.verify(token)
            } catch (_: InvalidTokenException) {
                context.challenge(SUPABASE_AUTH, AuthenticationFailedCause.InvalidCredentials) { challenge, call ->
                    call.respondText("Invalid bearer token", status = HttpStatusCode.Unauthorized)
                    challenge.complete()
                }
                return
            }

        val user = users.resolveBySubject(identity.subject)

        context.principal(
            AuthenticatedUser(
                userId = user.id,
                subject = identity.subject,
                isAnonymous = identity.isAnonymous,
            ),
        )
    }
}

/** Installs [SUPABASE_AUTH] using [verifier] and [users]. */
fun Application.installSupabaseAuthentication(
    verifier: SupabaseTokenVerifier,
    users: UserRepository,
    name: String = SUPABASE_AUTH,
) {
    install(Authentication) {
        register(
            SupabaseAuthenticationProvider(
                SupabaseAuthenticationProvider.Config(name, verifier, users),
            ),
        )
    }
}

/** The authenticated caller, inside an `authenticate(SUPABASE_AUTH)` route. */
fun ApplicationCall.authenticatedUser(): AuthenticatedUser =
    requireNotNull(principal<AuthenticatedUser>()) { "Route is not behind $SUPABASE_AUTH authentication" }

private fun ApplicationRequest.bearerToken(): String? {
    val header = headers["Authorization"] ?: return null
    val prefix = "Bearer "
    if (!header.startsWith(prefix, ignoreCase = true)) return null
    return header.substring(prefix.length).trim().takeIf { it.isNotEmpty() }
}
