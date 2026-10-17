package com.jmussel.chessgame.api

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.PieceType

/**
 * [coordinates] played from the start, as the server's `MoveView`s: each with its coordinates
 * and the SAN the real server derives from its recorded position (`D101`). Worked out here,
 * in the test, standing in for the server.
 */
fun playedMoves(vararg coordinates: String): List<MoveDto> {
    var game = ChessGame.newGame()

    return coordinates.map { text ->
        val move = Move.of(text.substring(0, 2), text.substring(2, 4), text.getOrNull(4)?.let(PieceType::fromLetter))
        // A stub that lists a move out of order (some tests only need the list to change) gets
        // its coordinates as its text, since no position names it.
        val san =
            if (ChessRules.isLegal(game.state, move)) {
                ChessRules.san(game.state, move).also { game = ChessRules.applyMove(game, move) }
            } else {
                text
            }
        MoveDto(from = move.from.name, to = move.to.name, promotion = move.promotion?.name, san = san)
    }
}

/** [coordinates] from the start, as a stubbed server's `moves` JSON array. */
fun movesJson(coordinates: List<String>): String =
    playedMoves(*coordinates.toTypedArray()).joinToString(",", "[", "]") { move ->
        val promotion = move.promotion?.let { ""","promotion":"$it"""" }.orEmpty()
        """{"from":"${move.from}","to":"${move.to}"$promotion,"san":"${move.san}"}"""
    }
