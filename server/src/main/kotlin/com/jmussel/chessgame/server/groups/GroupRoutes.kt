@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.groups

import com.jmussel.chessgame.server.api.GroupSummary
import com.jmussel.chessgame.server.api.toSummaryOrNull
import com.jmussel.chessgame.server.auth.authenticatedUser
import com.jmussel.chessgame.server.db.AddGroupMemberResult
import com.jmussel.chessgame.server.db.GroupRepository
import com.jmussel.chessgame.server.db.LeaveGroupResult
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.user.Username
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Creating a group, seeing the ones you are in, adding a friend to one, and leaving.
 *
 * A group exists only to make people invitable to each other's tables (`D049`), so these
 * routes are deliberately thin: there is no rename, no owner, no way to remove anyone else,
 * and nothing about games. Leaving is addressed at the caller — `DELETE
 * /groups/{id}/members/me` — because self-removal is the only removal there is.
 *
 * A group the caller is not in is reported as **not found**, not as forbidden. A stranger
 * should not be able to learn that a group exists, or how many members it has, by asking
 * about ids; a member is the only person for whom the group is a fact.
 *
 * Routes must sit behind authentication.
 */
fun Route.groupRoutes(
    users: UserRepository,
    groups: GroupRepository,
) {
    get("/groups") {
        val caller = call.authenticatedUser()

        call.respond(groups.groupsOf(caller.userId).map { GroupSummary.of(it, memberCount = groups.membersOf(it.id).size) })
    }

    post("/groups") {
        val caller = call.authenticatedUser()

        // The same rule as adding a friend, for the same reason (`D045`): a group is
        // populated from the creator's friends and shown to its members, and a creator with
        // no name would be invisible to every one of them.
        if (users.find(caller.userId)?.username == null) {
            call.respondText("Claim a username before creating a group", status = HttpStatusCode.Forbidden)
            return@post
        }

        val name = call.receiveText().trim()

        if (name.isEmpty() || name.length > MAX_GROUP_NAME_LENGTH) {
            call.respondText("A group needs a name of 1 to $MAX_GROUP_NAME_LENGTH characters", status = HttpStatusCode.BadRequest)
            return@post
        }

        val created = groups.create(caller.userId, name)

        call.respond(status = HttpStatusCode.Created, message = GroupSummary.of(created, memberCount = 1))
    }

    get("/groups/{groupId}/members") {
        val caller = call.authenticatedUser()
        val groupId = call.groupId() ?: return@get

        if (!groups.isMember(groupId, caller.userId)) {
            call.respondText("No such group", status = HttpStatusCode.NotFound)
            return@get
        }

        // A member with no username is a real member and is simply not listable, the same
        // way a nameless friend is not (`D045`).
        call.respond(groups.membersOf(groupId).mapNotNull { users.find(it)?.toSummaryOrNull() })
    }

    post("/groups/{groupId}/members") {
        val caller = call.authenticatedUser()
        val groupId = call.groupId() ?: return@post
        val requested = call.receiveText().trim()

        if (Username.ofOrNull(requested) == null) {
            call.respondText("Not a username", status = HttpStatusCode.BadRequest)
            return@post
        }

        val target = users.findByUsername(requested)
        if (target?.username == null) {
            call.respondText("No such user", status = HttpStatusCode.NotFound)
            return@post
        }

        when (groups.addMember(groupId, actor = caller.userId, target = target.id)) {
            AddGroupMemberResult.Added ->
                call.respondText(target.username, status = HttpStatusCode.OK)

            AddGroupMemberResult.AlreadyMember ->
                call.respondText("${target.username} is already in this group", status = HttpStatusCode.Conflict)

            // Both answer the same way on purpose: see the isolation note above.
            AddGroupMemberResult.NoSuchGroup, AddGroupMemberResult.NotAMember ->
                call.respondText("No such group", status = HttpStatusCode.NotFound)

            AddGroupMemberResult.NotAFriend ->
                call.respondText("Add ${target.username} as a friend first", status = HttpStatusCode.Forbidden)
        }
    }

    delete("/groups/{groupId}/members/me") {
        val caller = call.authenticatedUser()
        val groupId = call.groupId() ?: return@delete

        when (groups.leave(groupId, caller.userId)) {
            LeaveGroupResult.Left ->
                // Says what leaving does and, as importantly, what it does not: `D049` makes
                // this the release valve, and a player needs to know it costs them no game.
                call.respondText("Left the group; your games are unaffected", status = HttpStatusCode.OK)

            LeaveGroupResult.NotAMember ->
                call.respondText("No such group", status = HttpStatusCode.NotFound)
        }
    }
}

/** The longest a group name may be, matching the `groups_name_length` constraint. */
const val MAX_GROUP_NAME_LENGTH: Int = 48

private suspend fun ApplicationCall.groupId(): Uuid? {
    val parsed = runCatching { Uuid.parse(parameters["groupId"].orEmpty()) }.getOrNull()

    if (parsed == null) {
        respondText("Not a group id", status = HttpStatusCode.BadRequest)
    }
    return parsed
}
