package com.jmussel.chessgame.server.auth

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import java.net.URI
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.TimeUnit

/** Who a verified token says the caller is. */
data class SupabaseIdentity(
    /** The Supabase auth subject: the immutable identity behind an account. */
    val subject: String,
    val isAnonymous: Boolean,
)

/** Raised when a token is missing, malformed, expired, or not one of ours. */
class InvalidTokenException(
    override val message: String,
    override val cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Checks that a bearer token really was issued by our Supabase project.
 *
 * The signature is checked against the project's published JWKS, so the server never holds
 * a signing secret. Issuer and expiry are checked too: a token from another project, or an
 * expired one, is not ours to trust. The client is untrusted (`D004`), so nothing in the
 * token body is believed until the signature says it may be.
 */
class SupabaseTokenVerifier(
    private val issuer: String,
    private val keys: SigningKeys,
    private val audience: String = DEFAULT_AUDIENCE,
) {
    /** Where public signing keys come from, keyed by the token's `kid`. */
    fun interface SigningKeys {
        fun keyFor(keyId: String): java.security.PublicKey
    }

    /** The identity in [token], or an [InvalidTokenException] if it cannot be trusted. */
    fun verify(token: String): SupabaseIdentity {
        val decoded =
            try {
                verified(token)
            } catch (e: JWTVerificationException) {
                throw InvalidTokenException("Token rejected: ${e.message}", e)
            } catch (e: IllegalArgumentException) {
                throw InvalidTokenException("Token rejected: ${e.message}", e)
            }

        val subject =
            decoded.subject?.takeIf { it.isNotBlank() }
                ?: throw InvalidTokenException("Token has no subject")

        return SupabaseIdentity(
            subject = subject,
            isAnonymous = decoded.getClaim(ANONYMOUS_CLAIM).asBoolean() ?: false,
        )
    }

    private fun verified(token: String): DecodedJWT {
        val decoded = JWT.decode(token)
        val keyId = decoded.keyId ?: throw InvalidTokenException("Token has no key id")
        val algorithm = algorithmFor(decoded.algorithm, keys.keyFor(keyId))

        return JWT
            .require(algorithm)
            .withIssuer(issuer)
            .withAudience(audience)
            .build()
            .verify(token)
    }

    private fun algorithmFor(
        name: String?,
        key: java.security.PublicKey,
    ): Algorithm =
        when {
            name == "ES256" && key is ECPublicKey -> Algorithm.ECDSA256(key, null)
            name == "RS256" && key is RSAPublicKey -> Algorithm.RSA256(key, null)
            else -> throw InvalidTokenException("Unsupported token algorithm: $name")
        }

    companion object {
        /** Supabase issues user tokens with this audience. */
        const val DEFAULT_AUDIENCE: String = "authenticated"

        /** The claim Supabase sets on an anonymous account. */
        const val ANONYMOUS_CLAIM: String = "is_anonymous"

        /**
         * A verifier for the Supabase project at [supabaseUrl], reading keys from its
         * published JWKS with a small cache so every request does not fetch them.
         */
        fun forProject(supabaseUrl: String): SupabaseTokenVerifier {
            val jwks =
                JwkProviderBuilder(URI("$supabaseUrl/auth/v1/.well-known/jwks.json").toURL())
                    .cached(CACHED_KEYS, CACHE_HOURS, TimeUnit.HOURS)
                    .rateLimited(RATE_LIMIT_PER_MINUTE, 1, TimeUnit.MINUTES)
                    .build()

            return SupabaseTokenVerifier(issuer = "$supabaseUrl/auth/v1", keys = jwks.asSigningKeys())
        }

        /** Adapts an auth0 [JwkProvider] to [SigningKeys]. */
        fun JwkProvider.asSigningKeys(): SigningKeys =
            SigningKeys { keyId ->
                try {
                    get(keyId).publicKey
                } catch (e: Exception) {
                    throw InvalidTokenException("No signing key $keyId", e)
                }
            }

        private const val CACHED_KEYS = 10L
        private const val CACHE_HOURS = 24L
        private const val RATE_LIMIT_PER_MINUTE = 10L
    }
}
