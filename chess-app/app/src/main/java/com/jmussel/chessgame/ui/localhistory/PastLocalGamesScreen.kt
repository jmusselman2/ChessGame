package com.jmussel.chessgame.ui.localhistory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jmussel.chessgame.local.LocalGameSummary
import java.text.DateFormat
import java.util.Date

/**
 * Finished local games, newest first (`D084`, `M21.3`). Each opens read-only.
 *
 * What each line says is decided by [PastLocalGames]; the date is the device's own medium
 * date format.
 */
@Composable
fun PastLocalGamesScreen(
    state: PastLocalGamesUiState,
    modifier: Modifier = Modifier,
    onOpenGame: (LocalGameSummary) -> Unit = {},
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "PAST LOCAL GAMES", style = MaterialTheme.typography.titleSmall)

        when {
            state.loading -> Text(text = "Loading…", style = MaterialTheme.typography.bodyMedium)
            state.games.isEmpty() ->
                Text(text = "Local games you finish will appear here.", style = MaterialTheme.typography.bodyMedium)
            else -> {
                val dates = DateFormat.getDateInstance(DateFormat.MEDIUM)
                state.games.forEach { game ->
                    Text(
                        text = PastLocalGames.summaryFor(game) { millis -> dates.format(Date(millis)) },
                        modifier = Modifier.fillMaxWidth().clickable { onOpenGame(game) }.padding(vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
