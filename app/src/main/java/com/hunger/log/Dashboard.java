package com.hunger.log;

import android.content.Context;
import org.json.JSONArray;

final class Dashboard {
    static final int[][] BUTTONS = {
        {R.id.b0, R.id.b1, R.id.b2, R.id.b3, R.id.b4, R.id.b5},
        {R.id.f0, R.id.f1, R.id.f2, R.id.f3, R.id.f4, R.id.f5},
        {R.id.s0, R.id.s1, R.id.s2, R.id.s3, R.id.s4, R.id.s5}
    };
    static final int[] HEADINGS = {R.id.metric0, R.id.metric1, R.id.metric2};
    static final int[] SCORES = {R.id.score, R.id.focusScore, R.id.stressScore};
    static final int[] COLORS = {0xff69e8c0, 0xff6cafff, 0xffff86b6};
    static final String LEGEND = "<font color='#69e8c0'>━ 허기</font>　<font color='#6cafff'>━ 집중력</font>　<font color='#ff86b6'>━ 스트레스</font>";
    static JSONArray[] read(Context context) {
        JSONArray[] points = new JSONArray[3];
        for (Metric metric : Metric.values()) points[metric.ordinal()] = History.read(context, metric);
        return points;
    }
    static String heading(Metric metric, JSONArray points) {
        JSONArray last = points.optJSONArray(points.length() - 1);
        return metric.title + " · " + (last == null || last.optLong(0) < History.todayStart() ? "–" : 6 - last.optInt(1));
    }
    static String score(Metric metric, JSONArray points) {
        int score = metric.score(points, History.todayStart());
        String label = metric == Metric.FOCUS ? "집중 · 08~17" : metric == Metric.STRESS ? "스트레스 · 누적" : "허기";
        return label + "\n" + (score < 0 ? "😶 기록 없음" : History.face(score) + " " + score + "점");
    }
    static int padding(boolean compact) { return compact ? 10 : 14; }
    static int plotTop(boolean compact) { return compact ? 96 : 116; }
    static int fixedHeight(boolean compact) { return compact ? 352 : 424; }
    static int rowHeight(boolean compact) { return compact ? 32 : 38; }
    static int originBottom(boolean compact) { return compact ? 202 : 243; }
}
