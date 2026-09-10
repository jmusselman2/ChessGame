@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.groups

import com.jmussel.chessgame.server.db.AddGroupMemberResult
import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GroupMembersTable
import com.jmussel.chessgame.server.db.GroupRepository
import com.jmussel.chessgame.server.db.GroupsTable
import com.jmussel.chessgame.server.db.LeaveGroupResult
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.user.Username
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Group membership: creation, adding a friend, immediacy, and leaving (`D049`, `M19.2`).
 *
 * A group exists only to make people invitable to each other's tables (`D048`), so the
 * assertions here are about membership and eligibility and never about a game. Membership
 * mirrors friendship deliberately (`D009`): immediate, no accept step, unilateral exit.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class GroupMembershipTest {
    private class Fixture(
        val users: UserRepository,
        val friendships: FriendshipRepository,
        val groups: GroupRepository,
        val database: Database,
    ) {
        fun named(
            subject: String,
            username: String,
        ): Uuid {
            val user = users.resolveBySubject(subject)
            users.claimUsername(user.id, Username.of(username))
            return user.id
        }

        fun befriend(
            first: Uuid,
            second: Uuid,
        ) {
            friendships.add(first, second)
        }

        fun membershipRowCount(groupId: Uuid): Int =
            transaction(database) {
                GroupMembersTable
                    .selectAll()
                    .where { GroupMembersTable.groupId eq groupId }
                    .count()
                    .toInt()
            }

        fun leftAt(
            groupId: Uuid,
            userId: Uuid,
        ) = transaction(database) {
            GroupMembersTable
                .selectAll()
                .where { (GroupMembersTable.groupId eq groupId) and (GroupMembersTable.userId eq userId) }
                .single()[GroupMembersTable.leftAt]
        }

        fun groupRowCount(): Int = transaction(database) { GroupsTable.selectAll().count().toInt() }
    }

    private fun withGroups(block: (Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val friendships = FriendshipRepository(database)

            block(
                Fixture(
                    users = UserRepository(database),
                    friendships = friendships,
                    groups = GroupRepository(database, friendships),
                    database = database,
                ),
            )
        }

    // --- Creation -----------------------------------------------------------------------

    @Test
    fun creatingAGroupPutsTheCreatorInItImmediately() {
        withGroups { fixture ->
            val creator = fixture.named("group-creator", "Creator")

            val group = fixture.groups.create(creator, "Tuesday Night")

            assertEquals("Tuesday Night", group.name)
            assertEquals(creator, group.createdBy)
            // `D049` says a group is created *by a member*, so it is never observable empty.
            assertTrue(fixture.groups.isMember(group.id, creator), "the creator is in it from the first instant")
            assertEquals(listOf(creator), fixture.groups.membersOf(group.id))
        }
    }

    @Test
    fun twoGroupsMayShareAName() {
        withGroups { fixture ->
            val first = fixture.named("group-name-a", "Ana")
            val second = fixture.named("group-name-b", "Ben")

            val one = fixture.groups.create(first, "Tuesday")
            val other = fixture.groups.create(second, "Tuesday")

            // Unlike a username (`D007`), a group is identified by its id everywhere.
            assertTrue(one.id != other.id, "two groups called the same thing are two groups")
            assertEquals(2, fixture.groupRowCount())
        }
    }

    // --- Adding a friend ----------------------------------------------------------------

    @Test
    fun aMemberCanAddTheirOwnFriendAndMembershipIsImmediate() {
        withGroups { fixture ->
            val host = fixture.named("group-host", "Host")
            val friend = fixture.named("group-friend", "Friend")
            fixture.befriend(host, friend)
            val group = fixture.groups.create(host, "Regulars")

            val result = fixture.groups.addMember(group.id, actor = host, target = friend)

            assertEquals(AddGroupMemberResult.Added, result)
            // No accept step: the friend is in it now, without doing anything (`D049`).
            assertTrue(fixture.groups.isMember(group.id, friend), "membership takes effect immediately")
            assertEquals(listOf(host, friend), fixture.groups.membersOf(group.id))
        }
    }

    @Test
    fun someoneWhoIsNotAFriendCannotBeAdded() {
        withGroups { fixture ->
            val host = fixture.named("group-host-2", "Host")
            val stranger = fixture.named("group-stranger", "Stranger")
            val group = fixture.groups.create(host, "Regulars")

            val result = fixture.groups.addMember(group.id, actor = host, target = stranger)

            assertEquals(AddGroupMemberResult.NotAFriend, result)
            assertFalse(fixture.groups.isMember(group.id, stranger), "nothing was written")
            assertEquals(1, fixture.membershipRowCount(group.id))
        }
    }

    @Test
    fun aFriendWhoWasRemovedAsAFriendCanNoLongerBeAdded() {
        withGroups { fixture ->
            val host = fixture.named("group-host-3", "Host")
            val former = fixture.named("group-former", "Former")
            fixture.befriend(host, former)
            fixture.friendships.remove(host, former)
            val group = fixture.groups.create(host, "Regulars")

            // The gate is the friendship as it stands at the moment of the request, the same
            // way series creation reads what it reads (`D046`).
            assertEquals(AddGroupMemberResult.NotAFriend, fixture.groups.addMember(group.id, host, former))
        }
    }

    @Test
    fun unfriendingSomeoneDoesNotEjectThemFromAGroup() {
        withGroups { fixture ->
            val host = fixture.named("group-host-4", "Host")
            val friend = fixture.named("group-friend-4", "Friend")
            fixture.befriend(host, friend)
            val group = fixture.groups.create(host, "Regulars")
            fixture.groups.addMember(group.id, host, friend)

            fixture.friendships.remove(host, friend)

            // The friendship was the gate at the moment of adding, not a standing condition:
            // eligibility already granted is not withdrawn, and leaving is the release valve.
            assertTrue(fixture.groups.isMember(group.id, friend), "membership outlives the friendship that made it")
        }
    }

    @Test
    fun anyMemberCanAddTheirOwnFriendNotJustTheCreator() {
        withGroups { fixture ->
            val creator = fixture.named("group-creator-5", "Creator")
            val member = fixture.named("group-member-5", "Member")
            val theirFriend = fixture.named("group-their-friend-5", "Theirs")
            fixture.befriend(creator, member)
            fixture.befriend(member, theirFriend)
            val group = fixture.groups.create(creator, "Regulars")
            fixture.groups.addMember(group.id, creator, member)

            // `theirFriend` is a stranger to the creator; the adder's own friendship is what
            // counts (`D049`), and there is no owner whose permission is needed.
            assertEquals(AddGroupMemberResult.Added, fixture.groups.addMember(group.id, actor = member, target = theirFriend))
            assertEquals(listOf(creator, member, theirFriend), fixture.groups.membersOf(group.id))
        }
    }

    @Test
    fun someoneOutsideTheGroupCannotAddToIt() {
        withGroups { fixture ->
            val creator = fixture.named("group-creator-6", "Creator")
            val outsider = fixture.named("group-outsider-6", "Outsider")
            val theirFriend = fixture.named("group-outsiders-friend-6", "Theirs")
            fixture.befriend(outsider, theirFriend)
            val group = fixture.groups.create(creator, "Regulars")

            assertEquals(
                AddGroupMemberResult.NotAMember,
                fixture.groups.addMember(group.id, actor = outsider, target = theirFriend),
            )
            assertEquals(1, fixture.membershipRowCount(group.id))
        }
    }

    @Test
    fun addingSomeoneAlreadyInTheGroupChangesNothing() {
        withGroups { fixture ->
            val host = fixture.named("group-host-7", "Host")
            val friend = fixture.named("group-friend-7", "Friend")
            fixture.befriend(host, friend)
            val group = fixture.groups.create(host, "Regulars")
            fixture.groups.addMember(group.id, host, friend)

            assertEquals(AddGroupMemberResult.AlreadyMember, fixture.groups.addMember(group.id, host, friend))
            assertEquals(2, fixture.membershipRowCount(group.id), "one row per person per group")
        }
    }

    @Test
    fun addingYourselfIsAlreadyDone() {
        withGroups { fixture ->
            val host = fixture.named("group-host-8", "Host")
            val group = fixture.groups.create(host, "Regulars")

            assertEquals(AddGroupMemberResult.AlreadyMember, fixture.groups.addMember(group.id, host, host))
        }
    }

    @Test
    fun aGroupThatDoesNotExistIsSaidSo() {
        withGroups { fixture ->
            val host = fixture.named("group-host-9", "Host")
            val friend = fixture.named("group-friend-9", "Friend")
            fixture.befriend(host, friend)

            assertEquals(AddGroupMemberResult.NoSuchGroup, fixture.groups.addMember(Uuid.random(), host, friend))
        }
    }

    // --- Leaving ------------------------------------------------------------------------

    @Test
    fun aMemberCanLeaveUnilaterallyAndTheGroupSurvives() {
        withGroups { fixture ->
            val host = fixture.named("group-host-10", "Host")
            val friend = fixture.named("group-friend-10", "Friend")
            fixture.befriend(host, friend)
            val group = fixture.groups.create(host, "Regulars")
            fixture.groups.addMember(group.id, host, friend)

            assertEquals(LeaveGroupResult.Left, fixture.groups.leave(group.id, friend))

            assertFalse(fixture.groups.isMember(group.id, friend), "they are out")
            assertEquals(listOf(host), fixture.groups.membersOf(group.id))
            assertEquals(1, fixture.groupRowCount(), "the group itself is untouched")
            assertNotNull(fixture.leftAt(group.id, friend), "the row records when, rather than being deleted")
        }
    }

    @Test
    fun theCreatorCanLeaveTheirOwnGroupLikeAnyoneElse() {
        withGroups { fixture ->
            val creator = fixture.named("group-creator-11", "Creator")
            val friend = fixture.named("group-friend-11", "Friend")
            fixture.befriend(creator, friend)
            val group = fixture.groups.create(creator, "Regulars")
            fixture.groups.addMember(group.id, creator, friend)

            // There is no owner, so there is nobody whose leaving is a special case.
            assertEquals(LeaveGroupResult.Left, fixture.groups.leave(group.id, creator))
            assertEquals(listOf(friend), fixture.groups.membersOf(group.id))
        }
    }

    @Test
    fun leavingTwiceIsNotLeavingAgain() {
        withGroups { fixture ->
            val host = fixture.named("group-host-12", "Host")
            val group = fixture.groups.create(host, "Regulars")

            assertEquals(LeaveGroupResult.Left, fixture.groups.leave(group.id, host))
            assertEquals(LeaveGroupResult.NotAMember, fixture.groups.leave(group.id, host))
        }
    }

    @Test
    fun someoneWhoLeftCanBeAddedBackByAFriendInTheGroup() {
        withGroups { fixture ->
            val host = fixture.named("group-host-13", "Host")
            val friend = fixture.named("group-friend-13", "Friend")
            fixture.befriend(host, friend)
            val group = fixture.groups.create(host, "Regulars")
            fixture.groups.addMember(group.id, host, friend)
            fixture.groups.leave(group.id, friend)

            assertEquals(AddGroupMemberResult.Added, fixture.groups.addMember(group.id, host, friend))

            assertTrue(fixture.groups.isMember(group.id, friend))
            assertNull(fixture.leftAt(group.id, friend), "the row was revived rather than duplicated")
            assertEquals(2, fixture.membershipRowCount(group.id))
        }
    }

    @Test
    fun theLastMemberLeavingLeavesAnEmptyGroupThatGrantsNothing() {
        withGroups { fixture ->
            val host = fixture.named("group-host-14", "Host")
            val group = fixture.groups.create(host, "Regulars")

            fixture.groups.leave(group.id, host)

            // Kept rather than deleted: the rows are history, and an empty group is simply a
            // group nobody is in — it makes nobody eligible to anybody.
            assertEquals(1, fixture.groupRowCount())
            assertEquals(emptyList(), fixture.groups.membersOf(group.id))
            assertNotNull(fixture.groups.find(group.id))
        }
    }

    // --- Which groups someone is in -----------------------------------------------------

    @Test
    fun theGroupsSomeoneIsInAreTheOnesTheyHaveNotLeft() {
        withGroups { fixture ->
            val person = fixture.named("group-person-15", "Person")
            val stayed = fixture.groups.create(person, "Stayed")
            val left = fixture.groups.create(person, "Left")
            fixture.groups.leave(left.id, person)

            assertEquals(listOf(stayed.id), fixture.groups.groupsOf(person).map { it.id })
        }
    }

    @Test
    fun someoneInNoGroupsIsInNoGroups() {
        withGroups { fixture ->
            val person = fixture.named("group-person-16", "Person")

            assertEquals(emptyList(), fixture.groups.groupsOf(person))
        }
    }
}
