package com.diomaster.solanahunter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.SafeBrowsingInitializer;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final String PREFS = "hunter";
    private static final String SERVER = "server_url";
    private static final int BG = Color.rgb(7, 10, 20);
    private static final int PANEL = Color.rgb(18, 24, 42);
    private static final int PURPLE = Color.rgb(155, 92, 255);
    private static final int CYAN = Color.rgb(34, 211, 238);

    private LinearLayout root;
    private WebView web;
    private ProgressBar progress;
    private String serverUrl = "";

    private GradientDrawable shape(int color, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(28f);
        if (stroke != 0) d.setStroke(2, stroke);
        return d;
    }

    private TextView label(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        serverUrl = prefs().getString(SERVER, "");
        if (serverUrl.isEmpty()) showHome();
        else connectTo(serverUrl);
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void showHome() {
        web = null;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(28, 48, 28, 28);
        root.setBackgroundColor(BG);

        TextView mark = label("◈", 62, CYAN);
        root.addView(mark, new LinearLayout.LayoutParams(-1, 86));
        root.addView(label("SOLANA HUNTER", 27, Color.WHITE));
        root.addView(label("AI  •  SOLANA TRADING SYSTEM", 11, PURPLE));

        TextView intro = label("Ο έλεγχος του trading bot σου, από το κινητό.", 16, Color.LTGRAY);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, -2);
        ip.setMargins(0, 36, 0, 22);
        root.addView(intro, ip);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(22, 22, 22, 22);
        card.setBackground(shape(PANEL, Color.rgb(75, 56, 130)));
        card.addView(label("ΣΥΝΔΕΣΗ ΜΕ ΤΟ BOT", 15, Color.WHITE));

        TextView explain = label("Σύνδεσε το ασφαλές HTTPS dashboard του ScreenerBot.", 13, Color.LTGRAY);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, -2);
        ep.setMargins(0, 14, 0, 18);
        card.addView(explain, ep);

        Button connect = new Button(this);
        connect.setText("ΡΥΘΜΙΣΗ ΣΥΝΔΕΣΗΣ");
        connect.setTextColor(Color.WHITE);
        connect.setBackground(shape(Color.rgb(105, 55, 220), CYAN));
        card.addView(connect, new LinearLayout.LayoutParams(-1, 58));
        connect.setOnClickListener(v -> askServer());

        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(0, 10, 0, 0);
        root.addView(card, cp);

        TextView safety = label("Κρυπτογραφημένη σύνδεση HTTPS • Το trading εκτελείται στον server σου", 11, Color.GRAY);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.setMargins(0, 24, 0, 0);
        root.addView(safety, sp);
        setContentView(root);
    }

    private void askServer() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType( android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("https://your-server.example");
        input.setText(serverUrl);

        new AlertDialog.Builder(this)
            .setTitle("Διεύθυνση ScreenerBot")
            .setMessage("Βάλε τη διεύθυνση HTTPS του dashboard. Ο server πρέπει να είναι ενεργός και προστατευμένος με κωδικό.")
            .setView(input)
            .setNegativeButton("Άκυρο", null)
            .setPositiveButton("Σύνδεση", (d, w) -> {
                String value = input.getText().toString().trim();
                Uri parsed = Uri.parse(value);
                if (!"https".equalsIgnoreCase(parsed.getScheme()) || parsed.getHost() == null
                        || parsed.getUserInfo() != null) {
                    new AlertDialog.Builder(this)
                        .setTitle("Μη έγκυρη διεύθυνση")
                        .setMessage("Χρησιμοποίησε πλήρη διεύθυνση HTTPS, χωρίς όνομα χρήστη ή κωδικό μέσα στο URL.")
                        .setPositiveButton("OK", null).show();
                    return;
                }
                serverUrl = value.replaceAll("/+$", "");
                prefs().edit().putString(SERVER, serverUrl).apply();
                connectTo(serverUrl);
            }).show();
    }

    private void connectTo(String url) {
        serverUrl = url;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(16, 8, 16, 8);
        bar.setBackgroundColor(PANEL);

        TextView brand = label("SOLANA HUNTER", 16, Color.WHITE);
        bar.addView(brand, new LinearLayout.LayoutParams(0, 48, 1));

        Button home = new Button(this);
        home.setText("⌂");
        home.setContentDescription("Αρχική");
        home.setOnClickListener(v -> showHome());
        bar.addView(home, new LinearLayout.LayoutParams(52, 48));

        Button settings = new Button(this);
        settings.setText("⚙");
        settings.setContentDescription("Ρυθμίσεις σύνδεσης");
        settings.setOnClickListener(v -> askServer());
        bar.addView(settings, new LinearLayout.LayoutParams(52, 48));
        root.addView(bar);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        root.addView(progress, new LinearLayout.LayoutParams(-1, 4));

        web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setLoadsImagesAutomatically(true);
        ws.setSupportZoom(false);
        ws.setBuiltInZoomControls(false);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        ws.setSafeBrowsingEnabled(true);
        ws.setJavaScriptCanOpenWindowsAutomatically(false);
        ws.setSupportMultipleWindows(false);
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web, false);

        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) {
                progress.setProgress(value);
                progress.setVisibility(value >= 100 ? View.GONE : View.VISIBLE);
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri target = request.getUrl();
                Uri base = Uri.parse(serverUrl);
                // Keep dashboard navigation on the configured HTTPS origin only.
                return !("https".equalsIgnoreCase(target.getScheme())
                    && base.getHost() != null
                    && base.getHost().equalsIgnoreCase(target.getHost())
                    && base.getPort() == target.getPort());
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showConnectionError();
            }
        });

        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        web.loadUrl(url + "/");
    }

    private void showConnectionError() {
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
            .setTitle("Δεν έγινε σύνδεση")
            .setMessage("Έλεγξε ότι ο ScreenerBot server λειτουργεί, ότι η διεύθυνση είναι σωστή και ότι το HTTPS πιστοποιητικό είναι έγκυρο.")
            .setCancelable(false)
            .setNegativeButton("Ρυθμίσεις", (d, w) -> askServer())
            .setPositiveButton("Δοκιμή ξανά", (d, w) -> {
                if (web != null) web.reload();
            }).show();
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else if (web != null) showHome();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (web != null) {
            web.stopLoading();
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
