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
        status.setText("허기 기록 · 목표는 약간 배고픔");
        status.setTextSize(18);
        layout.addView(status);
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
        control(navigation, "현재", () -> { offset = 0; updateRange(); });
        control(navigation, "다음 ›", () -> { if (offset > 0) { offset--; updateRange(); } });
        rangeLabel = new TextView(this);
        rangeLabel.setGravity(android.view.Gravity.CENTER);
        layout.addView(rangeLabel);
        TextView legend = new TextView(this);
        legend.setText(android.text.Html.fromHtml("<font color='#087968'>● 허기 기록</font>　"
                + "<font color='#8b4cb6'>● 누적 평균</font>　<font color='#397852'>■ 안전 구간</font>"));
        layout.addView(legend);
        chart = new Chart();
        layout.addView(chart, new LinearLayout.LayoutParams(-1, 0, 1));
        for (int i = 0; i < History.LABELS.length; i++) {
            final int value = i;
            Button button = new Button(this);
            button.setText(History.LABELS[i]);
            button.setOnClickListener(v -> {
                int before = History.read(this).length();
                String result = History.record(this, value);
                status.setText(result);
                HomeWidget.refresh(this, result, value);
                offset = 0;
                updateRange();
                if (chart.points.length() > before) chart.pulse();
            });
            layout.addView(button);
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
        setContentView(layout);
    }

    @Override protected void onResume() {
        super.onResume();
        updateRange();
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
            float left = 88 * dp, right = getWidth() - 16 * dp;
            float top = 20 * dp, step = Math.max(0, getHeight() - 48 * dp) / 4;
            if (right <= left || step <= 0) return;
            paint.setTextSize(12 * dp);
            paint.setStrokeWidth(dp);
            paint.setColor(0x3039ad68);
            canvas.drawRect(left, top + 2 * step, right, top + 4 * step + 6 * dp, paint);
            for (int i = 0; i < 5; i++) {
                float y = top + i * step;
                paint.setColor(0xff223b35);
                canvas.drawText(History.LABELS[i], 0, y + 4 * dp, paint);
                paint.setColor(i == 3 ? 0xff73ad87 : 0xffdce4e0);
                canvas.drawLine(left, y, right, y, paint);
            }
            if (points == null || points.length() == 0) return;
            long first = start;
            long last = end;
            paint.setColor(0xff223b35);
            java.text.DateFormat time = new java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.KOREA);
            float bottom = top + 4 * step + 21 * dp;
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
            double sum = 0;
            paint.setStrokeWidth(2 * dp);
            boolean previousVisible = false;
            for (int i = 0; i < points.length(); i++) {
                JSONArray point = points.optJSONArray(i);
                int value = point.optInt(1);
                sum += value;
                if (point.optLong(0) < first || point.optLong(0) >= last) continue;
                float x = left + (right - left) * (point.optLong(0) - first) / Math.max(1, last - first);
                float y = top + step * (float) (average ? sum / (i + 1) : value);
                paint.setColor(color);
                if (previousVisible) canvas.drawLine(previousX, previousY, x, y, paint);
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
