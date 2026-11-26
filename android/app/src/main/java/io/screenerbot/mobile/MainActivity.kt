package io.screenerbot.mobile

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Android shell for the Render-hosted ScreenerBot instance.
 *
 * The trading engine and dashboard run on Render. This Activity is a secure
 * HTTPS WebView client; it deliberately does not start a second local engine.
 */
class MainActivity : Activity() {
    companion object {
        private const val DASHBOARD_URL = "https://solana-hunter-screenerbot.onrender.com/"
    }

    private lateinit var status: TextView
    private lateinit var retry: Button
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showConnectionScreen()
        connectToRender()
    }

    private fun showConnectionScreen() {
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
        status = TextView(this).apply {
            text = "Σύνδεση με το ScreenerBot Cloud στο Render…"
            textSize = 14f
            setTextColor(0xFFB8C8D8.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 18)
        }
        retry = Button(this).apply {
            text = "ΕΠΑΝΑΣΥΝΔΕΣΗ"
            setOnClickListener { connectToRender() }
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(subtitle, LinearLayout.LayoutParams(-1, -2))
        root.addView(status, LinearLayout.LayoutParams(-1, -2))
        root.addView(retry, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }

    private fun connectToRender() {
        runOnUiThread {
            status.text = "Σύνδεση με Render…\nΑναμονή απόκρισης cloud server."
            retry.isEnabled = false
        }

        val remote = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    status.text = "Φόρτωση ScreenerBot Cloud…"
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (view === webView || webView == null) {
                        retry.isEnabled = true
                        setContentView(view, ViewGroup.LayoutParams(-1, -1))
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        runOnUiThread {
                            status.text = "Δεν ήταν δυνατή η σύνδεση με Render.\n${error?.description ?: "Έλεγξε τη σύνδεσή σου στο internet."}"
                            retry.isEnabled = true
                            setContentView(connectionScreen, ViewGroup.LayoutParams(-1, -1))
                        }
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: android.webkit.WebResourceResponse?
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 200) >= 500) {
                        runOnUiThread {
                            status.text = "Ο Render server επέστρεψε HTTP ${errorResponse?.statusCode}. Δοκίμασε ξανά."
                            retry.isEnabled = true
                        }
                    }
                }
            }
        }
        webView = remote
        CookieManager.getInstance().setAcceptCookie(true)
        remote.loadUrl(DASHBOARD_URL, mapOf("Accept-Language" to "el-GR,el;q=0.9,en;q=0.8"))
    }

    private val connectionScreen: android.view.View
        get() = (status.parent as? android.view.View) ?: status

    override fun onDestroy() {
        webView?.stopLoading()
        webView?.destroy()
        webView = null
        super.onDestroy()
    }
}
