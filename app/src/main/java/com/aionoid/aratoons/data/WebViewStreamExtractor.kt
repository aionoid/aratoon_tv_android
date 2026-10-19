package com.aionoid.aratoons.data

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.HandlerThread
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
 */
class WebViewStreamExtractor {

    private val tag = "WVExtract"

    /**
     * Load [pageUrl] and capture the media URL its player requests.
     * @param referer Referer header sent with the main page request.
     * @return captured media URL, or null if nothing was captured within [timeoutMs].
     */
    suspend fun extract(
        context: Context,
        pageUrl: String,
        referer: String,
        timeoutMs: Long = 12_000
    ): String? = suspendCancellableCoroutine { cont ->
        val thread = HandlerThread("wv-extract").apply { start() }
        val handler = Handler(thread.looper)
        var webView: WebView? = null
        var done = false
        var captured: String? = null
        var probed = 0
        val appCtx = context.applicationContext

        fun finish(result: String?) {
            if (done) return
            done = true
            Log.d(tag, "finish: $result (page=$pageUrl)")
            handler.post {
                runCatching { webView?.destroy() }
                thread.quitSafely()
                if (cont.isActive) cont.resume(result)
            }
        }

        fun probe() {
            val wv = webView ?: return
            probed++
            runCatching {
                wv.evaluateJavascript(JS_PROBE) { value ->
                    val v = decodeJs(value)
                    if (captured == null && v.isNotEmpty()) {
                        captured = v
                        Log.d(tag, "probe captured: $v")
                    }
                    if (captured != null) {
                        finish(captured)
                    } else if (probed >= 3) {
                        finish(null)
                    }
                    // otherwise keep waiting for an intercepted request or timeout
                }
            }.onFailure { finish(captured) }
        }

        handler.post {
            webView = WebView(appCtx).apply {
                setBackgroundColor(Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                setWebChromeClient(object : WebChromeClient() {
                    override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                        Log.d(tag, "console: ${cm.message()}")
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
                            Log.d(tag, "intercept captured: $u")
                            handler.postDelayed({ finish(captured) }, 400)
                        }
                        return null
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        // Give the player JS time to initialize, then click play + probe.
                        handler.postDelayed({ probe() }, 1500)
                        handler.postDelayed({ probe() }, 4500)
                    }

                    override fun onReceivedError(
                        view: WebView,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?
                    ) {
                        Log.d(tag, "error: $description at $failingUrl")
                        handler.postDelayed({ probe() }, 800)
                    }
                })
                loadUrl(pageUrl, mapOf("Referer" to referer))
            }
        }
        handler.postDelayed({ finish(captured) }, timeoutMs)
        cont.invokeOnCancellation {
            handler.post {
                runCatching { webView?.destroy() }
                thread.quitSafely()
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
