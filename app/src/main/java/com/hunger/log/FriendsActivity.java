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
    private TextView feed;
    private long lastTick = Long.MIN_VALUE;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            bars.invalidate();
            StringBuilder messages = new StringBuilder();
            java.text.SimpleDateFormat time = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.KOREA);
            long tick = DemoFriends.tick(now);
            for (int i = 0; i < 3; i++) {
                if (i > 0) messages.append((char) 10).append((char) 10);
                messages.append(time.format(new java.util.Date((tick - i) * DemoFriends.INTERVAL)))
                        .append("  ").append(DemoFriends.message(tick - i));
            }
            feed.setText(messages);
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
        layout.addView(bars, new LinearLayout.LayoutParams(-1, 0, 1));
        layout.addView(text("친구들의 소식", 16, 0xff69e8c0));
        feed = text("", 13, 0xffc7d0d9);
        feed.setPadding(0, padding / 2, 0, padding / 2);
        feed.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        layout.addView(feed);
        layout.addView(text("허기점수 · 몸무게 변화 보기", 13, 0xffa6b0bb));
        LinearLayout historyButtons = new LinearLayout(this);
        for (int i = 0; i < DemoFriends.NAMES.length; i++) {
            final int friend = i;
            Button button = new Button(this);
            button.setText(DemoFriends.NAMES[i] + " 이력");
            button.setTextSize(12);
            button.setOnClickListener(view -> startActivity(new android.content.Intent(
                    this, DailyHistoryActivity.class).putExtra("friend", friend)));
            historyButtons.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
        }
        layout.addView(historyButtons);
        Button record = new Button(this);
        record.setText("내 상태 기록하기");
        record.setOnClickListener(view -> {
            startActivity(new android.content.Intent(this, MainActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
        });
        layout.addView(record);
        setContentView(layout);
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
        super.onPause();
    }

    private class Bars extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Bars() { super(FriendsActivity.this); }

        @Override protected void onDraw(Canvas canvas) {
            float dp = getResources().getDisplayMetrics().density;
            float top = 38 * dp, bottom = getHeight() - 82 * dp;
            if (bottom <= top) return;
            float lane = (getWidth() - 22 * dp) / 4;
            long now = System.currentTimeMillis();
            JSONArray points = History.read(FriendsActivity.this);
            JSONArray own = points.optJSONArray(points.length() - 1);
            StringBuilder description = new StringBuilder("허기 상태. ");
            for (int i = 0; i < 4; i++) {
                int index = i == 0 ? (own == null ? -1 : own.optInt(1)) : DemoFriends.state(i - 1, now);
                String name = i == 0 ? "나" : DemoFriends.NAMES[i - 1];
                float center = 22 * dp + lane * (i + 0.5f);
                float half = Math.min(19 * dp, lane * 0.28f);
                paint.setColor(0xff1e252c);
                canvas.drawRoundRect(center - half, top, center + half, bottom, 9 * dp, 9 * dp, paint);
                if (index >= 0) {
                    float fillTop = bottom - (bottom - top) * (6 - index) / 6;
                    paint.setColor(i == 0 ? 0xff69e8c0 : 0xffb59aff);
                    canvas.drawRoundRect(center - half, fillTop, center + half, bottom, 9 * dp, 9 * dp, paint);
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
                float ordinate = bottom - (bottom - top) * level / 6;
                canvas.drawText(String.valueOf(level), 0, ordinate + 3 * dp, paint);
            }
            setContentDescription(description);
        }
    }
}
