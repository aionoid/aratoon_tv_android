package com.aionoid.aratoons.data

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Headless WebView stream extractor.
 *
 * Loads a server embed page (Mixdrop, Uqload, Lulustream, …) in a hidden
 * WebView and captures the real media URL (m3u8/mp4) that the page's own
 * JavaScript requests. This sidesteps per-site eval/obfuscation: whatever
 * the page does to find its stream, the network request lands in
 * [WebViewClient.shouldInterceptRequest].
 *
 * The WebView must be created and destroyed on the main (UI) thread, so all
 * WebView work is posted to the main looper; the suspend call itself just
 * waits for a callback.
 *
 * Timing: on slow devices (e.g. Waydroid right after boot) the page's HTML +
 * JS can take well over 10s to initialize, so the deadline is generous and
 * the play-button probe is retried periodically until capture or deadline.
 */
class WebViewStreamExtractor {

    private val logTag = "WVExtract"
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Load [pageUrl] and capture the media URL its player requests.
     * @param referer Referer header sent with the main page request.
     * @param timeoutMs overall deadline (page load + stream setup).
     * @return captured media URL, or null if nothing was captured in time.
     */
    suspend fun extract(
        context: Context,
        pageUrl: String,
        referer: String,
        timeoutMs: Long = 35_000
    ): String? = suspendCancellableCoroutine { cont ->
        var webView: WebView? = null
        var done = false
        var captured: String? = null
        val appCtx = context.applicationContext

        fun finish(result: String?) {
            if (done) return
            done = true
            Log.d(logTag, "finish: $result (page=$pageUrl)")
            mainHandler.post {
                runCatching { webView?.destroy() }
                if (cont.isActive) cont.resume(result)
            }
        }

        fun probe() {
            if (done) return
            val wv = webView ?: return
            runCatching {
                wv.evaluateJavascript(JS_PROBE) { value ->
                    if (done) return@evaluateJavascript
                    val v = decodeJs(value)
                    if (captured == null && v.isNotEmpty()) {
                        captured = v
                        Log.d(logTag, "probe captured: $v")
                    }
                    if (captured != null) finish(captured)
                    // otherwise keep waiting for an intercepted request or deadline
                }
            }.onFailure { finish(captured) }
        }

        mainHandler.post {
            webView = WebView(appCtx).apply {
                setBackgroundColor(Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                setWebChromeClient(object : WebChromeClient() {
                    override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                        Log.d(logTag, "console: ${cm.message()}")
                        return true
                    }
                })
                setWebViewClient(object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        val u = request.url.toString()
                        val low = u.lowercase()
                        if (captured == null && (low.contains(".m3u8") || low.contains(".mp4"))) {
                            captured = u
                            Log.d(logTag, "intercept captured: $u")
                            // Brief pause so the player's own request settles.
                            mainHandler.postDelayed({ finish(captured) }, 400)
                        }
                        return null
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        Log.d(logTag, "page finished: $url")
                        // Player JS may need a moment before play can be clicked.
                        mainHandler.postDelayed({ probe() }, 1500)
                    }

                    override fun onReceivedError(
                        view: WebView,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?
                    ) {
                        Log.d(logTag, "error: $description at $failingUrl")
                        // Subframe errors (ads etc.) are common; probe anyway.
                        mainHandler.postDelayed({ probe() }, 1000)
                    }
                })
                loadUrl(pageUrl, mapOf("Referer" to referer))
            }
        }
        // Periodic probes: the page's stream-setup JS may run long after load
        // (or never, if bot detection blocks it). Keep trying until the deadline.
        for (delay in longArrayOf(5_000, 10_000, 15_000, 20_000, 25_000, 30_000)) {
            mainHandler.postDelayed({ probe() }, delay)
        }
        mainHandler.postDelayed({ finish(captured) }, timeoutMs)
        cont.invokeOnCancellation {
            mainHandler.post {
                runCatching { webView?.destroy() }
            }
        }
    }

    /** evaluateJavascript returns JSON — a string comes back quoted. */
    private fun decodeJs(json: String?): String {
        val s = json?.trim() ?: return ""
        if (s.length >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }
        return s
    }

    /**
     * Click a likely play button, then report any media URL visible in the
     * DOM: video.src, an m3u8/mp4 reference in the HTML, or the jwplayer
     * source config.
     */
    private val JS_PROBE = """
(function(){
  try {
    var b = document.querySelector('#play, .play, #vid_play, .play-button, .playbtn, .watch-play, button');
    if (b && !b.disabled) b.click();
  } catch(e) {}
  var out = '';
  try {
    var v = document.querySelector('video');
    if (v && v.src && v.src.indexOf('blob:') !== 0) out = v.src;
  } catch(e) {}
  if (!out) {
    try {
      var h = document.documentElement.innerHTML;
      var m = h.match(/https?:\/\/[^\s"'<>]+?\.(?:m3u8|mp4)(?:[?][^\s"'<>]*)?/);
      if (m) out = m[0];
    } catch(e) {}
  }
  if (!out) {
    try {
      var api = window.jwplayer;
      if (api) {
        var src = api().getSource();
        if (src) {
          if (typeof src === 'object' && !Array.isArray(src) && src.file) out = src.file;
          else if (Array.isArray(src) && src.length) out = src[0].file || '';
        }
      }
    } catch(e) {}
  }
  return out;
})()
"""
}
