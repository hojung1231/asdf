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
}
