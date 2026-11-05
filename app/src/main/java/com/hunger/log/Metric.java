package com.hunger.log;

import org.json.JSONArray;

enum Metric {
    HUNGER("허기", "history", History.LABELS),
    FOCUS("집중력", "history_focus", new String[]{"6. 초집중", "5. 높은 집중", "4. 잘 집중됨", "3. 보통", "2. 산만함", "1. 집중 불가"}),
    STRESS("스트레스", "history_stress", new String[]{"6. 극심한 스트레스", "5. 매우 스트레스 받음", "4. 꽤 스트레스 받음", "3. 약간 스트레스", "2. 대체로 편안함", "1. 매우 편안함"});

    final String title, preferences;
    final String[] labels;
    Metric(String title, String preferences, String[] labels) {
        this.title = title; this.preferences = preferences; this.labels = labels;
    }
    static Metric from(int id) { return id >= 0 && id < values().length ? values()[id] : HUNGER; }
    static Metric selected(android.content.Context context) {
        return from(context.getSharedPreferences("record_ui", 0).getInt("metric", 0));
    }
    void select(android.content.Context context) {
        context.getSharedPreferences("record_ui", 0).edit().putInt("metric", ordinal()).apply();
    }
    int background(int value) {
        return this == FOCUS || value >= 3 ? R.drawable.button_hungry : R.drawable.button_full;
    }
    String rule() {
        if (this == FOCUS) return "오늘 08~17시 기록만 · 6=100점 / 1=0점";
        if (this == STRESS) return "전체 누적 기록 · 1=100점 / 6=0점";
        return "누적 평균 2 이하 100점 · 4 이상 0점";
    }
    JSONArray scorePoints(JSONArray points, long dayStart) {
        if (this != FOCUS) return points;
        java.util.Calendar day = java.util.Calendar.getInstance();
        day.setTimeInMillis(dayStart);
        day.set(java.util.Calendar.MINUTE, 0);
        day.set(java.util.Calendar.SECOND, 0);
        day.set(java.util.Calendar.MILLISECOND, 0);
        day.set(java.util.Calendar.HOUR_OF_DAY, 8);
        long from = day.getTimeInMillis();
        day.set(java.util.Calendar.HOUR_OF_DAY, 17);
        return History.inPeriod(points, from, day.getTimeInMillis() + 1);
    }
    int score(JSONArray points, long dayStart) {
        JSONArray eligible = scorePoints(points, dayStart);
        if (eligible.length() == 0) return -1;
        double[] mean = History.averages(eligible, this);
        double level = 6 - mean[mean.length - 1];
        if (this == HUNGER) return History.scoreFromAverage(level);
        return (int) Math.round(Math.max(0, Math.min(100,
                (this == FOCUS ? level - 1 : 6 - level) * 20)));
    }
    String scoreText(JSONArray points) {
        int score = score(points, History.todayStart());
        String title = this == FOCUS ? "집중점수" : this == STRESS ? "스트레스점수" : "허기점수";
        return title + "\n" + (score < 0 ? "😶 기록 없음" : History.face(score) + " " + score + "점");
    }
    String legend() {
        return "<font color='#69e8c0'>━ " + title + " 기록</font>　<font color='#b59aff'>━ 누적 평균</font>"
                + (this == HUNGER ? "　▪ 목표 구간" : "");
    }
}
