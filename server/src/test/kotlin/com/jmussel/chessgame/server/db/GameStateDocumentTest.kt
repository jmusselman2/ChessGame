package com.jmussel.chessgame.server.db

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.Move
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Rebuilding a stored position, without a database.
 *
 * The draw-rule state is the interesting part: repetition counts cross the persistence
 * boundary as plain numbers, so reconstruction has to accept everything a real game
 * produces and reject counts no game could have produced.
 */
class GameStateDocumentTest {
    private fun repeatedPosition(): ChessGame {
        var game = ChessGame.newGame()
        listOf(
            Move.of("g1", "f3"),
            Move.of("g8", "f6"),
            Move.of("f3", "g1"),
            Move.of("f6", "g8"),
        ).forEach { game = ChessRules.applyMove(game, it) }
        return game
    }

    @Test
    fun aStoredRepetitionHistoryRebuildsExactly() {
        val state = repeatedPosition().state
        val rebuilt = GameStateDocument.of(state).toGameState()

        val counts = rebuilt.drawRuleState.positionCounts

        assertEquals(state, rebuilt)
        assertEquals(state.drawRuleState.positionCounts, counts)
        assertEquals(2, counts.values.max())
    }

    @Test
    fun aNewGameRebuildsExactly() {
        val state = ChessGame.newGame().state

        assertEquals(state, GameStateDocument.of(state).toGameState())
    }

    @Test
    fun anImpossibleStoredRepetitionCountIsRejected() {
        val document = GameStateDocument.of(repeatedPosition().state)

        listOf(0, -1).forEach { count ->
            val corrupted = document.copy(repetitions = document.repetitions.mapValues { count })

            assertFailsWith<IllegalArgumentException>("count $count") { corrupted.toGameState() }
        }
    }
}
