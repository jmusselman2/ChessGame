package com.jmussel.chessgame.local

import com.jmussel.chessgame.core.chess.MoveRecord
import com.jmussel.chessgame.core.chess.UndoneOperation
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * How the Undo operations Redo can restore are stored in `local_games.redo_path` (`D092`):
 * each operation's moves in coordinate notation, the whole position each was played from,
 * and the position after the last of them, all as [LocalStateDocument]s. Restoring one
 * replays nothing (`D061`).
 *
 * A persistence DTO, like [LocalStateDocument], with the same shape as the server's.
 */
@Serializable
internal data class LocalRedoPathDocument(
    val records: List<Record>,
    val stateAfter: LocalStateDocument,
) {
    /** One move the Undo took back, and the position it was played from. */
    @Serializable
    data class Record(
        val move: String,
        val positionBefore: LocalStateDocument,
    )

    fun toUndoneOperation(): UndoneOperation =
        UndoneOperation(
            records = records.map { MoveRecord(moveFromCoordinates(it.move), it.positionBefore.toGameState()) },
            stateAfter = stateAfter.toGameState(),
        )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val pathSerializer = ListSerializer(serializer())

        /** [path] as the JSON text stored in the database. */
        fun encode(path: List<UndoneOperation>): String =
            json.encodeToString(
                pathSerializer,
                path.map { operation ->
                    LocalRedoPathDocument(
                        records = operation.records.map { Record(it.move.toString(), LocalStateDocument.of(it.positionBefore)) },
                        stateAfter = LocalStateDocument.of(operation.stateAfter),
                    )
                },
            )

        /** The path stored as [text]. */
        fun decode(text: String): List<UndoneOperation> = json.decodeFromString(pathSerializer, text).map { it.toUndoneOperation() }
    }
}
