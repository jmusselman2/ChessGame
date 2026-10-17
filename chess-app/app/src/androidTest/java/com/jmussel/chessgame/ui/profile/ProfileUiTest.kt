package com.jmussel.chessgame.ui.profile

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jmussel.chessgame.api.FriendDto
import com.jmussel.chessgame.api.GroupSummaryDto
import com.jmussel.chessgame.api.ProfileDto
import com.jmussel.chessgame.api.RecordDto
import com.jmussel.chessgame.api.SharedGroupDto
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.ui.board.keepScreenOn
import com.jmussel.chessgame.ui.dashboard.DashboardScreen
import com.jmussel.chessgame.ui.groups.GroupActions
import com.jmussel.chessgame.ui.groups.GroupScreen
import com.jmussel.chessgame.ui.groups.GroupUiState
import com.jmussel.chessgame.ui.theme.ChessGameTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Relationship profiles (`D099`, `M22.5`), from fixed server answers: the profile screen, its
 * two entry points, and the player's own group row, which opens nothing. Not run by CI: see
 * `docs/DEVELOPMENT.md`.
 */
class ProfileUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun keepTheScreenOn() = keepScreenOn(composeRule.activity)

    @Test
    fun everyFieldOfTheProfileIsShown() {
        show { ProfileScreen(state = ProfileUiState(profile = alex)) }

        composeRule.onNodeWithText("Alex").assertIsDisplayed()
        composeRule.onNodeWithText("Joined September 2026").assertIsDisplayed()
        composeRule.onNodeWithText("4-2-1 W-L-D").assertIsDisplayed().assert(hasContentDescription("4 wins, 2 losses, 1 draw"))
        composeRule.onNodeWithText(Profiles.SHARED_GROUPS).assertIsDisplayed()
        composeRule.onNodeWithText("Tuesday").assertIsDisplayed()
    }

    @Test
    fun aProfileThatIsNoLongerAvailableOffersBack() {
        var back = 0
        show { ProfileScreen(state = ProfileUiState(username = "Alex", unavailable = true), onBack = { back++ }) }

        composeRule.onNodeWithText(Profiles.UNAVAILABLE).assertIsDisplayed()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.runOnIdle { assertEquals(1, back) }
        composeRule.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun aFriendRowsNameOpensTheirProfile() {
        val opened = mutableListOf<String>()
        show {
            DashboardScreen(
                entries = emptyList(),
                friends = listOf(FriendDto(userId = "user-Alex", username = "Alex", record = RecordDto(1, 0, 0))),
                versionName = "test",
                onOpenProfile = { opened += it.userId },
            )
        }

        composeRule.onNodeWithText("Alex").performClick()
        composeRule.runOnIdle { assertEquals(listOf("user-Alex"), opened) }
    }

    @Test
    fun anotherMembersNameOpensTheirProfileAndYourOwnRowOpensNothing() {
        val opened = mutableListOf<String>()
        show {
            GroupScreen(
                state =
                    GroupUiState(
                        group = GroupSummaryDto(groupId = "group-1", name = "Tuesday", createdBy = "user-Taylor", memberCount = 2),
                        members =
                            listOf(
                                UserSummaryDto(userId = "user-Taylor", username = "Taylor"),
                                UserSummaryDto(userId = "user-Robin", username = "Robin"),
                            ),
                        loaded = true,
                    ),
                ownUserId = "user-Taylor",
                actions = GroupActions(onOpenProfile = { opened += it.userId }),
            )
        }

        composeRule.onNodeWithText("Taylor").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        composeRule.onNodeWithText("Robin").performClick()
        composeRule.runOnIdle { assertEquals(listOf("user-Robin"), opened) }
    }

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { ChessGameTheme { content() } }
    }

    private val alex =
        ProfileDto(
            userId = "user-Alex",
            username = "Alex",
            joinedYear = 2026,
            joinedMonth = 9,
            record = RecordDto(wins = 4, losses = 2, draws = 1),
            sharedGroups = listOf(SharedGroupDto(groupId = "group-1", name = "Tuesday")),
        )
}
