package com.aionoid.aratoons.data

/**
 * Routing decision for a [VideoItem] from search / catalog results.
 *
 * The StarDima API returns `is_series: true` for BOTH series detail pages
 * (`/tvshow/{slug}`) AND episode pages (`/tvshow/{slug}/play/{id}`).
 * Only series detail pages should navigate to SeriesScreen; episode pages
 * must resolve the stream directly (Servers screen).
 *
 * This object is deliberately pure (no dependencies) so it can be
 * unit-tested without a browser or navigation.
 */
object VideoItemRouting {

    /**
     * Returns true when the item represents a series detail page that
     * should open the [SeriesScreen].
     *
     * Conditions (all must hold):
     *   1. [VideoItem.isSeries] is true.
     *   2. The URL does NOT contain `/play/` (i.e. it is a series detail
     *      page `/tvshow/{slug}`, not an episode page
     *      `/tvshow/{slug}/play/{id}`).
     */
    fun isSeriesPage(item: VideoItem): Boolean =
        item.isSeries && !item.url.contains("/play/")
}
