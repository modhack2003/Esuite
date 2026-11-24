package dev.esuite.uitests;

import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class BrowserKeyboardUiTest extends NativeUiDriver {
    private static final String IME = "dev.esuite.uitests/.FixtureIme";

    @Test public void websiteFieldsReceiveRealImeInputWithAndWithoutTools() throws Exception {
        assertEquals("1", device.executeShellCommand("getprop ro.kernel.qemu").trim());
        launch();
        enter("browser_url", "https://example.com/");
        click("browser_go");
        assertNotNull(device.wait(Until.findObject(By.clazz("android.webkit.WebView")), 45_000));
        click("browser_tools");
        click("browser_panel_picker");
        click("browser_panel_choice_console");
        click("console_mode");
        enter("console_input", """
                document.body.style.cssText = 'display:block;margin:0;padding:0;width:100%;max-width:none';
                document.body.innerHTML = '<main style="padding:4px;display:grid;gap:4px">' +
                  '<input aria-label="Page text" placeholder="Page text" data-field="text">' +
                  '<input aria-label="Page password" placeholder="Page password" data-field="password" type="password">' +
                  '<textarea aria-label="Page notes" placeholder="Page notes" data-field="notes" rows="1"></textarea>' +
                  '<div aria-label="Page editable" contenteditable="true" data-field="editable" ' +
                  'style="min-height:24px;border:1px solid">Page editable</div>' +
                  '<p id="typed" style="position:fixed;top:0;right:0;margin:0;pointer-events:none"></p></main>';
                document.body.addEventListener('input', event => {
                  const field = event.target;
                  const value = typeof field.value === 'string' ? field.value : field.textContent;
                  if (value.indexOf('Esuite42') !== -1) {
                    document.getElementById('typed').textContent = 'Received ' + field.dataset.field;
                  }
                });
                return 'Form ready';
                """);
        click("console_run");
        assertNotNull("The HTTPS form fixture did not initialize", findWebForm());
        click("browser_close_dock");

        String previousIme = device.executeShellCommand("settings get secure default_input_method").trim();
        String previousHardwareIme = device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim();
        try {
            device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1");
            device.executeShellCommand("ime enable " + IME);
            String selected = device.executeShellCommand("ime set " + IME);
            assertTrue(selected, selected.contains("selected"));
            enterInPage(0, "text", false);
            enterInPage(1, "password", false);
            click("browser_tools");
            click("browser_panel_picker");
            click("browser_panel_choice_page");
            enterInPage(2, "notes", false);
            assertNotNull("Back closed the dock instead of the keyboard", device.findObject(By.res("browser_dock")));
            click("browser_close_dock");
            enterInPage(3, "editable", true);
            checkpoint("website-ime");
        } finally {
            if (!previousIme.isBlank() && !previousIme.equals("null")) device.executeShellCommand("ime set " + previousIme);
            device.executeShellCommand("ime disable " + IME);
            if (previousHardwareIme.equals("null")) device.executeShellCommand("settings delete secure show_ime_with_hard_keyboard");
            else device.executeShellCommand("settings put secure show_ime_with_hard_keyboard " + previousHardwareIme);
        }
    }

    private void enterInPage(int position, String field, boolean paste) throws Exception {
        // Chromium exposes placeholders as accessibility hints, which UiAutomator cannot query.
        UiObject2 webPage = findWebForm();
        assertNotNull("Website form is missing", webPage);
        List<UiObject2> fields = webPage.findObjects(By.clazz("android.widget.EditText"));
        // Legacy Chromium exposes a contenteditable element as a generic focusable View.
        UiObject2 input = paste ? webPage.findObject(By.text("Page editable").focusable(true)) : null;
        if (input == null && fields.size() > position) input = fields.get(position);
        assertNotNull("Website editor is missing: " + field, input);
        input.click();
        String keyLabel = paste ? "Paste fixture text" : "Type fixture text";
        assertTrue("Tapping the website " + field + " field did not open the software keyboard",
                device.wait(Until.hasObject(By.text(keyLabel)), 8_000));
        device.waitForIdle(2_000);
        // Wait out the IME window transition and reacquire the key at its settled position.
        SystemClock.sleep(250);
        UiObject2 key = device.wait(Until.findObject(By.text(keyLabel).clickable(true)), 5_000);
        assertNotNull("Keyboard action was not available for " + field, key);
        key.click();
        if (!device.wait(Until.hasObject(By.text("Received " + field)), 15_000)) {
            checkpoint("website-ime-commit-" + field);
            String connectionState = device.executeShellCommand("logcat -d -t 30 -s ESuiteFixtureIme:I");
            fail("The DOM did not report committed text for " + field + " on " + device.getCurrentPackageName()
                    + "\n" + connectionState);
        }
        device.pressBack();
        assertTrue("Keyboard stayed open", device.wait(Until.gone(By.text("Type fixture text")), 5_000));
    }

    private UiObject2 findWebForm() {
        return device.wait(Until.findObject(By.clazz("android.webkit.WebView")
                .hasDescendant(By.clazz("android.widget.EditText"))), 10_000);
    }
}
