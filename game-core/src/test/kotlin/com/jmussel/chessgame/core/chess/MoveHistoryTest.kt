package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoveHistoryTest {
    private fun white(type: PieceType) = Piece(Side.WHITE, type)

    private fun black(type: PieceType) = Piece(Side.BLACK, type)

    private fun game(
        vararg placement: Pair<String, Piece>,
        sideToMove: Side = Side.WHITE,
        rights: CastlingRights = CastlingRights.NONE,
        halfmoveClock: Int = 0,
        enPassantTarget: String? = null,
    ): ChessGame =
        ChessGame(
            GameState(
                board = Board.of(placement.associate { (square, piece) -> Square.parse(square) to piece }),
                sideToMove = sideToMove,
                castlingRights = rights,
                enPassantTarget = enPassantTarget?.let { Square.parse(it) },
                drawRuleState = DrawRuleState(halfmoveClock = halfmoveClock),
            ),
        )

    /** Plays [move] and takes it straight back; the game must be exactly as it was. */
    private fun assertRoundTrips(
        before: ChessGame,
        move: Move,
    ) {
        val after = ChessRules.applyMove(before, move)
        val restored = ChessRules.undoLastMove(after)

        assertEquals(before.state, restored.state, "restoring $move")
        assertEquals(before.history, restored.history, "history after restoring $move")
        assertEquals(before, restored)
    }

    @Test
    fun aNewGameHasNoHistory() {
        val fresh = ChessGame.newGame()

        assertTrue(fresh.history.isEmpty())
        assertTrue(fresh.moves.isEmpty())
        assertNull(fresh.lastMove)
        assertNull(fresh.lastMover)
        assertEquals(StandardPosition.newGame(), fresh.state)
    }

    @Test
    fun eachMoveIsRecordedWithThePositionItWasPlayedFrom() {
        val fresh = ChessGame.newGame()
        val after = ChessRules.applyMove(fresh, Move.of("e2", "e4"))

        assertEquals(1, after.history.size)
        assertEquals(Move.of("e2", "e4"), after.lastMove)
        assertEquals(Side.WHITE, after.lastMover)
        assertEquals(fresh.state, after.history.single().positionBefore)
    }

    @Test
    fun theHistoryKeepsMovesInOrder() {
        var position = ChessGame.newGame()
        val played = listOf(Move.of("e2", "e4"), Move.of("e7", "e5"), Move.of("g1", "f3"))
        played.forEach { position = ChessRules.applyMove(position, it) }

        assertEquals(played, position.moves)
        assertEquals(Side.WHITE, position.lastMover)
    }

    @Test
    fun aQuietMoveRoundTrips() {
        assertRoundTrips(
            game("e1" to white(PieceType.KING), "d1" to white(PieceType.ROOK), "h8" to black(PieceType.KING)),
            Move.of("d1", "d5"),
        )
    }

    @Test
    fun aCaptureRoundTrips() {
        assertRoundTrips(
            game(
                "e1" to white(PieceType.KING),
                "d1" to white(PieceType.ROOK),
                "d7" to black(PieceType.KNIGHT),
                "h8" to black(PieceType.KING),
                halfmoveClock = 9,
            ),
            Move.of("d1", "d7"),
        )
    }

    @Test
    fun aDoublePawnAdvanceRoundTripsIncludingTheEnPassantTarget() {
        val before =
            game(
                "e1" to white(PieceType.KING),
                "e2" to white(PieceType.PAWN),
                "d4" to black(PieceType.PAWN),
                "h8" to black(PieceType.KING),
            )
        val after = ChessRules.applyMove(before, Move.of("e2", "e4"))

        assertEquals(Square.parse("e3"), after.state.enPassantTarget)
        assertRoundTrips(before, Move.of("e2", "e4"))
    }

    @Test
    fun anEnPassantCaptureRoundTrips() {
        val before =
            game(
                "e1" to white(PieceType.KING),
                "d5" to white(PieceType.PAWN),
                "e5" to black(PieceType.PAWN),
                "h8" to black(PieceType.KING),
                enPassantTarget = "e6",
            )

        assertRoundTrips(before, Move.of("d5", "e6"))
        assertEquals(
            black(PieceType.PAWN),
            ChessRules
                .undoLastMove(ChessRules.applyMove(before, Move.of("d5", "e6")))
                .state.board
                .pieceAt(Square.parse("e5")),
            "the captured pawn comes back",
        )
    }

    @Test
    fun castlingRoundTripsIncludingTheRookAndTheRights() {
        val before =
            game(
                "e1" to white(PieceType.KING),
                "h1" to white(PieceType.ROOK),
                "e8" to black(PieceType.KING),
                rights = CastlingRights.ALL,
            )
        val after = ChessRules.applyMove(before, Move.of("e1", "g1"))
        val restored = ChessRules.undoLastMove(after)

        assertFalse(after.state.castlingRights.hasAny(Side.WHITE))
        assertEquals(white(PieceType.ROOK), restored.state.board.pieceAt(Square.parse("h1")))
        assertEquals(white(PieceType.KING), restored.state.board.pieceAt(Square.parse("e1")))
        assertTrue(restored.state.castlingRights.has(Side.WHITE, CastlingSide.KING_SIDE))
        assertRoundTrips(before, Move.of("e1", "g1"))
    }

    @Test
    fun aPromotionRoundTrips() {
        val before =
            game(
                "e1" to white(PieceType.KING),
                "a7" to white(PieceType.PAWN),
                "h8" to black(PieceType.KING),
            )
        val restored = ChessRules.undoLastMove(ChessRules.applyMove(before, Move.of("a7", "a8", PieceType.QUEEN)))

        assertEquals(white(PieceType.PAWN), restored.state.board.pieceAt(Square.parse("a7")))
        assertRoundTrips(before, Move.of("a7", "a8", PieceType.QUEEN))
    }

    @Test
    fun theCountersAndRepetitionHistoryComeBack() {
        var position = ChessGame.newGame()
        listOf(
            Move.of("g1", "f3"),
            Move.of("g8", "f6"),
            Move.of("f3", "g1"),
        ).forEach { position = ChessRules.applyMove(position, it) }

        val before = position
        val after = ChessRules.applyMove(before, Move.of("f6", "g8"))
        val restored = ChessRules.undoLastMove(after)

        assertEquals(2, Repetition.occurrences(after.state))
        assertEquals(before.state.drawRuleState, restored.state.drawRuleState)
        assertEquals(before.state.halfmoveClock, restored.state.halfmoveClock)
        assertEquals(before.state.fullmoveNumber, restored.state.fullmoveNumber)
        assertEquals(before, restored)
    }

    @Test
    fun anIrreversibleMoveRoundTripsTheClearedRepetitionHistory() {
        var position = ChessGame.newGame()
        listOf(
            Move.of("g1", "f3"),
            Move.of("g8", "f6"),
            Move.of("f3", "g1"),
            Move.of("f6", "g8"),
        ).forEach { position = ChessRules.applyMove(position, it) }

        val after = ChessRules.applyMove(position, Move.of("e2", "e4"))
        val restored = ChessRules.undoLastMove(after)

        assertEquals(1, after.state.drawRuleState.positionCounts.size)
        assertEquals(2, Repetition.occurrences(position.state))
        assertEquals(position, restored)
    }

    @Test
    fun aGameEndingMoveRoundTripsBackToAnUnfinishedGame() {
        val before =
            game(
                "a1" to white(PieceType.KING),
                "b7" to white(PieceType.ROOK),
                "c6" to white(PieceType.ROOK),
                "h8" to black(PieceType.KING),
            )
        val after = ChessRules.applyMove(before, Move.of("c6", "c8"))
        val restored = ChessRules.undoLastMove(after)

        assertTrue(after.isOver)
        assertFalse(restored.isOver)
        assertNull(restored.result)
        assertEquals(before, restored)
    }

    @Test
    fun severalMovesUnwindOneAtATime() {
        var position = ChessGame.newGame()
        val snapshots = mutableListOf(position)

        listOf(
            Move.of("e2", "e4"),
            Move.of("c7", "c5"),
            Move.of("g1", "f3"),
            Move.of("d7", "d6"),
        ).forEach {
            position = ChessRules.applyMove(position, it)
            snapshots += position
        }

        snapshots.reversed().drop(1).forEach { expected ->
            position = ChessRules.undoLastMove(position)
            assertEquals(expected, position)
        }

        assertEquals(ChessGame.newGame(), position)
    }

    @Test
    fun thereIsNothingToUndoAtTheStartOfTheGame() {
        assertFailsWith<IllegalArgumentException> { ChessRules.undoLastMove(ChessGame.newGame()) }
    }

    @Test
    fun aClaimedDrawLeavesTheHistoryAlone() {
        var position = ChessGame.newGame()
        repeat(2) {
            listOf(
                Move.of("g1", "f3"),
                Move.of("g8", "f6"),
                Move.of("f3", "g1"),
                Move.of("f6", "g8"),
            ).forEach { position = ChessRules.applyMove(position, it) }
        }

        val claimed = ChessRules.claimDraw(position, DrawClaim.THREEFOLD_REPETITION)

        assertEquals(position.history, claimed.history)
        assertTrue(claimed.isOver)
    }
}
