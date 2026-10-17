package com.jmussel.chessgame.ui.profile

import com.jmussel.chessgame.api.ProfileDto

/**
 * Another player's profile screen (`D099`, `M22.5`): who it is about, what the server said,
 * and whether it can be shown.
 */
data class ProfileUiState(
    /** Whose profile this is, by immutable user id. */
    val userId: String? = null,
    /** Their name as the row that opened it showed it, until the server's answer arrives. */
    val username: String? = null,
    val profile: ProfileDto? = null,
    val loading: Boolean = false,
    /**
     * The server answered `404`: the two are no longer friends or in a group together, so the
     * profile is simply not available. Not a failure to retry.
     */
    val unavailable: Boolean = false,
    /** What went wrong with the last read, when it was not the profile being unavailable. */
    val message: String? = null,
)

/** What the profile screen says. Pure, so the wording is tested without a screen. */
object Profiles {
    /**
     * `"Joined September 2026"`: the month and year the server gave, already in UTC. The names
     * are spelled here rather than taken from `java.time`, which Android 5.1 (minSdk 22) lacks.
     */
    fun joinedText(profile: ProfileDto): String = "Joined ${MONTHS[profile.joinedMonth - 1]} ${profile.joinedYear}"

    private val MONTHS =
        listOf(
            "January",
            "February",
            "March",
            "April",
            "May",
            "June",
            "July",
            "August",
            "September",
            "October",
            "November",
            "December",
        )

    /** The shared groups, or what to say when there are none. */
    fun sharedGroupsEmptyText(): String = NO_SHARED_GROUPS

    const val UNAVAILABLE: String = "This profile is not available. You are no longer friends or in a group together."
    const val SHARED_GROUPS: String = "GROUPS YOU SHARE"
    const val RECORD: String = "YOUR RECORD"
    private const val NO_SHARED_GROUPS = "No groups in common"
}
