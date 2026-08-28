package com.jmussel.chessgame.ui.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.ui.theme.ChessGameTheme

/**
 * Adding and removing the people there is anything to do with.
 *
 * A friend is found by their exact username and added by name (`D009`); the friendship is
 * mutual the moment it is made, so there is nothing to accept and nobody to wait for.
 * Removing one asks first, and says what it will really do, because it does not do the
 * obvious thing (`D013`).
 */
@Composable
fun FriendsScreen(
    state: FriendsUiState,
    modifier: Modifier = Modifier,
    actions: FriendsActions = FriendsActions(),
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AddFriend(
            state = state,
            onFind = actions.onFind,
            onAdd = actions.onAdd,
            onDismissFound = actions.onDismissFound,
        )

        state.message?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }

        Text(text = FRIENDS, style = MaterialTheme.typography.titleSmall)

        when {
            state.loading && state.friends.isEmpty() -> Text(text = LOADING, style = MaterialTheme.typography.bodyMedium)
            state.loaded && state.friends.isEmpty() -> Text(text = NOBODY_YET, style = MaterialTheme.typography.bodyMedium)
            !state.loaded && state.friends.isEmpty() ->
                TextButton(onClick = actions.onRetry, enabled = !state.loading) { Text(text = RETRY) }
            else ->
                state.friends.forEach { friend ->
                    FriendRow(
                        friend = friend,
                        enabled = !state.busy,
                        onPlay = { actions.onPlay(friend) },
                        onRemove = { actions.onAskToRemove(friend) },
                    )
                }
        }
    }

    state.removing?.let { friend ->
        RemovalConfirmation(
            friend = friend,
            onConfirm = { actions.onConfirmRemove(friend) },
            onCancel = actions.onCancelRemove,
        )
    }
}

/** Finding someone by name, and then adding the person that turned out to be. */
@Composable
private fun AddFriend(
    state: FriendsUiState,
    onFind: (String) -> Unit,
    onAdd: (String) -> Unit,
    onDismissFound: () -> Unit,
) {
    var requested by remember { mutableStateOf("") }

    Text(text = ADD_HEADING, style = MaterialTheme.typography.titleSmall)

    OutlinedTextField(
        value = requested,
        onValueChange = {
            requested = it
            onDismissFound()
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.busy,
        singleLine = true,
        label = { Text(text = USERNAME) },
    )

    val found = state.found

    if (found == null) {
        Button(onClick = { onFind(requested) }, enabled = !state.busy && Friends.isSendable(requested)) {
            Text(text = FIND)
        }
        return
    }

    Text(text = "Found ${found.username}.", style = MaterialTheme.typography.bodyMedium)

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onAdd(found.username) }, enabled = !state.busy) { Text(text = ADD) }
        TextButton(onClick = onDismissFound, enabled = !state.busy) { Text(text = CANCEL) }
    }
}

/** One friend, and the two things to do about them. */
@Composable
private fun FriendRow(
    friend: UserSummaryDto,
    enabled: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = friend.username, style = MaterialTheme.typography.bodyLarge)

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onPlay, enabled = enabled) { Text(text = PLAY) }
            TextButton(onClick = onRemove, enabled = enabled) { Text(text = REMOVE) }
        }
    }
}

/** The question asked before a removal, with what it will really do. */
@Composable
private fun RemovalConfirmation(
    friend: UserSummaryDto,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(text = REMOVE_TITLE) },
        text = { Text(text = Friends.removalWarning(friend.username)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(text = REMOVE) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(text = KEEP) } },
    )
}

private const val ADD_HEADING = "Add a friend"
private const val FRIENDS = "FRIENDS"
private const val USERNAME = "Username"
private const val FIND = "Find"
private const val ADD = "Add friend"
private const val CANCEL = "Cancel"
private const val PLAY = "Play"
private const val REMOVE = "Remove"
private const val KEEP = "Keep"
private const val REMOVE_TITLE = "Remove friend"
private const val LOADING = "Loading…"
private const val NOBODY_YET = "Nobody yet. Add a friend by username to start playing."
private const val RETRY = "Try again"

@Preview(showBackground = true)
@Composable
private fun FriendsScreenPreview() {
    ChessGameTheme {
        FriendsScreen(
            state =
                FriendsUiState(
                    friends =
                        listOf(
                            UserSummaryDto(userId = "user-1", username = "Alex"),
                            UserSummaryDto(userId = "user-2", username = "Sam"),
                        ),
                    loaded = true,
                ),
        )
    }
}
