@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.db

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb
import kotlin.uuid.ExperimentalUuidApi

/**
 * The JSON used for everything stored in a `jsonb` column.
 *
 * `encodeDefaults` is on so a stored document always carries every field, and
 * `ignoreUnknownKeys` is on so an older server can still read a document written by a
 * newer one.
 */
val StorageJson: Json =
    Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

/**
 * Exposed mappings of the tables in `database/migrations/`.
 *
 * The SQL migrations are the source of truth; these objects only describe the columns to
 * Kotlin. Nothing here creates or alters a schema.
 */
object UsersTable : Table("users") {
    val id = uuid("id")
    val authSubject = text("auth_subject")
    val username = text("username").nullable()
    val usernameNormalized = text("username_normalized").nullable()
    val lastSeenAt = timestampWithTimeZone("last_seen_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

object FriendshipsTable : Table("friendships") {
    val userAId = uuid("user_a_id")
    val userBId = uuid("user_b_id")
    val createdAt = timestampWithTimeZone("created_at")
    val removedAt = timestampWithTimeZone("removed_at").nullable()

    override val primaryKey = PrimaryKey(userAId, userBId)
}

object GameSeriesTable : Table("game_series") {
    val id = uuid("id")
    val userAId = uuid("user_a_id")
    val userBId = uuid("user_b_id")
    val status = text("status")
    val closeAfterCurrentGame = bool("close_after_current_game")
    val currentGameId = uuid("current_game_id").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val closedAt = timestampWithTimeZone("closed_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object GamesTable : Table("games") {
    val id = uuid("id")
    val seriesId = uuid("series_id")
    val sequenceNumber = integer("sequence_number")
    val whiteUserId = uuid("white_user_id")
    val blackUserId = uuid("black_user_id")
    val status = text("status")
    val version = long("version")
    val sideToMove = text("side_to_move")
    val state = jsonb<GameStateDocument>("state", StorageJson)
    val result = text("result").nullable()
    val terminationReason = text("termination_reason").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    val endedAt = timestampWithTimeZone("ended_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object MovesTable : Table("moves") {
    val id = uuid("id")
    val gameId = uuid("game_id")
    val ply = integer("ply")
    val side = text("side")
    val fromSquare = text("from_square")
    val toSquare = text("to_square")
    val promotion = text("promotion").nullable()
    val positionBefore = jsonb<GameStateDocument>("position_before", StorageJson)
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

object GameEventsTable : Table("game_events") {
    val id = long("id").autoIncrement()
    val gameId = uuid("game_id").nullable()
    val seriesId = uuid("series_id").nullable()
    val actorId = uuid("actor_id").nullable()
    val type = text("type")
    val payload = jsonb<JsonObject>("payload", StorageJson)
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
