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

/** Shared independent UI driver. No app classes and no automatic data clearing. */
public abstract class NativeUiDriver {
    protected final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

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
    protected boolean usable(UiObject2 node) {
        if(node==null) return false;
        try {
            android.graphics.Rect bounds=node.getVisibleBounds();
            if(bounds.width()<8 || bounds.height()<8) return false;
            String id=node.getResourceName();
            if(id!=null && id.startsWith("nav_")) return true;
            UiObject2 home=device.findObject(By.res("nav_home"));
            int bottom=home==null?device.getDisplayHeight():home.getVisibleBounds().top;
            // Accessibility can expose controls clipped by the scroll viewport or covered by navigation.
            return bounds.top>device.getDisplayHeight()/24 && bounds.bottom<bottom-3;
        } catch(androidx.test.uiautomator.StaleObjectException ignored) { return false; }
    }
    protected android.graphics.Rect scrollBounds() {
        if (device.hasObject(By.res("browser_tab_page")) && !device.hasObject(By.res("browser_url"))) {
            UiObject2 panel = device.findObject(By.res("browser_inspector"));
            if (panel != null && panel.getVisibleBounds().height() > 40) return panel.getVisibleBounds();
        }
        return new android.graphics.Rect(0, device.getDisplayHeight()/8, device.getDisplayWidth(), device.getDisplayHeight()*23/24);
    }
    protected boolean inspectorScroll() { return device.hasObject(By.res("browser_tab_page")) && !device.hasObject(By.res("browser_url")); }
    protected int scrollX() {
        android.graphics.Rect area = scrollBounds();
        return area.left + Math.max(2, area.width()/100);
    }
    protected UiObject2 find(BySelector selector) {
        UiObject2 visible = device.wait(Until.findObject(selector), 700);
        if (usable(visible)) return visible;
        top();
        for (int attempt = 0; attempt < 10; attempt++) {
            UiObject2 found = device.wait(Until.findObject(selector), 700);
            if (usable(found)) return found;
            android.graphics.Rect area = scrollBounds();
            device.swipe(scrollX(), inspectorScroll() ? area.top + area.height()*3/4 : device.getDisplayHeight()*3/4, scrollX(), inspectorScroll() ? area.top + area.height()/4 : device.getDisplayHeight()/3, 25);
        }
        throw new AssertionError("Native control not found: " + selector);
    }
    protected UiObject2 tag(String name) {
        if (device.hasObject(By.res("browser_tab_page")) && !device.hasObject(By.res(name))) {
            String tab = null;
            if (name.equals("browser_go") || name.equals("browser_url")) tab = "browser_tab_page";
            if (name.equals("browser_read_selection") || name.equals("browser_edit") || name.startsWith("browser_apply_")) tab = "browser_tab_elements";
            if (tab != null) { device.findObject(By.res(tab)).click(); device.waitForIdle(1000); }
        }
        return find(By.res(name));
    }
    protected int actionSequence=0;
    protected void actionEvidence(String name) {
        if(!name.equals("api_send") && !name.equals("api_compare") && !name.equals("api_session_metadata")) return;
        try {
            java.io.File dir=InstrumentationRegistry.getInstrumentation().getContext().getExternalFilesDir(null);
            if(dir!=null) {
                String label=String.format(java.util.Locale.ROOT,"%02d-%s",++actionSequence,name);
                device.takeScreenshot(new java.io.File(dir,label+".png"));
                device.dumpWindowHierarchy(new java.io.File(dir,label+".xml"));
            }
        } catch(Exception error) { throw new AssertionError("Could not retain critical UI action evidence",error); }
    }
    protected void click(String name) {
        for (int attempt=0;attempt<5;attempt++) {
            try {
                tag(name); // May scroll. Wait AFTER this lookup so a fling cannot swallow the click.
                device.waitForIdle(2000);
                UiObject2 current=device.findObject(By.res(name));
                if(!usable(current)) continue;
                android.graphics.Rect before=current.getVisibleBounds();
                android.os.SystemClock.sleep(250);
                current=device.findObject(By.res(name));
                if(!usable(current) || !before.equals(current.getVisibleBounds())) continue;
                actionEvidence(name);
                current=device.findObject(By.res(name));
                if(!usable(current) || !before.equals(current.getVisibleBounds())) continue;
                current.click();
                device.waitForIdle(1000);
                actionEvidence(name);
                return;
            } catch (androidx.test.uiautomator.StaleObjectException stale) {
                if(attempt==4) throw stale;
            }
        }
        throw new AssertionError("Control never stopped moving: "+name);
    }
    protected void enter(String name, String value) {
        tag(name).setText(value);
        // SET_TEXT can open the IME and reflow Compose; close it as a user would before pressing the action.
        if (device.hasObject(By.pkg("com.google.android.inputmethod.latin")) || device.hasObject(By.pkg("com.android.inputmethod.latin"))) device.pressBack();
        device.waitForIdle(1000);
    }
    protected void top() {
        for (int n = 0; n < 8; n++) {
            android.graphics.Rect area = scrollBounds();
            device.swipe(scrollX(), inspectorScroll() ? area.top + area.height()/4 : device.getDisplayHeight()/3, scrollX(), inspectorScroll() ? area.top + area.height()*3/4 : device.getDisplayHeight()*3/4, 25);
        }
    }
    protected void launch() throws Exception {
        device.executeShellCommand("am start -W -S -n dev.esuite.app/.MainActivity");
        UiObject2 home = device.wait(Until.findObject(By.res("nav_home")), 20000);
        assertNotNull("Release navigation did not open", home); home.click(); top();
        assertTrue("Release home did not open", device.wait(Until.hasObject(By.text("Your workspace.")), 20000));
    }

    protected boolean containsText(UiObject2 node, String expected) {
        String text=node.getText();
        if(text!=null && text.contains(expected)) return true;
        for(UiObject2 child:node.getChildren()) if(containsText(child,expected)) return true;
        return false;
    }
    protected boolean waitText(String name, String expected, long timeout) {
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
    protected void output(String expected) {
        assertTrue("Browser output missing: " + expected, waitText("browser_output", expected,15000));
    }
    protected void evaluate(String script, String expected) {
        top(); click("browser_tab_console");
        enter("browser_script", script);
        click("browser_evaluate"); output(expected);
    }
    protected void readCookies(String expected) {
        top(); click("browser_tab_cookies");
        click("browser_read_cookies"); output(expected);
    }

    protected void checkpoint(String label) throws Exception {
        java.io.File dir=InstrumentationRegistry.getInstrumentation().getContext().getExternalFilesDir(null);
        assertNotNull("Fixture evidence directory unavailable",dir);
        assertTrue("Could not capture fixture screen",device.takeScreenshot(new java.io.File(dir,label+".png")));
        device.dumpWindowHierarchy(new java.io.File(dir,label+".xml"));
    }
}
