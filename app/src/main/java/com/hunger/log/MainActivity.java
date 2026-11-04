package com.hunger.log;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.DashPathEffect;
import android.graphics.Path;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;

public class MainActivity extends Activity {
    private static final int[] COLORS = {0xff69e8c0, 0xffb59aff, 0xff70baff, 0xffffb879};
    private final boolean[] selected = new boolean[4];
    private final CheckBox[] checks = new CheckBox[4];
    private final JSONArray[] series = new JSONArray[4];
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView date;
    private ComparisonChart chart;
    private long now;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            reload();
            handler.postDelayed(this, DemoFriends.INTERVAL - (System.currentTimeMillis() % DemoFriends.INTERVAL) + 50);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff101317);
        getWindow().setNavigationBarColor(0xff101317);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(0xff101317);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        LinearLayout header = new LinearLayout(this);
        TextView title = text("HUGGY.", 22, 0xff69e8c0);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button menu = new Button(this);
        menu.setText("메뉴 ⋯");
        menu.setTextSize(13);
        menu.setOnClickListener(view -> showMenu(menu));
        header.addView(menu);
        layout.addView(header);
        layout.addView(text("오늘의 허기 · 함께 보기", 21, 0xfff0f3f5));
        date = text("", 12, 0xffa6b0bb);
        layout.addView(date);
        layout.addView(text("친구는 데모 상태 · 체크한 사람의 그래프만 표시", 12, 0xffa6b0bb));
        for (int row = 0; row < 2; row++) {
            LinearLayout choices = new LinearLayout(this);
            for (int column = 0; column < 2; column++) {
                final int index = row * 2 + column;
                selected[index] = getPreferences(0).getBoolean("series" + index, true);
                CheckBox check = new CheckBox(this);
                check.setTextSize(13);
                check.setTextColor(COLORS[index]);
                check.setButtonTintList(android.content.res.ColorStateList.valueOf(COLORS[index]));
                check.setChecked(selected[index]);
                check.setOnCheckedChangeListener((button, checked) -> {
                    selected[index] = checked;
                    getPreferences(0).edit().putBoolean("series" + index, checked).apply();
                    chart.invalidate();
                });
                checks[index] = check;
                choices.addView(check, new LinearLayout.LayoutParams(0, -2, 1));
            }
            layout.addView(choices);
        }
        chart = new ComparisonChart();
        layout.addView(chart, new LinearLayout.LayoutParams(-1,
                (int) (320 * getResources().getDisplayMetrics().density)));
        TextView hint = text("내 허기 기록은 홈 화면의 HUGGY 위젯에서 입력하세요.", 12, 0xffa6b0bb);
        hint.setPadding(0, pad / 2, 0, 0);
        layout.addView(hint);
        android.widget.ScrollView page = new android.widget.ScrollView(this);
        page.addView(layout);
        setContentView(page);
    }

    private TextView text(String value, int size, int color) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(size);
        label.setTextColor(color);
        return label;
    }

    private void reload() {
        now = System.currentTimeMillis();
        long day = History.todayStart();
        date.setText(new java.text.SimpleDateFormat("yyyy.MM.dd · HH:mm 현재", java.util.Locale.KOREA)
                .format(new java.util.Date(now)));
        series[0] = History.inPeriod(History.read(this), day, now + 1);
        for (int i = 1; i < series.length; i++) series[i] = DemoFriends.recordsForDay(i - 1, day);
        for (int i = 0; i < checks.length; i++) {
            String name = i == 0 ? "나" : DemoFriends.NAMES[i - 1];
            JSONArray last = series[i].optJSONArray(series[i].length() - 1);
            checks[i].setText(name + (last == null ? " · 기록 없음" : " · " + (6 - last.optInt(1)) + "단계"));
        }
        chart.invalidate();
    }

    private void showMenu(View anchor) {
        android.widget.PopupMenu menu = new android.widget.PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, WeightHistory.todayLabel(this));
        menu.getMenu().add(0, 2, 1, "매일의 점수·몸무게");
        menu.getMenu().add(0, 3, 2, "친구 점수·몸무게 이력");
        menu.getMenu().add(0, 4, 3, "위젯 추가");
        menu.getMenu().add(0, 5, 4, "허기 기록 초기화");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: startActivity(new android.content.Intent(this, WeightActivity.class)); break;
                case 2: startActivity(new android.content.Intent(this, DailyHistoryActivity.class)); break;
                case 3:
                    new android.app.AlertDialog.Builder(this).setTitle("친구 기록 보기")
                            .setItems(DemoFriends.NAMES, (dialog, friend) -> startActivity(
                                    new android.content.Intent(this, DailyHistoryActivity.class)
                                            .putExtra("friend", friend))).show();
                    break;
                case 4:
                    android.appwidget.AppWidgetManager manager = android.appwidget.AppWidgetManager.getInstance(this);
                    if (android.os.Build.VERSION.SDK_INT >= 26 && manager.isRequestPinAppWidgetSupported()) {
                        manager.requestPinAppWidget(new android.content.ComponentName(this, HomeWidget.class), null, null);
                    } else {
                        new android.app.AlertDialog.Builder(this).setMessage(
                                "홈 화면을 길게 누르고 위젯에서 HUGGY를 선택하세요.").setPositiveButton("확인", null).show();
                    }
                    break;
                case 5:
                    new android.app.AlertDialog.Builder(this).setMessage("저장한 허기 기록을 모두 삭제할까요?")
                            .setNegativeButton("취소", null)
                            .setPositiveButton("삭제", (dialog, which) -> {
                                if (getSharedPreferences("history", 0).edit().remove("points").commit()) {
                                    reload();
                                    HomeWidget.refresh(this, "기록 초기화 완료", -1);
                                }
                            }).show();
                    break;
            }
            return true;
        });
        menu.show();
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refresh);
        refresh.run();
        HomeWidget.refresh(this, "HUGGY · 오늘의 현황", -1);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    private class ComparisonChart extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ComparisonChart() { super(MainActivity.this); }

        @Override protected void onDraw(Canvas canvas) {
            float dp = getResources().getDisplayMetrics().density;
            float left = 28 * dp, right = getWidth() - 16 * dp;
            float top = 30 * dp, bottom = getHeight() - 36 * dp;
            if (right <= left || bottom <= top) return;
            paint.setTextSize(12 * dp);
            paint.setStrokeWidth(dp);
            float step = (bottom - top) / 5;
            paint.setColor(0x1469e8c0);
            canvas.drawRect(left, top + step * 2, right, top + step * 4, paint);
            for (int index = 0; index < 6; index++) {
                float y = top + index * step;
                paint.setColor(0xff283039);
                canvas.drawLine(left, y, right, y, paint);
                paint.setColor(0xffa6b0bb);
                canvas.drawText(String.valueOf(6 - index), 0, y + 4 * dp, paint);
            }
            long first = now;
            boolean hasPoints = false;
            for (int i = 0; i < series.length; i++) {
                if (selected[i] && series[i] != null && series[i].length() > 0) {
                    first = Math.min(first, series[i].optJSONArray(0).optLong(0));
                    hasPoints = true;
                }
            }
            if (!hasPoints) {
                paint.setColor(0xffa6b0bb);
                canvas.drawText("체크한 사람의 오늘 기록이 없습니다.", left, top + 45 * dp, paint);
                return;
            }
            java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.KOREA);
            paint.setColor(0xffa6b0bb);
            canvas.drawText(format.format(new java.util.Date(first)), left, bottom + 25 * dp, paint);
            String current = format.format(new java.util.Date(now));
            if (now > first) canvas.drawText(current, right - paint.measureText(current), bottom + 25 * dp, paint);
            StringBuilder description = new StringBuilder("오늘의 허기 비교 그래프. ");
            for (int i = 0; i < series.length; i++) {
                if (!selected[i] || series[i] == null || series[i].length() == 0) continue;
                JSONArray points = series[i];
                Path path = new Path();
                float previousY = 0, lastX = 0, lastY = 0;
                for (int j = 0; j < points.length(); j++) {
                    JSONArray point = points.optJSONArray(j);
                    float x = left + (right - left) * (point.optLong(0) - first) / Math.max(1, now - first);
                    float y = top + point.optInt(1) * step;
                    if (j == 0) path.moveTo(x, y);
                    else if (y < previousY) {
                        path.lineTo(x, previousY);
                        path.lineTo(x, y);
                    } else path.lineTo(x, y);
                    previousY = y;
                    lastX = x;
                    lastY = y;
                }
                paint.setColor(COLORS[i]);
                paint.setStrokeWidth((i == 0 ? 2.8f : 1.8f) * dp);
                paint.setStyle(Paint.Style.STROKE);
                if (i > 0) paint.setPathEffect(new DashPathEffect(new float[]{(3 + i * 2) * dp, 3 * dp}, 0));
                canvas.drawPath(path, paint);
                paint.setPathEffect(null);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(lastX, lastY, (i == 0 ? 4 : 3) * dp, paint);
                JSONArray last = points.optJSONArray(points.length() - 1);
                description.append(i == 0 ? "나" : DemoFriends.NAMES[i - 1])
                        .append(" ").append(6 - last.optInt(1)).append("단계. ");
            }
            setContentDescription(description);
        }
    }
}
