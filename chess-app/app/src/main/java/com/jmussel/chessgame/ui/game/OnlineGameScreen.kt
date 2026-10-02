package com.jmussel.chessgame.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jmussel.chessgame.api.GameViewDto
import com.jmussel.chessgame.api.MoveDto
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.ui.board.ChessBoard
import com.jmussel.chessgame.ui.board.GameBackButton
import com.jmussel.chessgame.ui.board.GameEndHeadline
import com.jmussel.chessgame.ui.board.GameLayout
import com.jmussel.chessgame.ui.board.PromotionChoice
import com.jmussel.chessgame.ui.theme.ChessGameTheme

/**
 * One server-owned game.
 *
 * Everything on the screen comes from the server's last answer (`D004`): the position, the
 * side the viewer plays, whose move it is, the check, the moves played, and how it ended.
 * Nothing here can change any of it — playing a move is `M14.11` — so a finished game and a
 * game in progress are drawn the same way, and a game from history is read-only by
 * construction rather than by a flag.
 *
 * The board and the rest are arranged by `GameLayout` (`D073`). Back is part of this
 * screen rather than of the app's top row, and is drawn when [onBack] is given, in every
 * state, so a game that failed to load can still be left.
 *
 * Once a board has been drawn it stays drawn while the game is read again, while the server
 * wakes and while [liveUpdates] reconnects; a short notice says which is happening, and
 * offers only a read or a reconnect, never a command a second time (`M21.15`, `D037`).
 * [onRetry] reads the game again; [onReconnectNow] cuts the reconnect pause short.
 */
@Composable
fun OnlineGameScreen(
    state: OnlineGameState,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onRetry: () -> Unit = {},
    liveUpdates: LiveUpdates = LiveUpdates.Live,
    onReconnectNow: () -> Unit = {},
    onSquareTapped: (Square) -> Unit = {},
    onChoosePromotion: (PieceType) -> Unit = {},
    onCancelPromotion: () -> Unit = {},
    onUndo: () -> Unit = {},
    onClaimDraw: (String) -> Unit = {},
    onAskToResign: () -> Unit = {},
    onResign: () -> Unit = {},
    onCancelResignation: () -> Unit = {},
    onAskToLeaveSeries: () -> Unit = {},
    onLeaveSeries: () -> Unit = {},
    onCancelLeaveSeries: () -> Unit = {},
    onOpenNextGame: () -> Unit = {},
    onFindNextGame: () -> Unit = {},
    onDone: () -> Unit = {},
) {
    when (state) {
        is OnlineGameState.Loading ->
            NoGameYet(onBack = onBack, modifier = modifier) {
                SyncNoticeText(notice = OnlineGame.loadingNoticeFor(state))
            }

        is OnlineGameState.Failed ->
            NoGameYet(onBack = onBack, modifier = modifier) {
                Text(text = state.message, style = MaterialTheme.typography.bodyMedium)
                if (state.canRetry) {
                    TextButton(onClick = onRetry) { Text(text = SyncAction.TRY_AGAIN.label) }
                }
            }

        is OnlineGameState.Ready ->
            Game(
                state = state,
                modifier = modifier,
                onBack = onBack,
                connection =
                    Connection(
                        liveUpdates = liveUpdates,
                        onRetry = onRetry,
                        onReconnectNow = onReconnectNow,
                    ),
                onSquareTapped = onSquareTapped,
                onChoosePromotion = onChoosePromotion,
                onCancelPromotion = onCancelPromotion,
                onUndo = onUndo,
                onClaimDraw = onClaimDraw,
                onAskToResign = onAskToResign,
                onResign = onResign,
                onCancelResignation = onCancelResignation,
                series =
                    SeriesExit(
                        onAsk = onAskToLeaveSeries,
                        onLeave = onLeaveSeries,
                        onCancel = onCancelLeaveSeries,
                    ),
                after = AfterGameActions(onOpenNextGame = onOpenNextGame, onFindNextGame = onFindNextGame, onDone = onDone),
            )
    }
}

/** What a finished game offers next: the server's next game, another look for it, or the dashboard. */
private class AfterGameActions(
    val onOpenNextGame: () -> Unit,
    val onFindNextGame: () -> Unit,
    val onDone: () -> Unit,
)

/** Leaving the series, which is asked about first (`D052`). */
private class SeriesExit(
    val onAsk: () -> Unit,
    val onLeave: () -> Unit,
    val onCancel: () -> Unit,
)

/** Keeping the game on screen in step with the server: what is happening, and the safe ways to hurry it. */
private class Connection(
    val liveUpdates: LiveUpdates,
    val onRetry: () -> Unit,
    val onReconnectNow: () -> Unit,
)

/** A game still loading, or one that could not be loaded: Back, and what is happening. */
@Composable
private fun NoGameYet(
    onBack: (() -> Unit)?,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        onBack?.let { GameBackButton(onClick = it) }
        content()
    }
}

/** The game itself, drawn from the server's answer. */
@Composable
private fun Game(
    state: OnlineGameState.Ready,
    modifier: Modifier,
    onBack: (() -> Unit)?,
    connection: Connection,
    onSquareTapped: (Square) -> Unit,
    onChoosePromotion: (PieceType) -> Unit,
    onCancelPromotion: () -> Unit,
    onUndo: () -> Unit,
    onClaimDraw: (String) -> Unit,
    onAskToResign: () -> Unit,
    onResign: () -> Unit,
    onCancelResignation: () -> Unit,
    series: SeriesExit,
    after: AfterGameActions,
) {
    val game = state.game

    GameLayout(
        onBack = onBack,
        modifier = modifier,
        board = { side ->
            ChessBoard(
                board = OnlineGame.boardFrom(game.board),
                side = side,
                selectedSquare = state.selected,
                legalDestinations = OnlineGame.legalDestinations(state),
                lastMove = OnlineGame.lastMoveSquares(game),
                orientation = OnlineGame.sideOf(game),
                onSquareClick = onSquareTapped,
            )
        },
        controls = {
            Text(text = OnlineGame.headingFor(game), style = MaterialTheme.typography.titleSmall)

            // A finished game says how it ended above everything else (`M21.16`); the board and the
            // move list stay as they were, with the last move actually played highlighted.
            if (game.isOver) {
                GameEndHeadline(text = OnlineGame.statusFor(game))
            } else {
                Text(text = OnlineGame.statusFor(game), style = MaterialTheme.typography.bodyLarge)
            }

            // Whether this board is being brought up to date, or may be behind (`M21.15`). The
            // board itself stays the last one the server sent throughout.
            OnlineGame.syncNoticesFor(state, connection.liveUpdates).forEach { notice ->
                SyncNoticeView(
                    notice = notice,
                    onAction = { action ->
                        when (action) {
                            SyncAction.TRY_AGAIN, SyncAction.REFRESH_GAME -> connection.onRetry()
                            SyncAction.RECONNECT_NOW -> connection.onReconnectNow()
                        }
                    },
                )
            }

            state.pendingPromotion?.let { pending ->
                PromotionPrompt(
                    choices = pending.choices,
                    onChoose = onChoosePromotion,
                    onCancel = onCancelPromotion,
                )
            }

            // Offered only while the server's own answer says this player may take a move back
            // (`D016`); the server decides again when the command arrives.
            if (game.canUndo) {
                Button(onClick = onUndo, enabled = !state.submitting) { Text(text = UNDO) }
            }

            // Only the claims the server said are available, each labelled by its own rule
            // (`D019`); an automatic draw needs no claim and never appears here.
            game.availableDrawClaims.forEach { claim ->
                Button(onClick = { onClaimDraw(claim) }, enabled = !state.submitting) {
                    Text(text = OnlineGame.claimLabel(claim))
                }
            }

            // A player may give up on their opponent's move as readily as on their own
            // (`docs/PRODUCT.md`), and is asked first because it is final (`D018`).
            if (!game.isOver) {
                Button(onClick = onAskToResign, enabled = !state.submitting) { Text(text = RESIGN) }
            }

            // What the series did next, which the server decided when it finalized this game
            // (`D014`); nothing here creates or confirms a rematch.
            state.after?.let { next ->
                Text(text = OnlineGame.afterGameText(next, game), style = MaterialTheme.typography.bodyMedium)

                when (next) {
                    AfterGame.Looking -> Unit
                    is AfterGame.NextGame -> Button(onClick = after.onOpenNextGame) { Text(text = NEXT_GAME) }
                    AfterGame.SeriesOver -> Button(onClick = after.onDone) { Text(text = BACK_TO_DASHBOARD) }
                    AfterGame.NotFound ->
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = after.onFindNextGame) { Text(text = SyncAction.TRY_AGAIN.label) }
                            TextButton(onClick = after.onDone) { Text(text = BACK_TO_DASHBOARD) }
                        }
                }
            }

            // Leaving the series is not resigning: it ends the series and leaves this game alone
            // (`D052`). Offered while the server says the series goes on; afterwards, the note says
            // this is the last game (`D068`).
            if (game.seriesActive) {
                TextButton(onClick = series.onAsk, enabled = !state.submitting) { Text(text = LEAVE_SERIES) }
            }
            OnlineGame.lastGameNoteFor(game)?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }

            // What the server was last asked, and what it said about it.
            if (state.submitting) Text(text = SUBMITTING, style = MaterialTheme.typography.bodyMedium)
            state.message?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }

            Text(text = OnlineGame.positionFor(game), style = MaterialTheme.typography.bodySmall)
        },
        moveList = {
            OnlineGame.moveListLines(game).forEach { line ->
                Text(text = line, style = MaterialTheme.typography.bodySmall)
            }
        },
    )

    if (state.confirmingLeave) {
        AlertDialog(
            onDismissRequest = series.onCancel,
            title = { Text(text = LEAVE_TITLE) },
            text = { Text(text = OnlineGame.leaveWarningFor(game)) },
            confirmButton = { TextButton(onClick = series.onLeave) { Text(text = LEAVE_SERIES) } },
            dismissButton = { TextButton(onClick = series.onCancel) { Text(text = STAY) } },
        )
    }

    if (state.confirmingResignation) {
        AlertDialog(
            onDismissRequest = onCancelResignation,
            title = { Text(text = RESIGN_TITLE) },
            text = { Text(text = RESIGN_WARNING) },
            confirmButton = { TextButton(onClick = onResign) { Text(text = RESIGN) } },
            dismissButton = { TextButton(onClick = onCancelResignation) { Text(text = KEEP_PLAYING) } },
        )
    }
}

/** A notice's words, without its action. */
@Composable
private fun SyncNoticeText(notice: SyncNotice) {
    Text(text = notice.title, style = MaterialTheme.typography.titleSmall)
    notice.detail?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
}

/** A notice and the one safe thing it offers, if any. */
@Composable
private fun SyncNoticeView(
    notice: SyncNotice,
    onAction: (SyncAction) -> Unit,
) {
    Column {
        SyncNoticeText(notice = notice)
        notice.action?.let { action ->
            TextButton(onClick = { onAction(action) }) { Text(text = action.label) }
        }
    }
}

/** The four pieces a pawn may become, and the way out of the question. */
@Composable
private fun PromotionPrompt(
    choices: List<PieceType>,
    onChoose: (PieceType) -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = PROMOTE_TO, style = MaterialTheme.typography.bodyMedium)

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { choice ->
                PromotionChoice(type = choice, onClick = { onChoose(choice) })
            }

            TextButton(onClick = onCancel) { Text(text = CANCEL) }
        }
    }
}

private const val SUBMITTING = "Sending…"
private const val PROMOTE_TO = "Promote to"
private const val CANCEL = "Cancel"
private const val UNDO = "Undo"
private const val RESIGN = "Resign"
private const val RESIGN_TITLE = "Resign?"
private const val RESIGN_WARNING = "You lose this game. This cannot be undone."
private const val KEEP_PLAYING = "Keep playing"
private const val LEAVE_SERIES = "Leave series"
private const val LEAVE_TITLE = "Leave this series?"
private const val STAY = "Stay"
private const val NEXT_GAME = "Play the next game"
private const val BACK_TO_DASHBOARD = "Back to your games"

@Preview(showBackground = true)
@Composable
private fun OnlineGameScreenPreview() {
    ChessGameTheme {
        OnlineGameScreen(
            state =
                OnlineGameState.Ready(
                    GameViewDto(
                        gameId = "game-1",
                        seriesId = "series-1",
                        opponent = UserSummaryDto(userId = "user-1", username = "Alex"),
                        version = 3,
                        yourSide = "WHITE",
                        sideToMove = "WHITE",
                        yourTurn = true,
                        board =
                            listOf(
                                "rnbqkbnr",
                                "pppp.ppp",
                                "........",
                                "....p...",
                                "....P...",
                                "........",
                                "PPPP.PPP",
                                "RNBQKBNR",
                            ),
                        moves = listOf("e2e4", "e7e5"),
                        lastMove = MoveDto(from = "e7", to = "e5"),
                        moveNumber = 2,
                    ),
                ),
        )
    }
}
