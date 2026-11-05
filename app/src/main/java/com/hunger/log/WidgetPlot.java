package com.hunger.log;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import org.json.JSONArray;

final class WidgetPlot {
    private static final int MINT = 0xff69e8c0;
    private static final int PURPLE = 0xffb59aff;

    private static int firstToday(JSONArray points) {
        int first = 0;
        while (first < points.length() && points.optJSONArray(first).optLong(0) < History.todayStart()) first++;
        return first;
    }

    private static float x(JSONArray points, int first, int index, float width) {
        long start = points.optJSONArray(first).optLong(0);
        long end = points.optJSONArray(points.length() - 1).optLong(0);
        return 22 + (width - 38) * (points.optJSONArray(index).optLong(0) - start) / Math.max(1, end - start);
    }

    private static float y(double index, float height) {
        return 12 + (float) index * Math.max(1, height - 44) / 5;
    }

    static Bitmap draw(JSONArray points, int width, int height, float progress) {
        return draw(points, width, height, progress, Metric.HUNGER);
    }
    static Bitmap draw(JSONArray points, int width, int height, float progress, Metric metric) {
        width = Math.max(100, width);
        height = Math.max(50, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(10);
        paint.setColor(0x1469e8c0);
        if (metric == Metric.HUNGER) canvas.drawRoundRect(22, y(2, height), width - 16, y(4, height), 5, 5, paint);
        for (int i = 0; i < 6; i++) {
            float ordinate = y(i, height);
            paint.setColor(0xff283039);
            canvas.drawLine(22, ordinate, width - 16, ordinate, paint);
            paint.setColor(0xff87949f);
            canvas.drawText(String.valueOf(6 - i), 0, ordinate + 3, paint);
        }
        int first = firstToday(points);
        if (first == points.length()) {
            paint.setColor(0xffa6b0bb);
            canvas.drawText("오늘 첫 기록을 남겨보세요", 30, height / 2f, paint);
            return bitmap;
        }
        double[] averages = History.averages(points, metric);
        float lastX = 0, lastY = 0, lastAverage = 0;
        for (int i = first; i < points.length(); i++) {
            if (i == points.length() - 1 && progress < 1) break;
            float px = x(points, first, i, width);
            float py = y(points.optJSONArray(i).optDouble(1), height);
            float averageY = y(averages[i], height);
            paint.setStrokeWidth(1.8f);
            paint.setColor(MINT);
            if (i > first) {
                if (metric == Metric.HUNGER && py < lastY) {
                    canvas.drawLine(lastX, lastY, px, lastY, paint);
                    canvas.drawLine(px, lastY, px, py, paint);
                } else canvas.drawLine(lastX, lastY, px, py, paint);
            }
            canvas.drawCircle(px, py, 2.5f, paint);
            paint.setColor(PURPLE);
            if (i > first) canvas.drawLine(lastX, lastAverage, px, averageY, paint);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawCircle(px, averageY, 2.8f, paint);
            paint.setStyle(Paint.Style.FILL);
            lastX = px;
            lastY = py;
            lastAverage = averageY;
        }
        paint.setColor(0xff87949f);
        java.text.SimpleDateFormat time = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.KOREA);
        canvas.drawText(time.format(new java.util.Date(points.optJSONArray(first).optLong(0))), 22, height - 4, paint);
        if (points.length() - first > 1) {
            String end = time.format(new java.util.Date(points.optJSONArray(points.length() - 1).optLong(0)));
            canvas.drawText(end, width - 16 - paint.measureText(end), height - 4, paint);
        }
        return bitmap;
    }

    static Bitmap effect(JSONArray points, int width, int height, int selected, float progress, boolean compact) {
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, width), Math.max(1, height), Bitmap.Config.ARGB_8888);
        int first = firstToday(points);
        if (selected < 0 || first == points.length() || progress >= 1.43f) return bitmap;
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int last = points.length() - 1;
        float destinationX = (compact ? 10 : 14) + x(points, first, last,
                Math.max(100, compact ? width - 20 : Math.round((width - 28) * 1.65f / 2.65f)));
        float destinationY = (compact ? 122 : 146) + y(points.optJSONArray(last).optDouble(1), height - (compact ? 356 : 448));
        if (progress < 1) {
            float originX = width * 0.5f;
            float originY = height - (compact ? 202 : 243) + selected * (compact ? 32 : 38);
            for (int tail = 9; tail >= 0; tail--) {
                float t = Math.max(0, progress - tail * 0.027f);
                float u = 1 - t;
                float px = u * u * originX + 2 * u * t * (originX - 48) + t * t * destinationX;
                float py = u * u * originY + 2 * u * t * (destinationY + 18) + t * t * destinationY;
                paint.setColor(MINT);
                paint.setAlpha(210 - tail * 20);
                canvas.drawCircle(px, py, 6 - tail * 0.45f, paint);
                if (tail == 0) {
                    paint.setColor(0xffe1fff6);
                    canvas.drawCircle(px, py, 3, paint);
                }
            }
        } else {
            float t = Math.min(1, (progress - 1) / 0.43f);
            paint.setColor(MINT);
            paint.setAlpha((int) (255 * (1 - t)));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f);
            canvas.drawCircle(destinationX, destinationY, 5 + 22 * t, paint);
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                float radius = 8 + 18 * t;
                canvas.drawCircle(destinationX + (float) Math.cos(angle) * radius,
                        destinationY + (float) Math.sin(angle) * radius, 1.5f, paint);
            }
        }
        return bitmap;
    }
}
