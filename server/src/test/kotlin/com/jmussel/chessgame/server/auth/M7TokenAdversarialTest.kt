package com.jmussel.chessgame.server.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Independent evaluator coverage for M7's signed-token claim boundaries. */
class M7TokenAdversarialTest {
    private val issuer = "https://project.supabase.co/auth/v1"
    private val keyId = "evaluator-key"
    private val keyPair =
        KeyPairGenerator
            .getInstance("EC")
            .apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair()
    private val algorithm =
        Algorithm.ECDSA256(keyPair.public as ECPublicKey, keyPair.private as ECPrivateKey)
    private val verifier =
        SupabaseTokenVerifier(
            issuer = issuer,
            keys = SupabaseTokenVerifier.SigningKeys { keyPair.public },
        )

    @Test
    fun correctlySignedTokenWithoutExpirationIsRejected() {
        val noExpiry = token(expiresAt = null)

        assertFailsWith<InvalidTokenException> { verifier.verify(noExpiry) }
    }

    @Test
    fun expectedAudienceAmongSeveralSignedAudiencesIsAccepted() {
        val multipleAudiences = token(audiences = arrayOf("service_role", "authenticated"))

        assertEquals("evaluator-subject", verifier.verify(multipleAudiences).subject)
    }

    @Test
    fun signedTokenWithoutAnIssuerIsRejected() {
        val noIssuer = token(includeIssuer = false)

        assertFailsWith<InvalidTokenException> { verifier.verify(noIssuer) }
    }

    @Test
    fun signedTokenWithoutAnAudienceIsRejected() {
        val noAudience = token(audiences = emptyArray())

        assertFailsWith<InvalidTokenException> { verifier.verify(noAudience) }
    }

    @Test
    fun signedTokenWithBlankSubjectIsRejected() {
        val blankSubject = token(subject = "   ")

        assertFailsWith<InvalidTokenException> { verifier.verify(blankSubject) }
    }

    @Test
    fun signedTokenWithoutAKeyIdIsRejected() {
        val noKeyId = token(includeKeyId = false)

        assertFailsWith<InvalidTokenException> { verifier.verify(noKeyId) }
    }

    private fun token(
        subject: String = "evaluator-subject",
        audiences: Array<String> = arrayOf("authenticated"),
        expiresAt: Instant? = Instant.now().plusSeconds(3_600),
        includeIssuer: Boolean = true,
        includeKeyId: Boolean = true,
    ): String =
        JWT
            .create()
            .apply {
                if (includeKeyId) withKeyId(keyId)
                if (includeIssuer) withIssuer(issuer)
                if (audiences.isNotEmpty()) withAudience(*audiences)
                withSubject(subject)
                expiresAt?.let { withExpiresAt(Date.from(it)) }
            }.sign(algorithm)
}
