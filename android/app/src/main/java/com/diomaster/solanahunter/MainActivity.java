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
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

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
        // Run the bundled Rust engine locally in Android private storage.
        serverUrl = "http://127.0.0.1:8080";
        try {
            if (!NativeCore.start(getFilesDir().getAbsolutePath() + "/screenerbot")) {
                showNativeCoreError();
                return;
            }
            new android.os.Handler(getMainLooper()).postDelayed(() -> connectTo(serverUrl), 2500);
        } catch (UnsatisfiedLinkError error) {
            showNativeCoreError();
        }
    }

    private void showNativeCoreError() {
        new AlertDialog.Builder(this)
            .setTitle("Τοπικός πυρήνας δεν ξεκίνησε")
            .setMessage("Δεν φορτώθηκε ο ενσωματωμένος Rust πυρήνας. Δεν θα γίνει σύνδεση στο Render ως υποκατάστατο.")
            .setPositiveButton("OK", null).show();
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
                askPasswordAndConnect(serverUrl);
            }).show();
    }

    private void askPasswordAndConnect(String url) {
        // Reuse the server-issued session cookie saved by WebView. Ask for the
        // password again only when the server says that session has expired.
        String savedCookie = CookieManager.getInstance().getCookie(url);
        if (savedCookie != null && !savedCookie.isEmpty()) {
            verifySavedSession(url, savedCookie);
            return;
        }
        showPasswordDialog(url);
    }

    private void verifySavedSession(String url, String savedCookie) {
        new Thread(() -> {
            boolean valid = false;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url + "/api/auth/status").openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Cookie", savedCookie);
                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                StringBuilder body = new StringBuilder();
                if (stream != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream,
                            java.nio.charset.StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) body.append(line);
                    }
                }
                JSONObject response = new JSONObject(body.toString());
                JSONObject data = response.optJSONObject("data");
                valid = status >= 200 && status < 300
                        && (response.optBoolean("authenticated", false)
                        || (data != null && data.optBoolean("authenticated", false)));
            } catch (Exception ignored) {
                valid = false;
            } finally {
                if (connection != null) connection.disconnect();
            }
            final boolean sessionValid = valid;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (sessionValid) connectTo(url);
                else showPasswordDialog(url);
            });
        }).start();
    }

    private void showPasswordDialog(String url) {
        EditText password = new EditText(this);
        password.setSingleLine(true);
        password.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setHint("Κωδικός πρόσβασης");
        password.setPadding(24, 18, 24, 18);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("SOLANA HUNTER — Είσοδος")
            .setMessage("Βάλε τον κωδικό του dashboard για ασφαλή σύνδεση.")
            .setView(password)
            .setCancelable(false)
            .setNegativeButton("Ρυθμίσεις", (d, w) -> askServer())
            .setPositiveButton("ΣΥΝΔΕΣΗ", null)
            .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String entered = password.getText().toString();
            if (entered.isEmpty()) {
                password.setError("Βάλε τον κωδικό");
                return;
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            authenticate(url, entered, dialog);
        }));
        dialog.show();
    }

    private void authenticate(String url, String password, AlertDialog dialog) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            String error = "Δεν ήταν δυνατή η σύνδεση.";
            boolean authenticated = false;
            String cookie = null;
            try {
                URL endpoint = new URL(url + "/api/auth/login");
                connection = (HttpURLConnection) endpoint.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setRequestProperty("Accept", "application/json");
                JSONObject payload = new JSONObject();
                payload.put("password", password);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                int status = connection.getResponseCode();
                cookie = connection.getHeaderField("Set-Cookie");
                InputStream stream = status >= 200 && status < 400
                        ? connection.getInputStream() : connection.getErrorStream();
                StringBuilder body = new StringBuilder();
                if (stream != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream,
                            java.nio.charset.StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) body.append(line);
                    }
                }
                JSONObject response = new JSONObject(body.toString());
                authenticated = status >= 200 && status < 300
                        && response.optBoolean("success", false) && cookie != null;
                if (!authenticated) {
                    error = response.optString("message",
                            response.optString("error", "Ο κωδικός δεν έγινε δεκτός."));
                }
            } catch (Exception e) {
                error = "Δεν έγινε σύνδεση με τον server. Έλεγξε ότι το dashboard είναι ενεργό.";
            } finally {
                if (connection != null) connection.disconnect();
            }

            final boolean ok = authenticated;
            final String message = error;
            final String sessionCookie = cookie;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (ok) {
                    CookieManager manager = CookieManager.getInstance();
                    manager.setAcceptCookie(true);
                    manager.setCookie(url, sessionCookie);
                    manager.flush();
                    dialog.dismiss();
                    connectTo(url);
                } else {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    new AlertDialog.Builder(this)
                        .setTitle("Αποτυχία εισόδου")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show();
                }
            });
        }).start();
    }

    private void connectTo(String url) {
        serverUrl = url;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        // Compact native connection strip. The dashboard owns the SOLANA HUNTER
        // branding and navigation, so do not duplicate a second app header above it.
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(14, 2, 8, 2);
        bar.setBackgroundColor(BG);

        TextView session = label("●  SECURE SESSION", 10, Color.rgb(52, 211, 153));
        session.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        session.setLetterSpacing(0.08f);
        bar.addView(session, new LinearLayout.LayoutParams(0, 34, 1));

        Button settings = new Button(this);
        settings.setText("⚙");
        settings.setTextColor(Color.WHITE);
        settings.setContentDescription("Ρυθμίσεις σύνδεσης");
        settings.setMinWidth(44);
        settings.setMinimumHeight(36);
        settings.setOnClickListener(v -> askServer());
        bar.addView(settings, new LinearLayout.LayoutParams(48, 38));
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
