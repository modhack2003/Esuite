package dev.esuite.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** Exercises actual native controls in the optimized validation APK, with no target traffic. */
class WorkspaceUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun createScopedProjectSaveAndReloadObservation() {
        ui.waitUntil(20_000) { ui.onAllNodesWithText("Project name").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Project name").performTextInput("Local UI fixture")
        ui.onNodeWithText("Exact origin, e.g. https://api.example.test").performTextInput("https://example.test")
        ui.onNodeWithText("Create scoped project").performScrollTo().performClick()
        ui.onNode(hasText("Findings") and isSelectable()).performClick()
        ui.onNodeWithText("Observation title").performScrollTo().performTextInput("UI fixture observation")
        ui.onNodeWithText("Evidence / reproduction / next test").performScrollTo().performTextInput("Synthetic local evidence")
        ui.onNodeWithText("Save observation").performScrollTo().performClick()
        ui.onNodeWithText("Synthetic local evidence", substring = true).performScrollTo().assertIsDisplayed()
        ui.activityRule.scenario.recreate()
        ui.waitUntil(20_000) { ui.onAllNodesWithText("Observation title").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Synthetic local evidence", substring = true).performScrollTo().assertIsDisplayed()
    }
}
