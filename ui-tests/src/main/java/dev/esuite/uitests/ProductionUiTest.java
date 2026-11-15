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
        // Validate four real task inputs without sending a target request.
        click("nav_api"); enter("api_url", "https://example.test/?a={{P1}}&b={{P2}}&c={{P3}}&d={{P4}}");
        click("nav_home"); click("home_tools");
        enter("task_payload_1", "a"); enter("task_payload_2", "b");
        enter("task_payload_3", "c"); enter("task_payload_4", "d");
        click("task_preview"); assertTrue(waitText("task_preview_result", "4 requests", 10000));
        enter("task_payload_4", ""); click("task_preview");
        assertTrue(waitText("task_preview_result", "Provide values for P4", 10000));
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
