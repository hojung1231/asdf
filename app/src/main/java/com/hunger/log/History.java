package com.hunger.log;

import android.content.Context;
import org.json.JSONArray;

final class History {
    static final String[] LABELS = {"6. 몹시 배불러서 불쾌", "5. 만족스러운 배부름", "4. 약간 배부름", "3. 약간 허기짐", "2. 매우 허기짐", "1. 허기에 허덕임"};

    static JSONArray read(Context context) {
        return read(context, Metric.HUNGER);
    }
    static JSONArray read(Context context, Metric metric) {
        try {
            return new JSONArray(context.getSharedPreferences(metric.preferences, 0).getString("points", "[]"));
        } catch (org.json.JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    static long todayStart() {
        java.util.Calendar today = java.util.Calendar.getInstance();
        today.set(java.util.Calendar.HOUR_OF_DAY, 0);
        today.set(java.util.Calendar.MINUTE, 0);
        today.set(java.util.Calendar.SECOND, 0);
        today.set(java.util.Calendar.MILLISECOND, 0);
        return today.getTimeInMillis();
    }

    static int scoreFromAverage(double average) {
        return (int) Math.round(Math.max(0, Math.min(100, (4 - average) * 50)));
    }

    static int todayScore(JSONArray points) {
        if (points.length() == 0) return -1;
        double[] averages = averages(points);
        // Stored indexes run from 0 to 5, displayed levels run from 6 to 1.
        double displayedAverage = 6 - averages[averages.length - 1];
        return scoreFromAverage(displayedAverage);
    }
    static JSONArray inPeriod(JSONArray points, long from, long to) {
        JSONArray result = new JSONArray();
        for (int i = 0; i < points.length(); i++) {
            JSONArray point = points.optJSONArray(i);
            long time = point.optLong(0);
            if (time >= from && time < to) result.put(point);
        }
        return result;
    }

    static String face(int score) {
        return score >= 80 ? "😊" : score >= 50 ? "😐" : "😕";
    }

    static String scoreText(JSONArray points) {
        int score = todayScore(points);
        if (score < 0) return "오늘의 허기점수\n😶 기록 중";
        String face = face(score);
        return "오늘의 허기점수\n" + face + " " + score + "점";
    }
    static double[] averages(JSONArray points) {
        return averages(points, Metric.HUNGER);
    }
    static double[] averages(JSONArray points, Metric metric) {
        double[] result = new double[points.length()];
        double area = 0;
        for (int i = 0; i < points.length(); i++) {
            double value = points.optJSONArray(i).optDouble(1);
            if (i == 0) { result[i] = value; continue; }
            JSONArray previous = points.optJSONArray(i - 1);
            double old = previous.optDouble(1);
            long duration = points.optJSONArray(i).optLong(0) - previous.optLong(0);
            area += duration * (metric != Metric.HUNGER || value > old ? (value + old) / 2 : old);
            long elapsed = points.optJSONArray(i).optLong(0) - points.optJSONArray(0).optLong(0);
            result[i] = elapsed > 0 ? area / elapsed : value;
        }
        return result;
    }

    static boolean replaceValue(JSONArray points, int index, long time, int value) {
        if (value < 0 || value >= LABELS.length || index < 0 || index >= points.length()) return false;
        JSONArray point = points.optJSONArray(index);
        if (point == null || point.optLong(0) != time) return false;
        try { point.put(1, value); }
        catch (org.json.JSONException error) { throw new IllegalStateException(error); }
        return true;
    }

    static synchronized boolean edit(Context context, int index, long time, int value) {
        return edit(context, Metric.HUNGER, index, time, value);
    }
    static synchronized boolean edit(Context context, Metric metric, int index, long time, int value) {
        JSONArray points = read(context, metric);
        if (!replaceValue(points, index, time, value)) return false;
        boolean latest = metric == Metric.HUNGER && index == points.length() - 1;
        android.content.SharedPreferences preferences = context.getSharedPreferences(metric.preferences, 0);
        android.content.SharedPreferences.Editor editor = preferences.edit().putString("points", points.toString());
        if (latest) editor.putLong("social_change",
                Math.max(System.currentTimeMillis(), preferences.getLong("social_change", 0) + 1));
        boolean saved = editor.commit();
        if (saved && latest) SocialClient.recordChanged(context);
        return saved;
    }
    static synchronized String record(Context context, int value) {
        return record(context, Metric.HUNGER, value);
    }
    static synchronized String record(Context context, Metric metric, int value) {
        if (value < 0 || value >= 6) return "저장할 단계를 확인해 주세요";
        JSONArray points = read(context, metric);
        long now = System.currentTimeMillis();
        JSONArray last = points.optJSONArray(points.length() - 1);
        boolean revised = last != null && now - last.optLong(0) < 60_000;
        if (revised) {
            points.remove(points.length() - 1);
            now = last.optLong(0);
        }
        points.put(new JSONArray().put(now).put(value));
        android.content.SharedPreferences preferences = context.getSharedPreferences(metric.preferences, 0);
        android.content.SharedPreferences.Editor editor = preferences.edit().putString("points", points.toString());
        if (metric == Metric.HUNGER) editor.putLong("social_change", Math.max(System.currentTimeMillis(), preferences.getLong("social_change", 0) + 1));
        boolean saved = editor.commit();
        if (saved && metric == Metric.HUNGER) SocialClient.recordChanged(context);
        return saved ? (revised ? "✓ 최근 기록 수정" : "✦ 그래프에 기록 추가") : "저장 실패 · 다시 눌러 주세요";
    }
}
