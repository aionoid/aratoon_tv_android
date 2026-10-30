package com.aionoid.aratoons.data

import org.junit.Test
import org.junit.Assert.*

/**
 * TDD tests for [VideoItemRouting].
 *
 * These tests codify the routing contract that prevents the "search episode
 * opens the wrong series" bug (todo task 4). The StarDima API returns
 * `is_series: true` for BOTH series detail pages and episode pages, so
 * `isSeries` alone is insufficient; the URL shape (`/play/`) is the
 * discriminator.
 */
class VideoItemRoutingTest {

    private fun seriesDetail(slug: String): VideoItem = VideoItem(
        id = 100,
        title = "Series $slug",
        url = "/tvshow/$slug",
        posterUrl = "",
        isSeries = true,
        year = "2024",
        description = "A series"
    )

    private fun episode(slug: String, epId: Int): VideoItem = VideoItem(
        id = 5169,
        title = "The Episode",
        url = "/tvshow/$slug/play/$epId",
        posterUrl = "",
        isSeries = true,          // API returns true for episodes
        year = "2024",
        description = "An episode"
    )

    private fun movie(slug: String): VideoItem = VideoItem(
        id = 3853,
        title = "The Movie",
        url = "/movie/$slug",
        posterUrl = "",
        isSeries = false,
        year = "2024",
        description = "A movie"
    )

    @Test
    fun seriesDetailPage_isSeriesPage() {
        val item = seriesDetail("abc123")
        assertTrue(VideoItemRouting.isSeriesPage(item))
    }

    @Test
    fun episodePage_notSeriesPage() {
        val item = episode("abc123", 5169)
        assertFalse(VideoItemRouting.isSeriesPage(item))
    }

    @Test
    fun episodePage_numericSlugNotSeriesPage() {
        // The original bug: substringAfterLast("/") on an episode URL gives
        // the numeric episode ID (e.g. "5169"). This test ensures we never
        // navigate to /tvshow/5169.
        val item = episode("68c937d1c0f08", 34475)
        assertFalse(VideoItemRouting.isSeriesPage(item))
    }

    @Test
    fun movie_notSeriesPage() {
        val item = movie("6a85d39497f7d")
        assertFalse(VideoItemRouting.isSeriesPage(item))
    }

    @Test
    fun nonSeriesItem_withTvshowPath_notSeriesPage() {
        val item = VideoItem(
            id = 1, title = "Odd", url = "/tvshow/xyz", posterUrl = "",
            isSeries = false, year = "", description = ""
        )
        assertFalse(VideoItemRouting.isSeriesPage(item))
    }
}
