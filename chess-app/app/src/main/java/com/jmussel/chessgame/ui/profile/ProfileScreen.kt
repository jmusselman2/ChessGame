package com.jmussel.chessgame.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jmussel.chessgame.api.ProfileDto
import com.jmussel.chessgame.api.RecordDto
import com.jmussel.chessgame.api.SharedGroupDto
import com.jmussel.chessgame.ui.dashboard.DashboardSections
import com.jmussel.chessgame.ui.theme.ChessGameTheme

/**
 * Another player's relationship profile (`D099`): their name, when they joined, the player's
 * record against them and the groups the two share. Nothing here is worked out on the device:
 * every field is the server's.
 */
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val profile = state.profile
        (profile?.username ?: state.username)?.let { Text(text = it, style = MaterialTheme.typography.titleMedium) }

        when {
            state.unavailable -> {
                Text(text = Profiles.UNAVAILABLE, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onBack) { Text(text = BACK) }
            }

            profile != null -> {
                Text(text = Profiles.joinedText(profile), style = MaterialTheme.typography.bodyMedium)

                Text(text = Profiles.RECORD, style = MaterialTheme.typography.titleSmall)
                val description = DashboardSections.recordDescription(profile.record)
                Text(
                    text = DashboardSections.recordText(profile.record),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.semantics { contentDescription = description },
                )

                Text(text = Profiles.SHARED_GROUPS, style = MaterialTheme.typography.titleSmall)
                if (profile.sharedGroups.isEmpty()) {
                    Text(text = Profiles.sharedGroupsEmptyText(), style = MaterialTheme.typography.bodyMedium)
                } else {
                    profile.sharedGroups.forEach { Text(text = it.name, style = MaterialTheme.typography.bodyLarge) }
                }
            }

            state.loading -> Text(text = LOADING, style = MaterialTheme.typography.bodyMedium)

            else -> {
                state.message?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
                TextButton(onClick = onRetry) { Text(text = RETRY) }
            }
        }
    }
}

private const val BACK = "Back"
private const val LOADING = "Loading…"
private const val RETRY = "Try again"

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    ChessGameTheme {
        ProfileScreen(
            state =
                ProfileUiState(
                    profile =
                        ProfileDto(
                            userId = "user-1",
                            username = "Alex",
                            joinedYear = 2026,
                            joinedMonth = 9,
                            record = RecordDto(wins = 4, losses = 2, draws = 1),
                            sharedGroups = listOf(SharedGroupDto(groupId = "group-1", name = "Tuesday")),
                        ),
                ),
        )
    }
}
