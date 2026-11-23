package dev.esuite.uitests;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertTrue;

/** Optional compatibility check for the live page reported with the clipped chooser. */
@RunWith(AndroidJUnit4.class)
public final class ReportedSiteUiTest extends NativeUiDriver {
    @Test public void portfolioChooserShowsItsSafeModeButton() throws Exception {
        launch();
        enter("browser_url", "https://bikram-cyber.vercel.app/");
        click("browser_go");
        Pattern safeMode = Pattern.compile("^use safe mode$", Pattern.CASE_INSENSITIVE);
        boolean visible = device.wait(Until.hasObject(By.text(safeMode)), 35000) || device.hasObject(By.desc(safeMode));
        checkpoint(visible ? "reported-site-experience-chooser" : "reported-site-clipped");
        if (!visible) recordViewport();
        assertTrue("Portfolio experience chooser did not expose its Safe mode button", visible);
    }

    private void recordViewport() throws Exception {
        click("browser_tools");
        click("browser_tool_tab_console");
        enter("console_input", "(() => { const d = document.querySelector('[role=dialog]'); const p = document.createElement('div'); " +
                "p.style.cssText = 'height:100dvh;position:fixed;visibility:hidden'; document.documentElement.appendChild(p); " +
                "const metrics = [innerWidth,innerHeight,visualViewport.height,p.getBoundingClientRect().height," +
                "d && getComputedStyle(d).maxHeight,d && d.getBoundingClientRect().height,d && d.scrollHeight]; p.remove();" +
                "return 'Viewport metrics ' + metrics.join(', '); })()");
        click("console_run");
        UiObject2 result = device.wait(Until.findObject(By.res("console_result").textContains("Viewport metrics")), 12000);
        if (result != null) System.out.println(result.getText());
        checkpoint("reported-site-viewport-metrics");
    }
}
