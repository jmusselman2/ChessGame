@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.ZoneOffset
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A group as the database holds it. */
data class StoredGroup(
    val id: Uuid,
    val name: String,
    val createdBy: Uuid,
    val createdAt: Instant,
)

/** What happened to an add-to-group request. */
sealed interface AddGroupMemberResult {
    /** They are in the group now. */
    data object Added : AddGroupMemberResult

    /** They were already in it; nothing changed. */
    data object AlreadyMember : AddGroupMemberResult

    /** There is no such group. */
    data object NoSuchGroup : AddGroupMemberResult

    /**
     * The caller is not in this group, so they cannot add to it.
     *
     * Deliberately not distinguished from [NoSuchGroup] by the route: a non-member learning
     * that a group exists is a leak, however small.
     */
    data object NotAMember : AddGroupMemberResult

    /** The person being added is not a friend of the caller (`D049`). */
    data object NotAFriend : AddGroupMemberResult
}

/** What happened to a leave-group request. */
sealed interface LeaveGroupResult {
    /** They are out of the group. */
    data object Left : LeaveGroupResult

    /** They were not in it — no such group, or not a current member. */
    data object NotAMember : LeaveGroupResult
}

/**
 * Groups: standing pools of people that exist only to answer "may this person be invited"
 * (`D049`, `M19.2`).
 *
 * Membership mirrors friendship (`D009`) deliberately, so there is one mental model for
 * "someone added me to something": any member may create a group they belong to, any
 * member may add one of their own friends, membership takes effect immediately with no
 * accept step, and any member may leave unilaterally. There is no owner, no admin, and no
 * way to remove someone else — [leave] is the only exit, which is why the route for it is
 * addressed at the caller themselves.
 *
 * A group has no game-state role at all. Nothing here reads or writes a series, a game, or
 * a table, and [leave] revokes future eligibility and nothing else.
 *
 * Two of `D049`'s rules cannot be column constraints, because each needs a row in another
 * table, so they are enforced here inside one transaction:
 *
 * - **A group always has its creator in it.** [create] inserts the group and that first
 *   membership together, so a memberless group never exists to be observed.
 * - **The person added must be a friend of the adder.** That answer lives in
 *   `friendships`, which is also the table that can change it a moment later; like series
 *   creation (`D046`) the check is a gate at the moment of the request, not a guarantee
 *   about the future. Removing a friend afterwards does not eject them from a group.
 */
class GroupRepository(
    private val database: Database,
    private val friendships: FriendshipRepository,
) {
    /**
     * Creates a group called [name] with [creator] as its first member.
     *
     * Both rows in one transaction: `D049` says a group is created *by a member*, and the
     * only way to be sure of that from the first instant is to write the membership with
     * the group. Names are not unique — two people may each have a group called "Tuesday",
     * and a group is identified by its id everywhere (unlike a username, `D007`).
     */
    fun create(
        creator: Uuid,
        name: String,
    ): StoredGroup =
        transaction(database) {
            val id = Uuid.random()
            val now = Instant.now()

            GroupsTable.insert { row ->
                row[GroupsTable.id] = id
                row[GroupsTable.name] = name
                row[GroupsTable.createdBy] = creator
                row[GroupsTable.createdAt] = now.atOffset(ZoneOffset.UTC)
            }

            join(groupId = id, userId = creator, addedBy = creator, at = now)

            StoredGroup(id = id, name = name, createdBy = creator, createdAt = now)
        }

    /** The group with [groupId], or `null` when there is none. */
    fun find(groupId: Uuid): StoredGroup? =
        transaction(database) {
            GroupsTable
                .selectAll()
                .where { GroupsTable.id eq groupId }
                .singleOrNull()
                ?.let(::toGroup)
        }

    /**
     * Adds [target] to [groupId] on [actor]'s say-so.
     *
     * [actor] must be a current member and [target] must be a current friend of theirs.
     * Both are read inside the transaction that writes the membership, so neither can be
     * checked against a group the caller has since left.
     */
    fun addMember(
        groupId: Uuid,
        actor: Uuid,
        target: Uuid,
    ): AddGroupMemberResult =
        transaction(database) {
            if (find(groupId) == null) return@transaction AddGroupMemberResult.NoSuchGroup
            if (currentMembership(groupId, actor) == null) return@transaction AddGroupMemberResult.NotAMember

            // The actor is a member, so adding themselves is always already done. Checked
            // before the friendship, because nobody is their own friend (`D009`) and
            // `NotAFriend` would be a confusing way to say "you are already in this".
            if (actor == target) return@transaction AddGroupMemberResult.AlreadyMember
            if (!friendships.areFriends(actor, target)) return@transaction AddGroupMemberResult.NotAFriend

            val existing = membershipRow(groupId, target)

            when {
                existing == null -> {
                    join(groupId = groupId, userId = target, addedBy = actor, at = Instant.now())
                    AddGroupMemberResult.Added
                }

                existing[GroupMembersTable.leftAt] == null -> AddGroupMemberResult.AlreadyMember

                // The row read as left, but another member may be adding them back at the
                // same moment; only the caller whose update actually revived it is told
                // `Added`. Same guard, and the same reason, as reviving a friendship.
                else -> if (rejoin(groupId, target, actor)) AddGroupMemberResult.Added else AddGroupMemberResult.AlreadyMember
            }
        }

    /**
     * Takes [userId] out of [groupId], which they may always do.
     *
     * Unilateral by design: `D049` makes leaving the release valve for having been added to
     * a group by someone whose other friends are strangers. Nothing else is touched — no
     * table, no series, no game — and past eligibility is not undone, only future
     * eligibility.
     */
    fun leave(
        groupId: Uuid,
        userId: Uuid,
    ): LeaveGroupResult =
        transaction(database) {
            val left =
                GroupMembersTable.update(
                    {
                        (GroupMembersTable.groupId eq groupId) and
                            (GroupMembersTable.userId eq userId) and
                            GroupMembersTable.leftAt.isNull()
                    },
                ) { row ->
                    row[GroupMembersTable.leftAt] = Instant.now().atOffset(ZoneOffset.UTC)
                }

            if (left > 0) LeaveGroupResult.Left else LeaveGroupResult.NotAMember
        }

    /** Whether [userId] is in [groupId] right now. */
    fun isMember(
        groupId: Uuid,
        userId: Uuid,
    ): Boolean = transaction(database) { currentMembership(groupId, userId) != null }

    /** Everyone in [groupId] right now, in the order they joined. */
    fun membersOf(groupId: Uuid): List<Uuid> =
        transaction(database) {
            GroupMembersTable
                .selectAll()
                .where { (GroupMembersTable.groupId eq groupId) and GroupMembersTable.leftAt.isNull() }
                .orderBy(GroupMembersTable.joinedAt to SortOrder.ASC)
                .map { it[GroupMembersTable.userId] }
        }

    /** The groups [userId] is in right now, oldest first. */
    fun groupsOf(userId: Uuid): List<StoredGroup> =
        transaction(database) {
            val ids = currentGroupIds(userId)

            if (ids.isEmpty()) {
                emptyList()
            } else {
                GroupsTable
                    .selectAll()
                    .where { GroupsTable.id inList ids }
                    .orderBy(GroupsTable.createdAt to SortOrder.ASC)
                    .map(::toGroup)
            }
        }

    /**
     * Whether [first] and [second] are currently in a group together.
     *
     * The other half of invite-eligibility, and the reason it is transitive: the group is
     * the relationship, so two people who share one are eligible to each other without
     * ever having been friends (`D049`).
     */
    fun shareAGroup(
        first: Uuid,
        second: Uuid,
    ): Boolean {
        if (first == second) return false

        return transaction(database) {
            val shared = currentGroupIds(first)

            shared.isNotEmpty() &&
                GroupMembersTable
                    .selectAll()
                    .where {
                        (GroupMembersTable.userId eq second) and
                            (GroupMembersTable.groupId inList shared) and
                            GroupMembersTable.leftAt.isNull()
                    }.limit(1)
                    .any()
        }
    }

    private fun currentGroupIds(userId: Uuid): List<Uuid> =
        GroupMembersTable
            .selectAll()
            .where { (GroupMembersTable.userId eq userId) and GroupMembersTable.leftAt.isNull() }
            .map { it[GroupMembersTable.groupId] }

    /** The membership row for someone who is in the group right now, or `null`. */
    private fun currentMembership(
        groupId: Uuid,
        userId: Uuid,
    ): ResultRow? = membershipRow(groupId, userId)?.takeIf { it[GroupMembersTable.leftAt] == null }

    private fun membershipRow(
        groupId: Uuid,
        userId: Uuid,
    ): ResultRow? =
        GroupMembersTable
            .selectAll()
            .where { (GroupMembersTable.groupId eq groupId) and (GroupMembersTable.userId eq userId) }
            .singleOrNull()

    private fun join(
        groupId: Uuid,
        userId: Uuid,
        addedBy: Uuid,
        at: Instant,
    ) {
        GroupMembersTable.insert { row ->
            row[GroupMembersTable.groupId] = groupId
            row[GroupMembersTable.userId] = userId
            row[GroupMembersTable.addedBy] = addedBy
            row[GroupMembersTable.joinedAt] = at.atOffset(ZoneOffset.UTC)
        }
    }

    /**
     * Revives a membership that had been left, or reports that it was no longer left.
     *
     * The `left_at is not null` predicate is what makes the answer trustworthy under
     * concurrency: two members can both read the row as left, PostgreSQL serialises their
     * updates, and re-evaluating the predicate on the row the winner left behind makes the
     * loser's update match nothing. Without it both callers are told they added the person.
     */
    private fun rejoin(
        groupId: Uuid,
        userId: Uuid,
        addedBy: Uuid,
    ): Boolean =
        GroupMembersTable.update(
            {
                (GroupMembersTable.groupId eq groupId) and
                    (GroupMembersTable.userId eq userId) and
                    GroupMembersTable.leftAt.isNotNull()
            },
        ) { row ->
            row[GroupMembersTable.leftAt] = null
            row[GroupMembersTable.addedBy] = addedBy
            row[GroupMembersTable.joinedAt] = Instant.now().atOffset(ZoneOffset.UTC)
        } > 0

    private fun toGroup(row: ResultRow): StoredGroup =
        StoredGroup(
            id = row[GroupsTable.id],
            name = row[GroupsTable.name],
            createdBy = row[GroupsTable.createdBy],
            createdAt = row[GroupsTable.createdAt].toInstant(),
        )
}
