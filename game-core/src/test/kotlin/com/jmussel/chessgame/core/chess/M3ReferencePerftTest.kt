package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Published move-generation reference positions retained from the M3 evaluation.
 * Oracle: https://github.com/freeeve/pgn/blob/v3/perft_test.go
 */
class M3ReferencePerftTest {
    @Test
    fun kiwipeteMatchesThroughDepthThree() {
        assertPerft(
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            48,
            2_039,
            97_862,
        )
    }

    @Test
    fun rookAndPawnEndgameMatchesThroughDepthFour() {
        assertPerft(
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
            14,
            191,
            2_812,
            43_238,
        )
    }

    @Test
    fun promotionAndDiscoveredCheckPositionMatchesThroughDepthThree() {
        assertPerft(
            "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
            6,
            264,
            9_467,
        )
    }

    @Test
    fun promotionWithCheckPositionMatchesThroughDepthThree() {
        assertPerft(
            "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
            44,
            1_486,
            62_379,
        )
    }

    @Test
    fun tacticalPinPositionMatchesThroughDepthThree() {
        assertPerft(
            "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10",
            46,
            2_079,
            89_890,
        )
    }

    @Test
    fun enPassantCheckEvasionPositionMatchesThroughDepthTwo() {
        assertPerft(
            "8/8/8/8/k2Pp2Q/8/8/3K4 b - d3 0 1",
            6,
            136,
        )
    }

    @Test
    fun allPromotionChoicesPositionMatchesThroughDepthTwo() {
        assertPerft(
            "n1n5/PPPk4/8/8/8/8/4Kppp/5N1N b - - 0 1",
            24,
            496,
        )
    }

    @Test
    fun openCastlingPositionMatchesThroughDepthThree() {
        assertPerft(
            "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1",
            26,
            568,
            13_744,
        )
    }

    private fun assertPerft(
        fen: String,
        vararg expectedByDepth: Long,
    ) {
        val position = stateFromFen(fen)
        expectedByDepth.forEachIndexed { index, expected ->
            assertEquals(expected, perft(position, index + 1), "$fen at depth ${index + 1}")
        }
    }

    private fun perft(
        state: GameState,
        depth: Int,
    ): Long {
        if (depth == 0) return 1
        return ChessRules.legalMoves(state).sumOf { move -> perft(ChessRules.applyMove(state, move), depth - 1) }
    }

    private fun stateFromFen(fen: String): GameState {
        val fields = fen.split(' ')
        require(fields.size == 6) { "Expected six FEN fields: $fen" }

        val placement =
            buildMap {
                fields[0].split('/').forEachIndexed { row, rankText ->
                    var file = 0
                    rankText.forEach { symbol ->
                        if (symbol.isDigit()) {
                            file += symbol.digitToInt()
                        } else {
                            val side = if (symbol.isUpperCase()) Side.WHITE else Side.BLACK
                            put(Square.of(file, 7 - row), Piece(side, PieceType.fromLetter(symbol)))
                            file += 1
                        }
                    }
                    require(file == Square.FILES) { "Invalid FEN rank: $rankText" }
                }
            }
        val rights =
            CastlingRights(
                whiteKingSide = 'K' in fields[2],
                whiteQueenSide = 'Q' in fields[2],
                blackKingSide = 'k' in fields[2],
                blackQueenSide = 'q' in fields[2],
            )

        return Repetition.recording(
            GameState(
                board = Board.of(placement),
                sideToMove = if (fields[1] == "w") Side.WHITE else Side.BLACK,
                castlingRights = rights,
                enPassantTarget = fields[3].takeUnless { it == "-" }?.let(Square::parse),
                drawRuleState = DrawRuleState(halfmoveClock = fields[4].toInt()),
                fullmoveNumber = fields[5].toInt(),
            ),
        )
    }
}
