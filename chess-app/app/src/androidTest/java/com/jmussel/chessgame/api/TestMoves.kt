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
        val san = ChessRules.san(game.state, move)
        game = ChessRules.applyMove(game, move)
        MoveDto(from = move.from.name, to = move.to.name, promotion = move.promotion?.name, san = san)
    }
}
