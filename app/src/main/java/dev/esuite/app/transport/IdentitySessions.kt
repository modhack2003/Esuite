package dev.esuite.app.transport

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

private val BEARER_PATTERN = Regex("[A-Za-z0-9._~+/\\-]+=*")

class IdentityCookieJar : CookieJar {
    private var bearer: String? = null
    private val bearerPresent = kotlinx.coroutines.flow.MutableStateFlow(false)
    val hasBearer: kotlinx.coroutines.flow.StateFlow<Boolean> get() = bearerPresent

    @Synchronized fun setBearer(value: String?) {
        require(value == null || (value.length in 1..8185 && BEARER_PATTERN.matches(value))) {
            "Enter a bearer token without spaces or the Bearer prefix."
        }
        bearer = value
        bearerPresent.value = value != null
    }
    @Synchronized internal fun manualBearer() = bearer

    private val cookies = mutableListOf<Cookie>()
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { incoming ->
            this.cookies.removeAll { it.name == incoming.name && it.domain == incoming.domain && it.path == incoming.path }
            if (incoming.expiresAt > System.currentTimeMillis()) this.cookies.add(incoming)
        }
        this.cookies.removeAll { it.expiresAt <= System.currentTimeMillis() }
        while (this.cookies.size > 200) this.cookies.removeAt(0)
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> {
        cookies.removeAll { it.expiresAt <= System.currentTimeMillis() }
        return cookies.filter { it.matches(url) }
    }
    @Synchronized fun clear() {
        setBearer(null)
        cookies.clear()
    }
    @Synchronized fun replaceFromBrowser(url: HttpUrl, setCookies: List<String>): Int {
        require(setCookies.size <= 200) { "Too many cookies to transfer" }
        val parsed = setCookies.map { Cookie.parse(url, it) ?: throw IllegalArgumentException("A browser cookie could not be parsed") }
        cookies.clear(); saveFromResponse(url, parsed)
        return cookies.size
    }
    @Synchronized fun describe(): String = cookies.joinToString("\n") { "${it.name} · ${it.domain}${it.path} · secure=${it.secure} · HttpOnly=${it.httpOnly}" }
}

class IdentitySessions {
    private val jars = ConcurrentHashMap<Pair<String, String>, IdentityCookieJar>()
    fun jar(project: String, identity: String): IdentityCookieJar = jars.getOrPut(project to identity) { IdentityCookieJar() }
    fun clearProject(project: String) { jars.keys.filter { it.first == project }.forEach { jars.remove(it)?.clear() } }
    fun clearAll() { jars.values.forEach { it.clear() }; jars.clear() }
}
