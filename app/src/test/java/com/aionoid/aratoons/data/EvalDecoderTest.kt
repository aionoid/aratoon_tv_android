package com.aionoid.aratoons.data

import org.junit.Test
import org.junit.Assert.*

class EvalDecoderTest {

    // ------------------------------------------------------------------
    // toBase36
    // ------------------------------------------------------------------

    @Test
    fun toBase36_zero_returns_zeroString() {
        assertEquals("0", EvalDecoder.toBase36(0))
    }

    @Test
    fun toBase36_smallNumbers_matchJSToString36() {
        assertEquals("a", EvalDecoder.toBase36(10))
        assertEquals("z", EvalDecoder.toBase36(35))
        assertEquals("10", EvalDecoder.toBase36(36))
        assertEquals("11", EvalDecoder.toBase36(37))
        assertEquals("zz", EvalDecoder.toBase36(1295))
        assertEquals("100", EvalDecoder.toBase36(1296))
    }

    @Test
    fun toBase36_negative_returns_empty() {
        assertEquals("", EvalDecoder.toBase36(-1))
    }

    // ------------------------------------------------------------------
    // decodeBase36
    // ------------------------------------------------------------------

    @Test
    fun decodeBase36_returnsNull_whenNoEvalBlock() {
        assertNull(EvalDecoder.decodeBase36("no eval here"))
    }

    @Test
    fun decodeBase36_returnsNull_whenIncompleteEval() {
        assertNull(EvalDecoder.decodeBase36("eval(function(p,a,c,k,e,d){while(c--)"))
    }

    // ------------------------------------------------------------------
    // extractJwPlayerUrl
    // ------------------------------------------------------------------

    @Test
    fun extractJwPlayerUrl_returnsNull_whenNoEval() {
        assertNull(EvalDecoder.extractJwPlayerUrl("<html><body>No eval</body></html>"))
    }

    // ------------------------------------------------------------------
    // extractStreamHgUrl
    // ------------------------------------------------------------------

    @Test
    fun extractStreamHgUrl_returnsNull_whenNoEval() {
        assertNull(EvalDecoder.extractStreamHgUrl("<html><body>No eval</body></html>"))
    }

    // ------------------------------------------------------------------
    // extractLulustreamUrl
    // ------------------------------------------------------------------

    @Test
    fun extractLulustreamUrl_returnsNull_whenNoEval() {
        assertNull(EvalDecoder.extractLulustreamUrl("<html><body>No eval</body></html>"))
    }

    // ------------------------------------------------------------------
    // extractGoodstreamUrl
    // ------------------------------------------------------------------

    @Test
    fun extractGoodstreamUrl_plainSourcesConfig() {
        val html = """
            <script>
            var sources = [{file: "https://cdn.example.com/video/master.m3u8"}]
            </script>
        """.trimIndent()
        val url = EvalDecoder.extractGoodstreamUrl(html)
        assertNotNull(url)
        assertTrue(url!!.contains("master.m3u8"))
        assertTrue(url!!.contains("cdn.example.com"))
    }

    @Test
    fun extractGoodstreamUrl_genericM3u8InHtml() {
        val html = """
            <script>
            var videoSrc = "https://cdn.example.com/stream/index.m3u8";
            </script>
        """.trimIndent()
        val url = EvalDecoder.extractGoodstreamUrl(html)
        assertNotNull(url)
        assertTrue(url!!.contains("index.m3u8"))
    }

    @Test
    fun extractGoodstreamUrl_nullWhenNoM3u8() {
        assertNull(EvalDecoder.extractGoodstreamUrl("<html>No stream</html>"))
    }

    // ------------------------------------------------------------------
    // cleanJwPlayerUrl
    // ------------------------------------------------------------------

    @Test
    fun cleanJwPlayerUrl_format1UqloadGoodstream() {
        val input = "https://cdn.example.com/hls2/abc123_,t1,t2,t3,.garbage/master.m3u8"
        val output = EvalDecoder.cleanJwPlayerUrl(input)
        assertEquals("https://cdn.example.com/hls2/abc123_t3/master.m3u8", output)
    }

    @Test
    fun cleanJwPlayerUrl_format2Lulustream() {
        val input = "https://cdn.example.com/hls2/base/file/,123_high/master.m3u8"
        val output = EvalDecoder.cleanJwPlayerUrl(input)
        assertEquals("https://cdn.example.com/hls2/base/file/123_high/index-v1-a2.m3u8", output)
    }

    @Test
    fun cleanJwPlayerUrl_unchangedWhenNoPattern() {
        val input = "https://cdn.example.com/clean/stream.m3u8"
        assertEquals(input, EvalDecoder.cleanJwPlayerUrl(input))
    }

    // ------------------------------------------------------------------
    // extractJwPlayerCookies
    // ------------------------------------------------------------------

    @Test
    fun extractJwPlayerCookies_emptyWhenNoCookies() {
        assertEquals("", EvalDecoder.extractJwPlayerCookies("<html>No cookies</html>"))
    }

    @Test
    fun extractJwPlayerCookies_extractsCookiePairs() {
        val html = """
            <script>
            $.cookie("session", "abc123");
            $.cookie("token", "xyz789");
            </script>
        """.trimIndent()
        val cookies = EvalDecoder.extractJwPlayerCookies(html)
        assertTrue(cookies.contains("session=abc123"))
        assertTrue(cookies.contains("token=xyz789"))
    }
}
