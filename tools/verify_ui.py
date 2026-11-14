"""Exercise the actual bundled concept without a server or target requests.

Install: python -m pip install playwright==1.51.0
Then: python -m playwright install chromium
Run: python tools/verify_ui.py [--screenshots]
Optional: ESUITE_CHROMIUM_PATH=/path/to/chromium for a local browser.
"""
import argparse
import os
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[1]


def verify(screenshots: bool = False) -> None:
    with sync_playwright() as playwright:
        options = {}
        if os.environ.get("ESUITE_CHROMIUM_PATH"):
            options["executable_path"] = os.environ["ESUITE_CHROMIUM_PATH"]
        browser = playwright.chromium.launch(**options)
        page = browser.new_page(viewport={"width": 1440, "height": 1080})
        errors = []
        outbound = []
        page.on("pageerror", lambda error: errors.append(str(error)))
        page.on("request", lambda request: outbound.append(request.url) if request.url.startswith(("https://", "http://")) else None)
        page.goto((ROOT / "docs/ui/index.html").as_uri())
        device = page.locator(".es-device")
        expect(device.get_by_text("Your workspace.", exact=True)).to_be_visible()
        if screenshots:
            page.screenshot(path=str(ROOT / "docs/assets/ui-overview.png"), full_page=True)

        nav = page.get_by_role("navigation", name="App navigation")
        nav.get_by_role("button", name="Browser", exact=True).click()
        device.get_by_role("button", name="Cookies", exact=True).click()
        expect(device.get_by_text("session_demo", exact=True)).to_be_visible()
        expect(device.get_by_text("HttpOnly", exact=True)).to_be_visible()
        if screenshots:
            device.screenshot(path=str(ROOT / "docs/assets/ui-browser.png"))

        nav.get_by_role("button", name="Traffic", exact=True).click()
        page.get_by_role("textbox", name="Filter traffic").fill("POST")
        expect(device.locator(".es-request")).to_have_count(1)
        page.get_by_role("textbox", name="Filter traffic").fill("no-match")
        expect(device.get_by_text("No fixture requests match this filter.")).to_be_visible()
        page.get_by_role("textbox", name="Filter traffic").fill("")
        expect(device.locator(".es-request")).to_have_count(5)
        device.get_by_role("button", name="Start demo", exact=True).click()
        expect(device.get_by_text("Demo capture running", exact=True)).to_be_visible()
        device.get_by_role("button", name="Pause", exact=True).click()
        if screenshots:
            device.screenshot(path=str(ROOT / "docs/assets/ui-traffic.png"))

        device.locator(".es-request").first.click()
        expect(page.get_by_role("dialog", name="Request details")).to_be_visible()
        expect(page.get_by_role("button", name="Close request details")).to_be_focused()
        page.keyboard.press("Escape")
        expect(page.get_by_role("dialog")).to_have_count(0)
        device.locator(".es-request").first.click()
        page.get_by_role("button", name="Send to API Lab").click()
        expect(page.get_by_role("textbox", name="Request editor")).to_have_value("GET /api/profile HTTP/1.1\nHost: api.esuite.test\nAccept: application/json")
        page.get_by_role("button", name="Simulate send", exact=True).click()
        expect(device.locator("pre")).to_contain_text("user_a")
        page.get_by_role("combobox", name="Test identity").select_option("Account B")
        page.get_by_role("button", name="Simulate send", exact=True).click()
        expect(device.locator("pre")).to_contain_text("user_b")
        page.get_by_role("button", name="Compare A / B", exact=True).click()
        expect(device.get_by_text("Identity comparison", exact=True)).to_be_visible()
        page.get_by_role("button", name="Dismiss notice").click()
        if screenshots:
            device.screenshot(path=str(ROOT / "docs/assets/ui-api.png"))

        nav.get_by_role("button", name="Home", exact=True).click()
        device.get_by_role("button", name="Tools Decode & understand").click()
        input_box = page.get_by_role("textbox", name="Decoder input")
        output_box = page.get_by_role("textbox", name="Decoder output")
        input_box.fill("বাংলা 🔎")
        device.get_by_role("button", name="Encode", exact=True).click()
        encoded = output_box.input_value()
        input_box.fill(encoded)
        device.get_by_role("button", name="Decode", exact=True).click()
        expect(output_box).to_have_value("বাংলা 🔎")
        input_box.fill("%%invalid%%")
        device.get_by_role("button", name="Decode", exact=True).click()
        expect(output_box).to_have_value("Invalid Base64 or UTF-8 input.")

        nav.get_by_role("button", name="Findings", exact=True).click()
        note_button = device.get_by_role("button", name="Add demo note", exact=True)
        expect(note_button).to_be_disabled()
        page.get_by_role("textbox", name="Finding note").fill("Verify ownership checks using two test accounts.")
        note_button.click()
        expect(device.get_by_text("Verify ownership checks using two test accounts.", exact=True)).to_be_visible()
        page.reload()
        nav.get_by_role("button", name="Findings", exact=True).click()
        expect(device.get_by_text("No notes yet.", exact=False)).to_be_visible()

        page.set_viewport_size({"width": 390, "height": 1000})
        nav.get_by_role("button", name="Home", exact=True).click()
        assert page.evaluate("document.documentElement.scrollWidth <= window.innerWidth"), "Mobile page overflows horizontally"
        nav.get_by_role("button", name="Traffic", exact=True).click()
        expect(device.locator(".es-request")).to_have_count(5)
        assert not errors, f"Browser errors: {errors}"
        assert not outbound, f"Unexpected external requests: {outbound}"
        browser.close()
        print("PASS: navigation, inspector fixtures, traffic filters, request transfer, identity fixtures, comparison, Unicode decoder, errors, notes, modal focus/Escape, mobile overflow and no HTTP requests")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--screenshots", action="store_true")
    verify(parser.parse_args().screenshots)
