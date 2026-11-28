package io.screenerbot.mobile

import android.os.SystemClock
import android.content.Intent
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import androidx.test.core.app.ActivityScenario
import android.webkit.WebView
import android.view.ViewGroup
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class NativeEngineSmokeTest {
    @Test
    fun foregroundServiceStartsAuthenticatedLoopbackDashboard() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.startForegroundService(Intent(context, EngineService::class.java))

        val deadline = SystemClock.elapsedRealtime() + 120_000L
        var connection: String? = null
        var lastDiagnostics = "No diagnostics yet"

        while (SystemClock.elapsedRealtime() < deadline) {
            lastDiagnostics = NativeEngine.startupDiagnostics() ?: "No startup diagnostics"
            val candidate = NativeEngine.dashboardConnection()
            val parts = candidate?.split(":", limit = 2)
            val port = parts?.getOrNull(0)?.toIntOrNull()
            val token = parts?.getOrNull(1)
            if (port != null && port in 1..65535 && !token.isNullOrBlank()) {
                connection = candidate
                break
            }

            val startupError = NativeEngine.startupError()
            if (!startupError.isNullOrBlank()) {
                throw AssertionError("Foreground service engine startup failed: $startupError; $lastDiagnostics")
            }
            SystemClock.sleep(500)
        }

        assertNotNull(
            "Foreground service did not expose a valid local port and session token within 120 seconds. $lastDiagnostics",
            connection
        )

        val parts = connection!!.split(":", limit = 2)
        val port = parts[0].toInt()
        val token = parts[1]
        val request = URL("http://127.0.0.1:$port/api/health").openConnection() as HttpURLConnection
        try {
            request.connectTimeout = 5_000
            request.readTimeout = 5_000
            request.setRequestProperty("X-ScreenerBot-Token", token)
            val status = request.responseCode
            assertTrue(
                "Local authenticated health check failed: HTTP $status; $lastDiagnostics",
                status in 200..299
            )
        } finally {
            request.disconnect()
        }
        assertTrue("Local dashboard did not retain a valid port. $lastDiagnostics", port > 0)
    }

    @Test
    fun launcherActivityLoadsWebViewDashboardAndAuthenticatedBootstrap() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val deadline = SystemClock.elapsedRealtime() + 180_000L
            var probeResult: String? = null
            var lastPageState = "WebView not created yet"

            while (SystemClock.elapsedRealtime() < deadline && probeResult == null) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                val latch = CountDownLatch(1)
                scenario.onActivity { activity ->
                    val webView = findWebView(activity.window.decorView)
                    if (webView == null) {
                        lastPageState = "Launcher is showing startup screen"
                        latch.countDown()
                    } else {
                        webView.evaluateJavascript(
                            """
                            (() => {
                              if (document.readyState !== 'complete' || !document.body) {
                                return JSON.stringify({state: document.readyState, body: false});
                              }
                              if (!window.__screenerBotBootstrapProbe) {
                                window.__screenerBotBootstrapProbe = 'pending';
                                fetch('/api/system/bootstrap', {credentials: 'same-origin'})
                                  .then(async response => {
                                    const body = await response.json();
                                    document.documentElement.dataset.screenerbotBootstrap =
                                      JSON.stringify({status: response.status, keys: Object.keys(body),
                                        hasUiReady: Object.prototype.hasOwnProperty.call(body, 'ui_ready'),
                                        hasPhase: Object.prototype.hasOwnProperty.call(body, 'phase'),
                                        bodyText: document.body.innerText.slice(0, 300)});
                                  })
                                  .catch(error => {
                                    document.documentElement.dataset.screenerbotBootstrap =
                                      JSON.stringify({error: String(error)});
                                  });
                              }
                              return document.documentElement.dataset.screenerbotBootstrap || 'pending';
                            })()
                            """.trimIndent()
                        ) { value ->
                            val decoded = value?.removeSurrounding("\"", "\"")?.replace("\\\"", "\"")
                            if (!decoded.isNullOrBlank() && decoded != "pending" && decoded != "null") {
                                probeResult = decoded
                            } else {
                                lastPageState = "WebView loaded; waiting for authenticated bootstrap response"
                            }
                            latch.countDown()
                        }
                    }
                }
                latch.await(2, TimeUnit.SECONDS)
                if (probeResult == null) SystemClock.sleep(1000)
            }

            assertNotNull(
                "Launcher did not load dashboard and receive /api/system/bootstrap. Last state: $lastPageState",
                probeResult
            )
            val result = probeResult!!
            assertTrue("Bootstrap did not return HTTP 200: $result", result.contains("\"status\":200"))
            assertTrue("Bootstrap response missing ui_ready: $result", result.contains("\"hasUiReady\":true"))
            assertTrue("Bootstrap response missing phase: $result", result.contains("\"hasPhase\":true"))
        } finally {
            scenario.close()
        }
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val found = findWebView(view.getChildAt(index))
                if (found != null) return found
            }
        }
        return null
    }

}
