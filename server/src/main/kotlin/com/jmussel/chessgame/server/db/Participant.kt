@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import kotlinx.serialization.json.JsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * What kind of participant takes a seat (`D051`), and the one thing the platform knows about it.
 *
 * Only [USER] is a person, and only a person has a `users` row. What a non-user participant
 * does in a game belongs to that game type's rules; the platform knows only whether it takes
 * part in seat rotation (`D050`).
 */
enum class ParticipantKind(
    /** Whether it rotates through the turn order, or always takes the final turn. */
    val rotates: Boolean,
) {
    /** A person. */
    USER(rotates = true),

    /** Not a person, but plays a seat the way one would, so it rotates like one. */
    COMPUTER(rotates = true),

    /** Not a person; never rotates, and always takes the final turn of every game. */
    SCRIPTED(rotates = false),
}

/**
 * Someone, or something, at a table or in a game: a kind and the id it is known by.
 *
 * [ref] is a `users.id` for a [ParticipantKind.USER] and a `non_user_participants.id` for any
 * other kind. Nothing here is specific to a game type.
 */
data class Participant(
    val kind: ParticipantKind,
    val ref: Uuid,
) {
    /** The user this participant is, or `null` when it is not a person. */
    val userId: Uuid?
        get() = ref.takeIf { kind == ParticipantKind.USER }

    /** The stored form, `KIND:ref` — the same piece `tables.participant_set` is built from. */
    override fun toString(): String = "$kind:$ref"

    companion object {
        fun user(id: Uuid): Participant = Participant(ParticipantKind.USER, id)

        /** Parses [toString]'s form; a bare id is a user, which is how rotations stored before `V9` read. */
        fun parse(stored: String): Participant {
            val separator = stored.indexOf(':')
            if (separator < 0) return user(Uuid.parse(stored))

            return Participant(ParticipantKind.valueOf(stored.substring(0, separator)), Uuid.parse(stored.substring(separator + 1)))
        }
    }
}

/** The users among [this], in order: who, of these participants, is a person. */
val Collection<Participant>.userIds: List<Uuid>
    get() = mapNotNull { it.userId }

/** A non-user participant as the database holds it: its kind, and the state its rules keep. */
data class StoredNonUserParticipant(
    val id: Uuid,
    val kind: ParticipantKind,
    val state: JsonObject,
) {
    val participant: Participant
        get() = Participant(kind, id)
}
