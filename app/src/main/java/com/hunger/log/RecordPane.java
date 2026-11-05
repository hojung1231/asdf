package com.hunger.log;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import org.json.JSONArray;

final class RecordPane extends FrameLayout {
    private static final int[] BUTTONS = {R.id.b0, R.id.b1, R.id.b2, R.id.b3, R.id.b4, R.id.b5};
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int generation;
    private String status = "언제든 눌러 기록하세요.";
    private float progress = 2;
    private int selected = -1;
    private Metric renderedMetric;

    RecordPane(MainActivity activity) {
        super(activity);
        inflate(activity, R.layout.widget, this);
        setMinimumHeight(dp(620));
        TextView title = findViewById(R.id.brand);
        title.setText("내 기록 · 기록 수정 ›");
        title.setTextSize(15);
        title.setOnClickListener(view -> activity.startActivity(new android.content.Intent(activity, RecordHistoryActivity.class)
                .putExtra("metric", Metric.selected(activity).ordinal())));
        findViewById(R.id.weight).setOnClickListener(view -> {
            Metric metric = Metric.selected(activity);
            activity.startActivity(new android.content.Intent(activity, metric == Metric.HUNGER ? WeightActivity.class : DailyHistoryActivity.class)
                    .putExtra("metric", metric.ordinal()));
        });
        findViewById(R.id.score).setOnClickListener(view ->
                activity.startActivity(new android.content.Intent(activity, DailyHistoryActivity.class).putExtra("metric", Metric.selected(activity).ordinal())));
        findViewById(R.id.friendPanel).setOnClickListener(view -> activity.selectTab(1));
        int[] tabs = {R.id.metric0, R.id.metric1, R.id.metric2};
        for (int i = 0; i < tabs.length; i++) {
            final Metric metric = Metric.from(i);
            findViewById(tabs[i]).setOnClickListener(view -> {
                stop(); metric.select(activity); status = metric.rule(); refresh();
                HomeWidget.refresh(activity, status, -1);
            });
        }
        for (int i = 0; i < BUTTONS.length; i++) {
            final int value = i;
            TextView button = findViewById(BUTTONS[i]);
            button.setText(History.LABELS[i]);
            button.setOnClickListener(view -> {
                Metric metric = Metric.selected(activity);
                int before = History.read(activity, metric).length();
                status = History.record(activity, metric, value);
                boolean added = History.read(activity, metric).length() > before;
                HomeWidget.refresh(activity, status, -1);
                animateRecord(value, added);
            });
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void animateRecord(int value, boolean added) {
        int id = ++generation;
        handler.removeCallbacksAndMessages(null);
        int frames = added ? 20 : 4;
        for (int i = 0; i <= frames; i++) {
            final int frame = i;
            handler.postDelayed(() -> {
                if (id != generation) return;
                selected = frame == frames ? -1 : value;
                progress = frame == frames || !added ? 2 : frame / 14f;
                refresh();
            }, i * 55L);
        }
    }

    void stop() {
        generation++;
        handler.removeCallbacksAndMessages(null);
        selected = -1;
        progress = 2;
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        post(this::refresh);
    }

    void refresh() {
        Context context = getContext();
        AppUpdates.bind(context, this);
        Metric metric = Metric.selected(context);
        if (renderedMetric != metric) { stop(); status = metric.rule(); renderedMetric = metric; }
        ((TextView) findViewById(R.id.brand)).setText(metric.title + " · 기록 수정 ›");
        int[] tabs = {R.id.metric0, R.id.metric1, R.id.metric2};
        for (int i = 0; i < tabs.length; i++) {
            ((TextView) findViewById(tabs[i])).setTextColor(i == metric.ordinal() ? 0xff69e8c0 : 0xffa6b0bb);
            findViewById(tabs[i]).setSelected(i == metric.ordinal());
        }
        JSONArray points = History.read(context, metric);
        JSONArray last = points.optJSONArray(points.length() - 1);
        ((TextView) findViewById(R.id.current)).setText(last == null || last.optLong(0) < History.todayStart()
                ? "오늘 첫 " + metric.title + " 기록" : "지금 " + metric.title + (char) 10 + metric.labels[last.optInt(1)].substring(3));
        double[] means = History.averages(points, metric);
        String meanTitle = metric == Metric.FOCUS ? "전체 평균" : "누적 평균";
        ((TextView) findViewById(R.id.mean)).setText(points.length() == 0 ? meanTitle : meanTitle + (char) 10
                + String.format(java.util.Locale.KOREA, "%.1f / 6", 6 - means[means.length - 1]));
        ((TextView) findViewById(R.id.score)).setText(metric.scoreText(points));
        findViewById(R.id.score).setContentDescription(metric.rule());
        ((TextView) findViewById(R.id.weight)).setText(metric == Metric.HUNGER ? WeightHistory.todayLabel(context) : metric.rule() + "  ›");
        ((TextView) findViewById(R.id.status)).setText(status);
        ((TextView) findViewById(R.id.legend)).setText(android.text.Html.fromHtml(
                metric.legend()));
        JSONArray feed = SocialClient.cached(context).optJSONArray("feed");
        int[] ids = {R.id.friendMessage0, R.id.friendMessage1, R.id.friendMessage2};
        for (int i = 0; i < ids.length; i++) {
            org.json.JSONObject event = feed == null ? null : feed.optJSONObject(i);
            TextView text = findViewById(ids[i]);
            text.setVisibility(event != null || i == 0 ? VISIBLE : GONE);
            text.setText(event == null ? "새 친구 소식이 없습니다." : SocialClient.feedMessage(event));
        }
        for (int i = 0; i < BUTTONS.length; i++) {
            TextView button = findViewById(BUTTONS[i]);
            button.setText(metric.labels[i]); button.setBackgroundResource(metric.background(i));
            button.setTextColor(i == selected && (progress < 0.26f || progress == 2) ? 0xff69e8c0 : 0xffe2e7ec);
        }
        if (getWidth() == 0 || getHeight() == 0) return;
        float density = getResources().getDisplayMetrics().density;
        int width = Math.round(getWidth() / density), height = Math.round(getHeight() / density);
        Bitmap graph = WidgetPlot.draw(points, Math.max(100, Math.round((width - 28) * 1.65f / 2.65f)),
                Math.max(50, height - 448), progress, metric);
        ((ImageView) findViewById(R.id.plot)).setImageBitmap(graph);
        ((ImageView) findViewById(R.id.effect)).setImageBitmap(
                WidgetPlot.effect(points, width, height, selected, progress, false));
    }
}
