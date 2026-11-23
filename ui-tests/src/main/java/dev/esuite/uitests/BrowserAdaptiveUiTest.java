package dev.esuite.uitests;

import android.graphics.Rect;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class BrowserAdaptiveUiTest extends NativeUiDriver {
    @Test public void browserAndInspectorKeepTheirControlsAtLargeTextInBothOrientations() throws Exception {
        assertEquals("1", device.executeShellCommand("getprop ro.kernel.qemu").trim());
        String previous = device.executeShellCommand("settings get system font_scale").trim();
        try {
            device.executeShellCommand("settings put system font_scale 1.5");
            device.setOrientationNatural();
            launch();
            enter("browser_url", "https://example.com/");
            click("browser_go");
            assertNotNull(device.wait(Until.findObject(By.clazz("android.webkit.WebView")), 45_000));
            click("browser_tools");
            assertNotNull(tag("browser_panel_picker"));
            Rect portraitPage = pageBounds();
            Rect portraitDock = tag("browser_dock").getVisibleBounds();
            assertTrue("Portrait page and dock should stack", portraitPage.bottom <= portraitDock.top);
            checkpoint("browser-portrait-large-text");

            device.setOrientationLeft();
            for (int attempt = 0; attempt < 20 && device.getDisplayWidth() <= device.getDisplayHeight(); attempt++) {
                android.os.SystemClock.sleep(250);
            }
            assertTrue("Emulator did not enter landscape", device.getDisplayWidth() > device.getDisplayHeight());
            assertTrue(waitText("browser_url", "https://example.com/", 10_000));
            Rect landscapePage = pageBounds();
            Rect landscapeDock = tag("browser_dock").getVisibleBounds();
            assertTrue("Landscape page and inspector should be side by side", landscapePage.right <= landscapeDock.left);
            assertTrue("The page needs a usable viewport", landscapePage.width() >= 100 && landscapePage.height() >= 75);
            click("browser_panel_picker");
            click("browser_panel_choice_elements");
            assertNotNull(device.wait(Until.findObject(By.res("elements_node_0")), 10_000));
            checkpoint("browser-landscape-large-text");
            click("browser_close_dock");
            assertTrue(device.wait(Until.gone(By.res("browser_dock")), 5_000));
            assertNotNull(tag("browser_url"));
        } finally {
            device.setOrientationNatural();
            device.unfreezeRotation();
            if (previous.equals("null")) device.executeShellCommand("settings delete system font_scale");
            else device.executeShellCommand("settings put system font_scale " + previous);
        }
    }

    private Rect pageBounds() {
        UiObject2 page = device.findObject(By.res("browser_page"));
        if (page == null) page = device.findObject(By.clazz("android.webkit.WebView"));
        assertNotNull("The page disappeared", page);
        return page.getVisibleBounds();
    }
}
