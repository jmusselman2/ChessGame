package com.jmussel.chessgame.ui.groups

import androidx.compose.foundation.clickable
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
import com.jmussel.chessgame.api.GroupSummaryDto
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.ui.theme.ChessGameTheme

/**
 * The groups the player is in, and a way to start another (`D076`).
 *
 * A group is opened to play its members or add friends to it; nothing about a group is
 * done from the list.
 */
@Composable
fun GroupsScreen(
    state: GroupsUiState,
    modifier: Modifier = Modifier,
    actions: GroupsActions = GroupsActions(),
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CreateGroup(busy = state.busy, onCreate = actions.onCreate)

        state.message?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }

        Text(text = GROUPS, style = MaterialTheme.typography.titleSmall)

        when {
            state.loading && state.groups.isEmpty() -> Text(text = LOADING, style = MaterialTheme.typography.bodyMedium)
            state.loaded && state.groups.isEmpty() -> Text(text = NO_GROUPS, style = MaterialTheme.typography.bodyMedium)
            !state.loaded && state.groups.isEmpty() ->
                TextButton(onClick = actions.onRetry, enabled = !state.loading) { Text(text = RETRY) }
            else ->
                state.groups.forEach { group ->
                    GroupRow(group = group, enabled = !state.busy, onOpen = { actions.onOpen(group) })
                }
        }
    }
}

/** Naming a new group, which opens once the server has made it. */
@Composable
private fun CreateGroup(
    busy: Boolean,
    onCreate: (String) -> Unit,
) {
    var requested by remember { mutableStateOf("") }

    Text(text = CREATE_HEADING, style = MaterialTheme.typography.titleSmall)

    OutlinedTextField(
        value = requested,
        onValueChange = { requested = it },
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy,
        singleLine = true,
        label = { Text(text = NAME) },
    )

    Button(onClick = { onCreate(requested) }, enabled = !busy && Groups.isCreatable(requested)) { Text(text = CREATE) }
}

/** One group on the list: its name and size, tapped to open it. */
@Composable
private fun GroupRow(
    group: GroupSummaryDto,
    enabled: Boolean,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onOpen),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = group.name, style = MaterialTheme.typography.bodyLarge)
            Text(text = Groups.memberCount(group.memberCount), style = MaterialTheme.typography.bodySmall)
        }

        TextButton(onClick = onOpen, enabled = enabled) { Text(text = OPEN) }
    }
}

/**
 * One group: its members, each of whom can be played, the friends who could join, and
 * the way out.
 *
 * A member who is not a friend is played exactly like a friend (`D076`). Leaving asks first
 * and says what it will really do, because games with the members carry on.
 */
@Composable
fun GroupScreen(
    state: GroupUiState,
    ownUserId: String?,
    modifier: Modifier = Modifier,
    actions: GroupActions = GroupActions(),
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.group?.let { Text(text = it.name, style = MaterialTheme.typography.titleMedium) }

        if (state.unavailable) {
            Text(text = Groups.UNAVAILABLE, style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        state.message?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }

        if (!state.loaded) {
            if (state.loading) {
                Text(text = LOADING, style = MaterialTheme.typography.bodyMedium)
            } else {
                TextButton(onClick = actions.onRetry) { Text(text = RETRY) }
            }
            return@Column
        }

        Text(text = MEMBERS, style = MaterialTheme.typography.titleSmall)

        Groups.memberRows(state, ownUserId).forEach { row ->
            PersonRow(name = row.member.username) {
                if (row.isYou) {
                    Text(text = YOU, modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyMedium)
                } else {
                    TextButton(onClick = { actions.onPlay(row.member) }, enabled = !state.busy) { Text(text = PLAY) }
                }
            }
        }

        Text(text = ADD_HEADING, style = MaterialTheme.typography.titleSmall)

        Groups.nobodyToAdd(state)?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }

        Groups.addableFriends(state).forEach { friend ->
            PersonRow(name = friend.username) {
                TextButton(onClick = { actions.onAdd(friend) }, enabled = !state.busy) { Text(text = ADD) }
            }
        }

        TextButton(onClick = actions.onAskToLeave, enabled = !state.busy) { Text(text = LEAVE) }
    }

    if (state.confirmingLeave) {
        AlertDialog(
            onDismissRequest = actions.onCancelLeave,
            title = { Text(text = LEAVE_TITLE) },
            text = { Text(text = Groups.leaveWarning(state.group?.name.orEmpty())) },
            confirmButton = { TextButton(onClick = actions.onConfirmLeave) { Text(text = LEAVE) } },
            dismissButton = { TextButton(onClick = actions.onCancelLeave) { Text(text = STAY) } },
        )
    }
}

/** A name, and what can be done about them. */
@Composable
private fun PersonRow(
    name: String,
    action: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = name, style = MaterialTheme.typography.bodyLarge)
        action()
    }
}

private const val CREATE_HEADING = "Create a group"
private const val NAME = "Group name"
private const val CREATE = "Create"
private const val GROUPS = "YOUR GROUPS"
private const val OPEN = "Open"
private const val NO_GROUPS = "No groups yet. Create one to play friends of friends."
private const val MEMBERS = "MEMBERS"
private const val YOU = "You"
private const val PLAY = "Play"
private const val ADD_HEADING = "ADD A FRIEND"
private const val ADD = "Add"
private const val LEAVE = "Leave group"
private const val LEAVE_TITLE = "Leave group"
private const val STAY = "Stay"
private const val LOADING = "Loading…"
private const val RETRY = "Try again"

@Preview(showBackground = true)
@Composable
private fun GroupsScreenPreview() {
    ChessGameTheme {
        GroupsScreen(
            state =
                GroupsUiState(
                    groups =
                        listOf(
                            GroupSummaryDto(groupId = "group-1", name = "Tuesday", createdBy = "user-1", memberCount = 3),
                            GroupSummaryDto(groupId = "group-2", name = "Family", createdBy = "user-2", memberCount = 1),
                        ),
                    loaded = true,
                ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GroupScreenPreview() {
    ChessGameTheme {
        GroupScreen(
            state =
                GroupUiState(
                    group = GroupSummaryDto(groupId = "group-1", name = "Tuesday", createdBy = "user-1", memberCount = 3),
                    members =
                        listOf(
                            UserSummaryDto(userId = "user-1", username = "Taylor"),
                            UserSummaryDto(userId = "user-2", username = "Alex"),
                            UserSummaryDto(userId = "user-3", username = "Robin"),
                        ),
                    friends =
                        listOf(
                            UserSummaryDto(userId = "user-2", username = "Alex"),
                            UserSummaryDto(userId = "user-4", username = "Sam"),
                        ),
                    loaded = true,
                ),
            ownUserId = "user-1",
        )
    }
}
