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

/** Drives the optimized release APK as a separate application; no app classes are linked. */
@RunWith(AndroidJUnit4.class)
public class ProductionUiTest {
    private final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

    @org.junit.Rule public org.junit.rules.TestWatcher evidence = new org.junit.rules.TestWatcher() {
        @Override protected void failed(Throwable error, org.junit.runner.Description description) {
            try {
                java.io.File dir = InstrumentationRegistry.getInstrumentation().getContext().getExternalFilesDir(null);
                if(dir != null) {
                    device.takeScreenshot(new java.io.File(dir, description.getMethodName()+".png"));
                    java.io.File tree = new java.io.File(dir, description.getMethodName()+".xml");
                    device.dumpWindowHierarchy(tree);
                    System.out.println("Failure UI hierarchy: " + new String(java.nio.file.Files.readAllBytes(tree.toPath()), java.nio.charset.StandardCharsets.UTF_8));
                }
            } catch(Exception diagnostic) { System.out.println("Failure evidence unavailable: "+diagnostic); }
        }
    };
    @Before public void resetDisposableFixture() throws Exception {
        assertEquals("UI fixture runner supports disposable emulators only", "1", device.executeShellCommand("getprop ro.kernel.qemu").trim());
        assertTrue(device.executeShellCommand("pm clear dev.esuite.app").contains("Success"));
    }
    private UiObject2 find(BySelector selector) {
        UiObject2 visible = device.wait(Until.findObject(selector), 700);
        if (visible != null) return visible;
        top();
        for (int attempt = 0; attempt < 10; attempt++) {
            UiObject2 found = device.wait(Until.findObject(selector), 700);
            if (found != null) return found;
            device.swipe(device.getDisplayWidth()/100, device.getDisplayHeight()*3/4,
                    device.getDisplayWidth()/100, device.getDisplayHeight()/3, 25);
        }
        throw new AssertionError("Native control not found: " + selector);
    }
    private UiObject2 tag(String name) { return find(By.res(name)); }
    private void click(String name) {
        for (int attempt=0;attempt<3;attempt++) {
            try { device.waitForIdle(1000); tag(name).click(); return; }
            catch (androidx.test.uiautomator.StaleObjectException stale) {
                if(attempt==2) throw stale;
            }
        }
    }
    private void enter(String name, String value) {
        tag(name).setText(value);
        // SET_TEXT can open the IME and reflow Compose; close it as a user would before pressing the action.
        if (device.hasObject(By.pkg("com.google.android.inputmethod.latin")) || device.hasObject(By.pkg("com.android.inputmethod.latin"))) device.pressBack();
        device.waitForIdle(1000);
    }
    private void top() {
        for (int n = 0; n < 8; n++) device.swipe(device.getDisplayWidth()/100,
                device.getDisplayHeight()/3, device.getDisplayWidth()/100,
                device.getDisplayHeight()*3/4, 25);
    }
    private void launch() throws Exception {
        device.executeShellCommand("am start -W -S -n dev.esuite.app/.MainActivity");
        UiObject2 home = device.wait(Until.findObject(By.res("nav_home")), 20000);
        assertNotNull("Release navigation did not open", home); home.click(); top();
        assertTrue("Release home did not open", device.wait(Until.hasObject(By.text("Your workspace.")), 20000));
    }

    @Test public void scopedProjectAndEncryptedObservationSurviveProcessRestart() throws Exception {
        launch();
        enter("project_name", "UI persistence fixture");
        enter("project_origin", "https://example.test");
        click("create_project");
        click("nav_findings");
        enter("finding_title", "UI fixture observation");
        enter("finding_detail", "Synthetic local evidence");
        click("save_finding");
        top();
        assertTrue("Explicit encrypted save did not finish", device.wait(Until.hasObject(By.textContains("Workspace saved with AES-GCM")), 15000));
        // Fresh process, not just recreation with a retained ViewModel.
        device.executeShellCommand("am force-stop dev.esuite.app");
        launch();
        click("nav_findings");
        assertNotNull(find(By.textContains("Synthetic local evidence")));
    }
    private boolean containsText(UiObject2 node, String expected) {
        String text=node.getText();
        if(text!=null && text.contains(expected)) return true;
        for(UiObject2 child:node.getChildren()) if(containsText(child,expected)) return true;
        return false;
    }
    private boolean waitText(String name, String expected, long timeout) {
        tag(name);
        long until=android.os.SystemClock.uptimeMillis()+timeout;
        do {
            try {
                UiObject2 node=device.findObject(By.res(name));
                if(node!=null && containsText(node,expected)) return true;
            } catch(androidx.test.uiautomator.StaleObjectException ignored) { /* Reacquire after Compose reflow. */ }
            android.os.SystemClock.sleep(150);
        } while(android.os.SystemClock.uptimeMillis()<until);
        return false;
    }
    private void output(String expected) {
        assertTrue("Browser output missing: " + expected, waitText("browser_output", expected,15000));
    }
    private void evaluate(String script, String expected) {
        top(); click("browser_tab_console");
        enter("browser_script", script);
        click("browser_evaluate"); output(expected);
    }
    private void readCookies(String expected) {
        top(); click("browser_tab_cookies");
        click("browser_read_cookies"); output(expected);
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
        click("nav_api"); click("api_session_metadata"); top();
        assertNotNull(find(By.textContains("esuite_ui")));
        // Profile A persists after leaving Browser; B remains independent.
        top(); click("identity_B"); click("nav_browser"); readCookies("No cookies for this URL");
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
        click("api_compare"); top();
        assertTrue("A/B comparison did not finish", waitText("workspace_status", "Separate session jars used", 90000));
        enter("api_saved_name", "Synthetic GET"); click("api_save_draft");
        assertNotNull(find(By.text("Load: Synthetic GET")));
        enter("api_url", "https://outside.invalid/"); click("api_send");
        assertNotNull(find(By.text(java.util.regex.Pattern.compile("Error · .*"))));
        // The failed send must replace the old 200 result, with no request to outside.invalid.
        top(); assertTrue(waitText("workspace_status", "outside the selected project origin", 15000));
        click("nav_traffic"); assertNotNull(find(By.textContains("Identity comparison · identity B · 200")));
    }

}
