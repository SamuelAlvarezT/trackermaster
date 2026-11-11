package com.trackermaster.feature.expense

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.trackermaster.core.domain.model.ThemeMode
import com.trackermaster.core.domain.money.*
import com.trackermaster.core.ui.theme.TrackermasterTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28], qualifiers="w360dp-h800dp-mdpi")
class MoneyGroupUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun groupExpandsToDraftAndRemovalOnlyDeletesTheGroup() {
        val base=MoneyPreviewData.state(MoneyDestination.MOVEMENTS)
        val row=base.history.groups.first().rows.first()
        val draft=MoneyReceipt("draft","path","image/png","hash","draft.png")
        val group=MoneyGroup("group","Trip",0,listOf("movement:${row.movement.id}","receipt:draft"))
        val view=MoneyUserGroup(group,listOf(row),listOf(draft),listOf(Money(-100,"COP")),emptyMap())
        val events=mutableListOf<MoneyEvent>()
        compose.setContent { MoneyLocale("en") { TrackermasterTheme(ThemeMode.DARK,0) {
            MoneyHistoryScreen(base.copy(userGroups=listOf(view))) { events.add(it) }
        } } }
        compose.onNodeWithTag("group-expand:group").performClick()
        compose.onNodeWithText("Draft · draft.png").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(MoneyEvent.ReviewDraft("draft"),events.last()) }
        compose.onNodeWithTag("group-remove:group").performScrollTo().performClick()
        compose.onNodeWithText("Removing the group keeps its transactions and drafts.").assertExists()
        compose.onAllNodesWithText("Remove group").onLast().performClick()
        compose.runOnIdle { assertEquals(MoneyEvent.DeleteGroup("group"),events.last());assertFalse(events.any { it is MoneyEvent.Delete }) }
    }
}
