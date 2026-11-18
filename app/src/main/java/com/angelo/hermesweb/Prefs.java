package com.angelo.hermesweb;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/** Config persisted in the app: server host/port and optional login credentials. */
public class Prefs {

    private static final String FILE = "hermes_web_prefs";

    public final String host;
    public final int port;
    public final String user;
    public final String password;

    private Prefs(String host, int port, String user, String password) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
    }

    public String url() {
        if (host == null || host.trim().isEmpty()) return null;
        return "http://" + host.trim() + ":" + port;
    }

    public boolean isConfigured() {
        return url() != null;
    }

    public static Prefs load(Context ctx) {
        SharedPreferences sp = open(ctx);
        String host = sp.getString("host", "");
        int port = sp.getInt("port", 3000);
        String user = sp.getString("user", "");
        String password = sp.getString("password", "");
        return new Prefs(host, port, user, password);
    }

    public static void save(Context ctx, String host, int port, String user, String password) {
        open(ctx).edit()
                .putString("host", host.trim())
                .putInt("port", port)
                .putString("user", user)
                .putString("password", password)
                .apply();
    }

    /** Session-cookie persistence (WebView drops session cookies on restart). */
    public static void saveCookies(Context ctx, String origin, String cookie) {
        open(ctx).edit().putString("ck_" + origin, cookie).apply();
    }

    public static String getCookies(Context ctx, String origin) {
        return open(ctx).getString("ck_" + origin, null);
    }

    public static java.util.Set<String> cookieOrigins(Context ctx) {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (String k : open(ctx).getAll().keySet()) {
            if (k.startsWith("ck_")) out.add(k.substring(3));
        }
        return out;
    }

    public static void clearCookies(Context ctx) {
        SharedPreferences.Editor e = open(ctx).edit();
        for (String k : open(ctx).getAll().keySet()) {
            if (k.startsWith("ck_")) e.remove(k);
        }
        e.apply();
    }

    private static SharedPreferences open(Context ctx) {
        try {
            MasterKey mk = new MasterKey.Builder(ctx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    ctx, FILE, mk,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            // Fallback (should not happen in practice): plain prefs so the app still works.
            return ctx.getSharedPreferences(FILE + "_plain", Context.MODE_PRIVATE);
        }
    }
}
