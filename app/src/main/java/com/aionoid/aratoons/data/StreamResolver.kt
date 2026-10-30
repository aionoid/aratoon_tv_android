package com.aionoid.aratoons.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/**
 * Resolves a StarDima video URL to a playable stream (MP4 or HLS).
 *
 * Chain (port of the Kodi plugin):
 *   StarDima play page -> og:video (hyperwatching embed)
 *   -> data-page JSON (hashid + servers)
 *   -> per-server API (v2.hyperwatching.com/embed/{hash}/server/{id}/url)
 *   -> server-specific extraction (Mixdrop MP4, StreamHG/Uqload/Earnvids/
 *      Lulustream/Goodstream m3u8, strema.top proxy)
 *
 * ExoPlayer (unlike Kodi) natively plays HLS variant playlists with audio
 * groups, so master.m3u8 URLs are returned as-is and the player UI offers
 * audio track switching.
 */
class StreamResolver(private val context: Context?, private val http: OkHttpClient) {

    private val logTag = "Resolver"
    private val hwBase = "https://v2.hyperwatching.com"
    private val webExtractor = WebViewStreamExtractor()

    private val browserHeaders: Map<String, String> = mapOf(
        "User-Agent" to StardimaApi.USER_AGENT,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "en-us,en;q=0.5",
        "Sec-Fetch-Mode" to "navigate"
    )

    /** Priority order: Mixdrop first (direct MP4), then the JWPlayer servers. */
    private val priorityOrder = mapOf(
        "Mixdrop" to 0, "Uqload" to 1, "Lulustream" to 2,
        "Streamhg" to 3, "Earnvids" to 4, "Goodstream" to 5
    )

    // ------------------------------------------------------------------
    // Step 1: find the hyperwatching embed URL
    // ------------------------------------------------------------------

    suspend fun getEmbedUrl(pageUrl: String): String? = withContext(Dispatchers.IO) {
        var url = pageUrl
        if (!url.startsWith("http")) url = StardimaApi.BASE_URL + url
        if ("hyperwatching" in url) return@withContext url

        Log.d(logTag, "getEmbedUrl: $url")
        var starHtml = try {
            fetchText(url, mapOf("Referer" to StardimaApi.BASE_URL + "/"))
        } catch (e: IOException) {
            Log.d(logTag, "getEmbedUrl: fetch failed: ${e.message}")
            return@withContext null
        }

        // Movie detail pages redirect to /play/{id}
        if ("/movie/" in url && "/play/" !in url) {
            val movieId = Regex("/movie/([^?/]+)").find(url)?.groupValues?.get(1)
            if (movieId != null) {
                url = StardimaApi.BASE_URL + "/play/$movieId"
                starHtml = try {
                    fetchText(url, mapOf("Referer" to StardimaApi.BASE_URL + "/"))
                } catch (e: IOException) {
                    Log.d(logTag, "getEmbedUrl: /play fetch failed: ${e.message}")
                    return@withContext null
                }
            }
        }

        var embedUrl: String? = null

        // og:video (only if it points to an external embed)
        val og = Regex("<meta property=\"og:video\"[^>]+content=\"([^\"]+)\"").find(starHtml)
        if (og != null) {
            val ogVideo = og.groupValues[1]
            if ("hyperwatching" in ogVideo || "stardima" !in ogVideo) embedUrl = ogVideo
        }

        // iframe src
        if (embedUrl == null) {
            Regex("<iframe[^>]+src=\"([^\"]+)\"").find(starHtml)?.let { embedUrl = it.groupValues[1] }
        }

        // Follow a /play/ link on the page
        if (embedUrl == null) {
            val play = Regex("href=\"((?:https://)?(?:www\\.)?stardima\\.com/play/[^\"]+)\"").find(starHtml)
            if (play != null) {
                var playUrl = play.groupValues[1]
                if (!playUrl.startsWith("http")) playUrl = "https://$playUrl"
                val playHtml = fetchText(playUrl, mapOf("Referer" to StardimaApi.BASE_URL + "/"))
                Regex("<meta property=\"og:video\"[^>]+content=\"([^\"]+)\"").find(playHtml)
                    ?.let { embedUrl = it.groupValues[1] }
            }
        }

        // Cloudflare blocked the page — episode API fallback for /play/{id}
        if (embedUrl == null) {
            val epMatch = Regex("/play/(\\d+)$").find(url)
            if (epMatch != null) {
                val watchUrl = StardimaApi.episodeWatchUrl(http, epMatch.groupValues[1].toInt())
                if (watchUrl.isNotEmpty()) embedUrl = watchUrl
            }
        }

        if (embedUrl == null) Log.d(logTag, "getEmbedUrl: no embed found for $url")
        else Log.d(logTag, "getEmbedUrl: found $embedUrl")
        embedUrl
    }

    // ------------------------------------------------------------------
    // Step 2: servers from the embed page
    // ------------------------------------------------------------------

    private data class EmbedInfo(val hashid: String, val servers: List<ServerInfo>)

    private suspend fun fetchEmbedInfo(embedUrl: String, referer: String): EmbedInfo? {
        Log.d(logTag, "fetchEmbedInfo: $embedUrl")
        val hwHtml = try {
            fetchText(embedUrl, mapOf("Referer" to referer))
        } catch (e: IOException) {
            Log.d(logTag, "fetchEmbedInfo: fetch failed: ${e.message}")
            return null
        }
        val dataPage = Regex("data-page=\"([^\"]+)\"").find(hwHtml)?.groupValues?.get(1)
        if (dataPage == null) {
            Log.d(logTag, "fetchEmbedInfo: no data-page (html=${hwHtml.length}b)")
            return null
        }
        return try {
            val json = JSONObject(android.text.Html.fromHtml(dataPage, android.text.Html.FROM_HTML_MODE_LEGACY).toString())
            val video = json.optJSONObject("props")?.optJSONObject("video")
            if (video == null) {
                Log.d(logTag, "fetchEmbedInfo: no props.video")
                return null
            }
            val hashid = video.optString("hashid", "")
            val servers = ArrayList<ServerInfo>()
            val arr = video.optJSONArray("servers")
            if (arr == null) {
                Log.d(logTag, "fetchEmbedInfo: no servers array")
                return null
            }
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                if (s.optInt("id", 0) == 0) continue
                servers.add(ServerInfo(s.optString("name", ""), s.optInt("id", 0), s.optString("status", "")))
            }
            if (hashid.isEmpty() || servers.isEmpty()) {
                Log.d(logTag, "fetchEmbedInfo: empty hashid/servers")
                null
            } else EmbedInfo(hashid, servers).also {
                Log.d(logTag, "fetchEmbedInfo: ${servers.size} servers")
            }
        } catch (e: Exception) {
            Log.d(logTag, "fetchEmbedInfo: parse error: ${e.message}")
            null
        }
    }

    /** List of available servers for a StarDima URL (for the selection dialog). */
    suspend fun getServers(pageUrl: String): List<ServerInfo> = withContext(Dispatchers.IO) {
        val embedUrl = getEmbedUrl(pageUrl) ?: return@withContext emptyList()
        val info = fetchEmbedInfo(embedUrl, pageUrl) ?: return@withContext emptyList()
        info.servers.sortedBy { priorityOrder[it.name] ?: 99 }
    }

    // ------------------------------------------------------------------
    // Full resolution
    // ------------------------------------------------------------------

    /**
     * Resolve a StarDima video URL to a playable stream.
     * @param serverName when set, only that server is tried.
     */
    suspend fun resolve(pageUrl: String, serverName: String? = null): StreamResult? =
        withContext(Dispatchers.IO) {
            var url = pageUrl
            if (!url.startsWith("http")) url = StardimaApi.BASE_URL + url

            val embedUrl = getEmbedUrl(url) ?: return@withContext null
            val info = fetchEmbedInfo(embedUrl, url) ?: return@withContext null

            var servers = info.servers
            if (serverName != null) {
                val matched = servers.filter { it.name == serverName }
                if (matched.isEmpty()) return@withContext null
                servers = matched
            }
            val ordered = servers.sortedBy { priorityOrder[it.name] ?: 99 }
            val triedWatchUrls = ArrayList<String>()

            for (server in ordered) {
                // Step 3: server URL API
                val apiData = try {
                    fetchJson("$hwBase/embed/${info.hashid}/server/${server.id}/url",
                        mapOf("Referer" to embedUrl))
                } catch (_: IOException) {
                    continue
                }
                var watchUrl = apiData.optString("watch_url", "")
                if (watchUrl.isEmpty()) continue

                // --- iimag.top proxy: the site now wraps Uqload/Lulustream/
                //     Mixdrop/etc. in a JS form-submit stub (embed2/?id=...).
                //     The real server page is in the id= param and is directly
                //     fetchable — fetch it and continue (avoids the unreliable
                //     WebView fallback that causes "some work, some don't").
                val proxyUrl = watchUrl
                if ("iimag.top" in watchUrl) {
                    val idParam = queryParam(watchUrl, "id")
                    if (idParam != null) {
                        val realUrl = URLDecoder.decode(idParam, "UTF-8")
                        watchUrl = realUrl
                    }
                }
                triedWatchUrls.add(watchUrl)

                val playerHtml = try {
                    fetchText(watchUrl, mapOf("Referer" to proxyUrl))
                } catch (_: IOException) {
                    continue
                }

                // --- Mixdrop: direct MP4 ---
                if (server.name == "Mixdrop") {
                    Log.d(logTag, "Mixdrop: watch=$watchUrl html=${playerHtml.length}b " +
                        "evalHeader=${playerHtml.contains("eval(function(p,a,c,k,e,d)")}")
                    EvalDecoder.extractMixdropUrl(playerHtml)?.let { mp4 ->
                        return@withContext StreamResult(mp4, headers(mapOf("Referer" to origin(watchUrl))))
                    }
                    Log.d(logTag, "Mixdrop: direct decode failed, falling through")
                }

                // --- Uqload: base-36 eval decode (m3u8 from decoded JWPlayer) ---
                if (server.name == "Uqload") {
                    val m3u8 = EvalDecoder.extractJwPlayerUrl(playerHtml)
                    if (m3u8 != null) {
                        val cleaned = EvalDecoder.cleanJwPlayerUrl(m3u8)
                        warmUp(cleaned)
                        return@withContext StreamResult(cleaned, playerHeaders())
                    }
                }

                // --- Lulustream: katana eval decode ---
                if (server.name == "Lulustream") {
                    val m3u8 = EvalDecoder.extractLulustreamUrl(playerHtml)
                        ?: EvalDecoder.extractJwPlayerUrl(playerHtml)
                    if (m3u8 != null) {
                        val cleaned = EvalDecoder.cleanJwPlayerUrl(m3u8)
                        warmUp(cleaned)
                        return@withContext StreamResult(cleaned, playerHeaders())
                    }
                }

                // --- StreamHG direct (hgplaycdn.com) ---
                if ("hgplaycdn.com" in watchUrl) {
                    val m3u8 = EvalDecoder.extractStreamHgUrl(playerHtml)
                    if (m3u8 != null) {
                        val cleaned = EvalDecoder.cleanJwPlayerUrl(m3u8)
                        warmUp(cleaned)
                        return@withContext StreamResult(cleaned, playerHeaders())
                    }
                    continue
                }

                // --- Filemoon: iframe subdomain ---
                if ("filemoon" in watchUrl || "filemoon" in playerHtml) {
                    extractFilemoon(watchUrl, playerHtml)?.let {
                        return@withContext StreamResult(it, headers(mapOf("Referer" to origin(watchUrl))))
                    }
                }

                // --- Earnvids (minochinos.com) ---
                if ("minochinos.com" in watchUrl || "earnvids" in watchUrl || "earnvids" in playerHtml) {
                    val ev = EvalDecoder.extractJwPlayerUrl(playerHtml)
                    if (ev != null) {
                        val cleaned = EvalDecoder.cleanJwPlayerUrl(ev)
                        warmUp(cleaned)
                        return@withContext StreamResult(cleaned, playerHeaders())
                    }
                }

                // --- Generic: direct m3u8/source in the page ---
                Regex("src=\"([^\"]*\\.m3u8[^\"]*)\"").find(playerHtml)?.let {
                    return@withContext StreamResult(it.groupValues[1], headers(mapOf("Referer" to origin(watchUrl))))
                }
                Regex("<source[^>]+src=\"([^\"]+)\"").find(playerHtml)?.let {
                    return@withContext StreamResult(it.groupValues[1], headers(mapOf("Referer" to origin(watchUrl))))
                }
                Regex("data-(?:src|url)=\"([^\"]*\\.m3u8[^\"]*)\"").find(playerHtml)?.let {
                    return@withContext StreamResult(it.groupValues[1], headers(mapOf("Referer" to origin(watchUrl))))
                }

                // --- strema.top proxy: follow to the real embed ---
                if ("strema.top" in watchUrl) {
                    val idParam = queryParam(watchUrl, "id")
                    if (idParam != null) {
                        val actualEmbed = URLDecoder.decode(idParam, "UTF-8")
                        val actualHtml = try {
                            fetchText(actualEmbed, mapOf("Referer" to watchUrl))
                        } catch (_: IOException) {
                            null
                        }

                        if (actualHtml != null) {
                            val hasVideo = "<video" in actualHtml || "<source" in actualHtml
                            val hasJwPlayer = "eval(function(p,a,c,k,e,d)" in actualHtml
                            val isError = !hasVideo && !hasJwPlayer && (
                                "not found" in actualHtml.lowercase(Locale.ROOT) ||
                                "file has been deleted" in actualHtml.lowercase(Locale.ROOT) ||
                                ("challenge" in actualHtml.lowercase(Locale.ROOT) &&
                                    "cloudflare" in actualHtml.lowercase(Locale.ROOT)) ||
                                actualHtml.length < 200
                            )

                            // StreamHG via strema
                            if ("hgplaycdn.com" in actualEmbed) {
                                val m3u8 = EvalDecoder.extractStreamHgUrl(actualHtml)
                                if (m3u8 != null) {
                                    val cleaned = EvalDecoder.cleanJwPlayerUrl(m3u8)
                                    warmUp(cleaned)
                                    return@withContext StreamResult(cleaned, playerHeaders())
                                }
                                continue
                            }

                            // Goodstream via strema
                            if ("goodstream" in actualEmbed) {
                                val gs = resolvePlainEmbed(actualHtml)
                                if (gs != null) {
                                    return@withContext StreamResult(gs, headers(mapOf("Referer" to origin(actualEmbed))))
                                }
                                continue
                            }

                            // Lulustream / Uqload / StreamHG / Earnvids via strema
                            if (listOf("lulustream", "lulucdn", "tnmr.org", "luluvdo", "uqload.is", "hgplaycdn.com", "minochinos.com")
                                    .any { it in actualEmbed }) {
                                val resolved = resolvePlainEmbed(actualHtml)
                                if (resolved != null) {
                                    return@withContext StreamResult(resolved,
                                        headers(mapOf("Referer" to actualEmbed)))
                                }
                            }

                            // Generic JWPlayer fallback (skip on detected error pages)
                            if (!isError) {
                                val jw = EvalDecoder.extractJwPlayerUrl(actualHtml)
                                if (jw != null) {
                                    val cleaned = EvalDecoder.cleanJwPlayerUrl(jw)
                                    val referer = origin(actualEmbed)
                                    val cookie = EvalDecoder.extractJwPlayerCookies(actualHtml)
                                    if (verify(cleaned, referer, cookie.ifEmpty { null })) {
                                        val h = headers(mapOf("Referer" to referer))
                                        return@withContext StreamResult(cleaned, if (cookie.isEmpty()) h else h + ("Cookie" to cookie))
                                    }
                                    continue
                                }
                            }
                        }
                    }
                }
            }

            // --- WebView fallback: let the server page's own JS find the stream ---
            for (wu in triedWatchUrls.take(3)) {
                if (context == null) break
                val stream = webExtractor.extract(context, wu, embedUrl)
                if (stream != null) {
                    return@withContext StreamResult(stream, headers(mapOf("Referer" to origin(wu))))
                }
            }
            null
        }

    /**
     * Extract a stream from a plain (non-obfuscated or eval) embed page:
     * sources config first, then eval decoding.
     */
    private fun resolvePlainEmbed(embedHtml: String): String? {
        // Plain sources config (Goodstream & friends)
        Regex("sources:\\s*\\[\\s*\\{\\s*file\\s*:\\s*\"([^\"]+\\.m3u8[^\"]*)\"").find(embedHtml)?.let {
            return EvalDecoder.cleanJwPlayerUrl(it.groupValues[1])
        }
        Regex("\"file\"\\s*:\\s*\"([^\"]+\\.m3u8[^\"]*)\"").find(embedHtml)?.let {
            return EvalDecoder.cleanJwPlayerUrl(it.groupValues[1])
        }
        // Eval-encoded (Lulustream / Uqload)
        Regex("sources:\\s*\\[\\s*\\{\\s*file\\s*:\\s*\"([^\"]+\\.m3u8[^\"]*)\"")
            .find(EvalDecoder.decodeKatana(embedHtml) ?: "")?.let {
                return EvalDecoder.cleanJwPlayerUrl(it.groupValues[1])
            }
        EvalDecoder.extractLulustreamUrl(embedHtml)?.let {
            return EvalDecoder.cleanJwPlayerUrl(it)
        }
        EvalDecoder.extractJwPlayerUrl(embedHtml)?.let {
            return EvalDecoder.cleanJwPlayerUrl(it)
        }
        return null
    }

    /** Filemoon: fetch the iframe page and extract an m3u8 from it. */
    private suspend fun extractFilemoon(watchUrl: String, playerHtml: String): String? {
        val iframe = Regex("<iframe[^>]+src=\"([^\"]+)\"").find(playerHtml)?.groupValues?.get(1) ?: return null
        var iframeUrl = iframe
        if (!iframeUrl.startsWith("http")) {
            iframeUrl = if (iframeUrl.startsWith("//")) "https:$iframeUrl" else "https://$iframeUrl"
        }
        val iframeHtml = try {
            fetchText(iframeUrl, mapOf("Referer" to watchUrl))
        } catch (_: IOException) {
            return null
        }
        Regex("(https?://[^\"'\\s]+master[^\"'\\s]*\\.m3u8[^\"'\\s]*)").find(iframeHtml)?.let {
            return it.groupValues[1]
        }
        EvalDecoder.extractJwPlayerUrl(iframeHtml)?.let { return it }
        for (m in Regex("(https?://[^\"'\\s]{30,})").findAll(iframeHtml)) {
            val url = m.groupValues[1].trimEnd('"', ';', ',')
            if (".m3u8" in url || ".txt" in url) return url
        }
        return null
    }

    // ------------------------------------------------------------------
    // HTTP / utility helpers
    // ------------------------------------------------------------------

    /**
     * Extract the origin (scheme://host/) from a URL (package-private for testing).
     */
    fun origin(url: String): String {
        return try {
            val u = java.net.URI(url)
            "${u.scheme}://${u.host}/"
        } catch (_: Exception) {
            url
        }
    }

    /**
     * Extract a query parameter from a URL (package-private for testing).
     */
    fun queryParam(url: String, key: String): String? {
        val idx = url.indexOf('?')
        if (idx < 0) return null
        for (pair in url.substring(idx + 1).split("&")) {
            val eq = pair.indexOf('=')
            if (eq < 0) continue
            if (pair.substring(0, eq) == key) return pair.substring(eq + 1)
        }
        return null
    }

    private fun headers(extra: Map<String, String>): Map<String, String> {
        return browserHeaders + extra
    }

    /** Headers the player sends for m3u8 playback (no Referer unless required). */
    private fun playerHeaders(): Map<String, String> = browserHeaders

    private fun fetchText(url: String, extraHeaders: Map<String, String>): String {
        val req = Request.Builder().url(url).apply {
            browserHeaders.forEach { (k, v) -> header(k, v) }
            extraHeaders.forEach { (k, v) -> header(k, v) }
        }.build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} for $url")
            return resp.body!!.string()
        }
    }

    private fun fetchJson(url: String, extraHeaders: Map<String, String>): JSONObject {
        return JSONObject(fetchText(url, extraHeaders))
    }

    /**
     * Fetch the m3u8 once to "activate" the CDN token — some CDNs use TLS
     * fingerprinting and accept the first (OkHttp/Java-SSL) connection while
     * rejecting the player's subsequent one until the token is warmed.
     */
    private fun warmUp(m3u8Url: String) {
        try {
            val req = Request.Builder().url(m3u8Url).apply {
                browserHeaders.forEach { (k, v) -> header(k, v) }
            }.build()
            http.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string()
            }
        } catch (_: IOException) {
        }
    }

    /** Verify a stream URL actually serves media (GET, check content). */
    private fun verify(url: String, referer: String, cookie: String?): Boolean {
        return try {
            val h = browserHeaders + mapOf("Referer" to referer) +
                (cookie?.let { mapOf("Cookie" to it) } ?: emptyMap())
            val req = Request.Builder().url(url).apply {
                h.forEach { (k, v) -> header(k, v) }
            }.build()
            http.newCall(req).execute().use { resp ->
                if (resp.code != 200) return false
                val source = resp.body?.source() ?: return false
                val sample = source.peek().readUtf8(200L)
                if (".m3u8" in url || "mpegurl" in (resp.header("content-type") ?: "")) {
                    "#EXTM3U" in sample
                } else {
                    source.buffer.size > 50
                }
            }
        } catch (_: Exception) {
            false
        }
    }
}
