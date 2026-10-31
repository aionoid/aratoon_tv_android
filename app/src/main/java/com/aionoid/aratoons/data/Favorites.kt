package com.aionoid.aratoons.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Favourites (SharedPreferences-backed, max 100 entries).
 *
 * Mirrors [RecentlyWatched] but stores the full metadata needed to reopen
 * the item. Each entry carries:
 *   - [type] : "movie" | "series"
 *   - [url]  : playUrl (movie) or series slug (series) — used as the unique key
 *   - [title], [poster]
 *   - movie extras: [year], [desc], [quality]
 *   - [addedAt]: epoch millis (for display, most-recently-first ordering)
 */
object Favorites {

    private const val PREFS = "aratoons"
    private const val KEY = "favorites"
    private const val MAX_ITEMS = 100

    /**
     * One favourited item.
     *
     * @param type  "movie" or "series"
     * @param url   playUrl for movies, slug for series (the unique key)
     * @param year  movie year (empty for series)
     * @param desc  movie synopsis (empty for series)
     * @param quality movie quality badge (empty for series)
     * @param addedAt epoch millis
     */
    data class Entry(
        val type: String,
        val title: String,
        val poster: String,
        val url: String,
        val year: String = "",
        val desc: String = "",
        val quality: String = "",
        val addedAt: Long = 0
    ) {
        /** Unique key for the entry (used for dedup / removal). */
        val id: String get() = "$type:$url"
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): List<Entry> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<Entry>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(
                    Entry(
                        type = o.optString("type", "movie"),
                        title = o.optString("title", ""),
                        poster = o.optString("poster", ""),
                        url = o.optString("url", ""),
                        year = o.optString("year", ""),
                        desc = o.optString("desc", ""),
                        quality = o.optString("quality", ""),
                        addedAt = o.optLong("addedAt", 0)
                    )
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Returns true when the entry is already in the favourites list. */
    fun contains(context: Context, entry: Entry): Boolean =
        load(context).any { it.id == entry.id }

    /**
     * Add the entry (move to front if already present). Returns the updated list.
     * Sets [addedAt] to now so the entry appears most-recent first.
     */
    fun add(context: Context, entry: Entry): List<Entry> {
        val newEntry = entry.copy(addedAt = System.currentTimeMillis())
        val items = load(context).toMutableList()
        items.removeAll { it.id == newEntry.id }
        items.add(0, newEntry)
        while (items.size > MAX_ITEMS) items.removeAt(items.size - 1)
        save(context, items)
        return items
    }

    /** Remove the entry (if present). Returns the updated list. */
    fun remove(context: Context, entry: Entry): List<Entry> {
        val items = load(context).toMutableList()
        items.removeAll { it.id == entry.id }
        save(context, items)
        return items
    }

    /**
     * Toggle an entry: add if absent, remove if present.
     * Returns [true] when the entry is now a favourite, [false] otherwise.
     */
    fun toggle(context: Context, entry: Entry): Boolean {
        val current = load(context)
        if (current.any { it.id == entry.id }) {
            remove(context, entry)
            return false
        }
        add(context, entry)
        return true
    }

    private fun save(context: Context, items: List<Entry>) {
        val arr = JSONArray()
        for (e in items) {
            arr.put(
                JSONObject()
                    .put("type", e.type)
                    .put("title", e.title)
                    .put("poster", e.poster)
                    .put("url", e.url)
                    .put("year", e.year)
                    .put("desc", e.desc)
                    .put("quality", e.quality)
                    .put("addedAt", e.addedAt)
            )
        }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }
}
