package com.hunger.log;

import android.content.Context;
import org.json.JSONObject;

final class AppUpdates {
    static final String BASE = "https://github.com/hojung1231/asdf/releases/";
    static final java.util.concurrent.ExecutorService WORK = java.util.concurrent.Executors.newSingleThreadExecutor();
    private static boolean checking;

    static final class Release {
        final int code;
        final String name, url, sha;
        Release(String json) throws Exception {
            JSONObject data = new JSONObject(json);
            code = data.getInt("versionCode");
            name = data.getString("versionName");
            url = data.getString("url");
            sha = data.getString("sha256");
            if (code <= 0 || !name.matches("[0-9]+(\\.[0-9]+){1,3}")
                    || !sha.matches("[a-f0-9]{64}")
                    || !url.equals(BASE + "download/huggy-v" + name + "/HUGGY-" + name + ".apk"))
                throw new java.io.IOException("업데이트 정보가 올바르지 않습니다.");
        }
    }

    static android.content.SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("app_updates", Context.MODE_PRIVATE);
    }
    static Release available(Context c) {
        try {
            Release release = new Release(prefs(c).getString("release", ""));
            return release.code > BuildConfig.VERSION_CODE ? release : null;
        } catch (Exception ignored) { return null; }
    }
    static java.net.HttpURLConnection connect(String url) throws Exception {
        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "HUGGY/" + BuildConfig.VERSION_NAME);
        if (connection.getResponseCode() != 200) { connection.disconnect(); throw new java.io.IOException("인터넷 연결 또는 배포 상태를 확인해 주세요."); }
        if (!"https".equals(connection.getURL().getProtocol())) { connection.disconnect(); throw new java.io.IOException("안전한 연결이 필요합니다."); }
        return connection;
    }
    static Release fetch(Context c) throws Exception {
        java.net.HttpURLConnection connection = connect(BASE + "latest/download/huggy-update.json");
        String json;
        try (java.io.InputStream in = connection.getInputStream(); java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
                if (out.size() > 16384) throw new java.io.IOException("업데이트 정보가 너무 큽니다.");
            }
            json = out.toString("UTF-8");
        } finally { connection.disconnect(); }
        Release release = new Release(json);
        prefs(c).edit().putString("release", json).apply();
        return release;
    }
    static void check(Context context) {
        Context c = context.getApplicationContext();
        WORK.execute(() -> checkIfDue(c));
    }
    static void checkIfDue(Context c) {
        synchronized (AppUpdates.class) {
            long now = System.currentTimeMillis();
            long attempt = prefs(c).getLong("attempt", 0);
            if (checking || (now >= attempt && now - attempt < 3600000)) return;
            checking = true;
            prefs(c).edit().putLong("attempt", now).apply();
        }
            try { fetch(c); new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> HomeWidget.refresh(c, "HUGGY · 업데이트 확인", -1)); }
            catch (Exception ignored) { /* Offline use remains available; retry on the next check. */ }
            finally { synchronized (AppUpdates.class) { checking = false; } }
    }
    static boolean matches(java.io.File file, String expected) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        try (java.io.InputStream in = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[32768]; int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return hex.toString().equals(expected);
    }
    static void bind(Context c, android.view.View root) {
        android.widget.TextView button = root.findViewById(R.id.update);
        Release release = available(c);
        button.setVisibility(release == null ? android.view.View.GONE : android.view.View.VISIBLE);
        button.setText("↑ 업데이트");
        button.setOnClickListener(v -> c.startActivity(new android.content.Intent(c, UpdateActivity.class)));
    }
}
