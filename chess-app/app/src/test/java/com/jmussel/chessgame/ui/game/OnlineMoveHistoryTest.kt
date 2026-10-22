package com.jmussel.chessgame.ui.game

import com.jmussel.chessgame.api.GameViewDto
import com.jmussel.chessgame.api.MoveDto
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.api.playedMoves
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The live online move list (`D105`, `M23.2`): the server's SAN for each active move, the last
 * one current, whatever the board says. A refresh or realtime update is a new answer from the
 * server, and the list follows it.
 */
class OnlineMoveHistoryTest {
    private fun game(
        moves: List<MoveDto>,
        version: Long = 1,
    ) = GameViewDto(
        gameId = "game-1",
        seriesId = "series-1",
        opponent = UserSummaryDto(userId = "user-1", username = "Alex"),
        version = version,
        yourSide = "WHITE",
        sideToMove = if (moves.size % 2 == 0) "WHITE" else "BLACK",
        // The starting board, deliberately: the list never reads the position.
        board = listOf("rnbqkbnr", "pppppppp", "........", "........", "........", "........", "PPPPPPPP", "RNBQKBNR"),
        moves = moves,
        lastMove = moves.lastOrNull(),
    )

    private fun currentOf(game: GameViewDto): String? = OnlineGame.moveHistoryFor(game).current?.san

    @Test
    fun noMovesNoCurrentMove() {
        assertNull(currentOf(game(emptyList())))
    }

    @Test
    fun theLastServerMoveIsCurrentInTheServersOwnSan() {
        // The server's words are used as they are, even ones the board could not produce.
        val moves = listOf(MoveDto("e2", "e4", san = "e4"), MoveDto("e7", "e5", san = "e5"), MoveDto("g1", "f3", san = "Nf3"))
        val history = OnlineGame.moveHistoryFor(game(moves))

        assertEquals("Nf3", history.current?.san)
        assertEquals(listOf("1. e4 e5", "2. Nf3"), history.lines)
        assertEquals(1, history.entries.count { it.current })
    }

    @Test
    fun anOpponentMoveFromARefreshBecomesCurrent() {
        val mine = game(playedMoves("e2e4"), version = 2)
        val refreshed = game(playedMoves("e2e4", "c7c5"), version = 3)

        assertEquals("e4", currentOf(mine))
        assertEquals("c5", currentOf(refreshed))
    }

    @Test
    fun aRefreshThatChangesNothingKeepsTheSameCurrentMove() {
        val before = game(playedMoves("e2e4", "c7c5"), version = 3)
        val after = before.copy(version = 4, canOfferDraw = true)

        assertEquals(OnlineGame.moveHistoryFor(before), OnlineGame.moveHistoryFor(after))
    }

    @Test
    fun undoRedoAndAMatchingContinuationFollowTheServersActiveMoves() {
        val played = playedMoves("e2e4", "c7c5", "g1f3")

        assertEquals("c5", currentOf(game(played.dropLast(1), version = 5)))
        assertEquals("Nf3", currentOf(game(played, version = 6)))
        assertEquals("e4", currentOf(game(played.take(1), version = 7)))
    }
}
