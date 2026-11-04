package com.hunger.log;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;

public class MainActivity extends Activity {
    private TextView status;
    private Chart chart;
    private TextView score;
    private Button weight;
    private int period;
    private int offset;
    private TextView rangeLabel;
    private long start;
    private long end;

    private void updateRange() {
        java.util.Calendar date = java.util.Calendar.getInstance();
        date.set(java.util.Calendar.HOUR_OF_DAY, 0);
        date.set(java.util.Calendar.MINUTE, 0);
        date.set(java.util.Calendar.SECOND, 0);
        date.set(java.util.Calendar.MILLISECOND, 0);
        int field = java.util.Calendar.DAY_OF_YEAR;
        int stride = 1;
        if (period == 1) {
            int day = date.get(java.util.Calendar.DAY_OF_WEEK);
            date.add(field, -((day + 5) % 7));
            stride = 7;
        } else if (period == 2) {
            date.set(java.util.Calendar.DAY_OF_MONTH, 1);
            field = java.util.Calendar.MONTH;
        }
        date.add(field, -offset * stride);
        start = date.getTimeInMillis();
        date.add(field, stride);
        end = date.getTimeInMillis();
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy.MM.dd", java.util.Locale.KOREA);
        rangeLabel.setText(format.format(new java.util.Date(start)) + " ~ " + format.format(new java.util.Date(end - 1)));
        chart.reload();
        score.setText(History.scoreText(History.read(this)));
    }

    private void control(LinearLayout row, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(view -> action.run());
        row.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        status = new TextView(this);
        status.setText("HUGGY");
        status.setTextSize(18);
        layout.addView(status);
        score = new TextView(this);
        score.setTextSize(16);
        layout.addView(score);
        LinearLayout health = new LinearLayout(this);
        weight = new Button(this);
        weight.setTextSize(12);
        weight.setOnClickListener(view -> startActivity(new android.content.Intent(this, WeightActivity.class)));
        health.addView(weight, new LinearLayout.LayoutParams(0, -2, 1));
        control(health, "매일의 기록", () -> startActivity(new android.content.Intent(this, DailyHistoryActivity.class)));
        layout.addView(health);
        Button friends = new Button(this);
        friends.setText("친구들의 허기 상태 · 데모");
        friends.setOnClickListener(view -> startActivity(
                new android.content.Intent(this, FriendsActivity.class)));
        layout.addView(friends);
        LinearLayout periods = new LinearLayout(this);
        layout.addView(periods);
        String[] names = {"하루", "일주일", "한 달"};
        for (int i = 0; i < names.length; i++) {
            int mode = i;
            control(periods, names[i], () -> { period = mode; offset = 0; updateRange(); });
        }
        LinearLayout navigation = new LinearLayout(this);
        layout.addView(navigation);
        control(navigation, "‹ 이전", () -> { offset++; updateRange(); });
        control(navigation, "현재", () -> { period = 0; offset = 0; updateRange(); });
        control(navigation, "다음 ›", () -> { if (offset > 0) { offset--; updateRange(); } });
        rangeLabel = new TextView(this);
        rangeLabel.setGravity(android.view.Gravity.CENTER);
        layout.addView(rangeLabel);
        TextView legend = new TextView(this);
        legend.setText(android.text.Html.fromHtml("<font color='#087968'>● 허기 기록</font>　"
                + "<font color='#8b4cb6'>● 누적 평균</font>　<font color='#397852'>■ 안전 구간</font>"));
        layout.addView(legend);
        chart = new Chart();
        layout.addView(chart, new LinearLayout.LayoutParams(-1, (int) (260 * getResources().getDisplayMetrics().density)));
        LinearLayout choices = new LinearLayout(this);
        layout.addView(choices);
        LinearLayout[] columns = {new LinearLayout(this), new LinearLayout(this)};
        for (LinearLayout column : columns) { column.setOrientation(LinearLayout.VERTICAL); choices.addView(column, new LinearLayout.LayoutParams(0, -2, 1)); }
        for (int i = 0; i < History.LABELS.length; i++) {
            final int value = i;
            Button button = new Button(this);
            button.setText(History.LABELS[i]);
            button.setBackgroundTintList(null);
            button.setBackgroundResource(i < 3 ? R.drawable.button_full : R.drawable.button_hungry);
            button.setOnClickListener(v -> {
                int before = History.read(this).length();
                String result = History.record(this, value);
                status.setText(result);
                HomeWidget.refresh(this, result, value);
                offset = 0;
                updateRange();
                if (chart.points.length() > before) chart.pulse();
            });
            columns[i / 3].addView(button);
        }
        Button reset = new Button(this);
        reset.setText("기록 초기화");
        reset.setOnClickListener(view -> new android.app.AlertDialog.Builder(this)
                .setMessage("저장한 허기 기록을 모두 삭제할까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (dialog, which) -> {
                    if (getSharedPreferences("history", 0).edit().remove("points").commit()) {
                        status.setText("기록 초기화 완료");
                        updateRange();
                        HomeWidget.refresh(this, "기록 초기화 완료", -1);
                    }
                }).show());
        layout.addView(reset);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(layout);
        setContentView(scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        updateRange();
        weight.setText(WeightHistory.todayLabel(this));
        JSONArray last = chart.points.optJSONArray(chart.points.length() - 1);
        if (last != null && System.currentTimeMillis() - last.optLong(0) < 60_000) chart.pulse();
    }

    private class Chart extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private JSONArray points;
        private float pulse;

        Chart() { super(MainActivity.this); }
        void reload() { points = History.read(MainActivity.this); invalidate(); }
        void pulse() {
            android.animation.ValueAnimator animation = android.animation.ValueAnimator.ofFloat(1, 0);
            animation.setDuration(900);
            animation.addUpdateListener(a -> {
                pulse = (float) a.getAnimatedValue();
                invalidate();
            });
            animation.start();
        }

        @Override protected void onDraw(Canvas canvas) {
            float dp = getResources().getDisplayMetrics().density;
            float left = 28 * dp, right = getWidth() - 16 * dp;
            float top = 20 * dp, step = Math.max(0, getHeight() - 48 * dp) / 5;
            if (right <= left || step <= 0) return;
            paint.setTextSize(12 * dp);
            paint.setStrokeWidth(dp);
            paint.setColor(0x3039ad68);
            canvas.drawRect(left, top + 2 * step, right, top + 4 * step + 6 * dp, paint);
            for (int i = 0; i < 6; i++) {
                float y = top + i * step;
                paint.setColor(0xff223b35);
                canvas.drawText(String.valueOf(6 - i), 0, y + 4 * dp, paint);
                paint.setColor(i == 3 ? 0xff73ad87 : 0xffdce4e0);
                canvas.drawLine(left, y, right, y, paint);
            }
            if (points == null || points.length() == 0) return;
            long first = start;
            long last = end;
            if (period == 0) {
                boolean found = false;
                for (int i = 0; i < points.length(); i++) {
                    long timestamp = points.optJSONArray(i).optLong(0);
                    if (timestamp >= start && timestamp < end) {
                        if (!found) first = timestamp;
                        last = offset == 0 ? System.currentTimeMillis() : timestamp;
                        found = true;
                    }
                }
            }
            paint.setColor(0xff223b35);
            java.text.DateFormat time = new java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.KOREA);
            float bottom = top + 5 * step + 21 * dp;
            canvas.drawText(time.format(new java.util.Date(first)), left, bottom, paint);
            String end = time.format(new java.util.Date(last));
            if (last != first && right - left > 2 * paint.measureText(end) + 8 * dp) {
                canvas.drawText(end, right - paint.measureText(end), bottom, paint);
            }
            drawSeries(canvas, left, right, top, step, dp, first, last, false);
            drawSeries(canvas, left, right, top, step, dp, first, last, true);
        }

        private void drawSeries(Canvas canvas, float left, float right, float top, float step,
                                float dp, long first, long last, boolean average) {
            int color = average ? 0xff8b4cb6 : 0xff087968;
            float previousX = 0, previousY = 0;
            double[] averages = History.averages(points);
            paint.setStrokeWidth(2 * dp);
            boolean previousVisible = false;
            for (int i = 0; i < points.length(); i++) {
                JSONArray point = points.optJSONArray(i);
                int value = point.optInt(1);

                if (point.optLong(0) < first || point.optLong(0) > last) continue;
                float x = left + (right - left) * (point.optLong(0) - first) / Math.max(1, last - first);
                float y = top + step * (float) (average ? averages[i] : value);
                paint.setColor(color);
                if (previousVisible) {
                    if (!average && y < previousY) {
                        canvas.drawLine(previousX, previousY, x, previousY, paint);
                        canvas.drawLine(x, previousY, x, y, paint);
                    } else canvas.drawLine(previousX, previousY, x, y, paint);
                }
                if (!average && i == points.length() - 1 && pulse > 0) {
                    paint.setAlpha((int) (120 * pulse));
                    canvas.drawCircle(x, y, (5 + 13 * (1 - pulse)) * dp, paint);
                    paint.setAlpha(255);
                }
                if (average) {
                    paint.setStyle(Paint.Style.STROKE);
                    canvas.drawCircle(x, y, 4 * dp, paint);
                    paint.setStyle(Paint.Style.FILL);
                } else canvas.drawCircle(x, y, 3 * dp, paint);
                previousVisible = true;
                previousX = x;
                previousY = y;
            }
        }
    }
}
