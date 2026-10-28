package com.aionoid.aratoons.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Headless WebView stream extractor (orchestrator).
 *
 * The actual WebView lives in [WebViewExtractService], which runs in a
 * separate process (":wv"): if the vendor System WebView FATALs during
 * first-time init on a TV box (native SIGTRAP, not catchable in Java),
 * only that service process dies and [extract] simply returns null.
 *
 * Loads a server embed page (Mixdrop, Uqload, Lulustream, …) and captures
 * the real media URL (m3u8/mp4) that the page's own JavaScript requests.
 * This sidesteps per-site eval/obfuscation: whatever the page does to find
 * its stream, the network request lands in the service's
 * WebViewClient.shouldInterceptRequest.
 */
class WebViewStreamExtractor {

    private val logTag = WebViewExtractService.TAG

    /**
     * Load [pageUrl] and capture the media URL its player requests.
     * @param referer Referer header sent with the main page request.
     * @param timeoutMs overall deadline (page load + stream setup).
     * @return captured media URL, or null if nothing was captured in time
     *         (including the case where the WebView process crashed).
     */
    suspend fun extract(
        context: Context,
        pageUrl: String,
        referer: String,
        timeoutMs: Long = WebViewExtractService.DEFAULT_TIMEOUT_MS
    ): String? {
        val appCtx = context.applicationContext
        val reqId = "r${System.currentTimeMillis()}"
        val result = CompletableDeferred<String?>()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.getStringExtra(WebViewExtractService.EXTRA_REQ) == reqId) {
                    Log.d(logTag, "result: ${i.getStringExtra(WebViewExtractService.EXTRA_URL)}")
                    result.complete(i.getStringExtra(WebViewExtractService.EXTRA_URL))
                }
            }
        }
        WebViewExtractService.registerResultReceiver(appCtx, receiver)

        val intent = Intent(appCtx, WebViewExtractService::class.java)
            .putExtra(WebViewExtractService.EXTRA_PAGE, pageUrl)
            .putExtra(WebViewExtractService.EXTRA_REFERER, referer)
            .putExtra(WebViewExtractService.EXTRA_REQ, reqId)
            .putExtra(WebViewExtractService.EXTRA_TIMEOUT, timeoutMs)
        appCtx.startService(intent)

        // The service has its own deadline; allow a little extra for the
        // broadcast to arrive. If the :wv process crashed, this times out.
        val url = withTimeoutOrNull(timeoutMs + 10_000) { result.await() }
        runCatching { appCtx.unregisterReceiver(receiver) }
        return url
    }
}
