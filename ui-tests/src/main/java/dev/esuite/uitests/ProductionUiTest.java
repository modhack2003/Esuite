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
        enter("project_name", "UI persistence fixture");
        enter("project_origin", "https://example.test");
        click("create_project");
        click("switch_project"); find(By.text("New project")).click();
        enter("project_name", "Last used project fixture");
        enter("project_origin", "https://second.example.test");
        click("create_project");
        click("nav_api"); click("identity_B");
        click("nav_findings");
        enter("finding_title", "UI fixture observation");
        enter("finding_detail", "Synthetic local evidence");
        click("save_finding");
        top();
        assertTrue("Explicit encrypted save did not finish", waitText("workspace_save_status", "Workspace saved with AES-GCM", 15000));
        // Fresh process, not just recreation with a retained ViewModel.
        device.executeShellCommand("am force-stop dev.esuite.app");
        launch();
        assertTrue("Last project or safe initial identity was not restored", waitText("workspace_context", "Last used project fixture · https://second.example.test · identity A", 15000));
        click("nav_findings");
        assertNotNull(find(By.textContains("Synthetic local evidence")));
        click("finding_item");
        find(By.text("Preview observation report")).click();
        assertNotNull(device.wait(Until.findObject(By.textContains("esuite-observation")), 5000));
        device.pressBack(); device.pressBack();
        click("nav_home"); click("home_tools");
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
        enter("project_name", "Draft retention fixture");
        enter("project_origin", "https://example.test");
        click("create_project");
        click("nav_api");
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

    @Test public void browserHttpsCookiesStorageEditsAndPostScopeThroughRealControls() throws Exception {
        launch();
        enter("project_name", "Browser UI fixture");
        enter("project_origin", "https://example.com");
        click("create_project"); click("nav_browser");
        // Capability gating is intentional; never silently share profile A/B.
        Assume.assumeFalse("Installed WebView has no isolated profiles",
                device.wait(Until.hasObject(By.res("browser_unsupported")), 2000));
        click("browser_go"); output("Page loaded");
        evaluate("document.title", "Example Domain");
        evaluate("document.body.style.margin='0'; document.body.innerHTML='<div style=\"position:fixed;inset:0 0 50% 0;background:#ff00ff\"></div><div style=\"position:fixed;inset:50% 0 0 0;background:#00ffff\"></div>'; 'Paint fixture ready'", "Paint fixture ready");
        click("browser_tab_page"); assertVisiblePagePattern();

        evaluate("location.hash='inspector'; 'Anchor ready'", "Anchor ready");
        evaluate("document.title", "Example Domain");
        // Real HTTPS loaded above. Only synthetic DOM/storage/cookies are added below.
        evaluate("document.body.innerHTML='<button id=fixture style=\"position:absolute;top:8px;left:8px\">Inspect me</button>'; localStorage.setItem('esuite-ui','local-value'); sessionStorage.setItem('session-ui','session-value'); 'Fixture ready'", "Fixture ready");
        top(); click("browser_tab_elements"); click("browser_select");
        top(); find(By.text("Inspect me")).click();
        click("browser_read_selection"); output("BUTTON");
        enter("browser_edit", "<p id='replacement'>Replacement element</p>");
        click("browser_apply_html"); output("HTML updated locally");
        click("browser_read_selection"); output("replacement");
        evaluate("document.getElementById('replacement').textContent='x'.repeat(17000); 'Large fixture ready'", "Large fixture ready");
        top(); click("browser_tab_elements"); click("browser_read_selection"); output("\"htmlTruncated\": true");
        evaluate("'x'.repeat(25000)", "TRUNCATED: showing first 24000 of 25000 characters");
        top(); click("browser_tab_elements");
        enter("browser_edit", "color: rgb(255, 0, 0); outline: 1px solid blue");
        click("browser_apply_css"); output("CSS updated locally");
        evaluate("getComputedStyle(document.getElementById('replacement')).color", "rgb(255, 0, 0)");
        top(); click("browser_tab_storage"); click("browser_read_storage"); output("local-value"); output("session-value");
        top(); click("browser_tab_cookies");
        enter("browser_cookie_input", "esuite_ui=synthetic-cookie; Path=/; Secure; HttpOnly; Max-Age=3600");
        click("browser_set_cookie"); output("Cookie stored");
        click("browser_read_cookies"); output("esuite_ui=[HIDDEN]");
        click("browser_reveal"); click("browser_read_cookies"); output("synthetic-cookie");
        evaluate("document.cookie.includes('esuite_ui') ? 'LEAK' : 'HttpOnly hidden from page'", "HttpOnly hidden from page");
        readCookies("synthetic-cookie");
        click("browser_reveal"); click("browser_read_cookies"); output("esuite_ui=[HIDDEN]");
        click("browser_transfer"); output("memory-only cookie jar replaced");
        click("nav_api"); click("api_tab_identity"); click("api_session_metadata"); top();
        assertNotNull(find(By.textContains("esuite_ui")));
        // Profile A persists after leaving Browser; B remains independent.
        top(); click("identity_B"); click("nav_browser"); readCookies("No cookies for this URL");
        assertDarkInspector();
        top(); click("identity_A"); readCookies("esuite_ui=[HIDDEN]");
        top(); click("browser_go"); output("Page loaded");
        evaluate("const f=document.createElement('form'); f.method='POST'; f.action='https://outside.invalid/blocked'; document.body.appendChild(f); f.submit(); 'Submitting fixture'", "Blocked navigation outside project scope");
        top(); enter("browser_url", "https://outside.invalid/"); click("browser_go"); output("outside the selected project origin");
    }

    @Test public void apiSendCompareCollectionAndFailureReplacePreviousResult() throws Exception {
        launch(); enter("project_name", "API UI fixture");
        enter("project_origin", "https://example.com"); click("create_project");
        click("nav_api"); click("api_send"); top();
        assertTrue("HTTPS API request did not complete", waitText("workspace_status", "HTTP 200", 45000));
        click("api_tab_request"); click("api_compare"); top();
        assertTrue("A/B comparison did not finish", waitText("workspace_status", "Separate session jars used", 90000));
        click("api_tab_collection"); enter("api_saved_name", "Synthetic GET"); click("api_save_draft");
        assertNotNull(find(By.text("Load: Synthetic GET")));
        click("api_tab_request"); enter("api_url", "https://outside.invalid/"); click("api_send");
        assertNotNull(find(By.text(java.util.regex.Pattern.compile("(?s).*Error · .*"))));
        // The failed send must replace the old 200 result, with no request to outside.invalid.
        top(); assertTrue(waitText("workspace_status", "outside the selected project origin", 15000));
        click("nav_traffic"); find(By.textContains("Identity comparison · identity B · 200")).click();
        assertNotNull(device.wait(Until.findObject(By.text("Exchange detail")), 5000));
        assertNotNull(device.wait(Until.findObject(By.textContains("identity B")), 5000));
        device.pressBack();
        assertNotNull(find(By.textContains("Identity comparison · identity B · 200")));
    }

    private boolean hasPagePattern(android.graphics.Bitmap bitmap, android.graphics.Rect bounds) {
        if (bounds.width() < 40 || bounds.height() < 40 || bounds.left < 0 || bounds.top < 0 || bounds.right > bitmap.getWidth() || bounds.bottom > bitmap.getHeight()) return false;
        int matches = 0, total = 0;
        for (int x = 2; x < 8; x++) for (int y = 1; y < 9; y++) {
            int color = bitmap.getPixel(bounds.left + bounds.width() * x / 10, bounds.top + bounds.height() * y / 10);
            int expected = y < 5 ? android.graphics.Color.MAGENTA : android.graphics.Color.CYAN;
            if (Math.abs(android.graphics.Color.red(color)-android.graphics.Color.red(expected)) < 24 &&
                Math.abs(android.graphics.Color.green(color)-android.graphics.Color.green(expected)) < 24 &&
                Math.abs(android.graphics.Color.blue(color)-android.graphics.Color.blue(expected)) < 24) matches++;
            total++;
        }
        return matches >= total * 9 / 10;
    }
    private void assertDarkInspector() throws Exception {
        long until = android.os.SystemClock.uptimeMillis() + 10000;
        do {
            UiObject2 panel = device.findObject(By.res("browser_inspector"));
            android.graphics.Bitmap shot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            if (panel != null && shot != null) {
                android.graphics.Rect bounds = panel.getVisibleBounds();
                int matches = 0;
                for (int x = 1; x <= 5; x++) {
                    int color = shot.getPixel(bounds.left + bounds.width()*x/6, bounds.bottom - 8);
                    if (Math.abs(android.graphics.Color.red(color)-7) < 16 && Math.abs(android.graphics.Color.green(color)-16) < 16 && Math.abs(android.graphics.Color.blue(color)-25) < 16) matches++;
                }
                shot.recycle();
                if (matches == 5) { checkpoint("dark-inspector-profile-B"); return; }
            } else if (shot != null) shot.recycle();
            android.os.SystemClock.sleep(250);
        } while (android.os.SystemClock.uptimeMillis() < until);
        fail("Hidden page overpainted the inspector background");
    }
    private void assertVisiblePagePattern() throws Exception {
        android.graphics.Bitmap blank = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888);
        blank.eraseColor(android.graphics.Color.WHITE);
        assertFalse("A blank white WebView must fail the paint oracle", hasPagePattern(blank, new android.graphics.Rect(0,0,100,100)));
        blank.eraseColor(android.graphics.Color.rgb(7,16,25));
        assertFalse("The window background must fail the paint oracle", hasPagePattern(blank, new android.graphics.Rect(0,0,100,100)));
        blank.recycle();
        long until = android.os.SystemClock.uptimeMillis() + 15000;
        do {
            UiObject2 page = device.findObject(By.clazz("android.webkit.WebView"));
            android.graphics.Bitmap shot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            boolean painted = page != null && shot != null && hasPagePattern(shot, page.getVisibleBounds());
            if (shot != null) shot.recycle();
            if (painted) { checkpoint("visible-page-pattern"); return; }
            android.os.SystemClock.sleep(250);
        } while (android.os.SystemClock.uptimeMillis() < until);
        fail("Loaded page pattern did not paint inside actual hosted WebView bounds");
    }

}
