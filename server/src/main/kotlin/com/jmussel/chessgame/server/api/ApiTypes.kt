package com.jmussel.chessgame.server.api

import com.jmussel.chessgame.server.db.StoredUser
import kotlinx.serialization.Serializable

/**
 * A user as other users see them: the internal id everything references, and the name they
 * chose.
 *
 * Deliberately does not carry the auth subject, `lastSeenAt`, or anything else about the
 * account — the API tells one user only what they need to play with another.
 */
@Serializable
data class UserSummary(
    val userId: String,
    val username: String,
)

/** [StoredUser] as the API shows it, or `null` when they have not claimed a username yet. */
@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
fun StoredUser.toSummaryOrNull(): UserSummary? = username?.let { UserSummary(userId = id.toString(), username = it) }
