package dev.esuite.uitests;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import org.junit.Test;
import org.junit.Before;
import org.junit.Assume;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Drives the optimized release APK; fixture reset is specific to this suite. */
@RunWith(AndroidJUnit4.class)
public class ProductionUiTest extends NativeUiDriver {
    @Before public void resetDisposableFixture() throws Exception {
        assertEquals("UI fixture runner supports disposable emulators only", "1", device.executeShellCommand("getprop ro.kernel.qemu").trim());
        assertTrue(device.executeShellCommand("pm clear dev.esuite.app").contains("Success"));
    }
    @Test public void scopedProjectAndEncryptedObservationSurviveProcessRestart() throws Exception {
        launch();
        openSite("https://example.test");
        click("site_picker"); find(By.text("Open another site")).click();
        enter("site_url", "https://second.example.test"); click("site_continue");
        click("identity_B");
        click("nav_findings");
        enter("finding_title", "UI fixture observation");
        enter("finding_detail", "Synthetic local evidence");
        click("save_finding");
        top();
        assertTrue("Explicit encrypted save did not finish", waitText("workspace_save_status", "Saved on this device", 15000));
        // Fresh process, not just recreation with a retained ViewModel.
        device.executeShellCommand("am force-stop dev.esuite.app");
        launch();
        click("nav_findings");
        assertTrue("Last site or default identity was not restored", waitText("workspace_context", "https://second.example.test · identity A", 15000));
        assertNotNull(find(By.textContains("Synthetic local evidence")));
        click("finding_item");
        find(By.text("Preview observation report")).click();
        assertNotNull(device.wait(Until.findObject(By.textContains("esuite-observation")), 5000));
        device.pressBack(); device.pressBack();
        click("nav_tools");
        enter("tools_input", "ESuite");
        find(By.text("Base64 encode")).click();
        assertTrue(waitText("tools_output", "RVN1aXRl", 5000));
        click("tools_tab_review");
        assertNotNull(find(By.text("Review AWS JSON offline")));
        assertFalse(device.hasObject(By.res("task_preview")));
    }
    /**
     * QA-02 on a real device: pending editors must survive leaving and re-entering a module. This
     * injects text through UiAutomator's SET_TEXT, so it proves draft RETENTION only. It does NOT
     * exercise the IME, so it is a weaker check than the Vivo/Gboard entry path QA-03 requires;
     * autocorrection of technical input stays open until the physical-device pass.
     */
    @Test public void pendingApiAndFindingDraftsSurviveModuleSwitches() throws Exception {
        launch();
        openSite("https://example.test");
        enter("api_headers", "X-ESuite-QA: retained-header");
        click("nav_findings");
        enter("finding_title", "Retained draft title");
        enter("finding_detail", "Retained draft detail");
        click("nav_traffic");
        click("nav_api");
        assertTrue("Pending API header draft was lost on a module switch",
                waitText("api_headers", "X-ESuite-QA: retained-header", 10000));
        click("nav_traffic");
        click("nav_findings");
        assertTrue("Pending observation title was lost on a module switch",
                waitText("finding_title", "Retained draft title", 10000));
        assertTrue("Pending observation detail was lost on a module switch",
                waitText("finding_detail", "Retained draft detail", 10000));
        // Saving an observation clears its own drafts and returns to the list.
        click("save_finding");
        assertTrue(waitText("finding_editor_toggle", "New observation", 10000));
        click("finding_editor_toggle");
        assertFalse("Saving an observation left its title draft behind",
                waitText("finding_title", "Retained draft title", 3000));
    }

    @Test public void browserOpensWithoutASavedSiteAndInspectsTheCurrentPage() throws Exception {
        launch();
        assertNotNull(find(By.res("browser_url")));
        assertFalse(device.hasObject(By.res("project_name")));
        enter("browser_url", "example.com"); click("browser_go");
        assertTrue("The real page never became visible", device.wait(Until.hasObject(By.text("Example Domain")), 45000));
        click("browser_tools");
        assertTrue(waitText("browser_output", "Example Domain", 10000));
        click("browser_tool_tab_elements"); assertTrue(waitText("browser_output", "H1", 10000));
        click("browser_tool_tab_cookies");
        assertTrue(waitText("browser_tool_heading", "Cookie names and available flags", 10000));
        assertNotNull(find(By.res("browser_output")));
        click("browser_tool_tab_storage"); assertTrue(waitText("browser_output", "Local storage", 10000));
        click("browser_tool_tab_network"); assertTrue(waitText("browser_output", "Navigation /", 10000));
        click("browser_tool_tab_page"); click("browser_open_api");
        assertTrue(waitText("api_url", "https://example.com/", 15000));
        click("nav_browser");
        assertTrue(waitText("browser_url", "https://example.com/", 10000));
        click("open_settings"); assertNotNull(find(By.res("settings_title")));
        click("appearance_tab_light");
        assertTrue(tag("appearance_tab_light").isSelected());
        assertNotNull(find(By.textContains("Version 1.0.5")));
        assertNotNull(find(By.text("Credits")));
        device.pressBack();
        click("browser_tools");
        enter("browser_url", "http://example.com");
        click("browser_go");
        assertNotNull(find(By.textContains("Enter an HTTPS address")));
        checkpoint("browser-standalone");
    }

    private void openSite(String url) {
        click("nav_api");
        enter("site_url", url);
        click("site_continue");
    }

    @Test public void apiSendCompareCollectionAndFailureReplacePreviousResult() throws Exception {
        launch(); openSite("https://example.com");
        click("api_send"); top();
        assertTrue("HTTPS API request did not complete", waitText("workspace_status", "HTTP 200", 45000));
        click("api_tab_request"); click("api_compare"); top();
        assertTrue("A/B comparison did not finish", waitText("workspace_status", "Separate session jars used", 90000));
        click("api_tab_collection"); enter("api_saved_name", "Synthetic GET"); click("api_save_draft");
        assertNotNull(find(By.text("Load: Synthetic GET")));
        click("api_tab_request"); enter("api_url", "https://outside.invalid/"); click("api_send");
        assertNotNull(find(By.text(java.util.regex.Pattern.compile("(?s).*Error · .*"))));
        // The failed send must replace the old 200 result, with no request to outside.invalid.
        top(); assertTrue(waitText("workspace_status", "outside the selected site origin", 15000));
        click("nav_traffic"); find(By.textContains("Identity comparison · identity B · 200")).click();
        assertNotNull(device.wait(Until.findObject(By.text("Exchange detail")), 5000));
        assertNotNull(device.wait(Until.findObject(By.textContains("identity B")), 5000));
        device.pressBack();
        assertNotNull(find(By.textContains("Identity comparison · identity B · 200")));
    }

}
