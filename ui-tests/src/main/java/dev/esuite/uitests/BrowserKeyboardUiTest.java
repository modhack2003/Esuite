package dev.esuite.uitests;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import org.junit.Test;
import org.junit.runner.RunWith;

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
                  '<p id="typed" style="margin:0"></p></main>';
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
        assertTrue("The HTTPS form fixture did not initialize", device.wait(Until.hasObject(
                By.res("console_messages").descContains("Latest result: Form ready")), 10_000));
        click("browser_close_dock");

        String previousIme = device.executeShellCommand("settings get secure default_input_method").trim();
        String previousHardwareIme = device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim();
        try {
            device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1");
            device.executeShellCommand("ime enable " + IME);
            String selected = device.executeShellCommand("ime set " + IME);
            assertTrue(selected, selected.contains("selected"));
            enterInPage("Page text", "text", false);
            enterInPage("Page password", "password", false);
            click("browser_tools");
            click("browser_panel_picker");
            click("browser_panel_choice_page");
            enterInPage("Page notes", "notes", false);
            assertNotNull("Back closed the dock instead of the keyboard", device.findObject(By.res("browser_dock")));
            click("browser_close_dock");
            enterInPage("Page editable", "editable", true);
            checkpoint("website-ime");
        } finally {
            if (!previousIme.isBlank() && !previousIme.equals("null")) device.executeShellCommand("ime set " + previousIme);
            device.executeShellCommand("ime disable " + IME);
            if (previousHardwareIme.equals("null")) device.executeShellCommand("settings delete secure show_ime_with_hard_keyboard");
            else device.executeShellCommand("settings put secure show_ime_with_hard_keyboard " + previousHardwareIme);
        }
    }

    private void enterInPage(String label, String field, boolean paste) {
        UiObject2 input = device.wait(Until.findObject(By.desc(label)), 3_000);
        if (input == null) input = device.wait(Until.findObject(By.text(label)), 5_000);
        assertNotNull("Website editor is missing: " + label, input);
        input.click();
        UiObject2 key = device.wait(Until.findObject(By.text(paste ? "Paste fixture text" : "Type fixture text")), 8_000);
        assertNotNull("Tapping " + label + " did not open the software keyboard", key);
        key.click();
        device.pressBack();
        assertTrue("Keyboard stayed open", device.wait(Until.gone(By.text("Type fixture text")), 5_000));
        assertTrue("The DOM did not receive committed text for " + label,
                device.wait(Until.hasObject(By.text("Received " + field)), 8_000));
    }
}
