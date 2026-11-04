package com.hunger.log;

import android.content.Context;
import org.json.JSONArray;

final class History {
    static final String[] LABELS = {"매우 배부름", "배부름", "중간", "약간 배고픔", "많이 배고픔"};

    static JSONArray read(Context context) {
        try {
            return new JSONArray(context.getSharedPreferences("history", 0).getString("points", "[]"));
        } catch (org.json.JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    static synchronized String record(Context context, int value) {
        JSONArray points = read(context);
        long now = System.currentTimeMillis();
        JSONArray last = points.optJSONArray(points.length() - 1);
        boolean revised = last != null && now - last.optLong(0) < 60_000;
        if (revised) {
            points.remove(points.length() - 1);
            now = last.optLong(0);
        }
        points.put(new JSONArray().put(now).put(value));
        boolean saved = context.getSharedPreferences("history", 0).edit()
                .putString("points", points.toString()).commit();
        return saved ? (revised ? "✓ 최근 기록 수정" : "✦ 그래프에 기록 추가") : "저장 실패 · 다시 눌러 주세요";
    }
}
