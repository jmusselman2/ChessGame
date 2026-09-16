package com.jmussel.chessgame.ui.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jmussel.chessgame.api.SeriesSummaryDto
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.ui.theme.ChessGameTheme

/**
 * Play was tapped for a friend the player already has a series with, so nothing was started
 * and the player chooses (`D053`): open a game already under way, or start another series.
 */
data class PlayOffer(
    val username: String,
    /** The pair's active series, newest first, as the server offered them. */
    val existing: List<SeriesSummaryDto>,
    /** Starting another is in flight, so neither choice should be taken again. */
    val busy: Boolean = false,
    /** What went wrong with the last choice, if anything did. */
    val message: String? = null,
) {
    /** The game "Open" goes to: the newest offered series' current game. */
    val newestGameId: String?
        get() = existing.firstNotNullOfOrNull { it.currentGameId }
}

/** What the offer can ask the app to do. */
data class PlayOfferActions(
    val onOpen: () -> Unit = {},
    val onStartAnother: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

/**
 * What the offer says.
 *
 * Pure, so the wording is tested without a screen.
 */
object PlayOffers {
    fun title(offer: PlayOffer): String = "Already playing ${offer.username}"

    fun explanation(offer: PlayOffer): String =
        when (val count = offer.existing.size) {
            1 -> "You already have a game with ${offer.username}. Open it, or start another one alongside it?"
            else ->
                "You already have $count games with ${offer.username}. " +
                    "Open the newest, or start another one alongside them?"
        }
}

/** The choice Play offers when the pair already has a series (`D053`). */
@Composable
fun PlayOfferDialog(
    offer: PlayOffer,
    actions: PlayOfferActions,
) {
    AlertDialog(
        onDismissRequest = actions.onDismiss,
        title = { Text(text = PlayOffers.title(offer)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = PlayOffers.explanation(offer))
                offer.message?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onOpen, enabled = !offer.busy && offer.newestGameId != null) {
                Text(text = OPEN)
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onStartAnother, enabled = !offer.busy) { Text(text = START_ANOTHER) }
        },
    )
}

private const val OPEN = "Open"
private const val START_ANOTHER = "Start another"

@Preview(showBackground = true)
@Composable
private fun PlayOfferDialogPreview() {
    ChessGameTheme {
        PlayOfferDialog(
            offer =
                PlayOffer(
                    username = "Alex",
                    existing =
                        listOf(
                            SeriesSummaryDto(
                                seriesId = "series-1",
                                opponent = UserSummaryDto(userId = "user-1", username = "Alex"),
                                status = "ACTIVE",
                                currentGameId = "game-1",
                            ),
                        ),
                ),
            actions = PlayOfferActions(),
        )
    }
}
