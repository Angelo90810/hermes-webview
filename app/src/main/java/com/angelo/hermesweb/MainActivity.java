package com.angelo.hermesweb;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.google.android.material.color.MaterialColors;

public class MainActivity extends Activity {

    private WebView web;
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
        getWindow().setStatusBarColor(MaterialColors.getColor(
                findViewById(android.R.id.content),
                com.google.android.material.R.attr.colorSurface));
        web = findViewById(R.id.web);
        splash = findViewById(R.id.splash);
        splashText = findViewById(R.id.splashText);
        retry = findViewById(R.id.retry);
        refresh = findViewById(R.id.refresh);
        findViewById(R.id.splashSettings).setOnClickListener(v -> openSettings());

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
        web.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void openSettings() {
                runOnUiThread(MainActivity.this::openSettings);
            }
        }, "HermesApp");

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
                    injectGearButton();
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

        Prefs p = Prefs.load(this);
        if (!p.isConfigured()) {
            openSettings();
        } else {
            load(p);
        }
    }

    /** Injeta a engrenagem de configurações no próprio cabeçalho do webapp (canto direito vazio). */
    private void injectGearButton() {
        // innerHTML aqui é seguro: o valor é uma constante do app (SVG fixo da engrenagem),
        // nunca concatena input do usuário ou conteúdo da página — não há superfície de XSS.
        String js = "(function(){if(document.getElementById('hermes-gear'))return;"
                + "var b=document.createElement('div');b.id='hermes-gear';"
                + "b.style.cssText='position:fixed;top:9px;right:12px;width:30px;height:30px;z-index:2147483647;opacity:.62;cursor:pointer;filter:drop-shadow(0 0 3px rgba(0,0,0,.9))';"
                + "b.title='Configurações do app';"
                + "b.innerHTML=\"<svg width='26' height='26' viewBox='0 0 24 24'><path fill='#ffffff' d='M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z'/></svg>\";"
                + "b.addEventListener('click',function(){HermesApp.openSettings();});"
                + "document.body.appendChild(b);})();";
        web.evaluateJavascript(js, null);
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
