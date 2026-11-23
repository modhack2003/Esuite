"""Verify page-side inspector behavior in Chromium, without native capability claims."""
import json
import os
import re
from pathlib import Path
from playwright.sync_api import sync_playwright

root = Path(__file__).resolve().parents[1]
source = (root / "app/src/main/java/dev/esuite/app/browser/InspectorScripts.kt").read_text()
scripts = dict(re.findall(r'val (\w+) = """(.*?)"""', source, re.S))
with sync_playwright() as p:
    options = {"executable_path": os.environ["ESUITE_CHROMIUM_PATH"]} if "ESUITE_CHROMIUM_PATH" in os.environ else {}
    browser = p.chromium.launch(**options)
    page = browser.new_page()
    def serve(route):
        if '/assets/fixture.js' in route.request.url:
            route.fulfill(body='window.fixtureLoaded = true;', content_type='text/javascript')
        else:
            route.fulfill(body='''<html lang="en"><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                <main><h1>Fixture heading</h1><button id="profile" onclick="window.clicked=true">Profile</button></main>
                <script src="/assets/fixture.js?fixture=private"></script></html>''', content_type="text/html")
    page.route("**/*", serve)
    page.goto("https://example.test/")
    page.evaluate("localStorage.setItem('test', 'value'); sessionStorage.setItem('session', 'one')")
    outline = page.evaluate(scripts["documentOutline"])
    assert "HTML" in outline and "BODY" in outline and "BUTTON#profile" in outline
    assert "window.clicked" not in outline
    source_list = page.evaluate(scripts["sourceFiles"])
    assert "/assets/fixture.js" in source_list and "private" not in source_list
    timing = page.evaluate(scripts["networkTiming"])
    assert "script /assets/fixture.js" in timing and "private" not in timing
    assert re.search(r"Timed resources · [1-9][0-9]*", page.evaluate(scripts["performanceSummary"]))
    assert "Viewport declaration · width=device-width" in page.evaluate(scripts["viewportSummary"])
    assert "Secure context · Yes" in page.evaluate(scripts["securitySummary"])
    semantics = page.evaluate(scripts["accessibilitySummary"])
    assert "H1 · Fixture heading" in semantics and "Landmarks · 1 shown" in semantics
    metadata = page.evaluate(scripts["storageMetadata"])
    assert "test · 5 characters" in metadata and "session · 3 characters" in metadata
    assert "value" not in metadata and "one" not in metadata
    stored = json.loads(page.evaluate(scripts["storage"]))
    assert stored["localStorage"]["entries"][0]["value"] == "value"
    assert stored["sessionStorage"]["entries"][0]["value"] == "one"
    page.evaluate("""() => {
        localStorage.clear();
        localStorage.setItem('exact', 'x'.repeat(4096));
        localStorage.setItem('large', 'x'.repeat(4095) + '🧪');
    }""")
    entries = {entry["key"]: entry for entry in json.loads(page.evaluate(scripts["storage"]))["localStorage"]["entries"]}
    assert entries['exact']['valueCharacters'] == len(entries['exact']['value']) == 4096
    assert entries['exact']['valueTruncated'] is False
    assert entries['large']['valueCharacters'] == 4097 and entries['large']['valueTruncated'] is True
    assert entries['large']['value'] == 'x' * 4095, "Storage preview must preserve surrogate boundaries"
    page.evaluate("""() => {
        localStorage.clear();
        for (let i = 0; i < 101; i++) localStorage.setItem('key' + i, String.fromCharCode(1).repeat(4097));
    }""")
    bounded = page.evaluate(scripts["storage"])
    assert len(bounded) < 24000, "Page-side output must fit the native display cap"
    summary = json.loads(bounded)["localStorage"]
    assert summary["totalKeys"] == 101
    assert 0 < summary["returnedKeys"] == len(summary["entries"]) <= 100
    assert summary["omittedKeys"] + summary["returnedKeys"] == summary["totalKeys"]
    assert all(entry["valueTruncated"] for entry in summary["entries"])
    page.evaluate(scripts["select"])
    page.locator("#profile").click()
    selected = json.loads(page.evaluate(scripts["selection"]))
    assert selected["tag"] == "BUTTON" and 'id="profile"' in selected["html"]
    assert selected["htmlTruncated"] is False
    page.locator("#profile").evaluate("e=>e.textContent='x'.repeat(17000)")
    partial = json.loads(page.evaluate(scripts["selection"]))
    assert partial["htmlTruncated"] is True and partial["htmlCharacters"] > 17000
    assert len(partial["html"]) == partial["htmlLimit"] == 16000
    page.locator("#profile").evaluate("e=>e.textContent='Profile'")
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
    page.evaluate(scripts["select"])
    page.evaluate(scripts["clearSelection"])
    assert page.evaluate("window.__esuitePicker === undefined")
    browser.close()
print("PASS: element selection and editing, storage, source paths, timings, viewport, security and document semantics.")
