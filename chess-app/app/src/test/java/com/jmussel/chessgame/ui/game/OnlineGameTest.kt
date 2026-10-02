package com.jmussel.chessgame.ui.game

import com.jmussel.chessgame.api.ChessApiException
import com.jmussel.chessgame.api.GameViewDto
import com.jmussel.chessgame.api.MoveDto
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.core.chess.StandardPosition
import com.jmussel.chessgame.ui.ServerWaiting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Turning the server's answer about a game into what the screen draws.
 *
 * Nothing here works anything out about the game: the position, whose move it is, the
 * check, and the result all come from the server (`D004`), and this only says how to show
 * them.
 */
class OnlineGameTest {
    private val startingRows = StandardPosition.BOARD.toString().lines()

    private fun game(
        yourSide: String = "WHITE",
        sideToMove: String = "WHITE",
        yourTurn: Boolean = true,
        inCheck: Boolean = false,
        board: List<String> = startingRows,
        moves: List<String> = emptyList(),
        lastMove: MoveDto? = null,
        moveNumber: Int = 1,
        version: Long = 1,
        result: String? = null,
        terminationReason: String? = null,
        seriesActive: Boolean = true,
    ) = GameViewDto(
        gameId = "game-1",
        seriesId = "series-1",
        opponent = UserSummaryDto(userId = "user-1", username = "Alex"),
        version = version,
        yourSide = yourSide,
        sideToMove = sideToMove,
        yourTurn = yourTurn,
        inCheck = inCheck,
        board = board,
        moves = moves,
        lastMove = lastMove,
        moveNumber = moveNumber,
        result = result,
        terminationReason = terminationReason,
        seriesActive = seriesActive,
    )

    private fun refusal(status: Int) = ChessApiException(status = status, explanation = "no", message = "refused")

    @Test
    fun theStartingPositionIsReadBackAsItWasSent() {
        val board = OnlineGame.boardFrom(startingRows)

        assertEquals(StandardPosition.BOARD, board)
    }

    @Test
    fun eachPieceLandsOnTheSquareTheServerPutItOn() {
        val board =
            OnlineGame.boardFrom(
                listOf(
                    "....k...",
                    "........",
                    "........",
                    "........",
                    "........",
                    "........",
                    "....P...",
                    "....K...",
                ),
            )

        assertEquals(PieceType.KING, board.pieceAt(Square.parse("e8"))?.type)
        assertEquals(Side.BLACK, board.pieceAt(Square.parse("e8"))?.side)
        assertEquals(PieceType.PAWN, board.pieceAt(Square.parse("e2"))?.type)
        assertEquals(Side.WHITE, board.pieceAt(Square.parse("e1"))?.side)
        assertNull(board.pieceAt(Square.parse("d4")))
    }

    @Test
    fun theBoardFacesTheSideTheViewerIsPlaying() {
        assertEquals(Side.WHITE, OnlineGame.sideOf(game(yourSide = "WHITE")))
        assertEquals(Side.BLACK, OnlineGame.sideOf(game(yourSide = "BLACK")))
    }

    @Test
    fun theMoveJustPlayedIsTwoSquaresToHighlight() {
        val squares = OnlineGame.lastMoveSquares(game(lastMove = MoveDto(from = "e2", to = "e4")))

        assertEquals(setOf(Square.parse("e2"), Square.parse("e4")), squares)
    }

    @Test
    fun aGameWithNoMovesYetHighlightsNothing() {
        assertTrue(OnlineGame.lastMoveSquares(game()).isEmpty())
    }

    @Test
    fun theHeadingNamesTheOpponentAndTheSideYouPlay() {
        assertEquals("Alex • You are White", OnlineGame.headingFor(game(yourSide = "WHITE")))
        assertEquals("Alex • You are Black", OnlineGame.headingFor(game(yourSide = "BLACK")))
    }

    @Test
    fun theStatusSaysWhoseMoveItIs() {
        assertEquals("Your move", OnlineGame.statusFor(game(yourTurn = true)))
        assertEquals("Alex to move", OnlineGame.statusFor(game(yourTurn = false)))
    }

    @Test
    fun aCheckIsSaidAsWellAsWhoseMoveItIs() {
        assertEquals("Your move • Check", OnlineGame.statusFor(game(yourTurn = true, inCheck = true)))
    }

    @Test
    fun aFinishedGameSaysHowItEndedFromTheViewersSide() {
        assertEquals(
            "You won by checkmate",
            OnlineGame.statusFor(game(yourSide = "WHITE", result = "WHITE_WINS", terminationReason = "CHECKMATE")),
        )
        assertEquals(
            "Alex won by resignation",
            OnlineGame.statusFor(game(yourSide = "WHITE", result = "BLACK_WINS", terminationReason = "RESIGNATION")),
        )
        assertEquals(
            "Drawn by stalemate",
            OnlineGame.statusFor(game(result = "DRAW", terminationReason = "STALEMATE")),
        )
    }

    @Test
    fun aFinishedGameSaysNothingAboutWhoseMoveItIs() {
        val status = OnlineGame.statusFor(game(yourTurn = false, result = "DRAW", terminationReason = "STALEMATE"))

        assertFalse(status.contains("move"))
    }

    @Test
    fun theVersionIsShownBecauseACommandHasToCarryIt() {
        assertEquals("Move 18 • version 34", OnlineGame.positionFor(game(moveNumber = 18, version = 34)))
    }

    @Test
    fun theMovesAreNumberedInPairs() {
        val lines = OnlineGame.moveListLines(game(moves = listOf("e2e4", "e7e5", "g1f3")))

        assertEquals(listOf("1. e2e4 e7e5", "2. g1f3"), lines)
    }

    @Test
    fun aGameThatIsNotYoursIsNotWorthRetrying() {
        val refusal = refusal(403)

        assertFalse(OnlineGame.canRetry(refusal))
        assertTrue(OnlineGame.messageFor(refusal).contains("not yours"))
    }

    @Test
    fun aGameThatDoesNotExistIsNotWorthRetrying() {
        assertFalse(OnlineGame.canRetry(refusal(404)))
    }

    @Test
    fun anythingElseIsWorthRetrying() {
        assertTrue(OnlineGame.canRetry(refusal(500)))
        assertTrue(OnlineGame.canRetry(refusal(503)))
    }

    @Test
    fun leavingSaysTheGameInProgressIsNotAffected() {
        val warning = OnlineGame.leaveWarningFor(game())

        assertTrue(warning.contains("No more games with Alex"))
        assertTrue(warning.contains("can still be finished"))
    }

    @Test
    fun leavingAfterAFinishedGameDoesNotPromiseToFinishIt() {
        val warning = OnlineGame.leaveWarningFor(game(result = "WHITE_WINS", terminationReason = "CHECKMATE"))

        assertFalse(warning.contains("can still be finished"))
    }

    @Test
    fun aGameInASeriesThatGoesOnHasNoLastGameNote() {
        assertNull(OnlineGame.lastGameNoteFor(game()))
    }

    @Test
    fun anUnfinishedGameInALeftSeriesIsTheLastOne() {
        assertEquals(
            "A player left the series. This is the last game with Alex.",
            OnlineGame.lastGameNoteFor(game(seriesActive = false)),
        )
    }

    @Test
    fun aFinishedGameInALeftSeriesLeavesTheNoteToTheAfterGameLine() {
        assertNull(OnlineGame.lastGameNoteFor(game(seriesActive = false, result = "DRAW")))
    }

    @Test
    fun aLostConnectionSaysWhatToDoAboutIt() {
        assertTrue(OnlineGame.unreachableMessage().contains("connection"))
    }

    // --- A finished game (`M21.16`) ------------------------------------------------------

    @Test
    fun everyWayAnOnlineGameEndsIsSaidInAPlayersWords() {
        val endings =
            mapOf(
                "CHECKMATE" to "You won by checkmate",
                "RESIGNATION" to "You won by resignation",
                "STALEMATE" to "Drawn by stalemate",
                "INSUFFICIENT_MATERIAL" to "Drawn by insufficient material",
                "THREEFOLD_REPETITION_CLAIM" to "Drawn by threefold repetition",
                "FIFTY_MOVE_RULE_CLAIM" to "Drawn by the fifty-move rule",
                "FIVEFOLD_REPETITION" to "Drawn by fivefold repetition",
                "SEVENTY_FIVE_MOVE_RULE" to "Drawn by the seventy-five-move rule",
            )

        endings.forEach { (reason, said) ->
            val result = if (said.startsWith("You won")) "WHITE_WINS" else "DRAW"
            assertEquals(said, OnlineGame.statusFor(game(yourSide = "WHITE", result = result, terminationReason = reason)))
        }
        assertEquals(
            "the opponent is named when they won",
            "Alex won by checkmate",
            OnlineGame.statusFor(game(yourSide = "BLACK", result = "WHITE_WINS", terminationReason = "CHECKMATE")),
        )
    }

    @Test
    fun aResignedGameKeepsTheLastMoveThatWasPlayed() {
        val resigned =
            game(
                moves = listOf("e2e4", "e7e5"),
                lastMove = MoveDto(from = "e7", to = "e5"),
                result = "BLACK_WINS",
                terminationReason = "RESIGNATION",
            )

        assertEquals(setOf(Square.parse("e7"), Square.parse("e5")), OnlineGame.lastMoveSquares(resigned))
    }

    @Test
    fun aFinishedGameSaysWhatTheSeriesDidNextWithoutAskingForARematch() {
        val finished = game(result = "DRAW", terminationReason = "STALEMATE")

        assertEquals(
            "Finding your next game with Alex. The server starts it automatically.",
            OnlineGame.afterGameText(AfterGame.Looking, finished),
        )
        assertEquals("Your next game with Alex has started.", OnlineGame.afterGameText(AfterGame.NextGame("game-2"), finished))
        assertEquals("That was the last game with Alex.", OnlineGame.afterGameText(AfterGame.SeriesOver, finished))
        assertTrue(OnlineGame.afterGameText(AfterGame.NotFound, finished).startsWith("Could not find your next game"))
    }

    // --- Keeping up with the server (`M21.15`) ------------------------------------------

    @Test
    fun aGameStillLoadingSaysSoAndSaysWhenTheServerIsWaking() {
        assertEquals(SyncNotice("Loading the game…"), OnlineGame.loadingNoticeFor(OnlineGameState.Loading("game-1")))

        val waking = OnlineGame.loadingNoticeFor(OnlineGameState.Loading("game-1", waking = true))
        assertEquals("the same words as startup", SyncNotice(ServerWaiting.TITLE, ServerWaiting.DETAIL), waking)
    }

    @Test
    fun aGameUpToDateOverALiveConnectionSaysNothing() {
        val ready = OnlineGameState.Ready(game())

        assertEquals(emptyList<SyncNotice>(), OnlineGame.syncNoticesFor(ready, LiveUpdates.Live))
        assertEquals(
            "a connection still opening is not news: startup or the refresh beside it says enough",
            emptyList<SyncNotice>(),
            OnlineGame.syncNoticesFor(ready, LiveUpdates.Connecting),
        )
    }

    @Test
    fun eachKindOfCatchingUpSaysWhatItIs() {
        fun noticeFor(sync: GameSync) = OnlineGame.syncNoticesFor(OnlineGameState.Ready(game(), sync = sync), LiveUpdates.Live).single()

        assertEquals(SyncNotice("Refreshing the game…"), noticeFor(GameSync.Refreshing()))
        assertEquals(SyncNotice(ServerWaiting.TITLE, ServerWaiting.DETAIL), noticeFor(GameSync.Refreshing(waking = true)))

        val failed = noticeFor(GameSync.RefreshFailed("Could not reach the server.", canRetry = true))
        assertEquals("Could not reach the server.", failed.detail)
        assertEquals(SyncAction.TRY_AGAIN, failed.action)
        assertNull(
            "a refusal that will not change offers nothing to tap",
            noticeFor(GameSync.RefreshFailed("That game is gone.", canRetry = false)).action,
        )

        val unknown = noticeFor(GameSync.CommandOutcomeUnknown)
        assertEquals(SyncAction.REFRESH_GAME, unknown.action)
        assertTrue("it says the command was not sent again", unknown.detail.orEmpty().contains("not been sent again"))
    }

    @Test
    fun aDroppedConnectionSaysItIsReconnectingAndOffersToHurryOnlyBetweenAttempts() {
        val ready = OnlineGameState.Ready(game())

        val pausing = OnlineGame.syncNoticesFor(ready, LiveUpdates.Reconnecting(failedAttempts = 2, waiting = true)).single()
        assertEquals("Reconnecting live updates…", pausing.title)
        assertEquals(SyncAction.RECONNECT_NOW, pausing.action)

        val attempting = OnlineGame.syncNoticesFor(ready, LiveUpdates.Reconnecting(failedAttempts = 2, waiting = false)).single()
        assertNull("an attempt under way has nothing to hurry", attempting.action)
    }

    @Test
    fun aRefreshAndAReconnectAreBothSaidGameFirst() {
        val ready = OnlineGameState.Ready(game(), sync = GameSync.Refreshing())

        val notices = OnlineGame.syncNoticesFor(ready, LiveUpdates.Reconnecting(failedAttempts = 0, waiting = true))

        assertEquals(listOf("Refreshing the game…", "Reconnecting live updates…"), notices.map { it.title })
    }

    @Test
    fun nothingOfferedEverSendsACommandAgain() {
        // Every action a notice can carry is a read or a reconnect (`D037`).
        assertEquals(
            setOf(SyncAction.TRY_AGAIN, SyncAction.REFRESH_GAME, SyncAction.RECONNECT_NOW),
            SyncAction.entries.toSet(),
        )
    }

    @Test
    fun aRefreshThatCouldNotReachTheServerSaysTheBoardIsTheLastOneLoaded() {
        assertTrue(OnlineGame.refreshUnreachableMessage().contains("last loaded"))
    }
}
