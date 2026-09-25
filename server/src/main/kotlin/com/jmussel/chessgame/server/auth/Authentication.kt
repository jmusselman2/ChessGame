@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.auth

import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.user.LastSeenTracker
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.auth.principal
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import kotlin.coroutines.cancellation.CancellationException
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
    private val lastSeen = config.lastSeen

    class Config internal constructor(
        name: String,
        internal val verifier: SupabaseTokenVerifier,
        internal val users: UserRepository,
        internal val lastSeen: LastSeenTracker?,
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
                // Recorded, because a run of these is the difference between "one phone has
                // a stale session" and something worth looking at, and a silent 401 tells
                // nobody on this side (`M19.12`).
                //
                // **Deliberately without the exception's message.** The JWT library's own
                // messages can quote the malformed token back — a decode failure on
                // "The string '<payload>' doesn't have a valid JSON format" would put
                // credential material in a log file, which is precisely what `M16.5`
                // forbids. The path is enough to find the caller; the reason is not worth a
                // token.
                context.call.application.log.info(
                    "Rejected a bearer token on {} {}",
                    context.call.request.httpMethod.value,
                    context.call.request.path(),
                )

                context.challenge(SUPABASE_AUTH, AuthenticationFailedCause.InvalidCredentials) { challenge, call ->
                    call.respondText("Invalid bearer token", status = HttpStatusCode.Unauthorized)
                    challenge.complete()
                }
                return
            }

        // Outside the best-effort boundary below: a user who cannot be resolved is not a
        // caller, so this failure still fails the request.
        val user = users.resolveBySubject(identity.subject)

        recordActivity(context.call, user.id)

        context.principal(
            AuthenticatedUser(
                userId = user.id,
                subject = identity.subject,
                isAnonymous = identity.isAnonymous,
            ),
        )
    }

    /**
     * Records an authenticated request as activity; the tracker decides whether that is
     * worth a write (`D010`).
     *
     * Best effort, and only this (`D080`). By the time it runs the token is verified and the
     * user resolved, so the request is valid whatever happens here, and a lost `last_seen_at`
     * write must not turn it into a 500 — on `GET /me` that is the app failing to start. The
     * tracker has already handed a failed write's window back (`D043`), so the next request
     * retries. Only exceptions are caught: an `Error` is not a lost write, and cancellation
     * stays cancellation.
     *
     * The line names the user and the request, never a header — the token has no business
     * here and the tracker never sees it (`M16.5`).
     */
    private fun recordActivity(
        call: ApplicationCall,
        userId: Uuid,
    ) {
        try {
            lastSeen?.record(userId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            call.application.log.warn(
                "Could not record activity for user {} on {} {}; continuing, and the next request retries",
                userId,
                call.request.httpMethod.value,
                call.request.path(),
                failure,
            )
        }
    }
}

/** Installs [SUPABASE_AUTH] using [verifier] and [users]. */
fun Application.installSupabaseAuthentication(
    verifier: SupabaseTokenVerifier,
    users: UserRepository,
    lastSeen: LastSeenTracker? = LastSeenTracker(users),
    name: String = SUPABASE_AUTH,
) {
    install(Authentication) {
        register(
            SupabaseAuthenticationProvider(
                SupabaseAuthenticationProvider.Config(name, verifier, users, lastSeen),
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
