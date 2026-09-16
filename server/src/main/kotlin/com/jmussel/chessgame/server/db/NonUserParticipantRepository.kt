@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.ZoneOffset
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Participants that are not people (`D051`).
 *
 * Deliberately not in `users`: a person-shaped row would put one in friends lists, dashboards,
 * `last_seen_at`, and username uniqueness. Each is one instance with its own [JsonObject]
 * state, kept across turns and reloads for whichever game type's rules seat it. Nothing here
 * reads that state; no rules for a non-user participant exist yet (`D044`, `D063`).
 */
class NonUserParticipantRepository(
    private val database: Database,
) {
    /** A new non-user participant of [kind], with [state] as its starting state. */
    fun create(
        kind: ParticipantKind,
        state: JsonObject = JsonObject(emptyMap()),
    ): StoredNonUserParticipant {
        require(kind != ParticipantKind.USER) { "A person is a user, not a non-user participant" }

        return transaction(database) {
            val id = Uuid.random()

            NonUserParticipantsTable.insert { row ->
                row[NonUserParticipantsTable.id] = id
                row[NonUserParticipantsTable.kind] = kind.name
                row[NonUserParticipantsTable.state] = state
                row[NonUserParticipantsTable.createdAt] = Instant.now().atOffset(ZoneOffset.UTC)
            }

            StoredNonUserParticipant(id = id, kind = kind, state = state)
        }
    }

    /** The non-user participant with [id], or `null`. */
    fun find(id: Uuid): StoredNonUserParticipant? =
        transaction(database) {
            NonUserParticipantsTable
                .selectAll()
                .where { NonUserParticipantsTable.id eq id }
                .singleOrNull()
                ?.let { row ->
                    StoredNonUserParticipant(
                        id = row[NonUserParticipantsTable.id],
                        kind = ParticipantKind.valueOf(row[NonUserParticipantsTable.kind]),
                        state = row[NonUserParticipantsTable.state],
                    )
                }
        }

    /** Replaces [id]'s state. Returns whether there was such a participant. */
    fun saveState(
        id: Uuid,
        state: JsonObject,
    ): Boolean =
        transaction(database) {
            NonUserParticipantsTable.update({ NonUserParticipantsTable.id eq id }) { row ->
                row[NonUserParticipantsTable.state] = state
            } > 0
        }
}
