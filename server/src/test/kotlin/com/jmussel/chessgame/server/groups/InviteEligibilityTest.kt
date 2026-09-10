@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.groups

import com.jmussel.chessgame.server.db.DatabaseTestSupport
import com.jmussel.chessgame.server.db.Databases
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GameSeriesRepository
import com.jmussel.chessgame.server.db.GameSeriesTable
import com.jmussel.chessgame.server.db.GamesTable
import com.jmussel.chessgame.server.db.GroupRepository
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.user.Username
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Who may be invited to a table, and why (`D048`, `D049`, `M19.2`).
 *
 * Eligibility runs through the host only: a friendship is the direct answer and a shared
 * group is the transitive one. That transitivity is the whole point of a group — a
 * four-person table needs one relationship to the host, not six pairwise friendships.
 *
 * The *table* these people are eligible for does not exist until `M19.3`; this covers the
 * eligibility rule alone, which is the part `M19.2` owns.
 *
 * Skipped when this machine has no test database (see [DatabaseTestSupport]).
 */
class InviteEligibilityTest {
    private class Fixture(
        val users: UserRepository,
        val friendships: FriendshipRepository,
        val groups: GroupRepository,
        val eligibility: InviteEligibility,
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

        fun seriesCount(): Int = transaction(database) { GameSeriesTable.selectAll().count().toInt() }

        fun gameCount(): Int = transaction(database) { GamesTable.selectAll().count().toInt() }
    }

    private fun withEligibility(block: (Fixture) -> Unit) =
        DatabaseTestSupport.withMigratedDatabase { dataSource ->
            val database = Databases.connect(dataSource)
            val friendships = FriendshipRepository(database)
            val groups = GroupRepository(database, friendships)

            block(
                Fixture(
                    users = UserRepository(database),
                    friendships = friendships,
                    groups = groups,
                    eligibility = InviteEligibility(friendships, groups),
                    database = database,
                ),
            )
        }

    @Test
    fun aFriendIsInvitable() {
        withEligibility { fixture ->
            val host = fixture.named("invite-host", "Host")
            val friend = fixture.named("invite-friend", "Friend")
            fixture.befriend(host, friend)

            assertTrue(fixture.eligibility.canInvite(host, friend))
            assertEquals(InviteReason.FRIEND, fixture.eligibility.reasonFor(host, friend))
        }
    }

    @Test
    fun aStrangerIsNot() {
        withEligibility { fixture ->
            val host = fixture.named("invite-host-2", "Host")
            val stranger = fixture.named("invite-stranger", "Stranger")

            assertFalse(fixture.eligibility.canInvite(host, stranger))
            assertNull(fixture.eligibility.reasonFor(host, stranger))
        }
    }

    @Test
    fun twoPeopleWhoShareAGroupAreInvitableWithoutBeingFriends() {
        withEligibility { fixture ->
            val creator = fixture.named("invite-creator", "Creator")
            val ana = fixture.named("invite-ana", "Ana")
            val ben = fixture.named("invite-ben", "Ben")
            fixture.befriend(creator, ana)
            fixture.befriend(creator, ben)

            val group = fixture.groups.create(creator, "Tuesday")
            fixture.groups.addMember(group.id, creator, ana)
            fixture.groups.addMember(group.id, creator, ben)

            // Ana and Ben have never met. This is the transitivity `D049` grants, and the
            // reason a four-person table does not need six friendships.
            assertFalse(fixture.friendships.areFriends(ana, ben), "they are not friends")
            assertTrue(fixture.eligibility.canInvite(ana, ben), "but Ana may invite Ben")
            assertTrue(fixture.eligibility.canInvite(ben, ana), "and Ben may invite Ana")
            assertEquals(InviteReason.SHARED_GROUP, fixture.eligibility.reasonFor(ana, ben))
        }
    }

    @Test
    fun leavingTheGroupRevokesEligibilityFromThenOn() {
        withEligibility { fixture ->
            val creator = fixture.named("invite-creator-4", "Creator")
            val ana = fixture.named("invite-ana-4", "Ana")
            val ben = fixture.named("invite-ben-4", "Ben")
            fixture.befriend(creator, ana)
            fixture.befriend(creator, ben)
            val group = fixture.groups.create(creator, "Tuesday")
            fixture.groups.addMember(group.id, creator, ana)
            fixture.groups.addMember(group.id, creator, ben)
            assertTrue(fixture.eligibility.canInvite(ana, ben))

            fixture.groups.leave(group.id, ben)

            // `D049`'s release valve: leaving is unilateral and takes effect at once.
            assertFalse(fixture.eligibility.canInvite(ana, ben), "Ana can no longer reach Ben")
            assertFalse(fixture.eligibility.canInvite(ben, ana), "and Ben can no longer reach Ana")
            // Their own relationships to the host are untouched.
            assertTrue(fixture.eligibility.canInvite(creator, ben), "the friendship that got them there remains")
        }
    }

    @Test
    fun leavingAGroupTouchesNoSeriesAndNoGame() {
        withEligibility { fixture ->
            val host = fixture.named("invite-host-5", "Host")
            val friend = fixture.named("invite-friend-5", "Friend")
            fixture.befriend(host, friend)
            val group = fixture.groups.create(host, "Tuesday")
            fixture.groups.addMember(group.id, host, friend)

            // A real series with a real game, so "touches nothing" is checked against
            // something that exists. Groups have no game-state role at all (`D049`).
            val opened = GameSeriesRepository(fixture.database).openOrCreate(host, friend)
            val seriesBefore = fixture.seriesCount()
            val gamesBefore = fixture.gameCount()

            fixture.groups.leave(group.id, friend)

            assertEquals(seriesBefore, fixture.seriesCount(), "no series was created or closed")
            assertEquals(gamesBefore, fixture.gameCount(), "no game was touched")
            val series = GameSeriesRepository(fixture.database).find(opened.series.id)
            assertEquals(opened.series.status, series?.status, "the series is exactly as it was")
        }
    }

    @Test
    fun sharingSeparateGroupsWithTheSameHostIsNotSharingAGroup() {
        withEligibility { fixture ->
            val creator = fixture.named("invite-creator-6", "Creator")
            val ana = fixture.named("invite-ana-6", "Ana")
            val ben = fixture.named("invite-ben-6", "Ben")
            fixture.befriend(creator, ana)
            fixture.befriend(creator, ben)
            val monday = fixture.groups.create(creator, "Monday")
            val tuesday = fixture.groups.create(creator, "Tuesday")
            fixture.groups.addMember(monday.id, creator, ana)
            fixture.groups.addMember(tuesday.id, creator, ben)

            // Eligibility is co-membership of one group, not "known to the same person".
            assertFalse(fixture.eligibility.canInvite(ana, ben))
        }
    }

    @Test
    fun nobodyMayInviteThemselves() {
        withEligibility { fixture ->
            val host = fixture.named("invite-host-7", "Host")
            fixture.groups.create(host, "Tuesday")

            // The host is already at the table they are assembling (`D048`).
            assertFalse(fixture.eligibility.canInvite(host, host))
            assertNull(fixture.eligibility.reasonFor(host, host))
            assertFalse(fixture.groups.shareAGroup(host, host))
        }
    }

    @Test
    fun aFriendshipStillCountsWhenNoGroupIsShared() {
        withEligibility { fixture ->
            val host = fixture.named("invite-host-8", "Host")
            val friend = fixture.named("invite-friend-8", "Friend")
            fixture.befriend(host, friend)
            fixture.groups.create(host, "Nobody else in here")

            assertTrue(fixture.eligibility.canInvite(host, friend))
            assertEquals(InviteReason.FRIEND, fixture.eligibility.reasonFor(host, friend))
        }
    }
}
