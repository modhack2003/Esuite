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
    private void top() {
        for (int n = 0; n < 8; n++) device.swipe(device.getDisplayWidth()/100,
                device.getDisplayHeight()/3, device.getDisplayWidth()/100,
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
    private void output(String expected) {
        tag("browser_output");
        assertTrue("Browser output missing: " + expected,
                device.wait(Until.hasObject(By.res("browser_output").textContains(expected)), 15000));
    }
    private void evaluate(String script, String expected) {
        top(); tag("browser_tab_console").click();
        tag("browser_script").setText(script);
        tag("browser_evaluate").click(); output(expected);
    }
    private void readCookies(String expected) {
        top(); tag("browser_tab_cookies").click();
        tag("browser_read_cookies").click(); output(expected);
    }

    @Test public void browserHttpsCookiesStorageEditsAndPostScopeThroughRealControls() throws Exception {
        launch();
        tag("project_name").setText("Browser UI fixture");
        tag("project_origin").setText("https://example.com");
        tag("create_project").click(); tag("nav_browser").click();
        // Capability gating is intentional; never silently share profile A/B.
        Assume.assumeFalse("Installed WebView has no isolated profiles",
                device.wait(Until.hasObject(By.res("browser_unsupported")), 2000));
        tag("browser_go").click(); output("Page loaded");
        evaluate("document.title", "Example Domain");
        // Real HTTPS loaded above. Only synthetic DOM/storage/cookies are added below.
        evaluate("document.body.innerHTML='<button id=fixture style=\"position:absolute;top:8px;left:8px\">Inspect me</button>'; localStorage.setItem('esuite-ui','local-value'); sessionStorage.setItem('session-ui','session-value'); 'Fixture ready'", "Fixture ready");
        top(); tag("browser_tab_elements").click(); tag("browser_select").click();
        top(); find(By.text("Inspect me")).click();
        tag("browser_read_selection").click(); output("BUTTON");
        tag("browser_edit").setText("<p id='replacement'>Replacement element</p>");
        tag("browser_apply_html").click(); output("HTML updated locally");
        tag("browser_read_selection").click(); output("replacement");
        tag("browser_edit").setText("color: rgb(255, 0, 0); outline: 1px solid blue");
        tag("browser_apply_css").click(); output("CSS updated locally");
        evaluate("getComputedStyle(document.getElementById('replacement')).color", "rgb(255, 0, 0)");
        top(); tag("browser_tab_storage").click(); tag("browser_read_storage").click(); output("local-value"); output("session-value");
        top(); tag("browser_tab_cookies").click();
        tag("browser_cookie_input").setText("esuite_ui=synthetic-cookie; Path=/; Secure; HttpOnly; Max-Age=3600");
        tag("browser_set_cookie").click(); output("Cookie stored");
        tag("browser_read_cookies").click(); output("esuite_ui=[HIDDEN]");
        tag("browser_reveal").click(); tag("browser_read_cookies").click(); output("synthetic-cookie");
        evaluate("document.cookie.includes('esuite_ui') ? 'LEAK' : 'HttpOnly hidden from page'", "HttpOnly hidden from page");
        readCookies("synthetic-cookie");
        tag("browser_reveal").click(); tag("browser_read_cookies").click(); output("esuite_ui=[HIDDEN]");
        tag("browser_transfer").click(); output("memory-only cookie jar replaced");
        tag("nav_api").click(); tag("api_session_metadata").click(); top();
        assertNotNull(find(By.textContains("esuite_ui")));
        // Profile A persists after leaving Browser; B remains independent.
        top(); tag("identity_B").click(); tag("nav_browser").click(); readCookies("No cookies for this URL");
        top(); tag("identity_A").click(); readCookies("esuite_ui=[HIDDEN]");
        top(); tag("browser_go").click(); output("Page loaded");
        evaluate("const f=document.createElement('form'); f.method='POST'; f.action='https://outside.invalid/blocked'; document.body.appendChild(f); f.submit(); 'Submitting fixture'", "Blocked navigation outside project scope");
        top(); tag("browser_url").setText("https://outside.invalid/"); tag("browser_go").click(); output("outside project scope");
    }

}
