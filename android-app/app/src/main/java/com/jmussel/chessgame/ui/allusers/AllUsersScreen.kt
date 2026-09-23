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
import com.jmussel.chessgame.api.ListedUserDto
import com.jmussel.chessgame.ui.theme.ChessGameTheme

/**
 * Every user, each one tap from being a friend (`D071`): people to add first, then friends.
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

        val sections = AllUsers.sections(state)

        if (sections.toAdd.isEmpty() && sections.friends.isEmpty()) {
            Text(text = NOBODY_ELSE, style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        if (sections.toAdd.isEmpty()) {
            Text(text = NOBODY_TO_ADD, style = MaterialTheme.typography.bodyMedium)
        }
        sections.toAdd.forEach { row -> UserRow(row = row, onAdd = { actions.onAdd(row.user) }) }

        if (sections.friends.isNotEmpty()) {
            Text(text = FRIENDS, style = MaterialTheme.typography.titleSmall)
            sections.friends.forEach { row -> UserRow(row = row, onAdd = {}) }
        }
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

        when {
            row.user.friend -> Mark(text = FRIEND)
            row.added -> Mark(text = ADDED)
            else -> TextButton(onClick = onAdd, enabled = row.canAdd) { Text(text = ADD) }
        }
    }
}

/** What a row says in place of Add. */
@Composable
private fun Mark(text: String) {
    Text(text = text, modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyMedium)
}

private const val HEADING = "ALL USERS"
private const val ADD = "Add"
private const val ADDED = "Added ✓"
private const val LOADING = "Loading…"
private const val RETRY = "Try again"
private const val FRIEND = "Friend"
private const val FRIENDS = "FRIENDS"
private const val NOBODY_TO_ADD = "No one else to add."
private const val NOBODY_ELSE = "No other users yet."

@Preview(showBackground = true)
@Composable
private fun AllUsersScreenPreview() {
    ChessGameTheme {
        AllUsersScreen(
            state =
                AllUsersUiState(
                    users =
                        listOf(
                            ListedUserDto(userId = "user-1", username = "Alex", friend = false),
                            ListedUserDto(userId = "user-2", username = "Sam", friend = false),
                            ListedUserDto(userId = "user-3", username = "Robin", friend = true),
                        ),
                    added = setOf("user-2"),
                    loaded = true,
                ),
        )
    }
}
