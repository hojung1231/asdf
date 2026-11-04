package com.hunger.log;

// One shared, clock-driven simulation for the app and every widget instance.
final class DemoFriends {
    static final String[] NAMES = {"김길동", "이하나", "박민준"};
    static final long INTERVAL = 60_000L;
    private static final String[] SENTENCES = {
        "몹시 배불러서 불쾌합니다", "만족스럽게 배부릅니다", "약간 배부릅니다",
        "약간 허기집니다", "매우 허기집니다", "허기에 허덕입니다"
    };

    static long tick(long now) { return now / INTERVAL; }

    static int friend(long tick) { return (int) ((tick % NAMES.length + NAMES.length) % NAMES.length); }

    static long latestTick(int friend, long now) {
        long tick = tick(now);
        return tick - (((tick - friend) % NAMES.length + NAMES.length) % NAMES.length);
    }

    static int value(long tick) {
        long mixed = tick * 6364136223846793005L + 1442695040888963407L;
        mixed ^= mixed >>> 33;
        return (int) ((mixed % History.LABELS.length + History.LABELS.length) % History.LABELS.length);
    }

    static int state(int friend, long now) { return value(latestTick(friend, now)); }

    static String message(long tick) {
        return NAMES[friend(tick)] + "님이 " + SENTENCES[value(tick)];
    }
    static String feedMessage(long tick) {
        String time = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.KOREA)
                .format(new java.util.Date(tick * INTERVAL));
        return NAMES[friend(tick)] + " · " + time + (char) 10 + SENTENCES[value(tick)];
    }

    static org.json.JSONArray recordsForDay(int friend, long day) {
        java.util.Calendar date = java.util.Calendar.getInstance();
        date.setTimeInMillis(day);
        date.set(java.util.Calendar.HOUR_OF_DAY, 20);
        long last = Math.min(System.currentTimeMillis(), date.getTimeInMillis());
        date.set(java.util.Calendar.HOUR_OF_DAY, 8);
        long first = Math.min(last, date.getTimeInMillis());
        org.json.JSONArray points = new org.json.JSONArray();
        long[] times = {first, first + (last - first) / 2, last};
        long previous = Long.MIN_VALUE;
        for (long time : times) {
            if (time != previous) points.put(new org.json.JSONArray().put(time).put(state(friend, time)));
            previous = time;
        }
        return points;
    }

    static String weightForDay(int friend, long day) {
        java.util.Calendar date = java.util.Calendar.getInstance();
        date.setTimeInMillis(day);
        int sequence = (date.get(java.util.Calendar.YEAR) - 2026) * 365
                + date.get(java.util.Calendar.DAY_OF_YEAR);
        double weight = 72 + friend * 5 - sequence * 0.008
                + 0.3 * Math.sin(sequence * 0.18 + friend);
        return String.format(java.util.Locale.ROOT, "%.1f", weight);
    }
}