package com.aionoid.aratoons.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD tests for [Routes.movie] — the navigation contract introduced by the
 * movie-detail page (todo task #9).
 *
 * Unlike a series (which needs a season → episode step), a movie's "info
 * page" is reached with the full metadata set (play URL, title, poster,
 * year, description, quality) and then hands off to the server-choose screen
 * via the Watch-now button.
 *
 * These tests codify that the route:
 *   1. uses the `movie?` path,
 *   2. URL-encodes every parameter (Arabic titles, `&`, `=`, `+`, `#` in the
 *      play URL cannot break the query string),
 *   3. tolerates empty metadata (year / description / quality are blank for
 *      recently-watched and legacy entries), and
 *   4. round-trips losslessly through [Routes.dec] (the decode side used by
 *      the composable).
 */
class RoutesTest {

    /**
     * Extracts a single query parameter from a route string.
     * `movie?play=...&title=...` → for key `play`, returns the encoded value.
     */
    private fun queryParam(route: String, name: String): String? {
        val query = route.substringAfter("?", "")
        return query.split("&").find { it.startsWith("$name=") }?.substringAfter("=")
    }

    @Test
    fun movieRoute_hasMoviePathPrefix() {
        val route = Routes.movie("/play/123", "The Movie", "p.jpg", "2024", "Desc", "1080p")
        assertTrue(route.startsWith("movie?"))
    }

    @Test
    fun movieRoute_encodesAllParameters() {
        // Special chars that would corrupt an unencoded query string:
        // `/`, `&`, `=`, `+`, `#` in the play URL and poster.
        val play = "/play/123&x=1#frag"
        val title = "The Movie & Sequel + Part 2"
        val poster = "/img/a+b.jpg"
        val year = "2024"
        val desc = "A movie with = and & and # in it"
        val quality = "1080p"

        val route = Routes.movie(play, title, poster, year, desc, quality)

        assertTrue(route.contains("play=%2Fplay%2F123%26x%3D1%23frag"))
        assertTrue(route.contains("title=The+Movie+%26+Sequel+%2B+Part+2"))
        assertTrue(route.contains("poster=%2Fimg%2Fa%2Bb.jpg"))
        assertTrue(route.contains("year=2024"))
        assertTrue(route.contains("desc=A+movie+with+%3D+and+%26+and+%23+in+it"))
        assertTrue(route.contains("quality=1080p"))
    }

    @Test
    fun movieRoute_roundTripsAllParameters() {
        val play = "/play/456"
        val title = "The Movie"
        val poster = "p.jpg"
        val year = "2024"
        val desc = "A description"
        val quality = "1080p"

        val route = Routes.movie(play, title, poster, year, desc, quality)

        assertEquals(play, Routes.dec(queryParam(route, "play")))
        assertEquals(title, Routes.dec(queryParam(route, "title")))
        assertEquals(poster, Routes.dec(queryParam(route, "poster")))
        assertEquals(year, Routes.dec(queryParam(route, "year")))
        assertEquals(desc, Routes.dec(queryParam(route, "desc")))
        assertEquals(quality, Routes.dec(queryParam(route, "quality")))
    }

    @Test
    fun movieRoute_roundTripsArabicMetadata() {
        // Arabic metadata must survive encode/decode without mojibake.
        val play = "/play/456"
        val title = "فيلم عربي"
        val poster = "ar.jpg"
        val year = "2025"
        val desc = "وصف الفيلم"
        val quality = "720p"

        val route = Routes.movie(play, title, poster, year, desc, quality)

        // Percent-encoding (not raw UTF-8 bytes) — the raw string must not
        // leak unencoded Arabic into the query.
        assertTrue(route.contains("%"))
        assertEquals(title, Routes.dec(queryParam(route, "title")))
        assertEquals(desc, Routes.dec(queryParam(route, "desc")))
        assertEquals(play, Routes.dec(queryParam(route, "play")))
    }

    @Test
    fun movieRoute_handlesEmptyMetadata() {
        // Recently-watched / legacy entries carry only title/url/poster.
        val route = Routes.movie("/play/999", "Episode 5", "p.jpg", "", "", "")

        assertTrue(route.startsWith("movie?play="))
        // All optional params still appear (as empty values); the composable
        // simply skips showing them.
        assertTrue(route.contains("&year="))
        assertTrue(route.contains("&desc="))
        assertTrue(route.contains("&quality="))

        assertEquals("Episode 5", Routes.dec(queryParam(route, "title")))
        assertEquals("", Routes.dec(queryParam(route, "year")))
        assertEquals("", Routes.dec(queryParam(route, "desc")))
        assertEquals("", Routes.dec(queryParam(route, "quality")))
    }

    @Test
    fun movieRoute_distinctFromServersRoute() {
        // The movie detail page is a new route, distinct from the
        // server-choose route. (Server-choose is reached FROM the movie page,
        // not instead of it.)
        val movie = Routes.movie("/play/1", "M", "p.jpg", "", "", "")
        val servers = Routes.servers("/play/1", "M", "p.jpg")
        assertTrue(movie.startsWith("movie?"))
        assertTrue(servers.startsWith("servers?"))
        assertFalse(movie.startsWith("servers?"))
        assertFalse(servers.startsWith("movie?"))
    }
}
