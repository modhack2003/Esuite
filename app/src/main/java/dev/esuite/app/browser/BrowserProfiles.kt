package dev.esuite.app.browser

import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature

object BrowserProfiles {
    /** Must run on the UI thread after all WebViews using the profile have been destroyed. */
    fun clearProject(projectId: String) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return
        val store = ProfileStore.getInstance()
        listOf("A", "B").forEach { identity ->
            val name = "esuite_${projectId}_$identity"
            if (store.allProfileNames.contains(name)) check(store.deleteProfile(name)) { "Browser profile is still in use. Close the browser and retry." }
        }
    }
}
