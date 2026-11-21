package com.diomaster.solanahunter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebChromeClient;
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
    private TextView status;

    private GradientDrawable shape(int color, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(28f);
        if (stroke != 0) d.setStroke(2, stroke);
        return d;
    }

    private TextView label(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.CENTER); t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        showHome();
    }

    private void showHome() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(28, 48, 28, 28);
        root.setBackgroundColor(BG);

        TextView mark = label("◈", 62, CYAN);
        root.addView(mark, new LinearLayout.LayoutParams(-1, 86));
        TextView title = label("SOLANA HUNTER", 27, Color.WHITE);
        root.addView(title);
        TextView sub = label("AI  •  SOLANA TRADING SYSTEM", 11, PURPLE);
        root.addView(sub);

        TextView intro = label("Ο έλεγχος του trading bot σου, από το κινητό.", 16, Color.LTGRAY);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, -2);
        ip.setMargins(0, 36, 0, 22); root.addView(intro, ip);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(22, 22, 22, 22);
        card.setBackground(shape(PANEL, Color.rgb(75, 56, 130)));
        TextView heading = label("ΣΥΝΔΕΣΗ ΜΕ ΤΟ BOT", 15, Color.WHITE);
        card.addView(heading);
        TextView explain = label("Βάλε τη διεύθυνση HTTPS του δικού σου ScreenerBot server.", 13, Color.LTGRAY);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, -2);
        ep.setMargins(0, 14, 0, 18); card.addView(explain, ep);

        Button connect = new Button(this);
        connect.setText("ΣΥΝΔΕΣΗ");
        connect.setTextColor(Color.WHITE);
        connect.setBackground(shape(Color.rgb(105, 55, 220), CYAN));
        card.addView(connect, new LinearLayout.LayoutParams(-1, 58));
        connect.setOnClickListener(v -> askServer());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(0, 10, 0, 0); root.addView(card, cp);

        status = label("Ασφαλής σύνδεση • Το bot παραμένει στον server σου", 11, Color.GRAY);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.setMargins(0, 24, 0, 0); root.addView(status, sp);

        setContentView(root);
        String saved = prefs().getString(SERVER, "");
        if (!saved.isEmpty()) connectTo(saved);
    }

    private SharedPreferences prefs() { return getSharedPreferences(PREFS, MODE_PRIVATE); }

    private void askServer() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("https://your-server.example");
        input.setText(prefs().getString(SERVER, ""));
        new AlertDialog.Builder(this)
            .setTitle("Διεύθυνση ScreenerBot")
            .setMessage("Χρειάζεται ενεργός server με το dashboard του ScreenerBot.")
            .setView(input)
            .setNegativeButton("Άκυρο", null)
            .setPositiveButton("Σύνδεση", (d, w) -> {
                String url = input.getText().toString().trim();
                if (!url.startsWith("https://")) {
                    new AlertDialog.Builder(this).setMessage("Για ασφάλεια, χρησιμοποίησε διεύθυνση HTTPS.").setPositiveButton("OK", null).show();
                    return;
                }
                prefs().edit().putString(SERVER, url).apply();
                connectTo(url);
            }).show();
    }

    private void connectTo(String url) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(16, 8, 16, 8);
        bar.setBackgroundColor(PANEL);
        TextView brand = label("SOLANA HUNTER", 16, Color.WHITE);
        bar.addView(brand, new LinearLayout.LayoutParams(0, 48, 1));
        Button settings = new Button(this);
        settings.setText("⚙");
        settings.setOnClickListener(v -> askServer());
        bar.addView(settings, new LinearLayout.LayoutParams(54, 48));
        root.addView(bar);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        root.addView(progress, new LinearLayout.LayoutParams(-1, 4));
        web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setLoadsImagesAutomatically(true);
        ws.setSupportZoom(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String loaded) {
                progress.setVisibility(View.GONE);
            }
            @Override public void onPageStarted(WebView view, String loaded, android.graphics.Bitmap icon) {
                progress.setVisibility(View.VISIBLE);
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        web.loadUrl(url);
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
