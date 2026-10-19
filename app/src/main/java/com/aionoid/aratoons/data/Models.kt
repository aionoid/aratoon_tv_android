package com.aionoid.aratoons.data

/**
 * A video item from the StarDima JSON API (movies / series / episodes).
 *
 * @param url relative site path (e.g. /movie/slug, /tvshow/slug or /play/{id})
 */
data class VideoItem(
    val id: Int,
    val title: String,
    val url: String,
    val posterUrl: String,
    val isSeries: Boolean,
    val year: String,
    val description: String
)

data class Episode(
    val id: Int,
    val title: String
)

/** Result of scraping a series detail page. */
data class SeasonInfo(
    val title: String,
    val seasons: List<Int>,
    val poster: String,
    val description: String
)

/**
 * A resolved, playable stream.
 *
 * @param url  direct media URL (mp4 or m3u8)
 * @param headers HTTP headers the player must send (Referer/Origin/User-Agent …)
 */
data class StreamResult(
    val url: String,
    val headers: Map<String, String> = emptyMap()
) {
    val isHls: Boolean
        get() = url.contains(".m3u8") || url.endsWith(".txt") || "master.txt" in url
}

/** A playback server advertised by the hyperwatching embed. */
data class ServerInfo(
    val name: String,
    val id: Int,
    val status: String
)
