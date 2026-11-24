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
            route.fulfill(body='''<html lang="en"><head><meta name="viewport" content="width=device-width,initial-scale=1">
                <style>#profile { padding: 7px; color: rgb(0, 60, 120) } @media(max-width:1px) { #profile { color: red } }</style></head>
                <main><!-- fixture comment --><h1>Fixture heading</h1><button id="profile" onclick="window.clicked=true">Profile</button>
                    <input id="fixture-input" aria-label="Fixture input"></main>
                <script src="/assets/fixture.js?fixture=private"></script></html>''', content_type="text/html")
    page.route("**/*", serve)
    page.goto("https://example.test/")
    page.evaluate("localStorage.setItem('test', 'value'); sessionStorage.setItem('session', 'one')")
    def read_tree(expanded=None, search='', reveal=False):
        options = json.dumps({'expanded': expanded or {}, 'query': search, 'reveal': reveal})
        return json.loads(page.evaluate(scripts['elementTree'].replace('__ESUITE_TREE_OPTIONS__', options)))
    rows = read_tree()['rows']
    assert rows[0]['tag'] == 'html' and rows[0]['expanded']
    body = next(row for row in rows if row['tag'] == 'body')
    assert not body['expanded'] and not any(row['tag'] == 'main' for row in rows)
    main = next(row for row in read_tree({body['id']: 24})['rows'] if row['tag'] == 'main')
    rows = read_tree({body['id']: 24, main['id']: 24})['rows']
    button = next(row for row in rows if {'name': 'id', 'value': 'profile'} in row['attributes'])
    assert button['parent'] == main['id'] and button['depth'] == 3
    assert any(row['comment'] and 'fixture comment' in row['text'] for row in rows)
    assert any(row['tag'] == 'input' and row['voidTag'] for row in rows)
    found = read_tree(search='fixture input')['rows']
    assert [row['tag'] for row in found] == ['html', 'body', 'main', 'input']
    assert 'window.clicked' not in json.dumps(rows)
    assert page.evaluate(scripts["selectTreeElement"].replace('__ESUITE_NODE__', str(button['id']))) == 'Selected'
    assert json.loads(page.evaluate(scripts["selection"]))['tag'] == 'BUTTON'
    page.evaluate('document.querySelector("#profile").remove()')
    assert 'Refresh the tree' in page.evaluate(scripts["selectTreeElement"].replace('__ESUITE_NODE__', str(button['id'])))
    page.evaluate("document.querySelector('main').insertAdjacentHTML('beforeend', '<button id=profile onclick=window.clicked=true>Profile</button>')")
    source_list = page.evaluate(scripts["sourceFiles"])
    assert "/assets/fixture.js" in source_list and "private" not in source_list
    timing = json.loads(page.evaluate(scripts["networkTiming"]))['rows']
    asset = next(row for row in timing if row['path'] == '/assets/fixture.js')
    assert asset['kind'] == 'script' and asset['duration'] >= 0 and asset['start'] >= 0
    assert asset['redirect'] is None and asset['serviceWorker'] is None
    assert asset['renderBlocking'] in ('blocking', 'non-blocking', '')
    assert asset['contentType'] in ('text/javascript', '')
    assert isinstance(asset['delivery'], str)
    assert 'private' not in json.dumps(timing) and any(row['kind'] == 'document' for row in timing)
    page.evaluate("Promise.all([fetch('/repeat?fixture=private'),fetch('/repeat?fixture=private')].map(r => r.then(response => response.text())))")
    page.wait_for_function("performance.getEntriesByType('resource').filter(row => row.name.includes('/repeat?')).length === 2")
    repeated = [row for row in json.loads(page.evaluate(scripts['networkTiming']))['rows'] if row['path'] == '/repeat']
    assert len(repeated) == 2 and repeated[0]['id'] != repeated[1]['id']
    assert repeated == [row for row in json.loads(page.evaluate(scripts['networkTiming']))['rows'] if row['path'] == '/repeat']
    page.evaluate("performance.setResourceTimingBufferSize(performance.getEntriesByType('resource').length)")
    page.evaluate("fetch('/after-buffer-filled').then(response => response.text())")
    page.wait_for_function("window.__esuiteTimings.recent.some(row => row.name.endsWith('/after-buffer-filled'))")
    assert any(row['path'] == '/after-buffer-filled' for row in json.loads(page.evaluate(scripts['networkTiming']))['rows'])

    page.evaluate("document.querySelector('main').insertAdjacentHTML('beforeend','<section id=large>' + '<i>row</i>'.repeat(90) + '</section>')")
    large = next(row for row in read_tree(search='large')['rows'] if row['tag'] == 'section')
    first_batch = read_tree({body['id']: 24, main['id']: 24, large['id']: 24})['rows']
    assert sum(row['parent'] == large['id'] for row in first_batch) == 24
    assert next(row for row in first_batch if row['id'] == large['id'])['more']
    second_batch = read_tree({body['id']: 24, main['id']: 24, large['id']: 48})['rows']
    assert sum(row['parent'] == large['id'] for row in second_batch) == 48
    assert re.search(r"Timed resources · [1-9][0-9]*", page.evaluate(scripts["performanceSummary"]))
    assert "Viewport declaration · width=device-width" in page.evaluate(scripts["viewportSummary"])
    assert "Secure context · Yes" in page.evaluate(scripts["securitySummary"])
    semantics = page.evaluate(scripts["accessibilitySummary"])
    assert "H1 · Fixture heading" in semantics and "Landmarks · 1 shown" in semantics
    # Exercise the WebView fallback with a fixture that reproduces a collapsed dynamic-height dialog.
    page.evaluate("""() => {
        const style = document.createElement('style');
        style.textContent = '[role=dialog] {position:fixed;top:12px;width:240px;max-height:0;overflow-y:auto;padding:40px 0}';
        document.head.appendChild(style);
        const dialog = document.createElement('div');
        dialog.setAttribute('role', 'dialog');
        dialog.className = 'max-h-[90dvh]';
        dialog.innerHTML = '<p style="height:260px">A clipped dialog</p><button>Continue</button>';
        document.body.appendChild(dialog);
    }""")
    dialog = page.get_by_role('dialog')
    assert dialog.bounding_box()['height'] < 100
    assert page.evaluate(scripts["fitClippedDialogs"]) == 1
    assert dialog.bounding_box()['height'] > 240
    assert page.evaluate(scripts["fitClippedDialogs"]) == 0
    dialog.evaluate('element => element.remove()')
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
    assert page.evaluate(scripts["pickResult"]) == 'Picked'
    selected = json.loads(page.evaluate(scripts["selection"]))
    assert selected["tag"] == "BUTTON" and 'id="profile"' in selected["html"]
    assert selected["htmlTruncated"] is False
    assert len(selected['bounds']) == 4 and any(row['name'] == 'color' for row in selected['computed'])
    page.evaluate(scripts["highlightSelection"])
    for width in range(240, 250):
        page.locator("#profile").evaluate("(element, width) => element.style.width = width + 'px'", width)
        page.wait_for_function("""() => {
            const actual = window.__esuiteElement.getBoundingClientRect().width;
            return window.__esuiteHighlight.node.getBoundingClientRect().width === actual;
        }""", timeout=2000)
    page.locator("#profile").evaluate("element => element.style.removeProperty('width')")
    assert any(row['name'] == 'padding' for row in selected['layout'])
    assert any(rule['name'] == '#profile' and 'padding: 7px' in rule['value'] for rule in selected['rules'])
    assert all('color: red' not in rule['value'] for rule in selected['rules'])
    assert 'outline' not in selected['css'], 'The authored inline CSS should omit the inspector highlight'
    for index in range(10):
        page.locator("#profile").evaluate("""(element, index) => {
            element.textContent = '';
            const opening = element.outerHTML.indexOf('</button>');
            element.textContent = 'a'.repeat(index) + 'x'.repeat(15999 - opening - index) + '🧪';
        }""", index)
        clipped = json.loads(page.evaluate(scripts["selection"]))
        assert clipped["htmlTruncated"] and len(clipped["html"]) == clipped["htmlLimit"] - 1
        assert clipped["html"][-1] == "x", "The markup preview must omit a split surrogate pair"
    page.locator("#profile").evaluate("e=>e.textContent='x'.repeat(17000)")
    partial = json.loads(page.evaluate(scripts["selection"]))
    assert partial["htmlTruncated"] is True and partial["htmlCharacters"] > 17000
    assert len(partial["html"]) == partial["htmlLimit"] == 16000
    page.locator("#profile").evaluate("e=>e.textContent='Profile'")
    assert page.evaluate("window.clicked") is None, "Inspection click must not activate page behavior"
    page.locator("#profile").click()
    assert page.evaluate("window.clicked") is True, "Picker must detach after selection"
    # HTML replacement must retarget selection; CSS edits must affect the new node.
    def edit_element(kind, old, new):
        values = {'__ESUITE_EXPECTED__': json.dumps(old), '__ESUITE_VALUE__': json.dumps(new)}
        return page.evaluate(re.sub(r'__ESUITE_(?:EXPECTED|VALUE)__', lambda hit: values[hit[0]], scripts[kind]))
    def edit_html(old, new):
        return edit_element('html', old, new)
    assert 'changed' in edit_html('<button>stale</button>', '<p>Wrong</p>')
    old = json.loads(page.evaluate(scripts['selection']))['html']
    assert edit_html(old, '<p id="replacement">Edited</p>') == 'HTML updated locally'
    replaced = json.loads(page.evaluate(scripts["selection"]))
    assert replaced["tag"] == "P" and 'id="replacement"' in replaced["html"]
    page.locator("#replacement").evaluate("e => e.style.setProperty('padding', '3px')")
    assert 'changed the inline style' in edit_element('css', replaced['css'], 'color: red')
    assert page.locator("#replacement").evaluate("e => e.style.padding") == '3px'
    current = json.loads(page.evaluate(scripts["selection"]))
    assert edit_element('css', current['css'], 'color: rgb(255, 0, 0); outline: 1px solid blue') == 'CSS updated locally'
    assert page.locator("#replacement").evaluate("e=>getComputedStyle(e).color") == "rgb(255, 0, 0)"
    page.evaluate(scripts["select"])
    page.evaluate("document.body.dispatchEvent(new MouseEvent('click', {bubbles:true}))")
    page.evaluate(scripts['highlightSelection'])
    assert page.locator("#replacement").evaluate("e=>e.style.outlineColor==='blue' && e.style.outlineWidth==='1px'"), "Changing selection must preserve edited outline"
    page.evaluate(scripts["select"])
    page.locator("#replacement").click()
    page.evaluate(scripts["highlightSelection"])
    page.evaluate("""() => {
        const highlight = window.__esuiteHighlight;
        window.fixtureHighlight = highlight.node;
        window.fixtureDisconnected = [];
        for (const [name, observer] of [['resize', highlight.resizeObserver], ['removal', highlight.removalObserver]]) {
            const disconnect = observer.disconnect.bind(observer);
            observer.disconnect = () => { window.fixtureDisconnected.push(name); disconnect(); };
        }
    }""")
    page.evaluate(scripts["clearSelection"])
    assert page.evaluate("window.fixtureDisconnected.sort().join(',') === 'removal,resize' && !window.fixtureHighlight.isConnected")
    assert json.loads(page.evaluate(scripts["selection"])) is None
    page.evaluate(scripts["select"])
    page.locator("#replacement").click()
    assert edit_html(json.loads(page.evaluate(scripts['selection']))['html'], 'text only') == 'HTML updated locally'
    assert json.loads(page.evaluate(scripts["selection"])) is None
    page.evaluate(scripts["select"])
    page.evaluate(scripts["clearSelection"])
    assert page.evaluate("window.__esuitePicker === undefined")
    browser.close()
print("PASS: element selection and editing, storage, source paths, timings, viewport, security and document semantics.")
