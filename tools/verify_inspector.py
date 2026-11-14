"""Verify page-side inspector behavior in Chromium, without native capability claims."""
import json
import re
from pathlib import Path
from playwright.sync_api import sync_playwright

root = Path(__file__).resolve().parents[1]
source = (root / "app/src/main/java/dev/esuite/app/browser/InspectorScripts.kt").read_text()
scripts = dict(re.findall(r'val (\w+) = """(.*?)"""', source, re.S))
with sync_playwright() as p:
    browser = p.chromium.launch()
    page = browser.new_page()
    page.route("**/*", lambda route: route.fulfill(body='<html><button id="profile" onclick="window.clicked=true">Profile</button></html>', content_type="text/html"))
    page.goto("https://example.test/")
    page.evaluate("localStorage.setItem('test', 'value'); sessionStorage.setItem('session', 'one')")
    stored = json.loads(page.evaluate(scripts["storage"]))
    assert stored["localStorage"]["test"] == "value"
    assert stored["sessionStorage"]["session"] == "one"
    page.evaluate(scripts["select"])
    page.locator("#profile").click()
    selected = json.loads(page.evaluate(scripts["selection"]))
    assert selected["tag"] == "BUTTON" and 'id="profile"' in selected["html"]
    assert page.evaluate("window.clicked") is None, "Inspection click must not activate page behavior"
    page.locator("#profile").click()
    assert page.evaluate("window.clicked") is True, "Picker must detach after selection"
    # HTML replacement must retarget selection; CSS edits must affect the new node.
    page.evaluate(scripts["html"].replace("__ESUITE_VALUE__", json.dumps('<p id="replacement">Edited</p>')))
    replaced = json.loads(page.evaluate(scripts["selection"]))
    assert replaced["tag"] == "P" and 'id="replacement"' in replaced["html"]
    page.evaluate(scripts["css"].replace("__ESUITE_VALUE__", json.dumps('color: rgb(255, 0, 0); outline: 1px solid blue')))
    assert page.locator("#replacement").evaluate("e=>getComputedStyle(e).color") == "rgb(255, 0, 0)"
    page.evaluate(scripts["select"])
    page.evaluate("document.body.dispatchEvent(new MouseEvent('click', {bubbles:true}))")
    assert page.locator("#replacement").evaluate("e=>e.style.outlineColor==='blue' && e.style.outlineWidth==='1px'"), "Changing selection must preserve edited outline"
    page.evaluate(scripts["select"])
    page.locator("#replacement").click()
    page.evaluate(scripts["html"].replace("__ESUITE_VALUE__", json.dumps('text only')))
    assert page.evaluate(scripts["selection"]) == "No selected element"
    browser.close()
print("PASS: inspector selection, click suppression/detach and storage snapshots. Native WebView behavior still requires device tests.")
