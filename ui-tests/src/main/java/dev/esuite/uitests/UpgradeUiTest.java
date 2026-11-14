package dev.esuite.uitests;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Seed v1.0.0, install the update without clearing data, then verify in a fresh process. */
@RunWith(AndroidJUnit4.class)
public class UpgradeUiTest {
    private final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private UiObject2 tag(String name) {
        for (int i=0;i<10;i++) {
            UiObject2 found=device.wait(Until.findObject(By.res(name)),700);
            if(found!=null) return found;
            device.swipe(device.getDisplayWidth()/2,device.getDisplayHeight()*3/4,device.getDisplayWidth()/2,device.getDisplayHeight()/3,25);
        }
        throw new AssertionError("Upgrade control missing: "+name);
    }
    private void launch() throws Exception {
        assertEquals("1",device.executeShellCommand("getprop ro.kernel.qemu").trim());
        device.executeShellCommand("am force-stop dev.esuite.app");
        device.executeShellCommand("am start -W -n dev.esuite.app/.MainActivity");
        assertTrue(device.wait(Until.hasObject(By.text("Your workspace.")),20000));
    }
    @Test public void seedPreviousRelease() throws Exception {
        launch(); tag("project_name").setText("Same signer upgrade fixture");
        tag("project_origin").setText("https://example.test"); tag("create_project").click();
        tag("nav_findings").click(); tag("finding_title").setText("Preserve on upgrade");
        tag("finding_detail").setText("Synthetic upgrade evidence retained"); tag("save_finding").click();
        for(int n=0;n<5;n++) device.swipe(device.getDisplayWidth()/2,device.getDisplayHeight()/3,device.getDisplayWidth()/2,device.getDisplayHeight()*3/4,25);
        assertTrue(device.wait(Until.hasObject(By.textContains("Workspace saved with AES-GCM")),15000));
    }
    @Test public void verifyUpgradeRetainsEncryptedWorkspace() throws Exception {
        launch(); assertNotNull(device.wait(Until.findObject(By.textContains("Same signer upgrade fixture")),15000));
        tag("nav_findings").click();
        assertNotNull(device.wait(Until.findObject(By.textContains("Synthetic upgrade evidence retained")),15000));
    }
}
