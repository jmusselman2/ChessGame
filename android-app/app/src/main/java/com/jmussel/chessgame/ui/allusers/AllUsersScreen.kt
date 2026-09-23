package com.jmussel.chessgame.ui.allusers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.ui.theme.ChessGameTheme

/**
 * Everyone the player could add as a friend, each one tap away (`D071`).
 *
 * A way round typing an exact username while testing. Adding someone keeps the page open
 * and marks their row, so several can be added in a row.
 */
@Composable
fun AllUsersScreen(
    state: AllUsersUiState,
    modifier: Modifier = Modifier,
    actions: AllUsersActions = AllUsersActions(),
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = HEADING, style = MaterialTheme.typography.titleSmall)

        state.message?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }

        if (!state.loaded) {
            // Nothing has arrived yet, so there is nothing to show but what is happening.
            if (state.loading) {
                Text(text = LOADING, style = MaterialTheme.typography.bodyMedium)
            } else {
                TextButton(onClick = actions.onRetry) { Text(text = RETRY) }
            }
            return@Column
        }

        val rows = AllUsers.rows(state)

        if (rows.isEmpty()) {
            Text(text = NOBODY_TO_ADD, style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        rows.forEach { row -> UserRow(row = row, onAdd = { actions.onAdd(row.user) }) }
    }
}

/** One person, and Add, or what adding them did. */
@Composable
private fun UserRow(
    row: AllUsersRow,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = row.user.username, style = MaterialTheme.typography.bodyLarge)

        if (row.added) {
            Text(text = ADDED, modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyMedium)
        } else {
            TextButton(onClick = onAdd, enabled = row.canAdd) { Text(text = ADD) }
        }
    }
}

private const val HEADING = "ALL USERS"
private const val ADD = "Add"
private const val ADDED = "Added ✓"
private const val LOADING = "Loading…"
private const val RETRY = "Try again"
private const val NOBODY_TO_ADD = "No one else to add."

@Preview(showBackground = true)
@Composable
private fun AllUsersScreenPreview() {
    ChessGameTheme {
        AllUsersScreen(
            state =
                AllUsersUiState(
                    users =
                        listOf(
                            UserSummaryDto(userId = "user-1", username = "Alex"),
                            UserSummaryDto(userId = "user-2", username = "Sam"),
                            UserSummaryDto(userId = "user-3", username = "Robin"),
                        ),
                    added = setOf("user-2"),
                    loaded = true,
                ),
        )
    }
}
