package com.jmussel.chessgame.ui.game

import com.jmussel.chessgame.api.CapturedPieceDto
import com.jmussel.chessgame.api.ChessApiClient
import com.jmussel.chessgame.api.GameViewDto
import com.jmussel.chessgame.api.MoveDto
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.core.chess.Piece
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The captured-piece rows of an online game (`D104`, `M23.1`), from the server's projection
 * alone: each move's captured piece and White's material balance. The board here says nothing
 * about captures, and nothing is replayed or read from SAN.
 */
class OnlineCapturedPiecesTest {
    private fun move(
        from: String,
        to: String,
        san: String,
        captured: CapturedPieceDto? = null,
    ) = MoveDto(from = from, to = to, san = san, captured = captured)

    /** `1. e4 d5 2. exd5 Qxd5 3. Nc3 Qxa2`, as the server would send it. */
    private val moves =
        listOf(
            move("e2", "e4", "e4"),
            move("d7", "d5", "d5"),
            move("e4", "d5", "exd5", CapturedPieceDto("BLACK", "PAWN")),
            move("d8", "d5", "Qxd5", CapturedPieceDto("WHITE", "PAWN")),
            move("b1", "c3", "Nc3"),
            move("d5", "a2", "Qxa2", CapturedPieceDto("WHITE", "PAWN")),
        )

    private fun game(
        yourSide: String,
        balance: Int? = -4,
        played: List<MoveDto> = moves,
    ) = GameViewDto(
        gameId = "game-1",
        seriesId = "series-1",
        opponent = UserSummaryDto(userId = "user-1", username = "Alex"),
        version = 7,
        yourSide = yourSide,
        sideToMove = "WHITE",
        // Deliberately the starting board: rows never infer captures from the position.
        board = listOf("rnbqkbnr", "pppppppp", "........", "........", "........", "........", "PPPPPPPP", "RNBQKBNR"),
        moves = played,
        lastMove = played.lastOrNull(),
        whiteMaterialBalanceQuarterPoints = balance,
    )

    @Test
    fun theOpponentsRowIsAtTheTopAndYoursBelowWhicheverSideYouPlay() {
        val asWhite = OnlineGame.capturedRowsFor(game(yourSide = "WHITE"))
        assertEquals("Alex · Black", asWhite.top.label)
        assertEquals("You · White", asWhite.bottom.label)

        val asBlack = OnlineGame.capturedRowsFor(game(yourSide = "BLACK"))
        assertEquals("Alex · White", asBlack.top.label)
        assertEquals("You · Black", asBlack.bottom.label)
    }

    @Test
    fun bothPlayersSeeTheSameCapturesOnlyPlacedForTheirSide() {
        val asWhite = OnlineGame.capturedRowsFor(game(yourSide = "WHITE"))
        val asBlack = OnlineGame.capturedRowsFor(game(yourSide = "BLACK"))

        val blackCaptured = listOf(Piece(Side.WHITE, PieceType.PAWN), Piece(Side.WHITE, PieceType.PAWN))
        val whiteCaptured = listOf(Piece(Side.BLACK, PieceType.PAWN))
        assertEquals(blackCaptured, asWhite.top.captured)
        assertEquals(whiteCaptured, asWhite.bottom.captured)
        assertEquals(whiteCaptured, asBlack.top.captured)
        assertEquals(blackCaptured, asBlack.bottom.captured)
    }

    @Test
    fun theServersBalanceGoesToTheLeadingSidesRow() {
        val asWhite = OnlineGame.capturedRowsFor(game(yourSide = "WHITE", balance = -4))
        assertEquals("+1", asWhite.top.advantageText)
        assertNull(asWhite.bottom.advantageText)

        val asBlack = OnlineGame.capturedRowsFor(game(yourSide = "BLACK", balance = 37))
        assertEquals("+9.25", asBlack.top.advantageText)
        assertNull(asBlack.bottom.advantageText)

        val level = OnlineGame.capturedRowsFor(game(yourSide = "BLACK", balance = 0))
        assertNull(level.top.advantageText)
        assertNull(level.bottom.advantageText)
    }

    @Test
    fun anOlderServerWithoutTheBalanceShowsNoAdvantage() {
        val rows = OnlineGame.capturedRowsFor(game(yourSide = "WHITE", balance = null))

        assertNull(rows.top.advantageText)
        assertNull(rows.bottom.advantageText)
    }

    @Test
    fun anOlderServerWithoutCaptureFactsShowsNoCapturesRatherThanGuessing() {
        val bare = moves.map { it.copy(captured = null) }
        val rows = OnlineGame.capturedRowsFor(game(yourSide = "WHITE", balance = null, played = bare))

        assertTrue(rows.top.captured.isEmpty())
        assertTrue(rows.bottom.captured.isEmpty())
    }

    @Test
    fun undoAndRedoFollowTheServersActiveMoves() {
        val undone = OnlineGame.capturedRowsFor(game(yourSide = "WHITE", balance = 0, played = moves.dropLast(2)))
        assertEquals(1, undone.top.captured.size)
        assertNull(undone.top.advantageText)

        val redone = OnlineGame.capturedRowsFor(game(yourSide = "WHITE", balance = -4))
        assertEquals(2, redone.top.captured.size)
        assertEquals("+1", redone.top.advantageText)
    }

    @Test
    fun theServersFieldsAreReadAndAMissingOneIsNotZero() {
        val older =
            """{"gameId":"g","seriesId":"s","opponent":{"userId":"u","username":"Alex"},"version":3,"yourSide":"WHITE",""" +
                """"sideToMove":"BLACK","moves":[{"from":"e4","to":"d5","san":"exd5","captured":{"side":"BLACK","type":"PAWN"}}]}"""
        val current = older.dropLast(1) + ""","whiteMaterialBalanceQuarterPoints":4}"""
        val decoded = ChessApiClient.Json.decodeFromString(GameViewDto.serializer(), current)

        assertEquals(CapturedPieceDto("BLACK", "PAWN"), decoded.moves.single().captured)
        assertEquals(4, decoded.whiteMaterialBalanceQuarterPoints)

        val fromOlder = ChessApiClient.Json.decodeFromString(GameViewDto.serializer(), older)
        assertNull(fromOlder.whiteMaterialBalanceQuarterPoints)
    }
}
