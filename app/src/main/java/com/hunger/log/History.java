package com.hunger.log;

import android.content.Context;
import org.json.JSONArray;

final class History {
    static final String[] LABELS = {"6. 몹시 배불러서 불쾌", "5. 만족스러운 배부름", "4. 약간 배부름", "3. 약간 허기짐", "2. 매우 허기짐", "1. 허기에 허덕임"};

    static JSONArray read(Context context) {
        try {
            return new JSONArray(context.getSharedPreferences("history", 0).getString("points", "[]"));
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
    static String scoreText(JSONArray points) {
        int score = todayScore(points);
        if (score < 0) return "오늘의 허기점수\n😶 기록 중";
        String face = score >= 80 ? "😊" : score >= 50 ? "😐" : "😕";
        return "오늘의 허기점수\n" + face + " " + score + "점";
    }
    static double[] averages(JSONArray points) {
        double[] result = new double[points.length()];
        double area = 0;
        for (int i = 0; i < points.length(); i++) {
            double value = points.optJSONArray(i).optDouble(1);
            if (i == 0) { result[i] = value; continue; }
            JSONArray previous = points.optJSONArray(i - 1);
            double old = previous.optDouble(1);
            long duration = points.optJSONArray(i).optLong(0) - previous.optLong(0);
            area += duration * (value > old ? (value + old) / 2 : old);
            long elapsed = points.optJSONArray(i).optLong(0) - points.optJSONArray(0).optLong(0);
            result[i] = elapsed > 0 ? area / elapsed : value;
        }
        return result;
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
