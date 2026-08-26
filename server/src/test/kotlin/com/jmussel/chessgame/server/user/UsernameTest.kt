package com.jmussel.chessgame.server.user

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UsernameTest {
    @Test
    fun keepsTheSpellingTheOwnerChose() {
        val username = Username.of("Jordan")

        assertEquals("Jordan", username.value)
        assertEquals("Jordan", username.toString())
    }

    @Test
    fun normalizesToLowercaseForLookup() {
        assertEquals("jordan", Username.of("Jordan").normalized)
        assertEquals(Username.of("JORDAN").normalized, Username.of("jordan").normalized)
    }

    @Test
    fun acceptsThreeToTwentyFourCharacters() {
        assertNotNull(Username.ofOrNull("abc"))
        assertNotNull(Username.ofOrNull("a".repeat(24)))
    }

    @Test
    fun rejectsSomethingTooShort() {
        assertEquals(Username.Companion.Problem.TOO_SHORT, Username.problemWith("ab"))
        assertNull(Username.ofOrNull("ab"))
        assertNull(Username.ofOrNull(""))
    }

    @Test
    fun rejectsSomethingTooLong() {
        assertEquals(Username.Companion.Problem.TOO_LONG, Username.problemWith("a".repeat(25)))
        assertNull(Username.ofOrNull("a".repeat(25)))
    }

    @Test
    fun allowsLettersNumbersUnderscoreAndHyphen() {
        listOf("abc", "ABC", "a_b-c", "user123", "1234", "a-_-a").forEach {
            assertNotNull(Username.ofOrNull(it), "'$it' should be allowed")
        }
    }

    @Test
    fun rejectsAnythingElse() {
        listOf("has space", "dots.here", "emoji🙂x", "slash/es", "quote'd", "tab\tted", "plus+one").forEach {
            assertEquals(
                Username.Companion.Problem.DISALLOWED_CHARACTERS,
                Username.problemWith(it),
                "'$it' should be rejected",
            )
        }
    }

    @Test
    fun ofFailsLoudlyOnAnInvalidName() {
        assertFailsWith<IllegalArgumentException> { Username.of("no spaces allowed") }
    }

    @Test
    fun namesTheBoundaries() {
        assertEquals(3, Username.MIN_LENGTH)
        assertEquals(24, Username.MAX_LENGTH)
    }
}
