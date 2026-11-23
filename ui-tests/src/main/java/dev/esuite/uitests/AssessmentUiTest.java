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
        click("nav_traffic");
        click("activity_tab_history");
        UiObject2 response = activityControl(By.res("traffic_exchange").hasDescendant(By.textContains("HTTP exchange")));
        String rowId = response.getResourceName();
        assertEquals("traffic_exchange", rowId);
        activityControl(By.res("activity_select_review")).click();
        click("activity_review_selected");
        assertTrue(device.wait(Until.hasObject(By.res("review_summary")), 10000));
        click("review_save_finding");
        assertTrue(device.wait(Until.hasObject(By.textContains("Rule headers.strict-transport-security")), 8000));
        click("review_finding_confirm");
        assertTrue(waitText("review_finding_state", "Saved on this device.", 18000));
        checkpoint("assessment-saved-evidence");
        click("review_finding_close");
        click("review_close");

        activityControl(By.text("Clear site history")).click();
        find(By.text("Clear history")).click();
        click("nav_findings");
        assertTrue(device.wait(Until.hasObject(By.text("Evidence attached")), 10000));
        click("finding_item");
        assertTrue(device.wait(Until.hasObject(By.res("finding_evidence_0")), 10000));
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
