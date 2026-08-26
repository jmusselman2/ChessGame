package com.jmussel.chessgame.server.auth

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupabaseTokenVerifierTest {
    private val tokens = TestTokens()
    private val verifier = tokens.verifier()

    @Test
    fun aGenuineTokenYieldsItsSubject() {
        val identity = verifier.verify(tokens.tokenFor(subject = "auth-subject-1"))

        assertEquals("auth-subject-1", identity.subject)
        assertTrue(identity.isAnonymous)
    }

    @Test
    fun aNonAnonymousTokenIsMarkedAsSuch() {
        val identity = verifier.verify(tokens.tokenFor(subject = "auth-subject-1", isAnonymous = false))

        assertFalse(identity.isAnonymous)
    }

    @Test
    fun aTokenSignedByAnotherKeyIsRejected() {
        val forged = tokens.tokenFromAnotherKey(subject = "auth-subject-1")

        assertFailsWith<InvalidTokenException> { verifier.verify(forged) }
    }

    @Test
    fun aTokenFromAnotherProjectIsRejected() {
        val foreign = tokens.tokenFor(subject = "auth-subject-1", issuer = "https://someone-else.supabase.co/auth/v1")

        assertFailsWith<InvalidTokenException> { verifier.verify(foreign) }
    }

    @Test
    fun anExpiredTokenIsRejected() {
        val expired =
            tokens.tokenFor(subject = "auth-subject-1", expiresAt = Instant.now().minusSeconds(60))

        assertFailsWith<InvalidTokenException> { verifier.verify(expired) }
    }

    @Test
    fun aTokenForTheWrongAudienceIsRejected() {
        val wrongAudience = tokens.tokenFor(subject = "auth-subject-1", audience = "service_role")

        assertFailsWith<InvalidTokenException> { verifier.verify(wrongAudience) }
    }

    @Test
    fun aTokenWithAnUnknownKeyIdIsRejected() {
        val unknownKey = tokens.tokenFor(subject = "auth-subject-1", keyId = "some-other-key")

        assertFailsWith<InvalidTokenException> { verifier.verify(unknownKey) }
    }

    @Test
    fun rubbishIsRejected() {
        listOf("", "not-a-token", "a.b.c").forEach { junk ->
            assertFailsWith<InvalidTokenException>("'$junk' should not verify") { verifier.verify(junk) }
        }
    }

    @Test
    fun anUnsignedTokenIsRejected() {
        val none =
            com.auth0.jwt.JWT
                .create()
                .withKeyId(tokens.keyId)
                .withIssuer(tokens.issuer)
                .withAudience(SupabaseTokenVerifier.DEFAULT_AUDIENCE)
                .withSubject("auth-subject-1")
                .sign(
                    com.auth0.jwt.algorithms.Algorithm
                        .none(),
                )

        assertFailsWith<InvalidTokenException> { verifier.verify(none) }
    }

    @Test
    fun theVerifierNeedsNoSigningSecret() {
        // Only the public half of the key is ever handed to the verifier.
        assertEquals("EC", tokens.publicKey().algorithm)
    }
}
