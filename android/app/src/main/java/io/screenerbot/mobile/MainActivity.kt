package io.screenerbot.mobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private var attempts = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        showStartupScreen()
        startEngineService()
        waitForDashboard()
    }

    private fun showStartupScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(36, 36, 36, 36)
            setBackgroundColor(0xFF10131B.toInt())
        }
        val title = TextView(this).apply {
            text = "SCREENERBOT"
            textSize = 26f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            text = "Starting the local trading engine…"
            textSize = 15f
            setTextColor(0xFFB8C1D1.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }
        val retry = Button(this).apply {
            text = "Retry connection"
            setOnClickListener { attempts = 0; waitForDashboard() }
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(status, LinearLayout.LayoutParams(-1, -2))
        root.addView(retry, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }

    private fun startEngineService() {
        val intent = Intent(this, EngineService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }

    private fun waitForDashboard() {
        attempts++
        status.text = "Starting ScreenerBot engine… ($attempts)"
        executor.execute {
            val connection = try {
                NativeEngine.dashboardConnection()
            } catch (_: Throwable) {
                null
            }
            val session = connection?.split(":", limit = 2)
            val port = session?.getOrNull(0)?.toIntOrNull()
            val token = session?.getOrNull(1)
            val ready = if (port != null && !token.isNullOrBlank()) {
                try {
                    val request = URL("http://127.0.0.1:$port/api/health")
                        .openConnection() as HttpURLConnection
                    request.connectTimeout = 1500
                    request.readTimeout = 1500
                    request.setRequestProperty("X-ScreenerBot-Token", token)
                    val code = request.responseCode
                    request.disconnect()
                    code in 200..299
                } catch (_: Exception) {
                    false
                }
            } else false

            runOnUiThread {
                if (ready && port != null && token != null) openDashboard(port, token)
                else if (attempts < 120) handler.postDelayed({ waitForDashboard() }, 1000)
                else status.text = "Engine is not responding. Check its notification and retry."
            }
        }
    }

    private fun openDashboard(port: Int, token: String) {
        val origin = "http://127.0.0.1:$port"
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()

            // Match the desktop shell: inject the per-process session token only
            // into same-origin API calls. It is never placed in the page URL.
            val quotedToken = JSONObject.quote(token)
            val script = """
                (() => {
                  const token = $quotedToken;
                  const sameOrigin = (value) => {
                    try { return new URL(value, location.href).origin === location.origin; }
                    catch (_) { return false; }
                  };
                  const originalFetch = window.fetch.bind(window);
                  window.fetch = function(input, init) {
                    const value = typeof input === 'string' ? input : input.url;
                    if (!sameOrigin(value)) return originalFetch(input, init);
                    const sourceHeaders = init && init.headers
                      ? init.headers
                      : (input instanceof Request ? input.headers : undefined);
                    const headers = new Headers(sourceHeaders);
                    headers.set('X-ScreenerBot-Token', token);
                    return originalFetch(input, { ...(init || {}), headers });
                  };
                  const originalOpen = XMLHttpRequest.prototype.open;
                  const originalSend = XMLHttpRequest.prototype.send;
                  XMLHttpRequest.prototype.open = function(method, url, ...rest) {
                    this.__screenerBotLocal = sameOrigin(url);
                    return originalOpen.call(this, method, url, ...rest);
                  };
                  XMLHttpRequest.prototype.send = function(body) {
                    if (this.__screenerBotLocal) {
                      this.setRequestHeader('X-ScreenerBot-Token', token);
                    }
                    return originalSend.call(this, body);
                  };
                })();
            """.trimIndent()
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(this, script, setOf(origin))
            }
            loadUrl("$origin/", mapOf("X-ScreenerBot-Token" to token))
        }
        setContentView(webView, ViewGroup.LayoutParams(-1, -1))
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2602)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // The foreground service owns the engine lifecycle; closing this Activity
        // must not stop background monitoring or trading services.
    }

    private lateinit var webView: WebView
}
