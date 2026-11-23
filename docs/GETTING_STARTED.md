# Use ESuite on Android 9–17

Install the production-signed APK from [GitHub Releases](https://github.com/modhack2003/Esuite/releases/latest). An update with the same production certificate retains encrypted saved data. See the [1.0.6 release notes](RELEASE_1.0.6.md) for the browser tools and their checks.

## Browse and inspect a page

1. Open ESuite to Browser. Enter an HTTPS address or type search terms for DuckDuckGo. Use Go or the keyboard action to load it.
2. Use Back, Forward and Reload to navigate. The Reload control becomes Stop while a page is loading.
3. Open **Developer Tools** from the toolbar or press **F12** on a hardware keyboard. Drag the divider to resize the dock. It opens below the page on phones and beside it in wider layouts. The main Tools tab uses the same dock for converters, imports, and offline review.
4. Use **Elements** to see the document outline or pick an element in the live page. Read its HTML and computed styles, then edit inline CSS or replace the selected HTML. The changes apply to the current document.
5. In **Console**, run an expression like `document.title` or choose Script for multiple statements. Use `return` to show a script result and `await` for Promises. **Run** or **Ctrl+Enter** executes the input. Warnings and errors carry their own colors and labels.
6. **Network** shows recent same-origin methods, paths, and available resource timings. **Sources**, **Performance**, **Viewport**, **Security**, and **Accessibility** summarize the current page. **Cookies** lists names and available flags, while **Storage** lists keys and value lengths.

The browser profile belongs to ESuite and uses Android's system certificate validation. Settings shows the installed WebView version and provides Clear browsing data.

## Review and save site activity

- From Developer tools, use **Open in API Lab** to copy the page URL to a fresh request. You can also enter an HTTPS site directly from API Lab.
- Review the method, URL, headers and body before pressing **Send request**. API Lab checks the exact origin. **Session** manages separate API identities. Review embedded credentials in the request before using **Compare A and B**.
- **Activity** shows previously saved exchanges by site. **Findings** stores local observations and retained evidence. Search observations by title or note and open one to preview its report.
- **Tools** groups encoding, request imports and offline review. Imported requests open as drafts for manual review.

## Settings and local data

The Browser toolbar opens Settings. Choose System, Light or Dark appearance and turn interface animations on or off. Browsing data controls clear ESuite's browser cookies, storage and history. Saved site data controls manage the encrypted requests, activity, observations and report exports independently. About displays the app version, WebView details and credits.
