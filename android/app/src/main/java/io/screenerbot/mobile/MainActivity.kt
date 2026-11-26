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
import android.widget.ImageView
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
            setPadding(24, 28, 24, 28)
            setBackgroundColor(0xFF07111D.toInt())
        }
        val title = TextView(this).apply {
            text = "SCREENERBOT"
            textSize = 27f
            letterSpacing = 0.16f
            setTextColor(0xFFF4FBFF.toInt())
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "AI  •  SOLANA  •  MARKET INTELLIGENCE"
            textSize = 10f
            letterSpacing = 0.12f
            setTextColor(0xFF36E8B5.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 14)
        }
        val artwork = ImageView(this).apply {
            setImageResource(R.drawable.screenerbot_splash)
            contentDescription = "ScreenerBot — ρομπότ τεχνητής νοημοσύνης και γραφήματα αγοράς"
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }
        status = TextView(this).apply {
            text = "Εκκίνηση της μηχανής ScreenerBot…"
            textSize = 14f
            setTextColor(0xFFB8C8D8.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 18)
        }
        val retry = Button(this).apply {
            text = "Επανάληψη σύνδεσης"
            setOnClickListener { attempts = 0; startEngineService(); waitForDashboard() }
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(subtitle, LinearLayout.LayoutParams(-1, -2))
        root.addView(artwork, LinearLayout.LayoutParams(-1, 0, 1f))
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
        status.text = "Εκκίνηση της μηχανής ScreenerBot… ($attempts)"
        executor.execute {
            var probeDetail = "Δεν έχει ληφθεί ακόμη σύνδεση από τη μηχανή"
            val connection = try {
                NativeEngine.dashboardConnection()
            } catch (error: Throwable) {
                probeDetail = "JNI dashboardConnection: ${error.javaClass.simpleName}: ${error.message ?: "χωρίς λεπτομέρειες"}"
                null
            }
            val session = connection?.split(":", limit = 2)
            val port = session?.getOrNull(0)?.toIntOrNull()
            val token = session?.getOrNull(1)
            if (connection != null && (port == null || token.isNullOrBlank())) {
                probeDetail = "Η JNI επέστρεψε μη έγκυρη θύρα ή token: $connection"
            }
            val ready = if (port != null && !token.isNullOrBlank()) {
                try {
                    val request = URL("http://127.0.0.1:$port/api/health")
                        .openConnection() as HttpURLConnection
                    request.connectTimeout = 2500
                    request.readTimeout = 2500
                    request.setRequestProperty("X-ScreenerBot-Token", token)
                    val code = request.responseCode
                    request.disconnect()
                    probeDetail = "Τοπικό health check: HTTP $code"
                    code in 200..299
                } catch (error: Exception) {
                    probeDetail = "Τοπικό health check απέτυχε: ${error.javaClass.simpleName}: ${error.message ?: "χωρίς λεπτομέρειες"}"
                    false
                }
            } else false

            val startupError = if (!ready) {
                try { NativeEngine.startupError() } catch (_: Throwable) { null }
            } else null
            val diagnostics = try { NativeEngine.startupDiagnostics() } catch (_: Throwable) { null }

            runOnUiThread {
                if (ready && port != null && token != null) {
                    openDashboard(port, token)
                } else if (!startupError.isNullOrBlank()) {
                    status.text = "Σφάλμα εκκίνησης: $startupError"
                } else {
                    val servicePrefs = getSharedPreferences("engine_startup", MODE_PRIVATE)
                    val serviceStage = servicePrefs.getString("stage", "Service not reported")
                    val serviceError = servicePrefs.getString("error", "").orEmpty()
                    val serviceDiagnostics = if (serviceError.isNotBlank()) {
                        "Android service: $serviceStage — $serviceError"
                    } else {
                        "Android service: $serviceStage"
                    }
                    status.text = "Εκκίνηση ScreenerBot… ($attempts)\n$serviceDiagnostics\n${diagnostics ?: "Δεν υπάρχουν ακόμη στοιχεία μηχανής"}\n$probeDetail"
                    if (attempts < 120) {
                        handler.postDelayed({ waitForDashboard() }, 1000)
                    } else {
                        val servicePrefs = getSharedPreferences("engine_startup", MODE_PRIVATE)
                        val serviceStage = servicePrefs.getString("stage", "Service not reported")
                        val serviceError = servicePrefs.getString("error", "").orEmpty()
                        status.text = "Η μηχανή δεν ανταποκρίνεται.\nAndroid service: $serviceStage\n$serviceError\n${diagnostics ?: ""}\n$probeDetail"
                    }
                }
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

            // Inject the local per-process session token only into same-origin API calls.
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
                    headers.set('Accept-Language', 'el-GR,el;q=0.9,en;q=0.8');
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
                      this.setRequestHeader('Accept-Language', 'el-GR,el;q=0.9,en;q=0.8');
                    }
                    return originalSend.call(this, body);
                  };
                })();
            """.trimIndent()
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(this, script, setOf(origin))
            }
            loadUrl("$origin/", mapOf("X-ScreenerBot-Token" to token, "Accept-Language" to "el-GR,el;q=0.9,en;q=0.8"))
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
