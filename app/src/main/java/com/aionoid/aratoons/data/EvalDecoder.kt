package com.aionoid.aratoons.data

import android.util.Log

/**
 * Decoders for the obfuscated JS eval() configs used by the streaming
 * servers (Mixdrop, StreamHG, Uqload, Earnvids, Lulustream, Goodstream).
 *
 * Port of the Kodi plugin's decoders. The critical detail: the JS eval
 * function uses `c.toString(36)` — BASE-36, not hex or decimal.
 */
object EvalDecoder {

    private const val TAG = "Mixdrop"
    private const val B36_CHARS = "0123456789abcdefghijklmnopqrstuvwxyz"

    /** Same as JS `c.toString(36)`. */
    fun toBase36(n: Int): String {
        if (n == 0) return "0"
        var x = n
        val sb = StringBuilder()
        while (x > 0) {
            sb.insert(0, B36_CHARS[x % 36])
            x /= 36
        }
        return sb.toString()
    }

    /**
     * Extract the (encoded, count, keywords) triple from an eval() block.
     * Returns null when the block is not found.
     */
    private fun evalParts(html: String): Triple<String, Int, List<String>>? {
        var pos = html.indexOf("eval(function(p,a,c,k,e,d){while(c--)")
        if (pos < 0) pos = html.indexOf("eval(function(p,a,c,k,e,d)")
        if (pos < 0) return null

        var encStart = html.indexOf("return p}('", pos)
        if (encStart < 0) encStart = html.indexOf("}('", pos)
        if (encStart < 0) return null
        encStart += if (html.indexOf("return p}('", pos) >= 0) "return p}('".length else "}('".length

        val rest = html.substring(encStart)
        val close = Regex("',\\d+,(\\d+),'").find(rest) ?: return null
        val count = close.groupValues[1].toIntOrNull() ?: return null

        val encoded = rest.substring(0, close.range.start)
        val after = rest.substring(close.range.endInclusive)
        val splitPos = after.indexOf("'.split('|')")
        if (splitPos < 0) return null
        val keywords = after.substring(0, splitPos).split("|")
        return Triple(encoded, count, keywords)
    }

    /**
     * Decode a standard base-36 eval config and return the decoded JS.
     */
    fun decodeBase36(html: String): String? {
        val parts = evalParts(html) ?: return null
        val (encoded, count, keywords) = parts
        var decoded = encoded
        for (i in count - 1 downTo 0) {
            if (i >= keywords.size || keywords[i].isEmpty()) continue
            val token = toBase36(i)
            decoded = Regex("(?<![\\w])" + Regex.escape(token) + "(?![\\w])")
                .replace(decoded, Regex.escape(keywords[i]))
        }
        return decoded
    }

    /**
     * Mixdrop: identity decoder (e = function(c){return c}) — tokens are
     * plain decimal indices. Returns the decoded JS or null.
     */
    fun decodeMixdrop(html: String): String? {
        val pos = html.indexOf("eval(function(p,a,c,k,e,d)")
        if (pos < 0) {
            Log.d(TAG, "decodeMixdrop: no eval header (html=${html.length}b)")
            return null
        }
        val chunk = html.substring(pos, (pos + 300).coerceAtMost(html.length))
        val isIdentity = "return c}" in chunk && "toString" !in chunk
        if (!isIdentity) {
            Log.d(TAG, "decodeMixdrop: not identity header: " + chunk.substring(0, 120))
            return null
        }

        // jsdeflate call marker varies by build: "}('" (classic) or the
        // "}'(" variant — accept whichever appears first after the header.
        val i1 = html.indexOf("}'(", pos)
        val i2 = html.indexOf("}('", pos)
        val encStart = when {
            i1 >= 0 && i2 >= 0 -> minOf(i1, i2)
            i1 >= 0 -> i1
            else -> i2
        }
        if (encStart < 0) {
            Log.d(TAG, "decodeMixdrop: no call marker (i1=$i1 i2=$i2)")
            return null
        }
        val rest = html.substring(encStart + 3)
        val splitMarker = "'.split('|'),0,{}))"
        val splitPos = rest.indexOf(splitMarker)
        if (splitPos < 0) {
            Log.d(TAG, "decodeMixdrop: no split close")
            return null
        }
        val callArgs = rest.substring(0, splitPos)
        val close = Regex("',\\d+,(\\d+),'(.*)$").find(callArgs)
        if (close == null) {
            Log.d(TAG, "decodeMixdrop: no close args; tail=" +
                callArgs.substring(callArgs.length.coerceAtMost(200)))
            return null
        }
        val encoded = callArgs.substring(0, close.range.start)
        val count = close.groupValues[1].toIntOrNull() ?: return null
        var keywords = close.groupValues[2].split("|")

        // Rotated builds wrap every keyword fragment: "Q<frag>E" (the E
        // suffix is dropped for fragments at the end of a value) and use
        // "Q" as the joiner where the clean build uses "-". Unwrap the
        // table before decoding so the output matches the clean form.
        var wrapped = false
        if (keywords.size > 2 && keywords[1].startsWith("Q") && keywords[1].endsWith("E")) {
            wrapped = true
            keywords = keywords.map {
                var s = it
                if (s.startsWith("Q")) s = s.substring(1)
                if (s.length > 1 && s.endsWith("E")) s = s.substring(0, s.length - 1)
                s
            }
            Log.d(TAG, "decodeMixdrop: unwrapped keyword table (kw[1]=${keywords[1]})")
        }

        // Single-pass digit-run substitution (equivalent to the JS
        // per-keyword replace for clean builds, and required for wrapped
        // builds where a "Q" joiner sits between adjacent tokens and
        // breaks word-boundary regexes).
        val sb = StringBuilder(encoded.length * 2)
        var i = 0
        while (i < encoded.length) {
            val c = encoded[i]
            if (c.isDigit()) {
                var j = i
                while (j < encoded.length && encoded[j].isDigit()) j++
                val idx = encoded.substring(i, j).toIntOrNull()
                if (idx != null && idx < keywords.size && keywords[idx].isNotEmpty()) {
                    sb.append(keywords[idx])
                } else {
                    sb.append(encoded, i, j)
                }
                i = j
            } else {
                sb.append(c)
                i++
            }
        }
        var decoded = sb.toString()
        if (wrapped) {
            // Restore the "-" joiner the wrapped build replaced with "Q".
            decoded = Regex("(?<=[A-Za-z0-9])Q(?=[A-Za-z0-9])").replace(decoded, "-")
        }
        return decoded
    }

    /**
     * Mixdrop: extract the direct MP4 URL from the embed page.
     */
    fun extractMixdropUrl(html: String): String? {
        val decoded = decodeMixdrop(html) ?: return null
        // The MP4 reference may appear as wurl/furl/MDCore.N with either
        // quote style.
        val m = Regex("wurl[\"']?\\s*=\\s*[\"']([^\"']+\\.mp4[^\"']*)[\"']").find(decoded)
            ?: Regex("furl[\"']?\\s*=\\s*[\"']([^\"']+\\.mp4[^\"']*)[\"']").find(decoded)
            ?: Regex("MDCore\\.\\d+=[\"']([^\"']+\\.mp4[^\"']*)[\"']").find(decoded)
            ?: run {
                Log.d(TAG, "extractMixdropUrl: decoded ${decoded.length}b but no mp4 ref; " +
                    "head=" + decoded.substring(0, 200))
                return null
            }
        var mp4 = m.groupValues[1]
        if (!mp4.startsWith("http")) {
            mp4 = if (mp4.startsWith("//")) "https:$mp4" else "https://$mp4"
        }
        Log.d(TAG, "extractMixdropUrl: $mp4")
        return mp4
    }

    /**
     * Standard JWPlayer servers (Uqload, Earnvids, …): base-36 decode then
     * extract the first .m3u8 (preferred) or .mp4 URL.
     */
    fun extractJwPlayerUrl(html: String): String? {
        val decoded = decodeBase36(html) ?: return null
        Regex("(https?://[^\"'\\\\]+\\.m3u8[^\"'\\\\]*)").find(decoded)?.let {
            return it.groupValues[1]
        }
        Regex("(https?://[^\"'\\\\]+\\.mp4[^\"'\\\\]*)").find(decoded)?.let {
            return it.groupValues[1]
        }
        return null
    }

    /**
     * StreamHG (hgplaycdn.com): base-36 decode, prefer the .m3u8 URL with
     * query params over the bare .txt one.
     */
    fun extractStreamHgUrl(html: String): String? {
        val decoded = decodeBase36(html) ?: return null
        for (m in Regex("(https?://[^\\s\"'`<>,;]{30,})").findAll(decoded)) {
            val url = m.groupValues[1].trimEnd('"', ';', ',')
            if ("master" in url && ".m3u8" in url) return url
        }
        for (m in Regex("(https?://[^\\s\"'`<>,;]{30,})").findAll(decoded)) {
            val url = m.groupValues[1].trimEnd('"', ';', ',')
            if ("master" in url && ".txt" in url) return url
        }
        return null
    }

    /**
     * Lulustream: katana-style obfuscation followed by a standard base-36
     * eval decoder. Returns the decoded JS or null.
     */
    fun decodeKatana(html: String): String? = decodeBase36(html)

    /**
     * Lulustream: extract the m3u8 URL from the eval-encoded config.
     */
    fun extractLulustreamUrl(html: String): String? {
        val decoded = decodeKatana(html) ?: return null
        Regex("(https?://[^\\s\"'`<>,;]{30,})").find(decoded)?.let { m ->
            val url = m.groupValues[1].trimEnd('"', ';', ',')
            if ("m3u8" in url) return url
        }
        return null
    }

    /**
     * Goodstream: plain-text JWPlayer config — the m3u8 URL sits directly in
     * `sources: [{file: "..."}]`. No obfuscation.
     */
    fun extractGoodstreamUrl(html: String): String? {
        Regex("sources:\\s*\\[\\s*\\{\\s*file\\s*:\\s*[\"']([^\"']+\\.m3u8[^\"']*)[\"']").find(html)?.let {
            return it.groupValues[1]
        }
        Regex("(https?://[^'\"\\s]+\\.m3u8[^'\"\\s]*)").find(html)?.let {
            return it.groupValues[1]
        }
        return null
    }

    /**
     * Clean JWPlayer URL artifacts left by empty-keyword substitution:
     *  1) /FILE_ID_,t1,t2,t3,.garbage/master.m3u8 -> /FILE_ID_t3/master.m3u8
     *  2) Lulustream: /BASE/ID_qual,lang/...,,.urlset/master.m3u8
     *     -> /BASE/ID_qual/index-v1-a2.m3u8
     */
    fun cleanJwPlayerUrl(url: String): String {
        var out = url
        // Format 2: Lulustream
        val m2 = Regex(
            "(https://[^/]+/hls2/[^/]+/[^/]+/),(\\w+)_(\\w+).*?(?:master|index-v1-a\\d)\\.m3u8"
        ).find(out)
        if (m2 != null) {
            val base = m2.groupValues[1]
            val fileId = m2.groupValues[2]
            val quality = m2.groupValues[3]
            val query = if ("?" in out) out.substring(out.indexOf('?')) else ""
            return "$base${fileId}_$quality/index-v1-a2.m3u8$query"
        }
        // Format 1: Uqload/Goodstream
        val m1 = Regex(
            "(/[^,]+_),([^,]+),([^,]+),([^,]+),\\.[^/]*/(master|index-v1-a1)"
        ).find(out)
        if (m1 != null) {
            val basePath = m1.groupValues[1]
            val quality = m1.groupValues[3]
            val playlist = m1.groupValues[5]
            out = out.replace(m1.value, "$basePath$quality/$playlist")
        }
        return out
    }

    /**
     * Extract $.cookie('name', 'value') pairs from a JWPlayer page
     * (Uqload CDN requires them).
     */
    fun extractJwPlayerCookies(html: String): String {
        val cookies = ArrayList<String>()
        for (m in Regex("\\.cookie\\(['\"]([^'\"]+)['\"],\\s*['\"]([^'\"]+)['\"]").findAll(html)) {
            cookies.add("${m.groupValues[1]}=${m.groupValues[2]}")
        }
        return cookies.joinToString("; ")
    }
}
