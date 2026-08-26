package com.jmussel.chessgame.server.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.Date

/**
 * A stand-in for the Supabase signing key, so token verification can be tested without a
 * network call.
 *
 * The tokens are the real thing — genuinely signed ES256 JWTs with Supabase's claim shape —
 * only the key is ours.
 */
class TestTokens(
    val issuer: String = "https://project.supabase.co/auth/v1",
    val keyId: String = "test-key",
) {
    private val keyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    private val algorithm =
        Algorithm.ECDSA256(keyPair.public as ECPublicKey, keyPair.private as ECPrivateKey)

    /** The public half, as the verifier would fetch it from the JWKS. */
    val signingKeys: SupabaseTokenVerifier.SigningKeys =
        SupabaseTokenVerifier.SigningKeys { requested ->
            if (requested == keyId) keyPair.public else throw InvalidTokenException("No key $requested")
        }

    /** A verifier that trusts this key and issuer. */
    fun verifier(): SupabaseTokenVerifier = SupabaseTokenVerifier(issuer = issuer, keys = signingKeys)

    /** A token shaped like a Supabase anonymous session token. */
    fun tokenFor(
        subject: String,
        isAnonymous: Boolean = true,
        issuer: String = this.issuer,
        audience: String = SupabaseTokenVerifier.DEFAULT_AUDIENCE,
        expiresAt: Instant = Instant.now().plusSeconds(EXPIRY_SECONDS),
        keyId: String = this.keyId,
        algorithm: Algorithm = this.algorithm,
    ): String =
        JWT
            .create()
            .withKeyId(keyId)
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(subject)
            .withClaim(SupabaseTokenVerifier.ANONYMOUS_CLAIM, isAnonymous)
            .withIssuedAt(Date.from(Instant.now().minusSeconds(1)))
            .withExpiresAt(Date.from(expiresAt))
            .sign(algorithm)

    /** A token signed by a different key, as an impostor would produce. */
    fun tokenFromAnotherKey(subject: String): String {
        val other = TestTokens(issuer = issuer, keyId = keyId)
        return other.tokenFor(subject)
    }

    /** The public key, for tests that need it directly. */
    fun publicKey(): PublicKey = keyPair.public

    private companion object {
        const val EXPIRY_SECONDS = 3600L
    }
}
