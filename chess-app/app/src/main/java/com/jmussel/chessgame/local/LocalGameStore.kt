package com.jmussel.chessgame.local

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.GameOutcome
import com.jmussel.chessgame.core.chess.GameResult
import com.jmussel.chessgame.core.chess.GameState
import com.jmussel.chessgame.core.chess.MoveRecord
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.TerminationReason
import com.jmussel.chessgame.local.db.LocalGameDatabase
import com.jmussel.chessgame.local.db.Local_games

/** The two kinds of local game (`D084`). */
enum class LocalGameKind {
    PASS_AND_PLAY,
    COMPUTER,
}

/**
 * The computer's seat in a game against it (`D086`): which side the human plays, and the
 * computer's difficulty level.
 */
data class ComputerOpponent(
    val humanSide: Side,
    val difficulty: Int,
)

/** A stored local game: what identifies it, and the game itself, rebuilt from the store. */
data class StoredLocalGame(
    val id: Long,
    /** `null` for pass-and-play. */
    val computer: ComputerOpponent?,
    /** Milliseconds since the epoch. */
    val createdAt: Long,
    /** Milliseconds since the epoch, or `null` while the game is unfinished. */
    val completedAt: Long?,
    val game: ChessGame,
) {
    val kind: LocalGameKind
        get() = if (computer == null) LocalGameKind.PASS_AND_PLAY else LocalGameKind.COMPUTER

    val isActive: Boolean
        get() = completedAt == null
}

/** A finished local game as listed in local history, without its moves. */
data class LocalGameSummary(
    val id: Long,
    val computer: ComputerOpponent?,
    val createdAt: Long,
    val completedAt: Long,
    val result: GameResult,
) {
    val kind: LocalGameKind
        get() = if (computer == null) LocalGameKind.PASS_AND_PLAY else LocalGameKind.COMPUTER
}

/**
 * The canonical store for games whose players are all on this device: pass-and-play and
 * games against the computer (`D084`). Nothing here goes near the server.
 *
 * - **One unfinished game of each kind** (`D090`). At most one pass-and-play game and one
 *   game against the computer are `ACTIVE`, side by side. [startGame] replaces only the
 *   unfinished game of its own kind, deleting it outright, so a replaced game is never kept
 *   in history; the other kind's game is untouched. A unique partial index on the kind
 *   enforces the same rule in the database.
 * - **Finished games are kept**, newest first ([completedGames]).
 * - **Writes append and truncate.** A move appends one record and updates the state; a
 *   takeback removes records from the end and restores the position recorded before the
 *   first of them. The history is never rewritten.
 * - **Each change is one transaction**, so a committed database never holds a board that
 *   disagrees with its move history.
 * - **Restoring never replays a move** (`D029`, `D061`): the stored state and the stored
 *   records, each with its recorded prior position, are the `ChessGame`.
 *
 * Every call does blocking database work, so call it off the main thread. Times come from
 * [now], in milliseconds since the epoch.
 */
class LocalGameStore(
    database: LocalGameDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val games = database.localGamesQueries
    private val moves = database.localMovesQueries

    /** The unfinished local game of [kind], or `null` when there is none. */
    fun activeGame(kind: LocalGameKind): StoredLocalGame? =
        games.transactionWithResult { games.activeOfKind(kind.name).executeAsOneOrNull()?.let(::restore) }

    /** The local game with [id], finished or not, or `null` when there is none. */
    fun game(id: Long): StoredLocalGame? = games.transactionWithResult { games.byId(id).executeAsOneOrNull()?.let(::restore) }

    /** Every finished local game, newest first. */
    fun completedGames(): List<LocalGameSummary> =
        games.completed().executeAsList().map { row ->
            LocalGameSummary(
                id = row.id,
                computer = computerOf(row.human_side, row.difficulty),
                createdAt = row.created_at,
                completedAt = checkNotNull(row.completed_at),
                result =
                    GameResult(
                        GameOutcome.valueOf(checkNotNull(row.outcome)),
                        TerminationReason.valueOf(checkNotNull(row.termination_reason)),
                    ),
            )
        }

    /**
     * Starts a new local game from the standard position, against [computer] or, when it is
     * `null`, pass-and-play.
     *
     * The unfinished game of the same kind is deleted first, with its moves, in the same
     * transaction: there is only ever one of each kind, and a replaced game is not kept
     * (`D084`, `D090`). The other kind's unfinished game is left as it is. Asking the player
     * before replacing a game is the caller's job.
     */
    fun startGame(computer: ComputerOpponent? = null): StoredLocalGame =
        games.transactionWithResult {
            val kind = if (computer == null) LocalGameKind.PASS_AND_PLAY else LocalGameKind.COMPUTER
            moves.deleteForActiveOfKind(kind.name)
            games.deleteActiveOfKind(kind.name)

            val game = ChessGame.newGame()
            val createdAt = now()
            games.insert(
                kind = kind.name,
                createdAt = createdAt,
                state = LocalStateDocument.encode(game.state),
                humanSide = computer?.humanSide?.name,
                difficulty = computer?.difficulty?.toLong(),
            )
            StoredLocalGame(
                id = games.lastInsertId().executeAsOne(),
                computer = computer,
                createdAt = createdAt,
                completedAt = null,
                game = game,
            )
        }

    /**
     * Saves the move [game] has just played in the unfinished game [id]: its latest record is
     * appended and its state becomes the game's.
     *
     * [game] must be the stored game plus exactly one move. A move that ends the game
     * completes it, in the same transaction.
     */
    fun recordMove(
        id: Long,
        game: ChessGame,
    ) {
        val record = requireNotNull(game.history.lastOrNull()) { "A game with no moves has no move to record" }
        games.transaction {
            val stored = LocalStateDocument.decode(activeRow(id).state)
            val plies = moves.count(id).executeAsOne()
            require(game.history.size.toLong() == plies + 1) {
                "Game $id has $plies moves stored; recording one more cannot give ${game.history.size}"
            }
            require(record.positionBefore == stored) { "The move was not played from game $id's stored position" }

            moves.append(
                gameId = id,
                ply = plies + 1,
                move = record.move.toString(),
                positionBefore = LocalStateDocument.encode(record.positionBefore),
            )
            writeState(id, game.state)
        }
    }

    /**
     * Saves the end of the unfinished game [id] by resignation or a draw claim: [game] is the
     * stored game with a result and no new move. The game is completed and kept.
     */
    fun recordResult(
        id: Long,
        game: ChessGame,
    ) {
        require(game.isOver) { "A game that has not ended has no result to record" }
        games.transaction {
            val stored = LocalStateDocument.decode(activeRow(id).state)
            val plies = moves.count(id).executeAsOne()
            require(game.history.size.toLong() == plies) { "Game $id has $plies moves stored, not ${game.history.size}" }
            require(game.state.copy(result = null) == stored) { "The result was not reached from game $id's stored position" }

            writeState(id, game.state)
        }
    }

    /**
     * Takes back the last [plies] moves of the unfinished game [id] and returns the game as
     * it now is.
     *
     * The records are removed from the end, and the state becomes the position recorded
     * before the first of them, in one transaction. One ply is an ordinary undo; two are a
     * takeback against the computer (`D086`). Whether the player may take the moves back is
     * the caller's rule; a finished game's moves are final here too.
     */
    fun takeBack(
        id: Long,
        plies: Int = 1,
    ): ChessGame =
        games.transactionWithResult {
            activeRow(id)
            val stored = moves.count(id).executeAsOne()
            require(plies >= 1 && plies <= stored) { "Game $id has $stored moves; cannot take back $plies" }
            val keep = stored - plies

            val restored = moves.firstAfter(id, keep).executeAsOne()
            moves.truncate(id, keep)
            check(games.updateState(state = restored, id = id).value == 1L) { "Game $id is no longer unfinished" }

            restore(games.byId(id).executeAsOne()).game
        }

    private fun activeRow(id: Long): Local_games {
        val row = requireNotNull(games.byId(id).executeAsOneOrNull()) { "There is no local game $id" }
        require(row.status == ACTIVE) { "Local game $id has ended; its moves are final" }
        return row
    }

    private fun writeState(
        id: Long,
        state: GameState,
    ) {
        val result = state.result
        val updated =
            if (result == null) {
                games.updateState(state = LocalStateDocument.encode(state), id = id)
            } else {
                games.complete(
                    state = LocalStateDocument.encode(state),
                    completedAt = now(),
                    outcome = result.outcome.name,
                    terminationReason = result.reason.name,
                    id = id,
                )
            }
        check(updated.value == 1L) { "Game $id is no longer unfinished" }
    }

    private fun restore(row: Local_games): StoredLocalGame =
        StoredLocalGame(
            id = row.id,
            computer = computerOf(row.human_side, row.difficulty),
            createdAt = row.created_at,
            completedAt = row.completed_at,
            game =
                ChessGame(
                    state = LocalStateDocument.decode(row.state),
                    history =
                        moves.forGame(row.id).executeAsList().map {
                            MoveRecord(moveFromCoordinates(it.move), LocalStateDocument.decode(it.position_before))
                        },
                ),
        )

    private fun computerOf(
        humanSide: String?,
        difficulty: Long?,
    ): ComputerOpponent? =
        if (humanSide == null || difficulty == null) null else ComputerOpponent(Side.valueOf(humanSide), difficulty.toInt())

    private companion object {
        const val ACTIVE = "ACTIVE"
    }
}
