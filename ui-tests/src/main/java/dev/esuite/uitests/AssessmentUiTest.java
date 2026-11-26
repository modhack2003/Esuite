package dev.esuite.uitests;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AssessmentUiTest extends NativeUiDriver {
    @Test public void browserToMapToReviewToRetainedFinding() throws Exception {
        assertEquals("1", device.executeShellCommand("getprop ro.kernel.qemu").trim());
        assertTrue(device.executeShellCommand("pm clear dev.esuite.app").contains("Success"));
        launch();
        enter("browser_url", "https://example.com/");
        click("browser_go");
        assertTrue(device.wait(Until.hasObject(By.clazz("android.webkit.WebView")), 45000));
        click("browser_tools");
        click("browser_panel_picker");
        click("browser_panel_choice_network");
        assertTrue("Browser observations did not become available for Activity",
                device.wait(Until.hasObject(By.res("network_open_activity").enabled(true)), 10000));
        click("network_open_activity");
        assertTrue(device.wait(Until.hasObject(By.res("browser_activity_import")), 8000));
        click("browser_activity_create");
        assertTrue(device.wait(Until.hasObject(By.res("activity_history")), 10000));
        click("activity_commands");
        click("command_map");
        assertTrue(device.wait(Until.hasObject(By.res("activity_endpoint")), 10000));
        checkpoint("assessment-site-map");

        click("nav_api");
        click("api_send");
        top();
        assertTrue("HTTPS response did not arrive", waitText("workspace_status", "HTTP 200", 45000));
        if (android.os.Build.VERSION.SDK_INT == 35) {
            click("response_body_tab_hex");
            assertTrue("The response body should render offset-based hex", waitText("response_body_content", "00000000", 8000));
            click("response_body_tab_captured");
        }
        click("nav_traffic");
        click("activity_tab_history");
        if (android.os.Build.VERSION.SDK_INT == 35) verifyLiveObservationTimeZone();
        activityControl(By.res("activity_select_review")).click();
        click("activity_review_selected");
        assertTrue(device.wait(Until.hasObject(By.res("review_summary")), 10000));
        reviewAction(false).click();
        assertTrue(device.wait(Until.hasObject(By.textContains("Rule headers.strict-transport-security")), 8000));
        click("review_finding_confirm");
        assertTrue(waitText("review_finding_state", "Saved on this device.", 18000));
        checkpoint("assessment-saved-evidence");
        click("review_finding_close");
        click("review_close");

        if (android.os.Build.VERSION.SDK_INT == 35) verifyAdaptiveReview();
        activityControl(By.text("Clear site history")).click();
        find(By.text("Clear history")).click();
        click("nav_findings");
        click("finding_editor_toggle");
        assertTrue(device.wait(Until.hasObject(By.text("Evidence attached")), 10000));
        click("finding_item");
        assertTrue(device.wait(Until.hasObject(By.textContains("Show retained evidence")), 10000));
    }

    private void verifyLiveObservationTimeZone() throws Exception {
        String originalZone = device.executeShellCommand("getprop persist.sys.timezone").trim();
        String automatic = device.executeShellCommand("settings get global auto_time_zone").trim();
        assertTrue("Unexpected emulator time zone", originalZone.matches("[A-Za-z0-9/_+-]+"));
        try {
            device.executeShellCommand("settings put global auto_time_zone 0");
            setTimeZone("Etc/UTC");
            activityControl(By.res("traffic_exchange")).click();
            assertTrue("The current observation did not display UTC",
                    device.wait(Until.hasObject(By.res("activity_display_timestamp").textEndsWith(" UTC")), 8000));

            setTimeZone("Asia/Kathmandu");
            String abbreviation = java.time.format.DateTimeFormatter.ofPattern("z", java.util.Locale.getDefault())
                    .withZone(java.time.ZoneId.of("Asia/Kathmandu")).format(java.time.Instant.now());
            assertTrue("The visible timestamp did not update after a live Android time-zone change",
                    device.wait(Until.hasObject(By.res("activity_display_timestamp").textEndsWith(" " + abbreviation)), 8000));
            checkpoint("activity-time-zone-reformatted");
            device.pressBack();
            assertTrue(device.wait(Until.hasObject(By.res("activity_history")), 8000));
        } finally {
            try {
                setTimeZone(originalZone);
            } finally {
                if (automatic.equals("null")) device.executeShellCommand("settings delete global auto_time_zone");
                else if (automatic.equals("0") || automatic.equals("1")) {
                    device.executeShellCommand("settings put global auto_time_zone " + automatic);
                }
            }
        }
    }

    private void setTimeZone(String zone) throws Exception {
        String result = device.executeShellCommand("cmd alarm set-timezone " + zone).trim();
        assertEquals("Android could not change the emulator time zone: " + result,
                zone, device.executeShellCommand("getprop persist.sys.timezone").trim());
    }

    private void verifyAdaptiveReview() throws Exception {
        String previous = device.executeShellCommand("settings get system font_scale").trim();
        try {
            device.executeShellCommand("settings put system font_scale 1.5");
            device.setOrientationLeft();
            launch();
            assertTrue(device.getDisplayWidth() > device.getDisplayHeight());
            assertTrue(device.wait(Until.hasObject(By.pkg("dev.esuite.app").desc("Browser")), 10000));
            click("nav_traffic");
            click("activity_commands");
            enter("command_search", "/review");
            click("command_review");
            click("pick_exchange");
            assertTrue(device.wait(Until.hasObject(By.res("review_summary")), 10000));
            reviewAction(true).click();
            UiObject2 confirm = device.wait(Until.findObject(By.res("review_finding_confirm")), 8000);
            assertNotNull("Finding action should remain visible at 150% text in landscape", confirm);
            assertTrue(confirm.getVisibleBounds().height() >= 24);
            checkpoint("assessment-landscape-large-text");
            device.pressBack();
            device.pressBack();
        } catch (AssertionError failure) {
            checkpoint("assessment-landscape-failure");
            throw failure;
        } finally {
            device.setOrientationNatural();
            device.unfreezeRotation();
            if (previous.equals("null")) device.executeShellCommand("settings delete system font_scale");
            else device.executeShellCommand("settings put system font_scale " + previous);
        }
        launch();
        click("nav_traffic");
    }

    private UiObject2 reviewAction(boolean compact) {
        UiObject2 results = device.wait(Until.findObject(By.res("review_results")), 7000);
        assertNotNull("The review result list is missing", results);
        for (int attempt = 0; attempt < 14; attempt++) {
            android.graphics.Rect viewport = results.getVisibleBounds();
            for (UiObject2 action : device.findObjects(By.res("review_save_finding"))) {
                android.graphics.Rect bounds = action.getVisibleBounds();
                if (viewport.contains(bounds.centerX(), bounds.centerY()) && bounds.height() >= (compact ? 20 : 30)) {
                    return action;
                }
            }
            results.scroll(Direction.DOWN, 0.45f);
            device.waitForIdle(500);
        }
        throw new AssertionError("Save as finding action is missing from the review viewport " + results.getVisibleBounds());
    }

    private UiObject2 activityControl(BySelector selector) {
        UiObject2 history = device.wait(Until.findObject(By.res("activity_history")), 7000);
        assertNotNull("The scrollable Activity list is missing", history);
        for (int attempt = 0; attempt < 6; attempt++) {
            UiObject2 target = device.wait(Until.findObject(selector), 1000);
            if (usable(target)) return target;
            history.scroll(Direction.DOWN, 0.65f);
        }
        throw new AssertionError("Activity control missing: " + selector);
    }
}
