# Use ESuite on Android 9–17

Install the production-signed APK from [GitHub Releases](https://github.com/modhack2003/Esuite/releases/latest). An update with the same production certificate retains encrypted saved data. See the [1.0.5 release notes](RELEASE_1.0.5.md) for the Browser refresh and its checks.

## Browse and inspect a page

1. Open ESuite to Browser. Enter an HTTPS address or type search terms for DuckDuckGo. Use Go or the keyboard action to load it.
2. Use Back, Forward and Reload to navigate. The Reload control becomes Stop while a page is loading.
3. Open Developer tools after a page loads. Page shows the document title, Elements shows the document outline, Cookies lists names and supported flags, Storage lists keys and value lengths, and Console shows recent messages.
4. Network shows recent request methods and paths for the current origin. It uses a bounded in-memory buffer and omits query strings, headers and bodies.

The browser profile belongs to ESuite and uses Android's system certificate validation. Settings shows the installed WebView version and provides Clear browsing data.

## Review and save site activity

- From Developer tools, use **Open in API Lab** to copy the page URL to a fresh request. You can also enter an HTTPS site directly from API Lab.
- Review the method, URL, headers and body before pressing **Send request**. API Lab checks the exact origin. **Session** manages separate API identities. Review embedded credentials in the request before using **Compare A and B**.
- **Activity** shows previously saved exchanges by site. **Findings** stores local observations and retained evidence. Search observations by title or note and open one to preview its report.
- **Tools** groups encoding, request imports and offline review. Imported requests open as drafts for manual review.

## Settings and local data

The Browser toolbar opens Settings. Choose System, Light or Dark appearance and turn interface animations on or off. Browsing data controls clear ESuite's browser cookies, storage and history. Saved site data controls manage the encrypted requests, activity, observations and report exports independently. About displays the app version, WebView details and credits.
