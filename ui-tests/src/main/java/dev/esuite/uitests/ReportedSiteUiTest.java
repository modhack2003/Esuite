package dev.esuite.uitests;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
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
        assertTrue("Portfolio experience chooser did not expose its Safe mode button", device.wait(Until.hasObject(
                By.text(Pattern.compile("^use safe mode$", Pattern.CASE_INSENSITIVE))), 45000));
        checkpoint("reported-site-experience-chooser");
    }
}
