package com.jmussel.chessgame.server.user

/**
 * A validated, human-facing username.
 *
 * Usernames are 3–24 characters of letters, numbers, underscore, or hyphen, and are unique
 * case-insensitively — `Jordan` and `jordan` are the same identity (`D007`). [normalized]
 * is the lowercase form everything looks up by; [value] is the spelling the owner chose and
 * everyone sees.
 */
@JvmInline
value class Username private constructor(
    val value: String,
) {
    val normalized: String
        get() = value.lowercase()

    override fun toString(): String = value

    companion object {
        const val MIN_LENGTH: Int = 3
        const val MAX_LENGTH: Int = 24

        /** Letters, numbers, underscore, hyphen. No spaces. */
        val ALLOWED = Regex("^[A-Za-z0-9_-]+$")

        /** Why a username was refused. */
        enum class Problem {
            TOO_SHORT,
            TOO_LONG,
            DISALLOWED_CHARACTERS,
        }

        /** The problem with [candidate], or `null` when it is acceptable. */
        fun problemWith(candidate: String): Problem? =
            when {
                candidate.length < MIN_LENGTH -> Problem.TOO_SHORT
                candidate.length > MAX_LENGTH -> Problem.TOO_LONG
                !ALLOWED.matches(candidate) -> Problem.DISALLOWED_CHARACTERS
                else -> null
            }

        /** [candidate] as a username, or `null` when it is not a valid one. */
        fun ofOrNull(candidate: String): Username? = if (problemWith(candidate) == null) Username(candidate) else null

        /** [candidate] as a username, failing when it is not a valid one. */
        fun of(candidate: String): Username =
            requireNotNull(ofOrNull(candidate)) {
                "Invalid username '$candidate': ${problemWith(candidate)}"
            }
    }
}
