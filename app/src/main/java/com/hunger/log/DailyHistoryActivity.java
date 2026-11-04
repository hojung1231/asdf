package com.hunger.log;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

public class DailyHistoryActivity extends Activity {
    private final java.util.Calendar month = java.util.Calendar.getInstance();
    private TextView heading;
    private LinearLayout rows;
    private Button next;

    private TextView summary;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        month.set(java.util.Calendar.DAY_OF_MONTH, 1);
        if (state != null) month.setTimeInMillis(state.getLong("month", month.getTimeInMillis()));
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        heading = new TextView(this);
        heading.setTextSize(22);
        layout.addView(heading);
        TextView rule = new TextView(this);
        rule.setText("매일 첫 기록~마지막 기록의 시간 평균 기준" + (char) 10
                + "평균 2 이하 100점 · 3이면 50점 · 4 이상 0점");

        rule.append((char) 10 + "날짜를 누르면 몸무게 입력·수정");
        layout.addView(rule);
        LinearLayout navigation = new LinearLayout(this);
        addButton(navigation, "‹ 이전 달", () -> { month.add(java.util.Calendar.MONTH, -1); render(); });
        addButton(navigation, "이번 달", () -> {
            month.setTimeInMillis(System.currentTimeMillis());
            month.set(java.util.Calendar.DAY_OF_MONTH, 1);
            render();
        });
        next = addButton(navigation, "다음 달 ›", () -> { month.add(java.util.Calendar.MONTH, 1); render(); });
        layout.addView(navigation);
        summary = new TextView(this);
        summary.setTextSize(16);
        layout.addView(summary);
        ScrollView scroll = new ScrollView(this);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        layout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(layout);
    }

    private Button addButton(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(view -> action.run());
        parent.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
        return button;
    }

    @Override protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        heading.setText("나의 기록 · " + new java.text.SimpleDateFormat("yyyy년 M월 · 매일의 기록", java.util.Locale.KOREA)
                .format(month.getTime()));
        java.util.Calendar today = java.util.Calendar.getInstance();
        int currentMonth = today.get(java.util.Calendar.YEAR) * 12 + today.get(java.util.Calendar.MONTH);
        int selectedMonth = month.get(java.util.Calendar.YEAR) * 12 + month.get(java.util.Calendar.MONTH);
        next.setEnabled(selectedMonth < currentMonth);
        rows.removeAllViews();
        JSONArray points = History.read(this);
        JSONObject weights = WeightHistory.read(this);
        int days = month.getActualMaximum(java.util.Calendar.DAY_OF_MONTH);
        if (selectedMonth == currentMonth) days = today.get(java.util.Calendar.DAY_OF_MONTH);
        String newestWeight = null, oldestWeight = null;
        for (int day = days; day >= 1; day--) {
            java.util.Calendar start = (java.util.Calendar) month.clone();
            start.set(java.util.Calendar.DAY_OF_MONTH, day);
            start.set(java.util.Calendar.HOUR_OF_DAY, 0);
            start.set(java.util.Calendar.MINUTE, 0);
            start.set(java.util.Calendar.SECOND, 0);
            start.set(java.util.Calendar.MILLISECOND, 0);
            java.util.Calendar end = (java.util.Calendar) start.clone();
            end.add(java.util.Calendar.DAY_OF_MONTH, 1);
            JSONArray daily = History.inPeriod(points, start.getTimeInMillis(), end.getTimeInMillis());
            int score = History.todayScore(daily);
            String value = weights.optString(WeightHistory.dateKey(start.getTimeInMillis()), "");
            if (!value.isEmpty()) {
                if (newestWeight == null) newestWeight = value;
                oldestWeight = value;
            }
            java.util.Calendar yesterday = (java.util.Calendar) start.clone();
            yesterday.add(java.util.Calendar.DAY_OF_MONTH, -1);
            String previousWeight = weights.optString(WeightHistory.dateKey(yesterday.getTimeInMillis()), "");
            TextView row = new TextView(this);
            row.setTextSize(16);
            int pad = (int) (14 * getResources().getDisplayMetrics().density);
            row.setPadding(0, pad, 0, pad);
            String scoreLabel = score < 0 ? "허기 기록 없음" : History.face(score) + " " + score + "점";
            String detail = "몸무게 " + (value.isEmpty() ? "미입력" : value + " kg");
            if (!value.isEmpty() && !previousWeight.isEmpty()) {
                detail += "  ·  전일 대비 " + weightChange(previousWeight, value);
            }
            if (daily.length() > 0) {
                double[] means = History.averages(daily);
                detail += String.format(java.util.Locale.KOREA, "  ·  평균 %.2f  ·  %d회",
                        6 - means[means.length - 1], daily.length());
            }
            row.setText(WeightHistory.dateKey(start.getTimeInMillis()) + "   " + scoreLabel + (char) 10 + detail);
            {
                long date = start.getTimeInMillis();
                row.setOnClickListener(view -> startActivity(new android.content.Intent(
                        this, WeightActivity.class).putExtra("date", date)));
                android.util.TypedValue background = new android.util.TypedValue();
                getTheme().resolveAttribute(android.R.attr.selectableItemBackground, background, true);
                row.setBackgroundResource(background.resourceId);
            }
            rows.addView(row);
            android.view.View divider = new android.view.View(this);
            divider.setBackgroundColor(0xffdce4e0);
            rows.addView(divider, new LinearLayout.LayoutParams(-1, 1));
        }
        summary.setText(newestWeight == null ? "이 달의 몸무게 기록이 없습니다."
                : "이 달 기록 내 몸무게 변화 " + weightChange(oldestWeight, newestWeight));
    }

    private String weightChange(String from, String to) {
        java.math.BigDecimal change = new java.math.BigDecimal(to).subtract(new java.math.BigDecimal(from));
        return (change.signum() > 0 ? "+" : "") + change.stripTrailingZeros().toPlainString() + " kg";
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong("month", month.getTimeInMillis());
        super.onSaveInstanceState(state);
    }
}
