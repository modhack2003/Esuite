package dev.esuite.uitests;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Drives the optimized release APK as a separate application; no app classes are linked. */
@RunWith(AndroidJUnit4.class)
public class ProductionUiTest {
    private final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

    private UiObject2 find(BySelector selector) {
        for (int attempt = 0; attempt < 6; attempt++) {
            UiObject2 found = device.wait(Until.findObject(selector), 1500);
            if (found != null) return found;
            device.swipe(device.getDisplayWidth()/2, device.getDisplayHeight()*3/4,
                    device.getDisplayWidth()/2, device.getDisplayHeight()/3, 25);
        }
        throw new AssertionError("Native control not found: " + selector);
    }
    private UiObject2 tag(String name) { return find(By.res(name)); }
    private void top() {
        for (int n = 0; n < 4; n++) device.swipe(device.getDisplayWidth()/2,
                device.getDisplayHeight()/3, device.getDisplayWidth()/2,
                device.getDisplayHeight()*3/4, 25);
    }
    private void launch() throws Exception {
        device.executeShellCommand("am start -W -n dev.esuite.app/.MainActivity");
        assertTrue("Release home did not open", device.wait(Until.hasObject(By.text("Your workspace.")), 20000));
    }

    @Test public void scopedProjectAndEncryptedObservationSurviveProcessRestart() throws Exception {
        launch();
        tag("project_name").setText("UI persistence fixture");
        tag("project_origin").setText("https://example.test");
        tag("create_project").click();
        tag("nav_findings").click();
        tag("finding_title").setText("UI fixture observation");
        tag("finding_detail").setText("Synthetic local evidence");
        tag("save_finding").click();
        top();
        assertTrue("Explicit encrypted save did not finish", device.wait(Until.hasObject(By.textContains("Workspace saved with AES-GCM")), 15000));
        // Fresh process, not just recreation with a retained ViewModel.
        device.executeShellCommand("am force-stop dev.esuite.app");
        launch();
        tag("nav_findings").click();
        assertNotNull(find(By.textContains("Synthetic local evidence")));
    }
}
