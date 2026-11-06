package com.hunger.log;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import org.json.JSONArray;

final class WidgetPlot {
    static long[] range(JSONArray[] points) {
        long[] range = {Long.MAX_VALUE, Long.MIN_VALUE};
        long today = History.todayStart();
        for (JSONArray series : points) for (int i = 0; i < series.length(); i++) {
            long time = series.optJSONArray(i).optLong(0);
            if (time < today) continue;
            range[0] = Math.min(range[0], time);
            range[1] = Math.max(range[1], time);
        }
        return range;
    }
    static float x(long time, long[] range, float width) {
        return 22 + (width - 38) * (time - range[0]) / Math.max(1, range[1] - range[0]);
    }
    private static float y(double index, float height) {
        return 10 + (float) index * Math.max(1, height - 34) / 5;
    }
    static Bitmap draw(JSONArray[] points, int width, int height, int selectedMetric, float progress) {
        width = Math.max(100, width); height = Math.max(50, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(Math.min(10, Math.max(6, (height - 34) / 5f)));
        for (int i = 0; i < 6; i++) {
            float ordinate = y(i, height);
            paint.setColor(0xff283039);
            canvas.drawLine(22, ordinate, width - 16, ordinate, paint);
            paint.setColor(0xff87949f);
            canvas.drawText(String.valueOf(6 - i), 0, ordinate + 3, paint);
        }
        long[] range = range(points);
        if (range[0] > range[1]) {
            paint.setTextSize(10); paint.setColor(0xffa6b0bb);
            canvas.drawText("아래 버튼으로 오늘의 기록을 남겨보세요", 25, height / 2f, paint);
            return bitmap;
        }
        long today = History.todayStart();
        for (Metric metric : Metric.values()) {
            int column = metric.ordinal();
            JSONArray series = points[column];
            boolean previous = false;
            float lastX = 0, lastY = 0;
            paint.setColor(Dashboard.COLORS[column]); paint.setStrokeWidth(1.8f);
            for (int i = 0; i < series.length(); i++) {
                JSONArray point = series.optJSONArray(i);
                long time = point.optLong(0);
                if (time < today) continue;
                if (column == selectedMetric && i == series.length() - 1 && progress < 1) break;
                float px = x(time, range, width), py = y(point.optDouble(1), height);
                if (previous) {
                    if (metric == Metric.HUNGER && py < lastY) {
                        canvas.drawLine(lastX, lastY, px, lastY, paint);
                        canvas.drawLine(px, lastY, px, py, paint);
                    } else canvas.drawLine(lastX, lastY, px, py, paint);
                }
                canvas.drawCircle(px, py, 2.6f, paint);
                previous = true; lastX = px; lastY = py;
            }
        }
        paint.setColor(0xff87949f); paint.setTextSize(10);
        java.text.SimpleDateFormat time = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.KOREA);
        canvas.drawText(time.format(new java.util.Date(range[0])), 22, height - 4, paint);
        if (range[1] > range[0]) {
            String end = time.format(new java.util.Date(range[1]));
            canvas.drawText(end, width - 16 - paint.measureText(end), height - 4, paint);
        }
        return bitmap;
    }
    static Bitmap effect(JSONArray[] points, int width, int height, int column, int selected, float progress, boolean compact) {
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, width), Math.max(1, height), Bitmap.Config.ARGB_8888);
        if (column < 0 || column >= 3 || selected < 0 || progress >= 1.43f) return bitmap;
        long[] range = range(points);
        JSONArray last = points[column].optJSONArray(points[column].length() - 1);
        if (last == null || last.optLong(0) < History.todayStart() || range[0] > range[1]) return bitmap;
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int padding = Dashboard.padding(compact);
        float destinationX = padding + x(last.optLong(0), range, Math.max(100, width - 2 * padding));
        float destinationY = Dashboard.plotTop(compact) + y(last.optDouble(1), Math.max(50, height - Dashboard.fixedHeight(compact)));
        int color = Dashboard.COLORS[column];
        if (progress < 1) {
            float originX = padding + (width - padding * 2) * (column + 0.5f) / 3;
            float originY = height - Dashboard.originBottom(compact) + selected * Dashboard.rowHeight(compact);
            for (int tail = 9; tail >= 0; tail--) {
                float t = Math.max(0, progress - tail * 0.027f), u = 1 - t;
                float px = u * u * originX + 2 * u * t * (originX - 48) + t * t * destinationX;
                float py = u * u * originY + 2 * u * t * (destinationY + 18) + t * t * destinationY;
                paint.setColor(color); paint.setAlpha(210 - tail * 20);
                canvas.drawCircle(px, py, 6 - tail * 0.45f, paint);
                if (tail == 0) { paint.setColor(0xfff4ffff); canvas.drawCircle(px, py, 3, paint); }
            }
        } else {
            float t = Math.min(1, (progress - 1) / 0.43f);
            paint.setColor(color); paint.setAlpha((int) (255 * (1 - t)));
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f);
            canvas.drawCircle(destinationX, destinationY, 5 + 22 * t, paint);
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                canvas.drawCircle(destinationX + (float) Math.cos(angle) * (8 + 18 * t),
                        destinationY + (float) Math.sin(angle) * (8 + 18 * t), 1.5f, paint);
            }
        }
        return bitmap;
    }
}
