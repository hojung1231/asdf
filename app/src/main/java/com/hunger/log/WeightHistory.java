package com.hunger.log;

import android.content.Context;
import org.json.JSONObject;

final class WeightHistory {
    static String dateKey(long timestamp) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT)
                .format(new java.util.Date(timestamp));
    }

    static JSONObject read(Context context) {
        try {
            return new JSONObject(context.getSharedPreferences("weights", 0).getString("days", "{}"));
        } catch (org.json.JSONException error) {
            throw new IllegalStateException(error);
        }
    }

    static String normalize(String input) {
        String value = input.trim().replace(',', '.');
        if (!value.matches("[0-9]{1,3}([.][0-9]{1,2})?")) return null;
        java.math.BigDecimal number = new java.math.BigDecimal(value);
        return number.signum() > 0 ? number.stripTrailingZeros().toPlainString() : null;
    }

    static synchronized boolean save(Context context, long date, String value) {
        JSONObject days = read(context);
        try {
            days.put(dateKey(date), value);
        } catch (org.json.JSONException error) {
            throw new IllegalStateException(error);
        }
        return context.getSharedPreferences("weights", 0).edit().putString("days", days.toString()).commit();
    }

    static String todayLabel(Context context) {
        String value = read(context).optString(dateKey(System.currentTimeMillis()), "");
        return value.isEmpty() ? "＋ 몸무게 입력" : "몸무게 " + value + " kg · 수정";
    }
}
