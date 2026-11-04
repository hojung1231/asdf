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
        for (int i = 0; i < History.LABELS.length; i++) {
            final int value = i;
            Button button = new Button(this);
            button.setText(History.LABELS[i]);
            button.setOnClickListener(v -> {
                int before = History.read(this).length();
                String result = History.record(this, value);
                status.setText(result);
                HomeWidget.refresh(this, result, value);
                chart.reload();
                if (chart.points.length() > before) chart.pulse();
            });
            layout.addView(button);
        }
        TextView legend = new TextView(this);
        legend.setText("● 허기 기록     ● 누적 평균     ┄ 목표\n가로축은 기록 시간");
        layout.addView(legend);
        chart = new Chart();
        layout.addView(chart, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(layout);
    }

    @Override protected void onResume() {
        super.onResume();
        chart.reload();
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
            for (int i = 0; i < 5; i++) {
                float y = top + i * step;
                paint.setColor(0xff223b35);
                canvas.drawText(History.LABELS[i], 0, y + 4 * dp, paint);
                paint.setColor(i == 3 ? 0xff73ad87 : 0xffdce4e0);
                canvas.drawLine(left, y, right, y, paint);
            }
            if (points == null || points.length() == 0) return;
            long first = points.optJSONArray(0).optLong(0);
            long last = points.optJSONArray(points.length() - 1).optLong(0);
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
            for (int i = 0; i < points.length(); i++) {
                JSONArray point = points.optJSONArray(i);
                int value = point.optInt(1);
                sum += value;
                float x = left + (right - left) * (point.optLong(0) - first) / Math.max(1, last - first);
                float y = top + step * (float) (average ? sum / (i + 1) : value);
                paint.setColor(color);
                if (i > 0) canvas.drawLine(previousX, previousY, x, y, paint);
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
                previousX = x;
                previousY = y;
            }
        }
    }
}
