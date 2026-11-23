package dev.esuite.uitests;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.SystemClock;
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
        boolean visible = device.wait(Until.hasObject(By.text(safeMode)), 8000) || device.hasObject(By.desc(safeMode));
        // WebView can paint the chooser before Android exposes the button in its accessibility tree.
        if (!visible) visible = awaitSafeModeButtonPaint();
        checkpoint(visible ? "reported-site-experience-chooser" : "reported-site-clipped");
        if (!visible) recordViewport();
        assertTrue("Portfolio experience chooser did not expose its Safe mode button", visible);
    }

    private boolean awaitSafeModeButtonPaint() {
        long deadline = SystemClock.uptimeMillis() + 20000;
        while (SystemClock.uptimeMillis() < deadline) {
            UiObject2 webView = device.findObject(By.clazz("android.webkit.WebView"));
            if (webView != null && hasRedButton(webView.getVisibleBounds())) return true;
            SystemClock.sleep(350);
        }
        return false;
    }

    private boolean hasRedButton(Rect bounds) {
        Bitmap screenshot = pixelScreenshot();
        if (screenshot == null) return false;
        try {
            int filledRows = 0;
            for (int y = bounds.top; y < Math.min(bounds.bottom, screenshot.getHeight()); y++) {
                int run = 0;
                boolean filled = false;
                for (int x = bounds.left; x < Math.min(bounds.right, screenshot.getWidth()); x++) {
                    int color = screenshot.getPixel(x, y);
                    run = Color.red(color) > 180 && Color.green(color) < 75 && Color.blue(color) < 75 ? run + 1 : 0;
                    if (run >= bounds.width() / 4) { filled = true; break; }
                }
                filledRows = filled ? filledRows + 1 : 0;
                // The filled Safe mode button spans many rows. Glitch decorations and borders are thin.
                if (filledRows >= 14) return true;
            }
            return false;
        } finally {
            screenshot.recycle();
        }
    }

    private void recordViewport() throws Exception {
        click("browser_tools");
        Rect grip = tag("dock_resize_handle").getVisibleBounds();
        device.swipe(grip.centerX(), grip.centerY(), grip.centerX(), grip.centerY() - device.getDisplayHeight() / 7, 30);
        click("browser_tool_tab_console");
        enter("console_input", "(() => { const d = document.querySelector('[role=dialog]'); const p = document.createElement('div'); " +
                "p.style.cssText = 'height:100dvh;position:fixed;visibility:hidden'; document.documentElement.appendChild(p); " +
                "const metrics = [innerWidth,innerHeight,visualViewport.height,p.getBoundingClientRect().height," +
                "d && getComputedStyle(d).maxHeight,d && d.getBoundingClientRect().height,d && d.scrollHeight,d && d.style.maxHeight]; p.remove();" +
                "return 'Viewport metrics ' + metrics.join(', '); })()");
        click("console_run");
        UiObject2 result = device.wait(Until.findObject(By.res("console_result").textContains("Viewport metrics")), 12000);
        if (result != null) System.out.println(result.getText());
        checkpoint("reported-site-viewport-metrics");
    }
}
