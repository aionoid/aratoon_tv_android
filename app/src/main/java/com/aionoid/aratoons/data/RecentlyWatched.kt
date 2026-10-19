package com.aionoid.aratoons.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Recently watched items (SharedPreferences-backed, max 50 entries).
 */
object RecentlyWatched {

    private const val PREFS = "aratoons"
    private const val KEY = "recently_watched"
    private const val MAX_ITEMS = 50

    data class Entry(
        val title: String,
        val url: String,
        val poster: String,
        val time: Long
    )

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
                        o.optString("title", ""),
                        o.optString("url", ""),
                        o.optString("poster", ""),
                        o.optLong("time", 0)
                    )
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Add (or move to front) an entry. Returns the updated list. */
    fun add(context: Context, title: String, url: String, poster: String): List<Entry> {
        val items = load(context).toMutableList()
        items.removeAll { it.url == url }
        items.add(0, Entry(title, url, poster, System.currentTimeMillis()))
        while (items.size > MAX_ITEMS) items.removeAt(items.size - 1)
        save(context, items)
        return items
    }

    fun remove(context: Context, url: String): List<Entry> {
        val items = load(context).toMutableList()
        items.removeAll { it.url == url }
        save(context, items)
        return items
    }

    private fun save(context: Context, items: List<Entry>) {
        val arr = JSONArray()
        for (e in items) {
            arr.put(
                JSONObject()
                    .put("title", e.title)
                    .put("url", e.url)
                    .put("poster", e.poster)
                    .put("time", e.time)
            )
        }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }
}
