package dev.esuite.uitests;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Seed the immutable v1.0.7 baseline, then verify the update without clearing app data. */
@RunWith(AndroidJUnit4.class)
public class UpgradeUiTest extends NativeUiDriver {
    private static final String NOTE="Synthetic upgrade evidence retained";
    private void readObservation() {
        click("nav_findings");
        assertNotNull(find(By.textContains(NOTE)));
    }
    @Test public void seedPreviousRelease() throws Exception {
        launch(); click("nav_api");
        enter("site_url","https://example.test"); click("site_continue");
        click("nav_findings"); enter("finding_title","Preserve on upgrade");
        enter("finding_detail",NOTE); click("save_finding"); top();
        assertTrue(waitText("workspace_save_status", "Saved on this device", 15000));
        // Prove the baseline note was actually durable before installing an update.
        launch(); readObservation(); checkpoint("baseline-note-after-process-restart");
    }
    @Test public void verifyUpgradeRetainsEncryptedWorkspace() throws Exception {
        launch();
        readObservation();
        top(); assertTrue(waitText("workspace_context", "https://example.test", 15000)); checkpoint("upgraded-note-retained");
    }
}
