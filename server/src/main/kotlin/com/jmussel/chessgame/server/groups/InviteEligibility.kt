@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.groups

import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GroupRepository
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Whether one person may invite another (`D048`, `D049`).
 *
 * `D048` routes eligibility **through the host only**: each invited participant needs a
 * relationship to whoever is assembling the table, and none to each other. That is one
 * question with two answers — a friendship, or a group in common — and this is where it is
 * asked, so `M19.3`'s table creation gets a gate rather than reimplementing the rule.
 *
 * It is deliberately a plain lookup over two repositories and not an interface. Both
 * halves are concrete today and there is nothing to substitute; when a second ruleset makes
 * something here take a parameter it does not have, that is the moment to generalise it and
 * not before (`D044`).
 *
 * **Not the whole invite check.** `D048` also requires the *table* to be valid — its size
 * within the game type's range, its participant set distinct — and that belongs with the
 * table, which does not exist until `M19.3`. This answers only "is this person in reach of
 * that host".
 */
class InviteEligibility(
    private val friendships: FriendshipRepository,
    private val groups: GroupRepository,
) {
    /**
     * Whether [host] may invite [target] to a table they are assembling.
     *
     * A friendship is the direct answer; a shared group is the transitive one, and that
     * transitivity is the point of groups — a four-person table needs one relationship to
     * the host, not six pairwise friendships (`D049`). Nobody may invite themselves,
     * because the host is already at the table.
     */
    fun canInvite(
        host: Uuid,
        target: Uuid,
    ): Boolean {
        if (host == target) return false

        return friendships.areFriends(host, target) || groups.shareAGroup(host, target)
    }

    /** Why [host] may invite [target], or `null` when they may not. For explaining a refusal. */
    fun reasonFor(
        host: Uuid,
        target: Uuid,
    ): InviteReason? {
        if (host == target) return null

        return when {
            friendships.areFriends(host, target) -> InviteReason.FRIEND
            groups.shareAGroup(host, target) -> InviteReason.SHARED_GROUP
            else -> null
        }
    }
}

/** What makes someone invitable. */
enum class InviteReason {
    /** They are a friend of the host (`D009`). */
    FRIEND,

    /** They share one of the host's groups (`D049`), without necessarily being a friend. */
    SHARED_GROUP,
}
