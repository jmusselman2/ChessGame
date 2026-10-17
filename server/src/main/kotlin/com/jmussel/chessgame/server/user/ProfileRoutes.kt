@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.user

import com.jmussel.chessgame.server.api.ProfileView
import com.jmussel.chessgame.server.api.RecordView
import com.jmussel.chessgame.server.api.SharedGroupView
import com.jmussel.chessgame.server.auth.authenticatedUser
import com.jmussel.chessgame.server.db.ChessRecord
import com.jmussel.chessgame.server.db.DashboardQueries
import com.jmussel.chessgame.server.db.FriendshipRepository
import com.jmussel.chessgame.server.db.GroupRepository
import com.jmussel.chessgame.server.db.UserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.time.ZoneOffset
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Another user's ChessGame profile (`D099`, `M22.5`).
 *
 * `GET /profiles/{userId}` names the subject by immutable user id, never by username. Every
 * read checks the relationship as it is now: the viewer must be the subject's friend or share
 * a group with them. Anyone else — and anyone asking about an id that does not exist, or about
 * themselves — gets the same `404`, so the endpoint never confirms that a user exists.
 *
 * The answer holds exactly the username, the UTC month and year they joined, the viewer's
 * lifetime chess record against them, and the groups the two share now; never the subject's
 * other groups.
 *
 * Routes must sit behind authentication.
 */
fun Route.profileRoutes(
    users: UserRepository,
    friendships: FriendshipRepository,
    groups: GroupRepository,
    dashboard: DashboardQueries,
) {
    get("/profiles/{userId}") {
        val caller = call.authenticatedUser()
        val subjectId = runCatching { Uuid.parse(call.parameters["userId"].orEmpty()) }.getOrNull()
        val subject = subjectId?.takeIf { it != caller.userId }?.let(users::find)
        val username = subject?.username

        val related =
            subject != null &&
                (friendships.areFriends(caller.userId, subject.id) || groups.shareAGroup(caller.userId, subject.id))

        if (subject == null || username == null || !related) {
            call.respondText(UNAVAILABLE, status = HttpStatusCode.NotFound)
            return@get
        }

        val joined = requireNotNull(subject.createdAt) { "A stored user has a creation time" }.atOffset(ZoneOffset.UTC)
        val subjectsGroups = groups.groupsOf(subject.id).map { it.id }.toSet()
        val shared = groups.groupsOf(caller.userId).filter { it.id in subjectsGroups }

        call.respond(
            ProfileView(
                userId = subject.id.toString(),
                username = username,
                joinedYear = joined.year,
                joinedMonth = joined.monthValue,
                record = RecordView.of(dashboard.chessRecordsFor(caller.userId)[subject.id] ?: ChessRecord()),
                sharedGroups = shared.map { SharedGroupView(groupId = it.id.toString(), name = it.name) },
            ),
        )
    }
}

/** What a profile the caller may not see, or that does not exist, is answered with. */
private const val UNAVAILABLE = "No such profile"
