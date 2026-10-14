@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.game

import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.DrawClaim
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.server.db.ChangeKind
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.StaleGameVersionException
import com.jmussel.chessgame.server.db.StoredGame
import com.jmussel.chessgame.server.db.UndoAnswer
import com.jmussel.chessgame.server.db.UndoAnswerKind
import com.jmussel.chessgame.server.db.UndoRecord
import com.jmussel.chessgame.server.db.UserRepository
import com.jmussel.chessgame.server.series.SeriesService
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** What the server did with a command, and why if it did nothing. */
sealed interface CommandResult {
    /** The command was accepted; [game] is the canonical state it produced. */
    data class Applied(
        val game: StoredGame,
    ) : CommandResult

    /** No game with that id. */
    data object NoSuchGame : CommandResult

    /** The caller is not one of the two players. */
    data object NotAParticipant : CommandResult

    /** The game has already finished. */
    data class GameOver(
        val game: StoredGame,
    ) : CommandResult

    /** It is the other player's move. */
    data class NotYourTurn(
        val game: StoredGame,
    ) : CommandResult

    /**
     * The game has moved on since the caller read it, so the command was written against a
     * version that no longer exists (`D021`).
     */
    data class StaleVersion(
        val game: StoredGame,
    ) : CommandResult

    /** The move is not legal in this position. */
    data class IllegalMove(
        val game: StoredGame,
        val move: Move,
    ) : CommandResult

    /** That draw cannot be claimed in this position. */
    data class NoSuchClaim(
        val game: StoredGame,
        val claim: DrawClaim,
    ) : CommandResult

    /** This player has no move to take back right now (`D016`). */
    data class NothingToUndo(
        val game: StoredGame,
    ) : CommandResult

    /**
     * This player may not send an undo request now: there is no move of theirs answered by the
     * opponent, or they already have one pending (`D091`).
     */
    data class CannotRequestUndo(
        val game: StoredGame,
    ) : CommandResult

    /** No undo request waits on this player to answer it (`D091`). */
    data class NoUndoRequest(
        val game: StoredGame,
    ) : CommandResult
}

/**
 * The authoritative game commands.
 *
 * The client is untrusted (`D004`): it sends intent — "this player wants to play this move
 * on this version of this game" — and every question about whether that may happen is
 * answered here, from the canonical state, in this order:
 *
 * ```text
 * load game
 * → validate participant
 * → validate expected version
 * → validate the game is still running
 * → validate turn
 * → run chess-core
 * → persist in one transaction, incrementing the version
 * ```
 *
 * The version check is made twice on purpose: once here for a clear answer, and again as
 * part of the guarded write, which is what actually settles a race (`D021`).
 */
class GameCommandService(
    private val database: Database,
    private val games: GameRepository,
    private val series: SeriesService,
    private val users: UserRepository? = null,
) {
    /**
     * Records that this user's command was accepted (`M19.11`).
     *
     * Called from inside the command's transaction, after the guarded write has landed, so
     * `last_action_at` commits with the mutation it describes: a refused command, or one
     * that lost the version race, leaves no trace claiming otherwise. Only an *accepted
     * mutation* comes through here — `load` returns `Applied` too and is a read, which is
     * why this is not hung off `CommandResult.Applied`.
     *
     * [users] is optional so a test that only cares about game rules need not wire it.
     */
    private fun recordAction(userId: Uuid) {
        users?.touchLastActionInTransaction(userId)
    }

    /** Plays [move] in [gameId] on behalf of [userId]. */
    fun makeMove(
        userId: Uuid,
        gameId: Uuid,
        expectedVersion: Long,
        move: Move,
    ): CommandResult =
        transaction(database) {
            val stored = games.loadForUpdate(gameId) ?: return@transaction CommandResult.NoSuchGame

            sideOf(stored, userId)?.let { side ->
                validate(stored, side, expectedVersion)?.let { return@transaction it }

                if (!ChessRules.isLegal(stored.game, move)) {
                    return@transaction CommandResult.IllegalMove(stored, move)
                }

                val played = ChessRules.applyMove(stored.game, move)

                try {
                    games.save(
                        id = gameId,
                        expectedVersion = expectedVersion,
                        game = played,
                        auditEvent = MOVE_MADE,
                        actor = userId,
                        change = ChangeKind.MOVE,
                    )
                } catch (_: StaleGameVersionException) {
                    // Another command won the race between the read and the write.
                    return@transaction CommandResult.StaleVersion(reload(gameId, stored))
                }

                applied(userId, gameId, stored)
            } ?: CommandResult.NotAParticipant
        }

    /**
     * Claims a draw in [gameId] on behalf of [userId].
     *
     * Only the player to move may claim, as in standard chess: the claim is about the
     * position they are being asked to play from. The server decides whether the claim is
     * real — `chess-core` answers from the position's own history, not from anything the
     * client asserts (`D019`).
     */
    fun claimDraw(
        userId: Uuid,
        gameId: Uuid,
        expectedVersion: Long,
        claim: DrawClaim,
    ): CommandResult =
        transaction(database) {
            val stored = games.loadForUpdate(gameId) ?: return@transaction CommandResult.NoSuchGame

            sideOf(stored, userId)?.let { side ->
                validate(stored, side, expectedVersion)?.let { return@transaction it }

                if (!ChessRules.canClaimDraw(stored.game.state, claim)) {
                    return@transaction CommandResult.NoSuchClaim(stored, claim)
                }

                val claimed = ChessRules.claimDraw(stored.game, claim)

                try {
                    games.save(
                        id = gameId,
                        expectedVersion = expectedVersion,
                        game = claimed,
                        auditEvent = DRAW_CLAIMED,
                        actor = userId,
                        change = ChangeKind.DRAW_CLAIM,
                    )
                } catch (_: StaleGameVersionException) {
                    return@transaction CommandResult.StaleVersion(reload(gameId, stored))
                }

                applied(userId, gameId, stored)
            } ?: CommandResult.NotAParticipant
        }

    /**
     * Resigns [gameId] on behalf of [userId], losing the game.
     *
     * Either player may resign at any point in a game that is still running, whether or
     * not it is their turn — giving up is not a move. The confirmation belongs to the UI;
     * once this accepts it, it is final and cannot be undone (`D018`). The series then
     * carries on exactly as it does after any other completed game: the rematch if it is
     * active, the end of it if it was closing.
     */
    fun resign(
        userId: Uuid,
        gameId: Uuid,
        expectedVersion: Long,
    ): CommandResult =
        transaction(database) {
            val stored = games.loadForUpdate(gameId) ?: return@transaction CommandResult.NoSuchGame

            sideOf(stored, userId)?.let { side ->
                when {
                    stored.version != expectedVersion -> return@transaction CommandResult.StaleVersion(stored)
                    stored.game.isOver -> return@transaction CommandResult.GameOver(stored)
                }

                val resigned = ChessRules.resign(stored.game, side)

                try {
                    games.save(
                        id = gameId,
                        expectedVersion = expectedVersion,
                        game = resigned,
                        auditEvent = PLAYER_RESIGNED,
                        actor = userId,
                        change = ChangeKind.RESIGNATION,
                    )
                } catch (_: StaleGameVersionException) {
                    return@transaction CommandResult.StaleVersion(reload(gameId, stored))
                }

                applied(userId, gameId, stored)
            } ?: CommandResult.NotAParticipant
        }

    /**
     * Takes back [userId]'s latest move in [gameId].
     *
     * The rule is `D016` exactly, and `chess-core` is what applies it: only the player who
     * made the latest move may take it back, only while the opponent has not answered, and
     * never once the game has ended (`D017`). The undone move leaves the history, so the
     * previous move becomes takeable-back again by whoever made it.
     *
     * While the opponent's undo request waits on this player, their Undo is an accept
     * instead (`D091`): both moves go. So is an Undo sent from the version just before that
     * request, when the request is the only change since: the two crossed, and both players
     * wanted the reply undone.
     *
     * An undo is an accepted mutation like any other, so it increments the version
     * (`D021`) — a client holding the old version cannot then play into a position that no
     * longer exists.
     */
    fun undoMove(
        userId: Uuid,
        gameId: Uuid,
        expectedVersion: Long,
    ): CommandResult =
        transaction(database) {
            val stored = games.loadForUpdate(gameId) ?: return@transaction CommandResult.NoSuchGame

            sideOf(stored, userId)?.let { side ->
                when {
                    stored.version != expectedVersion ->
                        return@transaction if (crossedByRequest(stored, userId, expectedVersion)) {
                            accept(stored, userId, side, UndoAnswerKind.CROSSED_REQUEST_FIRST)
                        } else {
                            CommandResult.StaleVersion(stored)
                        }
                    stored.game.isOver -> return@transaction CommandResult.GameOver(stored)
                    stored.undo.pendingFrom == stored.opponentOf(userId) ->
                        return@transaction accept(stored, userId, side, UndoAnswerKind.ACCEPTED)
                    !ChessRules.canUndo(stored.game, side) ->
                        return@transaction CommandResult.NothingToUndo(stored)
                }

                val undone = ChessRules.undo(stored.game, side)

                try {
                    games.save(
                        id = gameId,
                        expectedVersion = expectedVersion,
                        game = undone,
                        auditEvent = MOVE_UNDONE,
                        actor = userId,
                        change = ChangeKind.UNDO,
                        undoneMove = stored.game.moves.last(),
                    )
                } catch (_: StaleGameVersionException) {
                    return@transaction CommandResult.StaleVersion(reload(gameId, stored))
                }

                recordAction(userId)
                CommandResult.Applied(reload(gameId, stored))
            } ?: CommandResult.NotAParticipant
        }

    /**
     * Sends [userId]'s undo request in [gameId]: asks the opponent to undo [userId]'s move
     * and the reply it got (`D091`).
     *
     * Only on [userId]'s turn, with the opponent's reply the latest move and [userId]'s move
     * before it, and with no request of theirs already pending ([mayRequestUndo]).
     *
     * A request sent from the version just before the opponent undid that reply, when the
     * undo is the only change since, crossed it. It is completed at once: [userId]'s move,
     * now the latest, is undone, which [userId] could have done alone (`D016`).
     */
    fun requestUndo(
        userId: Uuid,
        gameId: Uuid,
        expectedVersion: Long,
    ): CommandResult =
        transaction(database) {
            val stored = games.loadForUpdate(gameId) ?: return@transaction CommandResult.NoSuchGame

            sideOf(stored, userId)?.let { side ->
                if (stored.version != expectedVersion) {
                    return@transaction if (crossedByUndo(stored, userId, side, expectedVersion)) {
                        completeCrossedRequest(stored, userId, side)
                    } else {
                        CommandResult.StaleVersion(stored)
                    }
                }
                validate(stored, side, expectedVersion)?.let { return@transaction it }

                if (!mayRequestUndo(stored, side)) return@transaction CommandResult.CannotRequestUndo(stored)

                try {
                    games.save(
                        id = gameId,
                        expectedVersion = expectedVersion,
                        game = stored.game,
                        auditEvent = UNDO_REQUESTED,
                        actor = userId,
                        change = ChangeKind.UNDO_REQUEST,
                        undo = UndoRecord(pendingFrom = userId),
                    )
                } catch (_: StaleGameVersionException) {
                    return@transaction CommandResult.StaleVersion(reload(gameId, stored))
                }

                recordAction(userId)
                CommandResult.Applied(reload(gameId, stored))
            } ?: CommandResult.NotAParticipant
        }

    /**
     * Accepts the undo request waiting on [userId] in [gameId]: the opponent's move and
     * [userId]'s reply are both undone (`D091`). Only at the version the request was made at,
     * which is the current one while it waits, since any other change clears it.
     */
    fun acceptUndo(
        userId: Uuid,
        gameId: Uuid,
        expectedVersion: Long,
    ): CommandResult =
        answerUndo(userId, gameId, expectedVersion) { stored, side ->
            accept(stored, userId, side, UndoAnswerKind.ACCEPTED)
        }

    /**
     * Declines the undo request waiting on [userId] in [gameId] (`D091`). Both moves stand, and
     * the requester may ask again at once.
     */
    fun declineUndo(
        userId: Uuid,
        gameId: Uuid,
        expectedVersion: Long,
    ): CommandResult =
        answerUndo(userId, gameId, expectedVersion) { stored, _ ->
            val answer = answerOf(stored, stored.opponentOf(userId), UndoAnswerKind.DECLINED)

            try {
                games.save(
                    id = stored.id,
                    expectedVersion = stored.version,
                    game = stored.game,
                    auditEvent = UNDO_DECLINED,
                    actor = userId,
                    change = ChangeKind.UNDO_DECLINE,
                    undo = UndoRecord(answer = answer),
                )
            } catch (_: StaleGameVersionException) {
                return@answerUndo CommandResult.StaleVersion(reload(stored.id, stored))
            }

            recordAction(userId)
            CommandResult.Applied(reload(stored.id, stored))
        }

    /** The checks Accept and Decline share, then [answer] inside the same locked transaction. */
    private fun answerUndo(
        userId: Uuid,
        gameId: Uuid,
        expectedVersion: Long,
        answer: (StoredGame, Side) -> CommandResult,
    ): CommandResult =
        transaction(database) {
            val stored = games.loadForUpdate(gameId) ?: return@transaction CommandResult.NoSuchGame

            sideOf(stored, userId)?.let { side ->
                when {
                    stored.version != expectedVersion -> CommandResult.StaleVersion(stored)
                    stored.game.isOver -> CommandResult.GameOver(stored)
                    stored.undo.pendingFrom != stored.opponentOf(userId) -> CommandResult.NoUndoRequest(stored)
                    else -> answer(stored, side)
                }
            } ?: CommandResult.NotAParticipant
        }

    /**
     * Undoes the requester's move and [userId]'s reply to it, restoring the position before
     * the requester's move with the requester to move. Each undo restores recorded state, so
     * nothing is replayed (`D061`).
     */
    private fun accept(
        stored: StoredGame,
        userId: Uuid,
        side: Side,
        kind: UndoAnswerKind,
    ): CommandResult {
        val answer = answerOf(stored, stored.opponentOf(userId), kind)
        val undone = ChessRules.undo(ChessRules.undo(stored.game, side), side.opposite)

        try {
            games.save(
                id = stored.id,
                expectedVersion = stored.version,
                game = undone,
                auditEvent = UNDO_ACCEPTED,
                actor = userId,
                change = ChangeKind.UNDO_ACCEPT,
                undo = UndoRecord(answer = answer),
                crossing = kind == UndoAnswerKind.CROSSED_REQUEST_FIRST,
            )
        } catch (_: StaleGameVersionException) {
            return CommandResult.StaleVersion(reload(stored.id, stored))
        }

        recordAction(userId)
        return CommandResult.Applied(reload(stored.id, stored))
    }

    /** Undoes [userId]'s move once the opponent's undo of the reply has crossed their request. */
    private fun completeCrossedRequest(
        stored: StoredGame,
        userId: Uuid,
        side: Side,
    ): CommandResult {
        val answer =
            UndoAnswer(
                kind = UndoAnswerKind.CROSSED_UNDO_FIRST,
                requester = userId,
                requesterMove = stored.game.moves.last(),
                replyMove = requireNotNull(stored.lastChange?.undoneMove) { "A crossing undo names its move" },
            )

        try {
            games.save(
                id = stored.id,
                expectedVersion = stored.version,
                game = ChessRules.undo(stored.game, side),
                auditEvent = UNDO_REQUESTED,
                actor = userId,
                change = ChangeKind.UNDO_REQUEST,
                undo = UndoRecord(answer = answer),
                crossing = true,
            )
        } catch (_: StaleGameVersionException) {
            return CommandResult.StaleVersion(reload(stored.id, stored))
        }

        recordAction(userId)
        return CommandResult.Applied(reload(stored.id, stored))
    }

    /**
     * Whether an undo sent by [userId] from [expectedVersion] crossed the opponent's undo
     * request: the request was applied to exactly that version, it is the only change since,
     * and it is still pending (`D091`).
     */
    private fun crossedByRequest(
        stored: StoredGame,
        userId: Uuid,
        expectedVersion: Long,
    ): Boolean {
        val change = stored.lastChange ?: return false
        val requester = stored.opponentOf(userId)

        return change.kind == ChangeKind.UNDO_REQUEST &&
            change.actor == requester &&
            change.appliedTo == expectedVersion &&
            stored.version == expectedVersion + 1 &&
            stored.undo.pendingFrom == requester &&
            !stored.game.isOver
    }

    /**
     * Whether an undo request sent by [userId] from [expectedVersion] crossed the opponent's
     * undo of their reply: that undo was applied to exactly that version and is the only
     * change since, and the move it uncovered is [userId]'s to undo (`D091`).
     */
    private fun crossedByUndo(
        stored: StoredGame,
        userId: Uuid,
        side: Side,
        expectedVersion: Long,
    ): Boolean {
        val change = stored.lastChange ?: return false

        return change.kind == ChangeKind.UNDO &&
            change.actor == stored.opponentOf(userId) &&
            change.appliedTo == expectedVersion &&
            stored.version == expectedVersion + 1 &&
            change.undoneMove != null &&
            ChessRules.canUndo(stored.game, side)
    }

    /** The answer to the request pending in [stored], which is about its two latest moves. */
    private fun answerOf(
        stored: StoredGame,
        requester: Uuid,
        kind: UndoAnswerKind,
    ): UndoAnswer {
        val moves = stored.game.moves

        return UndoAnswer(
            kind = kind,
            requester = requester,
            requesterMove = moves[moves.size - 2],
            replyMove = moves.last(),
        )
    }

    /** The canonical game as [userId] may see it, or why they may not. */
    fun load(
        userId: Uuid,
        gameId: Uuid,
    ): CommandResult {
        val stored = games.load(gameId) ?: return CommandResult.NoSuchGame
        sideOf(stored, userId) ?: return CommandResult.NotAParticipant
        return CommandResult.Applied(stored)
    }

    /**
     * The accepted command's result, once everything that follows from it has happened.
     *
     * A move or a claim that ended the game hands the series its rematch before answering
     * (`D015`), inside this command's transaction: the finished game, its result, and the
     * game that follows it are one commit, so no client can see a series whose game is
     * over and whose next game does not exist yet.
     *
     * It is also where the actor's `last_action_at` is recorded (`M19.11`), because this is
     * the one place that means "a mutation from this user was accepted" — [load] answers
     * `Applied` as well and is a read. [undoMove] and the undo-request commands do not come
     * through here, because an undo can never finish a game, so they record their own.
     */
    private fun applied(
        userId: Uuid,
        gameId: Uuid,
        fallback: StoredGame,
    ): CommandResult {
        recordAction(userId)

        val saved = reload(gameId, fallback)

        if (saved.isComplete) series.settleAfter(saved, actor = userId)

        return CommandResult.Applied(saved)
    }

    /** Which side [userId] plays in [game], or `null` when they are not in it. */
    private fun sideOf(
        game: StoredGame,
        userId: Uuid,
    ): Side? =
        when (userId) {
            game.whiteUserId -> Side.WHITE
            game.blackUserId -> Side.BLACK
            else -> null
        }

    /**
     * The first reason this command cannot proceed, or `null` when it may.
     *
     * The version comes first because it is the caller's claim about *which* state they
     * acted on: if that is wrong, everything else they believe about the game — whose turn
     * it is, whether it is still running — may be wrong too, and "refresh" is the only
     * useful answer. Once the version agrees, a refusal describes the game itself.
     */
    private fun validate(
        stored: StoredGame,
        side: Side,
        expectedVersion: Long,
    ): CommandResult? =
        when {
            stored.version != expectedVersion -> CommandResult.StaleVersion(stored)
            stored.game.isOver -> CommandResult.GameOver(stored)
            stored.game.sideToMove != side -> CommandResult.NotYourTurn(stored)
            else -> null
        }

    private fun reload(
        gameId: Uuid,
        fallback: StoredGame,
    ): StoredGame = games.load(gameId) ?: fallback

    companion object {
        /** The audit event a played move records (`ARCHITECTURE.md` §9). */
        const val MOVE_MADE: String = "MoveMade"

        /** The audit event a claimed draw records. */
        const val DRAW_CLAIMED: String = "DrawClaimed"

        /** The audit event a take-back records. */
        const val MOVE_UNDONE: String = "MoveUndone"

        /** The audit event a resignation records. */
        const val PLAYER_RESIGNED: String = "PlayerResigned"

        /** The audit event an undo request records (`D091`). */
        const val UNDO_REQUESTED: String = "UndoRequested"

        /** The audit event an accepted undo request records, by Accept or by Undo. */
        const val UNDO_ACCEPTED: String = "UndoAccepted"

        /** The audit event a declined undo request records. */
        const val UNDO_DECLINED: String = "UndoDeclined"

        /**
         * Whether the player on [side] may send an undo request in [stored] (`D091`): the game
         * is running, it is their turn, the opponent's reply is the latest move with theirs
         * before it, and they have no request pending.
         */
        fun mayRequestUndo(
            stored: StoredGame,
            side: Side,
        ): Boolean =
            !stored.game.isOver &&
                stored.game.sideToMove == side &&
                stored.game.history.size >= 2 &&
                stored.undo.pendingFrom == null
    }
}
