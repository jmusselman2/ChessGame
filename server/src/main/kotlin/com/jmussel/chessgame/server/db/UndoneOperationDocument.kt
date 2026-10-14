package com.jmussel.chessgame.server.db

import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.MoveRecord
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.core.chess.UndoneOperation
import kotlinx.serialization.Serializable

/**
 * How one Undo operation Redo can restore is stored in `games.redo_path` (`V13`, `D092`).
 *
 * A persistence DTO, like [GameStateDocument]: the moves the Undo took back, each with the
 * whole position it was played from, and the position after the last of them. Nothing in it
 * is ever shown to a client (`D069`).
 */
@Serializable
data class UndoneOperationDocument(
    val records: List<RecordDocument>,
    val stateAfter: GameStateDocument,
) {
    /** One move the Undo took back, and the position it was played from. */
    @Serializable
    data class RecordDocument(
        val from: String,
        val to: String,
        val promotion: String? = null,
        val positionBefore: GameStateDocument,
    ) {
        fun toMoveRecord(): MoveRecord =
            MoveRecord(
                move = Move(Square.parse(from), Square.parse(to), promotion?.let(PieceType::valueOf)),
                positionBefore = positionBefore.toGameState(),
            )
    }

    fun toUndoneOperation(): UndoneOperation =
        UndoneOperation(records = records.map(RecordDocument::toMoveRecord), stateAfter = stateAfter.toGameState())

    companion object {
        fun of(operation: UndoneOperation): UndoneOperationDocument =
            UndoneOperationDocument(
                records =
                    operation.records.map { record ->
                        RecordDocument(
                            from = record.move.from.name,
                            to = record.move.to.name,
                            promotion = record.move.promotion?.name,
                            positionBefore = GameStateDocument.of(record.positionBefore),
                        )
                    },
                stateAfter = GameStateDocument.of(operation.stateAfter),
            )
    }
}
