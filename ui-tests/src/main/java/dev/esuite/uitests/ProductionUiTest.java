package dev.esuite.uitests;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import android.graphics.Rect;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
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
        revealToolResult();
        assertTrue(waitText("tools_output", "RVN1aXRl", 5000));
        scrollTools(false);
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
        Rect full = pageBounds();
        click("browser_tools");
        Rect split = pageBounds();
        assertTrue("Developer Tools must reserve part of the screen for the live page", split.height() < full.height() && split.height() > full.height() / 4);
        Rect grip = tag("dock_resize_handle").getVisibleBounds();
        device.swipe(grip.centerX(), grip.centerY(), grip.centerX(), grip.centerY() - full.height() / 8, 30);
        device.waitForIdle(2000);
        assertTrue("Dragging the splitter must resize the live page", pageBounds().height() < split.height());
        assertTrue(waitText("browser_output", "Example Domain", 10000));
        selectBrowserTool("elements"); assertTrue("Document outline is missing the page body", waitText("browser_output", "BODY", 10000));
        selectBrowserTool("console");
        enter("console_input", "await Promise.resolve(6 * 7)"); click("console_run");
        assertTrue("Console expression did not resolve", device.wait(Until.hasObject(By.res("console_result").text("42")), 10000));
        click("console_mode");
        enter("console_input", "console.warn('fixture warning'); throw new Error('fixture error');"); click("console_run");
        assertTrue("Script exception is missing", device.wait(Until.hasObject(By.res("console_error").textContains("fixture error")), 10000));
        assertTrue("Console warnings need their own severity", device.wait(Until.hasObject(By.res("console_warning").text("fixture warning")), 5000));
        checkpoint("browser-console-dock");
        selectBrowserTool("cookies");
        assertTrue(waitText("browser_tool_heading", "Cookie names and available flags", 10000));
        assertNotNull(find(By.res("browser_output")));
        selectBrowserTool("storage"); assertTrue(waitText("browser_output", "Local storage", 10000));
        selectBrowserTool("network"); assertTrue(waitText("browser_output", "Navigation /", 10000));
        selectBrowserTool("viewport");
        assertTrue("Mobile viewport should follow the device width", device.wait(Until.hasObject(By.res("browser_output")
                .text(java.util.regex.Pattern.compile("(?s)Layout viewport · (?:[2-5][0-9]{2}) × .*"))), 10000));
        click("nav_tools");
        assertNotNull(tag("tools_input"));
        assertTrue("General tools should also leave the page visible", pageBounds().height() > full.height() / 4);
        click("browser_tools");
        selectBrowserTool("page"); click("browser_open_api");
        assertTrue(waitText("api_url", "https://example.com/", 15000));
        click("nav_browser");
        assertTrue(waitText("browser_url", "https://example.com/", 10000));
        click("open_settings"); assertNotNull(find(By.res("settings_title")));
        click("appearance_tab_light");
        assertTrue(tag("appearance_tab_light").isSelected());
        assertNotNull(find(By.textContains("Version 1.0.6")));
        assertNotNull(find(By.text("Credits")));
        device.pressBack();
        click("browser_tools");
        enter("browser_url", "http://example.com");
        click("browser_go");
        assertNotNull(find(By.textContains("Enter an HTTPS address")));
        checkpoint("browser-standalone");
    }

    private Rect pageBounds() {
        UiObject2 page = device.findObject(By.res("browser_page"));
        assertNotNull("The WebView disappeared", page);
        return page.getVisibleBounds();
    }

    private void selectBrowserTool(String panel) {
        String id = "browser_tool_tab_" + panel;
        for (int i = 0; i < 12; i++) {
            UiObject2 tab = device.findObject(By.res(id));
            if (tab != null && tab.getVisibleBounds().width() >= 48) { click(id); return; }
            tag("browser_tool_tabs").swipe(i < 6 ? Direction.RIGHT : Direction.LEFT, 0.64f);
        }
        throw new AssertionError("Developer Tools tab not visible: " + panel);
    }

    private void revealToolResult() {
        for (int i = 0; i < 5 && !usable(device.findObject(By.res("tools_output"))); i++) scrollTools(true);
    }

    private void scrollTools(boolean down) {
        UiObject2 dock = device.findObject(By.res("browser_dock"));
        assertNotNull("Tools dock is missing", dock);
        Rect panel = dock.getVisibleBounds();
        int x = panel.left + panel.width() / 12;
        int lower = panel.bottom - panel.height() / 8;
        int upper = panel.centerY();
        device.swipe(x, down ? lower : upper, x, down ? upper : lower, 20);
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
