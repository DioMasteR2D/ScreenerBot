package io.screenerbot.mobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var webView: WebView
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
            val ready = try {
                val connection = URL("http://127.0.0.1:8080/").openConnection() as HttpURLConnection
                connection.connectTimeout = 1500
                connection.readTimeout = 1500
                connection.requestMethod = "GET"
                val code = connection.responseCode
                connection.disconnect()
                code in 200..499
            } catch (_: Exception) {
                false
            }
            runOnUiThread {
                if (ready) openDashboard()
                else if (attempts < 90) handler.postDelayed({ waitForDashboard() }, 1000)
                else status.text = "Engine is not responding yet. Check the notification and retry."
            }
        }
    }

    private fun openDashboard() {
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            loadUrl("http://127.0.0.1:8080/")
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
}
