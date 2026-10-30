package com.aionoid.aratoons.data

import okhttp3.OkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * StarDima site API — port of the Kodi plugin's scraping logic.
 *
 * StarDima is a Laravel/Blade site (Arabic RTL) that exposes JSON APIs for
 * search, catalogs, seasons and episodes. Catalog/search endpoints require
 * the `X-Requested-With: XMLHttpRequest` header.
 */
object StardimaApi {

    const val BASE_URL = "https://www.stardima.com"
    const val STORAGE_URL = BASE_URL + "/storage"
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"

    val apiHeaders: Map<String, String> = mapOf(
        "User-Agent" to USER_AGENT,
        "X-Requested-With" to "XMLHttpRequest",
        "Accept" to "application/json, text/plain, */*"
    )

    /** Server hardcodes 15 items per API page. */
    const val ITEMS_PER_API_PAGE = 15

    /** Resolve a poster_url from the API: full URLs pass through, relative
     *  paths are resolved under /storage/ (NOT the site root). */
    fun resolvePoster(poster: String?): String {
        if (poster.isNullOrEmpty()) return ""
        return if (poster.startsWith("http")) poster else STORAGE_URL + "/" + poster
    }

    /**
     * TMDB poster URLs are width-tagged (e.g. /t/p/w500/). The API returns
     * w500 by default, which looks soft on TV. Swap the width token for a
     * larger one so large surfaces (hero) stay sharp. Port of the Flutter
     * helper of the same name.
     */
    fun upscalePoster(url: String, width: Int = 780): String {
        val m = Regex("/t/p/w\\d+/").find(url) ?: return url
        // TMDB only serves a fixed set of widths; clamp to the nearest valid
        // one so a mis-specified width (e.g. w520) never 400s.
        val valid = intArrayOf(300, 400, 500, 780, 1280)
        val clamped = valid.minByOrNull { Math.abs(it - width) } ?: width
        return url.replaceFirst(m.value, "/t/p/w$clamped/")
    }

    private fun parseVideo(v: JSONObject): VideoItem {
        val url = v.optString("url", "")
        return VideoItem(
            id = v.optInt("id", 0),
            title = v.optString("title", ""),
            url = url.removePrefix(BASE_URL),
            posterUrl = resolvePoster(v.optString("poster_url", "")),
            isSeries = v.optBoolean("is_series", false),
            year = v.optString("year", ""),
            description = v.optString("description", ""),
            quality = v.optString("video_quality", ""),
            backgroundUrl = resolvePoster(v.optString("background_url", ""))
        )
    }

    private fun parseVideosArray(arr: JSONArray): List<VideoItem> {
        val out = ArrayList<VideoItem>(arr.length())
        for (i in 0 until arr.length()) {
            val v = arr.optJSONObject(i) ?: continue
            out.add(parseVideo(v))
        }
        return out
    }

    /**
     * Search API: /search?query=X&page=N
     * @return (items, lastPage)
     */
    suspend fun search(http: OkHttpClient, query: String, page: Int = 1): Pair<List<VideoItem>, Int> =
        withContext(Dispatchers.IO) {
            var url = BASE_URL + "/search?query=" + URLEncoder.encode(query, "UTF-8")
            if (page > 1) url += "&page=$page"
            val json = fetchJson(http, url, apiHeaders)
            val items = parseVideosArray(json.optJSONArray("videos") ?: JSONArray())
            val lastPage = json.optJSONObject("pagination")?.optInt("last_page", 1) ?: 1
            items to lastPage
        }

    /**
     * Catalog: {path}?page=N (e.g. /aflam, /aflam?language=sub, /mosalsalat).
     * Uses the JSON API when available; falls back to HTML scraping for
     * catalog pages without a JSON endpoint (e.g. /newrelases, single page).
     * @return (items, lastPage)
     */
    suspend fun catalog(http: OkHttpClient, path: String, page: Int = 1): Pair<List<VideoItem>, Int> =
        withContext(Dispatchers.IO) {
            var url = BASE_URL + path
            if (page > 1) {
                val sep = if ("?" in path) "&" else "?"
                url += sep + "page=$page"
            }
            val text = fetchText(http, url, apiHeaders)
            val json = try {
                JSONObject(text)
            } catch (_: Exception) {
                null
            }
            if (json != null) {
                val items = parseVideosArray(json.optJSONArray("videos") ?: JSONArray())
                val lastPage = json.optJSONObject("pagination")?.optInt("last_page", 1) ?: 1
                items to lastPage
            } else {
                // HTML catalog (no pagination)
                parseHtmlItems(text) to 1
            }
        }

    /**
     * Parse catalog items from a server-rendered HTML page
     * (group/item blocks with link + title + poster).
     */
    fun parseHtmlItems(html: String): List<VideoItem> {
        val items = ArrayList<VideoItem>()
        for (block in html.split(Regex("<div\\s+class=\"[^\"]*group/item"))) {
            val link = Regex(
                "<a\\s+href=\"((?:https://)?(?:www\\.)?stardima\\.com/(?:tvshow|movie|play)/[^\"]+)\""
            ).find(block) ?: continue
            val title = Regex("<h4[^>]+>[ \t]*([^<]+)[ \t]*</h4>").find(block)
                ?: Regex("<h3[^>]+>[ \t]*([^<]+)[ \t]*</h3>").find(block)
                ?: continue
            val poster = Regex("<img[^>]+src=\"([^\"]+)\"").find(block)?.groupValues?.get(1) ?: ""
            var url = link.groupValues[1]
            if (!url.startsWith("http")) url = "https://$url"
            items.add(
                VideoItem(
                    id = 0,
                    title = unescapeHtml(title.groupValues[1].trim()),
                    url = url.removePrefix(BASE_URL),
                    posterUrl = resolveHtmlPoster(poster),
                    isSeries = "/tvshow/" in url,
                    year = "",
                    description = ""
                )
            )
        }
        return items
    }

    private fun resolveHtmlPoster(poster: String): String {
        if (poster.isEmpty()) return ""
        return if (poster.startsWith("http")) poster
        else if (poster.startsWith("//")) "https:$poster"
        else if (poster.startsWith("/")) BASE_URL + poster
        else BASE_URL + "/" + poster
    }

    /** Episodes of a season via /series/season/{id}. */
    suspend fun episodes(http: OkHttpClient, seasonId: Int): List<Episode> =
        withContext(Dispatchers.IO) {
            val json = fetchJson(http, BASE_URL + "/series/season/$seasonId", apiHeaders)
            val arr = json.optJSONArray("episodes") ?: JSONArray()
            val out = ArrayList<Episode>(arr.length())
            for (i in 0 until arr.length()) {
                val ep = arr.optJSONObject(i) ?: continue
                out.add(
                    Episode(
                        ep.optInt("id", 0),
                        ep.optString("title", ""),
                        ep.optInt("episode_number", 0)
                    )
                )
            }
            out
        }

    /** watch_url of an episode via /series/episode/{id}. */
    suspend fun episodeWatchUrl(http: OkHttpClient, episodeId: Int): String =
        withContext(Dispatchers.IO) {
            val json = fetchJson(http, BASE_URL + "/series/episode/$episodeId", apiHeaders)
            json.optJSONObject("episode")?.optString("watch_url", "") ?: ""
        }

    /**
     * Scrape a series detail page (/tvshow/{slug}) for seasons + metadata.
     * Season IDs come from data-initial-season-id (most reliable), with
     * fallbacks to the first episode page and finally data-video-id.
     */
    suspend fun seasons(http: OkHttpClient, seriesSlug: String): SeasonInfo =
        withContext(Dispatchers.IO) {
            val url = BASE_URL + "/tvshow/$seriesSlug"
            val html = fetchText(http, url)

            var poster = ""
            Regex("<meta property=\"og:image\"[^>]+content=\"([^\"]+)\"").find(html)
                ?.groupValues?.get(1)?.let { p -> poster = p }
            var title = ""
            Regex("<meta property=\"og:title\"[^>]+content=\"([^\"]+)\"").find(html)
                ?.groupValues?.get(1)?.let { t -> title = unescapeHtml(t) }
            if (title.isEmpty()) {
                Regex("<title[^>]+>([^<]+)</title>").find(html)?.groupValues?.get(1)?.let { t ->
                    title = t.trim().substringBefore(" - ").substringBefore(" | ").trim()
                }
            }
            var desc = ""
            Regex("<meta name=\"description\"[^>]+content=\"([^\"]+)\"").find(html)
                ?.groupValues?.get(1)?.let { d -> desc = unescapeHtml(d) }

            val seasons = ArrayList<Int>()

            // 1) data-initial-season-id on the series page (most reliable)
            Regex("data-initial-season-id=\"(\\d+)\"").find(html)?.groupValues?.get(1)?.toInt()?.let {
                seasons.add(it)
            }

            // 2) Fallback: scrape the first /play/ page for season ids
            if (seasons.isEmpty()) {
                val playId = Regex("href=\"[^\"]*/play/(\\d+)\"").find(html)?.groupValues?.get(1)
                if (playId != null) {
                    val epHtml = fetchText(http, BASE_URL + "/tvshow/$seriesSlug/play/$playId")
                    Regex("data-initial-season-id=\"(\\d+)\"").findAll(epHtml)
                        .map { it.groupValues[1].toInt() }.forEach { if (it !in seasons) seasons.add(it) }
                    Regex("data-season-id=\"(\\d+)\"").findAll(epHtml)
                        .map { it.groupValues[1].toInt() }.forEach { if (it !in seasons) seasons.add(it) }
                }
            }

            // 3) Final fallback: data-video-id (may point to the wrong series)
            if (seasons.isEmpty()) {
                Regex("data-video-id=\"(\\d+)\"").findAll(html)
                    .map { it.groupValues[1].toInt() }.forEach { if (it !in seasons) seasons.add(it) }
            }

            SeasonInfo(title, seasons, poster, desc)
        }

    /**
     * Scrape /newrelases and split into episodes / movies / series lists
     * (server-rendered tabs, all present in the HTML).
     * @return Triple(episodes, movies, series)
     */
    suspend fun newReleases(http: OkHttpClient): Triple<List<VideoItem>, List<VideoItem>, List<VideoItem>> =
        withContext(Dispatchers.IO) {
            val html = fetchText(http, BASE_URL + "/newrelases")

            fun section(content: String): String? {
                val m = Regex(
                    "data-content=\"$content\"(.*?)(?=<section\\s+class=\"tab-content|<script)",
                    RegexOption.DOT_MATCHES_ALL
                ).find(html)
                return m?.groupValues?.get(1)
            }

            fun extractItems(sectionHtml: String?): List<VideoItem> {
                if (sectionHtml.isNullOrEmpty()) return emptyList()
                val items = ArrayList<VideoItem>()
                for (block in sectionHtml.split(Regex("<div\\s+class=\"[^\"]*group/item"))) {
                    val link = Regex(
                        "<a\\s+href=\"((?:https://)?(?:www\\.)?stardima\\.com/(?:tvshow|movie|play)/[^\"]+)\""
                    ).find(block) ?: continue
                    val title = Regex("<h4[^>]+>[ \\t]*([^<]+)[ \\t]*</h4>").find(block)
                        ?: Regex("<h3[^>]+>[ \\t]*([^<]+)[ \\t]*</h3>").find(block)
                        ?: continue
                    val poster = Regex("<img[^>]+src=\"([^\"]+)\"").find(block)?.groupValues?.get(1) ?: ""
                    var url = link.groupValues[1]
                    if (!url.startsWith("http")) url = "https://$url"
                    items.add(
                        VideoItem(
                            id = 0,
                            title = unescapeHtml(title.groupValues[1].trim()),
                            url = url.removePrefix(BASE_URL),
                            posterUrl = if (poster.startsWith("http")) poster
                                else if (poster.startsWith("//")) "https:$poster"
                                else if (poster.isNotEmpty()) BASE_URL + (if (poster.startsWith("/")) poster else "/$poster")
                                else "",
                            isSeries = "/tvshow/" in url,
                            year = "",
                            description = ""
                        )
                    )
                }
                return items
            }

            Triple(
                extractItems(section("episodes")),
                extractItems(section("movies")),
                extractItems(section("series"))
            )
        }

    /**
     * "الرائج" (Popular) carousel from the HOME PAGE — server-rendered
     * `embla__slide` blocks: poster img + link overlay (h3 title,
     * description, /tvshow|/movie URL). No AJAX behind it; the id suffix
     * (e.g. embla-alrayg-19) is just a section id.
     */
    suspend fun popular(http: OkHttpClient): List<VideoItem> =
        withContext(Dispatchers.IO) {
            val html = fetchText(http, BASE_URL + "/")
            val h2 = html.indexOf("الرائج</h2>")
            if (h2 < 0) return@withContext emptyList()
            val start = html.lastIndexOf("<div class=\"w-full relative group/carousel\">", h2)
            val end = html.indexOf("w-full relative group/carousel", h2)
            val block = if (start >= 0 && end > h2) {
                html.substring(start, end)
            } else {
                html.substring(h2, (h2 + 30000).coerceAtMost(html.length))
            }

            val items = ArrayList<VideoItem>()
            val seen = HashSet<String>()
            for (slide in block.split(Regex("<div\\s+class=\"embla__slide"))) {
                val title = Regex("<h3[^>]*>\\s*([^<]+?)\\s*</h3>").find(slide) ?: continue
                val link = Regex(
                    "<a[^>]+href=\"((?:https://)?(?:www\\.)?stardima\\.com/(?:tvshow|movie)/[^\"]+)\""
                ).find(slide) ?: continue
                val poster = Regex("<img[^>]+src=\"([^\"]+)\"").find(slide)
                    ?.groupValues?.get(1) ?: ""
                val desc = Regex("<p[^>]*text-gray-400[^>]*>([\\s\\S]*?)</p>").find(slide)
                var url = link.groupValues[1]
                if (!url.startsWith("http")) url = "https://$url"
                if (!seen.add(url)) continue
                items.add(
                    VideoItem(
                        id = 0,
                        title = unescapeHtml(title.groupValues[1].trim()),
                        url = url.removePrefix(BASE_URL),
                        posterUrl = resolveHtmlPoster(poster),
                        isSeries = "/tvshow/" in url,
                        year = "",
                        description = Regex("\\s+").replace(
                            unescapeHtml(desc?.groupValues?.get(1).orEmpty().trim()), " "
                        )
                    )
                )
            }
            items
        }

    /** Scrape og:title / og:image from a video page (used for recently-watched). */
    suspend fun titleAndPoster(http: OkHttpClient, url: String): Pair<String, String> =
        withContext(Dispatchers.IO) {
            val fullUrl = if (url.startsWith("http")) url else BASE_URL + url
            var title = ""
            var poster = ""
            try {
                val html = fetchText(http, fullUrl)
                Regex("<meta property=\"og:title\"[^>]+content=\"([^\"]+)\"").find(html)
                    ?.groupValues?.get(1)?.let { title = it }
                Regex("<meta property=\"og:image\"[^>]+content=\"([^\"]+)\"").find(html)
                    ?.groupValues?.get(1)?.let { poster = it }
                if (title.isEmpty()) {
                    Regex("<title[^>]+>([^<]+)</title>").find(html)?.groupValues?.get(1)?.let { t ->
                        title = t.trim()
                        for (sep in listOf(" - ", " | ")) {
                            if (sep in title) {
                                title = title.substringBefore(sep)
                                break
                            }
                        }
                    }
                }
            } catch (_: IOException) {
            }
            if (title.isEmpty()) {
                val m = Regex("/play/([^/]+)$").find(url)
                    ?: Regex("/movie/([^/]+)$").find(url)
                    ?: Regex("/tvshow/([^/]+?)(?:/play/\\d+)?$").find(url)
                title = m?.groupValues?.get(1) ?: ""
            }
            title to poster
        }

    // ------------------------------------------------------------------
    // HTTP helpers
    // ------------------------------------------------------------------

    private fun fetchText(http: OkHttpClient, url: String, headers: Map<String, String> = emptyMap()): String {
        val req = okhttp3.Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} for $url")
            return resp.body!!.string()
        }
    }

    private fun fetchJson(http: OkHttpClient, url: String, headers: Map<String, String>): JSONObject {
        return JSONObject(fetchText(http, url, headers))
    }

    /** Unescape HTML entities (named + numeric). */
    fun unescapeHtml(text: String): String {
        var out = text
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
        // Catch-all for numeric entities (e.g. &#215; = ×)
        out = Regex("&#(\\d+);").replace(out) { m ->
            m.groupValues[1].toIntOrNull()?.let { code ->
                if (code in 32..0x10FFFF) code.toChar().toString() else " "
            } ?: " "
        }
        return out
    }
}
