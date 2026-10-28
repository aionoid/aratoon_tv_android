package com.aionoid.aratoons.data

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat

/**
 * Headless WebView stream extractor, running in its own process (":wv").
 *
 * Why a separate process: the vendor System WebView (Chromium) can FATAL
 * during first-time in-process init on some TV boxes (e.g. a missing
 * shape-detection provider turns into a native SIGTRAP — not catchable in
 * Java). Running it in ":wv" means that crash kills this service process
 * instead of the whole app; the extractor then simply times out.
 *
 * The service receives the page URL via [startService] extras and reports
 * the captured media URL (or null) with a broadcast on [ACTION_RESULT].
 * The WebView is created on this process's main looper, as required.
 */
class WebViewExtractService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var done = false
    private var captured: String? = null
    private var reqId: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pageUrl = intent?.getStringExtra(EXTRA_PAGE)
        if (pageUrl == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val referer = intent.getStringExtra(EXTRA_REFERER) ?: ""
        reqId = intent.getStringExtra(EXTRA_REQ) ?: ""
        val timeoutMs = intent.getLongExtra(EXTRA_TIMEOUT, DEFAULT_TIMEOUT_MS)

        Log.d(TAG, "start: $pageUrl req=$reqId (pid=${android.os.Process.myPid()})")

        fun finish(result: String?) {
            if (done) return
            done = true
            Log.d(TAG, "finish: $result req=$reqId")
            val send = Intent(ACTION_RESULT).setPackage(packageName)
                .putExtra(EXTRA_REQ, reqId)
                .putExtra(EXTRA_URL, result)
            sendBroadcast(send)
            runCatching { webView?.destroy() }
            webView = null
            stopSelf()
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
                        Log.d(TAG, "probe captured: $v")
                    }
                    if (captured != null) finish(captured)
                    // otherwise keep waiting for an intercepted request or deadline
                }
            }.onFailure { finish(captured) }
        }

        // onStartCommand runs on this process's main thread — a legal place
        // to create the WebView.
        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            setWebChromeClient(object : WebChromeClient() {
                override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                    Log.d(TAG, "console: ${cm.message()}")
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
                        Log.d(TAG, "intercept captured: $u")
                        // Brief pause so the player's own request settles.
                        mainHandler.postDelayed({ finish(captured) }, 400)
                    }
                    return null
                }

                override fun onPageFinished(view: WebView, url: String) {
                    Log.d(TAG, "page finished: $url")
                    // Player JS may need a moment before play can be clicked.
                    mainHandler.postDelayed({ probe() }, 1500)
                }

                override fun onReceivedError(
                    view: WebView,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    Log.d(TAG, "error: $description at $failingUrl")
                    // Subframe errors (ads etc.) are common; probe anyway.
                    mainHandler.postDelayed({ probe() }, 1000)
                }
            })
            loadUrl(pageUrl, mapOf("Referer" to referer))
        }

        // Periodic probes: the page's stream-setup JS may run long after load
        // (or never, if bot detection blocks it). Keep trying until the deadline.
        for (delay in longArrayOf(5_000, 10_000, 15_000, 20_000, 25_000, 30_000)) {
            mainHandler.postDelayed({ probe() }, delay)
        }
        mainHandler.postDelayed({ finish(captured) }, timeoutMs)
        return START_NOT_STICKY
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

    companion object {
        const val TAG = "WVExtract"
        const val ACTION_RESULT = "com.aionoid.aratoons.WV_EXTRACT_RESULT"
        const val EXTRA_PAGE = "page"
        const val EXTRA_REFERER = "referer"
        const val EXTRA_REQ = "req"
        const val EXTRA_URL = "url"
        const val EXTRA_TIMEOUT = "timeout"
        const val DEFAULT_TIMEOUT_MS = 35_000L

        /** Register [receiver] for [ACTION_RESULT] broadcasts. */
        fun registerResultReceiver(context: Context, receiver: android.content.BroadcastReceiver) {
            ContextCompat.registerReceiver(
                context, receiver,
                android.content.IntentFilter(ACTION_RESULT),
                ContextCompat.RECEIVER_EXPORTED
            )
        }
    }
}
