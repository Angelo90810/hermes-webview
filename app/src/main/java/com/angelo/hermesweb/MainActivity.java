package com.angelo.hermesweb;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.MaterialColors;

public class MainActivity extends Activity {

    private WebView web;
    private MaterialToolbar toolbar;
    private View splash;
    private android.widget.TextView splashText;
    private com.google.android.material.button.MaterialButton retry;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout refresh;
    private boolean splashVisible = true;
    private boolean errored = false;
    private android.webkit.ValueCallback<android.net.Uri[]> fileCallback;
    private static final int REQ_SETTINGS = 1;
    private static final int REQ_FILE = 2;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        toolbar = findViewById(R.id.toolbar);
        getWindow().setStatusBarColor(MaterialColors.getColor(
                findViewById(android.R.id.content),
                com.google.android.material.R.attr.colorSurface));
        web = findViewById(R.id.web);
        splash = findViewById(R.id.splash);
        splashText = findViewById(R.id.splashText);
        retry = findViewById(R.id.retry);
        refresh = findViewById(R.id.refresh);

        int surface = MaterialColors.getColor(
                findViewById(android.R.id.content),
                com.google.android.material.R.attr.colorSurface);
        web.setBackgroundColor(surface);

        refresh.setOnRefreshListener(() -> {
            refresh.setRefreshing(true);
            web.reload();
        });
        // pull-to-refresh só vale no topo da página
        web.getViewTreeObserver().addOnScrollChangedListener(() ->
                refresh.setEnabled(web.getScrollY() == 0));

        retry.setOnClickListener(v -> {
            Prefs p = Prefs.load(this);
            if (p.isConfigured()) load(p);
        });

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setSavePassword(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView v, String url, Bitmap favicon) {
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                refresh.setRefreshing(false);
                persistCookies(url);
                injectCredentials(url);
                Prefs p = Prefs.load(MainActivity.this);
                if (p.isConfigured() && url != null && url.startsWith(p.url())) {
                    hideSplash();
                }
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest request, android.webkit.WebResourceError error) {
                if (!request.isForMainFrame()) return;
                errored = true;
                refresh.setRefreshing(false);
                splash.setVisibility(View.VISIBLE);
                splashVisible = true;
                splashText.setText("Não consegui falar com o PC.\nConfira se o PC está ligado e se você está na rede certa (Wi-Fi ou Tailscale).");
                retry.setVisibility(View.VISIBLE);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false; // keep navigation inside the app
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, android.webkit.ValueCallback<android.net.Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    Intent intent = params.createIntent();
                    startActivityForResult(intent, REQ_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        toolbar.getMenu().add(Menu.NONE, 1, Menu.NONE, "Configurações")
                .setIcon(R.drawable.ic_settings)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                openSettings();
                return true;
            }
            return false;
        });

        Prefs p = Prefs.load(this);
        if (!p.isConfigured()) {
            openSettings();
        } else {
            load(p);
        }
    }

    private void load(Prefs p) {
        errored = false;
        retry.setVisibility(View.GONE);
        splashText.setText("Conectando ao Hermes…");
        splash.setVisibility(View.VISIBLE);
        splashVisible = true;
        restoreCookies();
        web.loadUrl(p.url());
    }

    private void hideSplash() {
        errored = false;
        splashVisible = false;
        splash.setVisibility(View.GONE);
    }

    /** Save cookies for the visited origin so the Nous session survives app restarts. */
    private void persistCookies(String url) {
        if (url == null) return;
        android.net.Uri u = android.net.Uri.parse(url);
        String origin = u.getScheme() + "://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "");
        String cookie = android.webkit.CookieManager.getInstance().getCookie(origin);
        if (cookie != null && !cookie.isEmpty()) {
            Prefs.saveCookies(this, origin, cookie);
        }
        android.webkit.CookieManager.getInstance().flush();
    }

    /** Re-inject stored cookies before loading, so session cookies survive restarts. */
    private void restoreCookies() {
        android.webkit.CookieManager cm = android.webkit.CookieManager.getInstance();
        for (String origin : Prefs.cookieOrigins(this)) {
            String cookie = Prefs.getCookies(this, origin);
            if (cookie != null && !cookie.isEmpty()) {
                cm.setCookie(origin + "/", cookie);
            }
        }
        cm.flush();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null && web.getUrl() != null) {
            persistCookies(web.getUrl());
        }
    }

    /** Auto-fill the saved login when the webapp shows an empty login form. */
    private void injectCredentials(String url) {
        Prefs p = Prefs.load(this);
        if (p.user.isEmpty() && p.password.isEmpty()) return;
        if (url == null || !url.startsWith(p.url())) return;
        String js =
                "(function(){var f=document.querySelector('form');if(!f)return;" +
                "var u=f.querySelector('input[type=text],input[type=email],input[name*=user],input[name*=login]');" +
                "var pw=f.querySelector('input[type=password]');" +
                "var changed=false;" +
                "if(u&&!u.value){u.value=" + jstr(p.user) + ";changed=true;}" +
                "if(pw&&!pw.value){pw.value=" + jstr(p.password) + ";changed=true;}" +
                "if(changed){u&&u.dispatchEvent(new Event('input',{bubbles:true}));pw&&pw.dispatchEvent(new Event('input',{bubbles:true}));}})();";
        web.evaluateJavascript(js, null);
    }

    private static String jstr(String v) {
        return org.json.JSONObject.quote(v == null ? "" : v);
    }

    private void openSettings() {
        startActivityForResult(new Intent(this, SettingsActivity.class), REQ_SETTINGS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE) {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                fileCallback = null;
            }
            return;
        }
        if (requestCode == REQ_SETTINGS && resultCode == RESULT_OK) {
            Prefs p = Prefs.load(this);
            if (p.isConfigured()) {
                web.clearCache(true);
                android.webkit.CookieManager.getInstance().removeAllCookies(null);
                Prefs.clearCookies(this);
                load(p);
            } else {
                Toast.makeText(this, "Configure IP e porta para carregar o Hermes", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
