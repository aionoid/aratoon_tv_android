package com.aionoid.aratoons.data

import org.junit.Test
import org.junit.Assert.*
import okhttp3.OkHttpClient

class StreamResolverTest {

    // queryParam and origin don't use context or http, so we can pass
    // null for context (platform type) and a real OkHttpClient.
    private val resolver = StreamResolver(null, OkHttpClient())

    // ------------------------------------------------------------------
    // queryParam
    // ------------------------------------------------------------------

    @Test
    fun queryParam_basicExtraction() {
        val url = "https://iimag.top/embed2/?id=https%3A%2F%2Fmixdrop.top%2Fe%2Fabc123"
        val result = resolver.queryParam(url, "id")
        assertNotNull(result)
        assertTrue(result.contains("mixdrop.top"))
        assertTrue(result.contains("abc123"))
    }

    @Test
    fun queryParam_returnsNull_whenNoQueryParam() {
        val url = "https://example.com/no-params"
        assertNull(resolver.queryParam(url, "id"))
    }

    @Test
    fun queryParam_returnsNull_whenParamMissing() {
        val url = "https://example.com/page?foo=bar"
        assertNull(resolver.queryParam(url, "id"))
    }

    @Test
    fun queryParam_multipleParams() {
        val url = "https://example.com/page?id=123&foo=bar&baz=qux"
        assertEquals("123", resolver.queryParam(url, "id"))
        assertEquals("bar", resolver.queryParam(url, "foo"))
        assertEquals("qux", resolver.queryParam(url, "baz"))
        assertNull(resolver.queryParam(url, "missing"))
    }

    @Test
    fun queryParam_stremaTopProxyUrl() {
        val url = "https://strema.top/embed?id=https%3A%2F%2Fhgplaycdn.com%2Fv1%2Fmaster.m3u8&token=abc"
        val result = resolver.queryParam(url, "id")
        assertNotNull(result)
        assertTrue(result.contains("hgplaycdn.com"))
        assertTrue(result.contains("master.m3u8"))
    }

    // ------------------------------------------------------------------
    // origin
    // ------------------------------------------------------------------

    @Test
    fun origin_httpsUrl() {
        assertEquals("https://example.com/", resolver.origin("https://example.com/path/to/page"))
    }

    @Test
    fun origin_httpUrl() {
        assertEquals("http://example.com/", resolver.origin("http://example.com/some/path"))
    }

    @Test
    fun origin_withPort() {
        // java.net.URI.host excludes the port; origin() uses u.host only.
        assertEquals("https://example.com/", resolver.origin("https://example.com:8080/path"))
    }

    @Test
    fun origin_noPath() {
        assertEquals("https://example.com/", resolver.origin("https://example.com"))
    }

    @Test
    fun origin_invalidUrl_returnsOriginal() {
        assertEquals("not a url", resolver.origin("not a url"))
    }

    // ------------------------------------------------------------------
    // iimag.top proxy: extract real URL from id= param
    // ------------------------------------------------------------------

    @Test
    fun iimagTopProxy_mixdrop() {
        val iimagUrl = "https://iimag.top/embed2/?id=https%3A%2F%2Fmixdrop.top%2Fe%2Fz1omnr3rtgmd8l1"
        val idParam = resolver.queryParam(iimagUrl, "id")
        val realUrl = java.net.URLDecoder.decode(idParam!!, "UTF-8")
        assertEquals("https://mixdrop.top/e/z1omnr3rtgmd8l1", realUrl)
    }

    @Test
    fun iimagTopProxy_uqload() {
        val iimagUrl = "https://iimag.top/embed2/?id=https%3A%2F%2Fuqload.vc%2Fembed-gghcys13helw.html"
        val idParam = resolver.queryParam(iimagUrl, "id")
        val realUrl = java.net.URLDecoder.decode(idParam!!, "UTF-8")
        assertEquals("https://uqload.vc/embed-gghcys13helw.html", realUrl)
    }

    @Test
    fun iimagTopProxy_lulustream() {
        val iimagUrl = "https://iimag.top/embed2/?id=https%3A%2F%2Flulustream.com%2Fe%2F678s1gaxsyil"
        val idParam = resolver.queryParam(iimagUrl, "id")
        val realUrl = java.net.URLDecoder.decode(idParam!!, "UTF-8")
        assertEquals("https://lulustream.com/e/678s1gaxsyil", realUrl)
    }
}
