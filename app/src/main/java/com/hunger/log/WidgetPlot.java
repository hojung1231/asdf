package com.hunger.log;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import org.json.JSONArray;

final class WidgetPlot {
    private static final int MINT = 0xff54ffce;
    private static final int PURPLE = 0xffd395ff;
    private static final int MUTED = 0xffa9b4d0;
    private static final float LEFT = 18, RIGHT = 282, TOP = 52, STEP = 19;

    static Bitmap draw(JSONArray points, String status, int selected, float progress) {
        Bitmap bitmap = Bitmap.createBitmap(450, 525, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(1.5f, 1.5f);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        canvas.drawColor(0xff090b13);
        label(canvas, paint, "허기 기록", 16, 23, 16, MINT);
        label(canvas, paint, "기록", 156, 22, 10, MINT);
        label(canvas, paint, "누적 평균", 188, 22, 10, PURPLE);
        label(canvas, paint, "목표", 248, 22, 10, MUTED);
        paint.setColor(0xff111624);
        canvas.drawRoundRect(10, 34, 290, 141, 12, 12, paint);
        for (int level = 0; level < 5; level++) {
            float y = TOP + level * STEP;
            paint.setColor(level == 3 ? 0xff468174 : 0xff263049);
            canvas.drawLine(LEFT, y, RIGHT, y, paint);
        }
        label(canvas, paint, status, 16, 159, 11, MUTED);
        for (int i = 0; i < History.LABELS.length; i++) {
            float y = 170 + i * 36;
            paint.setColor(i == selected ? 0xff17433b : 0xff141a2c);
            canvas.drawRoundRect(12, y + 3, 288, y + 33, 10, 10, paint);
            label(canvas, paint, History.LABELS[i], 29, y + 23, 13, i == 3 || i == selected ? MINT : MUTED);
        }

        int count = points.length();
        if (count == 0) {
            label(canvas, paint, "버튼을 눌러 첫 기록을 남겨보세요", 55, 93, 12, MUTED);
            return bitmap;
        }

        long start = points.optJSONArray(0).optLong(0);
        long end = points.optJSONArray(count - 1).optLong(0);
        double sum = 0;
        float lastX = LEFT, lastY = TOP, lastAverage = TOP;
        for (int i = 0; i < count; i++) {
            JSONArray point = points.optJSONArray(i);
            int value = point.optInt(1);
            sum += value;
            float x = LEFT + (RIGHT - LEFT) * (point.optLong(0) - start) / Math.max(1, end - start);
            float y = TOP + value * STEP;
            float average = TOP + STEP * (float) (sum / (i + 1));
            if (i == count - 1 && progress < 1) {
                float fromY = 188 + selected * 36;
                float eased = progress * (2 - progress);
                float orbX = 260 + (x - 260) * eased;
                float orbY = fromY + (y - fromY) * eased;
                paint.setColor(0x7054ffce);
                canvas.drawCircle(orbX, orbY, 9, paint);
                paint.setColor(MINT);
                canvas.drawCircle(orbX, orbY, 4, paint);
                break;
            }
            paint.setStrokeWidth(1.5f);
            paint.setColor(MINT);
            if (i > 0) canvas.drawLine(lastX, lastY, x, y, paint);
            canvas.drawCircle(x, y, i == count - 1 ? 4 : 2.5f, paint);
            paint.setColor(PURPLE);
            if (i > 0) canvas.drawLine(lastX, lastAverage, x, average, paint);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawCircle(x, average, 3.5f, paint);
            paint.setStyle(Paint.Style.FILL);
            lastX = x;
            lastY = y;
            lastAverage = average;
        }
        return bitmap;
    }

    private static void label(Canvas canvas, Paint paint, String text, float x, float y, int size, int color) {
        paint.setColor(color);
        paint.setTextSize(size);
        canvas.drawText(text, x, y, paint);
    }
}
