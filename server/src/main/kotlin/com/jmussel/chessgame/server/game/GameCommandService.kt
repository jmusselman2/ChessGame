@file:OptIn(ExperimentalUuidApi::class)

package com.jmussel.chessgame.server.game

import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.DrawClaim
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.server.db.GameRepository
import com.jmussel.chessgame.server.db.StaleGameVersionException
import com.jmussel.chessgame.server.db.StoredGame
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
 * → run game-core
 * → persist in one transaction, incrementing the version
 * ```
 *
 * The version check is made twice on purpose: once here for a clear answer, and again as
 * part of the guarded write, which is what actually settles a race (`D021`).
 */
class GameCommandService(
    private val database: Database,
    private val games: GameRepository,
) {
    /** Plays [move] in [gameId] on behalf of [userId]. */
    fun makeMove(
        userId: Uuid,
        gameId: Uuid,
        expectedVersion: Long,
        move: Move,
    ): CommandResult =
        transaction(database) {
            val stored = games.load(gameId) ?: return@transaction CommandResult.NoSuchGame

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
                    )
                } catch (_: StaleGameVersionException) {
                    // Another command won the race between the read and the write.
                    return@transaction CommandResult.StaleVersion(reload(gameId, stored))
                }

                CommandResult.Applied(reload(gameId, stored))
            } ?: CommandResult.NotAParticipant
        }

    /**
     * Claims a draw in [gameId] on behalf of [userId].
     *
     * Only the player to move may claim, as in standard chess: the claim is about the
     * position they are being asked to play from. The server decides whether the claim is
     * real — `game-core` answers from the position's own history, not from anything the
     * client asserts (`D019`).
     */
    fun claimDraw(
        userId: Uuid,
        gameId: Uuid,
        expectedVersion: Long,
        claim: DrawClaim,
    ): CommandResult =
        transaction(database) {
            val stored = games.load(gameId) ?: return@transaction CommandResult.NoSuchGame

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
                    )
                } catch (_: StaleGameVersionException) {
                    return@transaction CommandResult.StaleVersion(reload(gameId, stored))
                }

                CommandResult.Applied(reload(gameId, stored))
            } ?: CommandResult.NotAParticipant
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

    private companion object {
        /** The audit event a played move records (`ARCHITECTURE.md` §9). */
        const val MOVE_MADE = "MoveMade"

        /** The audit event a claimed draw records. */
        const val DRAW_CLAIMED = "DrawClaimed"
    }
}
