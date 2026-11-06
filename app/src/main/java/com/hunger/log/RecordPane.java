package com.hunger.log;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import org.json.JSONArray;

final class RecordPane extends FrameLayout {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int generation;
    private String status = "항목별로 언제든 기록하세요.";
    private float progress = 2;
    private int selected = -1;
    private int selectedMetric = -1;

    RecordPane(MainActivity activity) {
        super(activity);
        inflate(activity, R.layout.widget, this);
        setMinimumHeight(dp(620));
        TextView title = findViewById(R.id.brand);
        title.setText("HUGGY · 기록 수정 ›");
        title.setOnClickListener(view -> new android.app.AlertDialog.Builder(activity).setTitle("수정할 기록")
                .setItems(new String[]{"허기", "집중력", "스트레스"}, (dialog, which) ->
                        open(activity, RecordHistoryActivity.class, Metric.from(which))).show());
        findViewById(R.id.weight).setOnClickListener(view -> activity.startActivity(new android.content.Intent(activity, WeightActivity.class)));
        for (Metric metric : Metric.values()) {
            int column = metric.ordinal();
            findViewById(Dashboard.SCORES[column]).setOnClickListener(view -> open(activity, DailyHistoryActivity.class, metric));
            findViewById(Dashboard.HEADINGS[column]).setOnClickListener(view -> open(activity, RecordHistoryActivity.class, metric));
            for (int i = 0; i < 6; i++) {
                final int value = i;
                findViewById(Dashboard.BUTTONS[column][i]).setOnClickListener(view -> {
                    int before = History.read(activity, metric).length();
                    status = metric.title + " · " + History.record(activity, metric, value);
                    metric.select(activity);
                    boolean added = History.read(activity, metric).length() > before;
                    HomeWidget.refresh(activity, status, -1);
                    animateRecord(column, value, added);
                });
            }
        }
    }
    private void open(MainActivity activity, Class<?> destination, Metric metric) {
        activity.startActivity(new android.content.Intent(activity, destination).putExtra("metric", metric.ordinal()));
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void animateRecord(int column, int value, boolean added) {
        int id = ++generation;
        handler.removeCallbacksAndMessages(null);
        int frames = added ? 20 : 4;
        for (int i = 0; i <= frames; i++) {
            final int frame = i;
            handler.postDelayed(() -> {
                if (id != generation) return;
                selectedMetric = frame == frames ? -1 : column;
                selected = frame == frames ? -1 : value;
                progress = frame == frames || !added ? 2 : frame / 14f;
                refresh();
            }, i * 55L);
        }
    }
    void stop() {
        generation++;
        handler.removeCallbacksAndMessages(null);
        selected = selectedMetric = -1;
        progress = 2;
    }
    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        post(this::refresh);
    }
    void refresh() {
        Context context = getContext();
        AppUpdates.bind(context, this);
        JSONArray[] points = Dashboard.read(context);
        ((TextView) findViewById(R.id.weight)).setText(WeightHistory.todayLabel(context));
        ((TextView) findViewById(R.id.status)).setText(status);
        ((TextView) findViewById(R.id.legend)).setText(android.text.Html.fromHtml(Dashboard.LEGEND));
        for (Metric metric : Metric.values()) {
            int column = metric.ordinal();
            ((TextView) findViewById(Dashboard.HEADINGS[column])).setText(Dashboard.heading(metric, points[column]));
            TextView score = findViewById(Dashboard.SCORES[column]);
            score.setText(Dashboard.score(metric, points[column]));
            score.setContentDescription(metric.title + " · " + metric.rule());
            for (int i = 0; i < 6; i++) {
                TextView button = findViewById(Dashboard.BUTTONS[column][i]);
                button.setText(metric.labels[i]);
                button.setContentDescription(metric.title + " · " + metric.labels[i]);
                button.setTextColor(column == selectedMetric && i == selected && (progress < 0.26f || progress == 2)
                        ? Dashboard.COLORS[column] : 0xffe2e7ec);
            }
        }
        if (getWidth() == 0 || getHeight() == 0) return;
        float density = getResources().getDisplayMetrics().density;
        int width = Math.round(getWidth() / density), height = Math.round(getHeight() / density);
        ((ImageView) findViewById(R.id.plot)).setImageBitmap(WidgetPlot.draw(points, width - 28,
                height - Dashboard.fixedHeight(false), selectedMetric, progress));
        ((ImageView) findViewById(R.id.effect)).setImageBitmap(WidgetPlot.effect(points, width, height,
                selectedMetric, selected, progress, false));
    }
}
