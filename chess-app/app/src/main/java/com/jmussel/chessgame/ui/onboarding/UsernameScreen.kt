package com.jmussel.chessgame.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jmussel.chessgame.ui.theme.ChessGameTheme

/**
 * Choosing a username, which is the one thing a new installation has to do.
 *
 * There is no sign-in to go with it (`D006`). A new name makes a new player; a name that
 * already belongs to someone makes this installation that player, which is how someone
 * gets back to their account on another phone or after reinstalling (`D082`). The screen
 * says both before the player commits, and that a name cannot be changed later
 * (`docs/PRODUCT.md`). It says nothing about any other app.
 *
 * Whether a name is allowed is the server's answer (`D007`), so a refusal is shown in the
 * server's own words.
 */
@Composable
fun UsernameScreen(
    claim: UsernameClaim,
    modifier: Modifier = Modifier,
    onClaim: (String) -> Unit = {},
) {
    var requested by remember { mutableStateOf("") }
    val claiming = claim is UsernameClaim.Claiming

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = HEADING, style = MaterialTheme.typography.titleSmall)
        Text(text = ONCE_ONLY, style = MaterialTheme.typography.bodyMedium)

        OutlinedTextField(
            value = requested,
            onValueChange = { requested = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = !claiming,
            singleLine = true,
            label = { Text(text = LABEL) },
        )

        (claim as? UsernameClaim.Rejected)?.let { rejected ->
            Text(text = rejected.message, style = MaterialTheme.typography.bodyMedium)
        }

        Button(
            onClick = { onClaim(requested) },
            enabled = !claiming && UsernameOnboarding.isSendable(requested),
        ) {
            Text(text = if (claiming) CLAIMING else CLAIM)
        }
    }
}

private const val HEADING = "Choose a username"
private const val ONCE_ONLY =
    "This is how friends will find you, and it cannot be changed later. " +
        "If you already have a username, enter it to carry on as that player."
private const val LABEL = "Username"
private const val CLAIM = "Claim"
private const val CLAIMING = "Claiming…"

@Preview(showBackground = true)
@Composable
private fun UsernameScreenPreview() {
    ChessGameTheme {
        UsernameScreen(claim = UsernameClaim.Rejected("A username needs at least 3 characters"))
    }
}
