package dev.esuite.uitests;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.SystemClock;
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
        growDock(device.getDisplayHeight() / 10);
        revealUtilityControl(By.res("tools_input"), true);
        enter("tools_input", "ESuite");
        BySelector encode = By.text("Base64 encode");
        revealUtilityControl(encode, true);
        find(encode).click();
        revealUtilityControl(By.res("tools_output"), true);
        assertTrue(waitText("tools_output", "RVN1aXRl", 5000));
        revealUtilityControl(By.res("tools_tab_review"), false);
        click("tools_tab_review");
        BySelector review = By.text("Review AWS JSON offline");
        revealUtilityControl(review, true);
        assertNotNull(find(review));
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
        growDock(full.height() / 6);
        assertTrue("Dragging the splitter must resize the live page", pageBounds().height() < split.height());
        assertTrue(waitText("browser_output", "Example Domain", 10000));
        selectBrowserTool("elements"); assertTrue("Document outline is missing the page body", waitText("browser_output", "BODY", 10000));
        selectBrowserTool("console");
        enter("console_input", "await Promise.resolve(6 * 7).then(n => { if (n === 42) document.documentElement.style.background = '#237E5B'; return n; })");
        click("console_run");
        assertTrue("Console expression must apply the awaited result to the live page", viewPaints(pageBounds(), 35, 126, 91));
        click("console_mode");
        click("console_levels");
        enter("console_input", "console.warn('fixture warning');"); click("console_run");
        assertTrue("The console should paint its amber warning", viewPaints(tag("console_messages").getVisibleBounds(), 244, 198, 131));
        enter("console_input", "throw new Error('fixture error');"); click("console_run");
        assertTrue("The console should paint its red script exception", viewPaints(tag("console_messages").getVisibleBounds(), 255, 180, 174));
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
        if (page == null) page = device.findObject(By.clazz("android.webkit.WebView"));
        assertNotNull("The WebView disappeared", page);
        return page.getVisibleBounds();
    }

    private void growDock(int distance) {
        Rect grip = tag("dock_resize_handle").getVisibleBounds();
        device.swipe(grip.centerX(), grip.centerY(), grip.centerX(), grip.centerY() - distance, 30);
        device.waitForIdle(2000);
    }

    private boolean viewPaints(Rect bounds, int red, int green, int blue) {
        long deadline = SystemClock.uptimeMillis() + 12000;
        while (SystemClock.uptimeMillis() < deadline) {
            Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            if (screenshot != null) {
                Bitmap pixels = screenshot.getConfig() == Bitmap.Config.HARDWARE ? screenshot.copy(Bitmap.Config.ARGB_8888, false) : screenshot;
                int matches = 0;
                try {
                    for (int y = bounds.top; y < Math.min(bounds.bottom, pixels.getHeight()); y++) {
                        for (int x = bounds.left; x < Math.min(bounds.right, pixels.getWidth()); x++) {
                            int color = pixels.getPixel(x, y);
                            if (Math.abs(Color.red(color) - red) < 12 && Math.abs(Color.green(color) - green) < 12
                                    && Math.abs(Color.blue(color) - blue) < 12 && ++matches >= 6) return true;
                        }
                    }
                } finally {
                    if (pixels != screenshot) pixels.recycle();
                    screenshot.recycle();
                }
            }
            SystemClock.sleep(250);
        }
        return false;
    }

    private void selectBrowserTool(String panel) {
        click("browser_panel_picker");
        click("browser_panel_choice_" + panel);
    }

    private void revealUtilityControl(BySelector selector, boolean down) {
        for (int i = 0; i < 4 && !usable(device.findObject(selector)); i++) scrollTools(down);
        for (int i = 0; i < 4 && !usable(device.findObject(selector)); i++) scrollTools(!down);
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
