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
    browser.close()
print("PASS: inspector selection, click suppression/detach and storage snapshots. Native WebView behavior still requires device tests.")
