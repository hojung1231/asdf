package com.hunger.log;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Canvas;
import android.graphics.Paint;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;

public class FriendsActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Bars bars;
    private LinearLayout feed;
    private android.widget.ScrollView feedScroll;
    private long lastTick = Long.MIN_VALUE;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            bars.setStates(now);
            long tick = DemoFriends.tick(now);
            boolean changed = lastTick != Long.MIN_VALUE && lastTick != tick;
            feed.removeAllViews();
            for (int i = 7; i >= 0; i--) {
                TextView bubble = text(DemoFriends.feedMessage(tick - i), 12, 0xffe2e7ec);
                int padding = (int) (9 * getResources().getDisplayMetrics().density);
                bubble.setPadding(padding, padding, padding, padding);
                bubble.setBackgroundResource(R.drawable.friend_message);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                params.bottomMargin = padding / 2;
                feed.addView(bubble, params);
                if (changed && i == 0) {
                    bubble.setTranslationY(padding * 2);
                    bubble.setAlpha(0);
                    bubble.animate().translationY(0).alpha(1).setDuration(250).start();
                }
            }
            feedScroll.post(() -> feedScroll.fullScroll(View.FOCUS_DOWN));
            if (lastTick != tick) {
                lastTick = tick;
                HomeWidget.refresh(FriendsActivity.this, "HUGGY · 친구 소식 갱신", -1);
            }
            handler.postDelayed(this, DemoFriends.INTERVAL - (now % DemoFriends.INTERVAL) + 50);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff101317);
        getWindow().setNavigationBarColor(0xff101317);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(0xff101317);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        TextView title = text("함께 보는 허기", 24, 0xfff0f3f5);
        layout.addView(title);
        layout.addView(text("데모 · 가상 친구 3명 · 약 1분마다 상태 변경", 12, 0xffa6b0bb));
        bars = new Bars();
        layout.addView(bars, new LinearLayout.LayoutParams(-1,
                (int) (280 * getResources().getDisplayMetrics().density)));
        layout.addView(text("친구들의 소식", 16, 0xff69e8c0));
        feedScroll = new android.widget.ScrollView(this);
        feed = new LinearLayout(this);
        feed.setOrientation(LinearLayout.VERTICAL);
        feedScroll.addView(feed);
        layout.addView(feedScroll, new LinearLayout.LayoutParams(-1,
                (int) (150 * getResources().getDisplayMetrics().density)));
        Button histories = new Button(this);
        histories.setText("친구별 점수·몸무게 이력 ›");
        histories.setOnClickListener(view -> new android.app.AlertDialog.Builder(this)
                .setTitle("친구 기록 보기")
                .setItems(DemoFriends.NAMES, (dialog, friend) -> startActivity(
                        new android.content.Intent(this, DailyHistoryActivity.class)
                                .putExtra("friend", friend))).show());
        layout.addView(histories);
        Button record = new Button(this);
        record.setText("내 상태 기록하기");
        record.setOnClickListener(view -> {
            startActivity(new android.content.Intent(this, MainActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
        });
        layout.addView(record);
        android.widget.ScrollView page = new android.widget.ScrollView(this);
        page.addView(layout);
        setContentView(page);
    }

    private TextView text(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refresh);
        refresh.run();
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refresh);
        bars.stop();
        for (int i = 0; i < bars.positions.length; i++) bars.positions[i] = Math.max(1, bars.targets[i]);
        super.onPause();
    }

    private class Bars extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] positions = {1, 1, 1, 1};
        private int[] targets = {-1, -1, -1, -1};
        private android.animation.ValueAnimator movement;

        Bars() { super(FriendsActivity.this); }

        void stop() {
            if (movement != null) movement.cancel();
        }

        void setStates(long now) {
            JSONArray points = History.read(FriendsActivity.this);
            JSONArray own = points.optJSONArray(points.length() - 1);
            int[] next = new int[4];
            next[0] = own == null ? -1 : 6 - own.optInt(1);
            for (int i = 1; i < next.length; i++) next[i] = 6 - DemoFriends.state(i - 1, now);
            if (java.util.Arrays.equals(targets, next)) return;
            stop();
            targets = next;
            float[] from = positions.clone();
            movement = android.animation.ValueAnimator.ofFloat(0, 1);
            movement.setDuration(700);
            movement.setInterpolator(new android.view.animation.DecelerateInterpolator());
            movement.addUpdateListener(animation -> {
                float fraction = (float) animation.getAnimatedValue();
                for (int i = 0; i < positions.length; i++) {
                    if (targets[i] >= 1) positions[i] = from[i] + (targets[i] - from[i]) * fraction;
                }
                invalidate();
            });
            movement.start();
        }

        @Override protected void onDraw(Canvas canvas) {
            float dp = getResources().getDisplayMetrics().density;
            float top = 38 * dp, bottom = getHeight() - 82 * dp;
            if (bottom <= top) return;
            float lane = (getWidth() - 22 * dp) / 4;
            StringBuilder description = new StringBuilder("허기 상태. ");
            for (int i = 0; i < 4; i++) {
                int index = targets[i] < 1 ? -1 : 6 - targets[i];
                String name = i == 0 ? "나" : DemoFriends.NAMES[i - 1];
                float center = 22 * dp + lane * (i + 0.5f);
                paint.setColor(0xff29323b);
                canvas.drawRoundRect(center - 3 * dp, top, center + 3 * dp, bottom, 3 * dp, 3 * dp, paint);
                for (int level = 1; level <= 6; level++) {
                    float markY = bottom - (bottom - top) * (level - 1) / 5;
                    paint.setColor(0xff52616b);
                    canvas.drawCircle(center, markY, 2 * dp, paint);
                }
                if (index >= 0) {
                    float orbY = bottom - (bottom - top) * (positions[i] - 1) / 5;
                    int color = i == 0 ? 0xff69e8c0 : 0xffb59aff;
                    paint.setColor(color);
                    paint.setAlpha(35);
                    canvas.drawCircle(center, orbY, 18 * dp, paint);
                    paint.setAlpha(100);
                    canvas.drawCircle(center, orbY, 12 * dp, paint);
                    paint.setColor(color);
                    canvas.drawCircle(center, orbY, 8 * dp, paint);
                    paint.setColor(0xfff0fff9);
                    canvas.drawCircle(center - 2 * dp, orbY - 2 * dp, 2.5f * dp, paint);
                }
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setColor(0xfff0f3f5);
                paint.setTextSize(18 * dp);
                canvas.drawText(index < 0 ? "—" : String.valueOf(6 - index), center, top - 12 * dp, paint);
                paint.setTextSize(13 * dp);
                canvas.drawText(name, center, bottom + 23 * dp, paint);
                paint.setTextSize(10 * dp);
                paint.setColor(0xffa6b0bb);
                String label = index < 0 ? "기록 대기" : History.LABELS[index].substring(3);
                // Wrap labels within each column without changing glyph proportions.
                int split = label.length() / 2;
                if (paint.measureText(label) > lane - 3 * dp) {
                    canvas.drawText(label.substring(0, split), center, bottom + 42 * dp, paint);
                    canvas.drawText(label.substring(split), center, bottom + 56 * dp, paint);
                } else canvas.drawText(label, center, bottom + 42 * dp, paint);
                description.append(name).append(" ").append(label).append(". ");
            }
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setColor(0xff87949f);
            paint.setTextSize(10 * dp);
            for (int level = 1; level <= 6; level++) {
                float ordinate = bottom - (bottom - top) * (level - 1) / 5;
                canvas.drawText(String.valueOf(level), 0, ordinate + 3 * dp, paint);
            }
            setContentDescription(description);
        }
    }
}
