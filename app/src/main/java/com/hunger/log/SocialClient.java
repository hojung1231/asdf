package com.hunger.log;

import android.content.Context;
import org.json.JSONObject;
import org.json.JSONArray;
import java.net.HttpURLConnection;
import java.net.URL;

final class SocialClient {
    static final java.util.concurrent.ExecutorService EXECUTOR = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final Context context;
    SocialClient(Context context) { this.context = context.getApplicationContext(); }

    static boolean configured() {
        return BuildConfig.SUPABASE_URL.startsWith("https://") && !BuildConfig.SUPABASE_KEY.isEmpty();
    }

    private JSONObject session() throws Exception { return SocialStore.read(context, "session"); }

    boolean signedIn() throws Exception { return session().has("refresh_token"); }

    private JSONObject request(String path, JSONObject body, String token) throws Exception {
        if (!configured()) throw new java.io.IOException("서버 연결을 준비 중입니다.");
        HttpURLConnection connection = (HttpURLConnection) new URL(BuildConfig.SUPABASE_URL + path).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("apikey", BuildConfig.SUPABASE_KEY);
        connection.setRequestProperty("Content-Type", "application/json");
        if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setDoOutput(true);
        try {
            try (java.io.OutputStream output = connection.getOutputStream()) {
                output.write(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            int code = connection.getResponseCode();
            java.io.InputStream stream = code < 400 ? connection.getInputStream() : connection.getErrorStream();
            StringBuilder raw = new StringBuilder();
            if (stream != null) try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) raw.append(line);
            }
            JSONObject result = raw.length() == 0 || raw.toString().equals("null") ? new JSONObject()
                    : new JSONObject(raw.toString());
            if (code >= 400) {
                if (code == 401) throw new java.io.IOException("로그인이 만료되었거나 인증에 실패했습니다. 다시 로그인해 주세요.");
                throw new java.io.IOException(result.optString("msg", result.optString("message",
                        result.optString("error_description", "요청을 처리하지 못했습니다. (" + code + ")"))));
            }
            return result;
        } finally { connection.disconnect(); }
    }

    private void saveSession(JSONObject response) throws Exception {
        if (!response.has("refresh_token")) return;
        JSONObject saved = new JSONObject().put("refresh_token", response.getString("refresh_token"))
                .put("access_token", response.getString("access_token"))
                .put("expires_at", System.currentTimeMillis() + response.optLong("expires_in", 3600) * 1000)
                .put("user_id", response.getJSONObject("user").getString("id"));
        SocialStore.write(context, "session", saved);
    }

    private String token() throws Exception {
        JSONObject saved = session();
        if (!saved.has("refresh_token")) throw new java.io.IOException("로그인해 주세요.");
        if (saved.optLong("expires_at") < System.currentTimeMillis() + 60000) {
            JSONObject fresh = request("/auth/v1/token?grant_type=refresh_token",
                    new JSONObject().put("refresh_token", saved.getString("refresh_token")), null);
            saveSession(fresh);
            saved = session();
        }
        return saved.getString("access_token");
    }

    void createDeviceAccount(String nickname) throws Exception {
        if (!signedIn()) {
            JSONObject result = request("/auth/v1/signup", new JSONObject(), null);
            if (!result.has("refresh_token")) throw new java.io.IOException("기기 계정 설정을 확인해 주세요.");
            saveSession(result);
        }
        updateProfile(nickname, false);
        snapshot();
    }
    JSONObject rpc(String name, JSONObject arguments) throws Exception {
        return request("/rest/v1/rpc/" + name, arguments, token());
    }

    JSONObject profile() throws Exception { return rpc("huggy_profile", new JSONObject()); }

    JSONObject updateProfile(String nickname, boolean sharing) throws Exception {
        JSONObject profile = rpc("huggy_update_profile",
                new JSONObject().put("p_name", nickname).put("p_sharing", sharing));
        SocialStore.write(context, "profile", profile);
        if (!sharing) SocialStore.write(context, "pending", new JSONObject());
        return profile;
    }

    void queueLatest(boolean force) throws Exception {
        JSONObject profile = SocialStore.read(context, "profile");
        if (!signedIn() || !profile.optBoolean("sharing")) return;
        long change = context.getSharedPreferences("history", 0).getLong("social_change", 0);
        if (!force && change <= SocialStore.read(context, "sync_meta").optLong("last_change")) return;
        JSONArray points = History.read(context);
        JSONArray last = points.optJSONArray(points.length() - 1);
        if (last == null) return;
        JSONObject old = SocialStore.read(context, "pending");
        if (old.has("p_event") && old.optLong("local_change") == change) return;
        JSONObject pending = new JSONObject().put("local_change", change).put("user_id", session().getString("user_id"))
                .put("p_level", 6 - last.optInt(1)).put("p_recorded_at", last.optLong(0))
                .put("p_changed_at", Math.max(System.currentTimeMillis(), old.optLong("p_changed_at") + 1))
                .put("p_event", java.util.UUID.randomUUID().toString());
        SocialStore.write(context, "pending", pending);
        SocialSync.schedule(context);
    }

    void flush() throws Exception {
        JSONObject pending = SocialStore.read(context, "pending");
        if (!pending.has("p_event") || !signedIn()) return;
        if (!pending.optString("user_id").equals(session().optString("user_id"))) {
            SocialStore.write(context, "pending", new JSONObject());
            return;
        }
        JSONObject body = new JSONObject(pending.toString());
        body.remove("user_id");
        body.remove("local_change");
        rpc("huggy_record", body);
        SocialStore.write(context, "sync_meta", new JSONObject().put("last_change", pending.optLong("local_change")));
        SocialStore.write(context, "pending", new JSONObject());
    }

    JSONObject snapshot() throws Exception {
        JSONObject result = rpc("huggy_snapshot", new JSONObject());
        SocialStore.write(context, "snapshot", result);
        return result;
    }

    void logout() throws Exception {
        JSONObject saved = session();
        if (saved.has("access_token")) {
            try { request("/auth/v1/logout?scope=local", new JSONObject(), token()); }
            finally { SocialStore.clear(context); SocialSync.cancel(context); }
        } else SocialStore.clear(context);
        SocialSync.cancel(context);
    }

    static String feedMessage(JSONObject event) {
        int level = event.optInt("level");
        if (level < 1 || level > 6) return "상태 기록 없음";
        String time = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.KOREA)
                .format(new java.util.Date(event.optLong("recorded_at")));
        return event.optString("nickname") + " · " + time + (char) 10 + History.LABELS[6 - level].substring(3);
    }

    static JSONObject cached(Context context) {
        try { return SocialStore.read(context, "snapshot"); }
        catch (Exception error) { return new JSONObject(); }
    }

    static void recordChanged(Context context) {
        Context app = context.getApplicationContext();
        if (configured()) SocialSync.schedule(app);
        EXECUTOR.execute(() -> {
            try { new SocialClient(app).queueLatest(false); }
            catch (Exception error) {
                app.getSharedPreferences("sync_status", 0).edit().putString("error", "상태 공유 저장을 확인해 주세요.").apply();
            }
        });
    }
}
