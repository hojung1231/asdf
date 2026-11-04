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

    RecordPane(MainActivity activity) {
        super(activity);
        inflate(activity, R.layout.widget, this);
        setMinimumHeight(dp(520));
        TextView title = findViewById(R.id.brand);
        title.setText("내 기록 · 기록 수정 ›");
        title.setTextSize(15);
        title.setOnClickListener(view -> activity.startActivity(new android.content.Intent(activity, RecordHistoryActivity.class)));
        findViewById(R.id.weight).setOnClickListener(view ->
                activity.startActivity(new android.content.Intent(activity, WeightActivity.class)));
        findViewById(R.id.score).setOnClickListener(view ->
                activity.startActivity(new android.content.Intent(activity, DailyHistoryActivity.class)));
        findViewById(R.id.friendPanel).setOnClickListener(view -> activity.selectTab(1));
        for (int i = 0; i < BUTTONS.length; i++) {
            final int value = i;
            TextView button = findViewById(BUTTONS[i]);
            button.setText(History.LABELS[i]);
            button.setOnClickListener(view -> {
                int before = History.read(activity).length();
                status = History.record(activity, value);
                boolean added = History.read(activity).length() > before;
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
        JSONArray points = History.read(context);
        JSONArray last = points.optJSONArray(points.length() - 1);
        ((TextView) findViewById(R.id.current)).setText(last == null || last.optLong(0) < History.todayStart()
                ? "오늘 첫 기록" : "지금 나의 허기" + (char) 10 + History.LABELS[last.optInt(1)].substring(3));
        double[] means = History.averages(points);
        ((TextView) findViewById(R.id.mean)).setText(points.length() == 0 ? "누적 평균" : "누적 평균" + (char) 10
                + String.format(java.util.Locale.KOREA, "%.1f / 6", 6 - means[means.length - 1]));
        ((TextView) findViewById(R.id.score)).setText(History.scoreText(points));
        ((TextView) findViewById(R.id.weight)).setText(WeightHistory.todayLabel(context));
        ((TextView) findViewById(R.id.status)).setText(status);
        ((TextView) findViewById(R.id.legend)).setText(android.text.Html.fromHtml(
                "<font color='#69e8c0'>━ 허기 기록</font>　<font color='#b59aff'>━ 누적 평균</font>　▪ 목표 구간"));
        JSONArray feed = SocialClient.cached(context).optJSONArray("feed");
        int[] ids = {R.id.friendMessage0, R.id.friendMessage1, R.id.friendMessage2};
        for (int i = 0; i < ids.length; i++) {
            org.json.JSONObject event = feed == null ? null : feed.optJSONObject(i);
            TextView text = findViewById(ids[i]);
            text.setVisibility(event != null || i == 0 ? VISIBLE : GONE);
            text.setText(event == null ? "새 친구 소식이 없습니다." : SocialClient.feedMessage(event));
        }
        for (int i = 0; i < BUTTONS.length; i++) ((TextView) findViewById(BUTTONS[i])).setTextColor(
                i == selected && (progress < 0.26f || progress == 2) ? 0xff69e8c0 : 0xffe2e7ec);
        if (getWidth() == 0 || getHeight() == 0) return;
        float density = getResources().getDisplayMetrics().density;
        int width = Math.round(getWidth() / density), height = Math.round(getHeight() / density);
        Bitmap graph = WidgetPlot.draw(points, Math.max(100, Math.round((width - 28) * 1.65f / 2.65f)),
                Math.max(50, height - 346), progress);
        ((ImageView) findViewById(R.id.plot)).setImageBitmap(graph);
        ((ImageView) findViewById(R.id.effect)).setImageBitmap(
                WidgetPlot.effect(points, width, height, selected, progress, false));
    }
}
