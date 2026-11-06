package com.hunger.log;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class HomeWidget extends AppWidgetProvider {

    private static final android.os.Handler FRAMES = new android.os.Handler(android.os.Looper.getMainLooper());

    private static final String FRIEND_TICK = "com.hunger.log.DEMO_TICK";

    private static PendingIntent demoIntent(Context context) {
        return PendingIntent.getBroadcast(context, 100,
                new Intent(context, HomeWidget.class).setAction(FRIEND_TICK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void scheduleUpdates(Context context) {
        android.app.AlarmManager alarms = (android.app.AlarmManager)
                context.getSystemService(Context.ALARM_SERVICE);
        alarms.setInexactRepeating(android.app.AlarmManager.ELAPSED_REALTIME,
                android.os.SystemClock.elapsedRealtime() + 60_000L,
                60_000L, demoIntent(context));
    }

    @Override public void onEnabled(Context context) {
        scheduleUpdates(context);
    }

    @Override public void onDisabled(Context context) {
        AppWidgetManager widgets = AppWidgetManager.getInstance(context);
        if (widgets.getAppWidgetIds(new ComponentName(context, HomeWidget.class)).length > 0
                || widgets.getAppWidgetIds(new ComponentName(context, CompactWidget.class)).length > 0) return;
        android.app.AlarmManager alarms = (android.app.AlarmManager)
                context.getSystemService(Context.ALARM_SERVICE);
        alarms.cancel(demoIntent(context));
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        scheduleUpdates(context);
        AppUpdates.check(context);
        refresh(context, "HUGGY · 그래프 열기", -1);
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                                     int id, android.os.Bundle options) {
        scheduleUpdates(context);
        AppUpdates.check(context);
        refresh(context, "HUGGY · 그래프 열기", -1);
    }

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (FRIEND_TICK.equals(intent.getAction())) {
            SocialSync.schedule(context);
            if (!flying) refresh(context, "HUGGY · 기록 갱신", -1);
            return;
        }
        if ("com.hunger.log.RECORD".equals(intent.getAction())) {
            int value = intent.getIntExtra("value", -1);
            if (value >= 0 && value < 6) {
                Metric metric = Metric.from(intent.getIntExtra("metric", 0));
                metric.select(context);
                int before = History.read(context, metric).length();
                String status = metric.title + " · " + History.record(context, metric, value);
                boolean saved = History.read(context, metric).length() > before;
                animate(context, status, metric.ordinal(), value, saved, goAsync());
            }
        }
    }
    private static int animationId;
    private static boolean flying;
    private static void animate(Context context, String status, int column, int selected, boolean saved, PendingResult pending) {
        if (flying && !saved) { if (pending != null) pending.finish(); return; }
        int id = ++animationId;
        flying = saved;
        int frames = saved ? 20 : 4;
        for (int i = 0; i <= frames; i++) {
            final int frame = i;
            FRAMES.postDelayed(() -> {
                try {
                    if (id != animationId) return;
                    if (frame == frames) {
                        flying = false;
                        refresh(context, status, -1, -1, 2);
                    } else refresh(context, status, column, selected, saved ? frame / 14f : 2);
                } finally { if (frame == frames && pending != null) pending.finish(); }
            }, i * 55L);
        }
    }
    static void refresh(Context context, String status, int selected) {
        if (selected < 0) refresh(context, status, -1, -1, 2);
        else animate(context, status, Metric.selected(context).ordinal(), selected, false, null);
    }
    private static void refresh(Context context, String status, int selectedMetric, int selected, float progress) {
        org.json.JSONArray[] points = Dashboard.read(context);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (int kind = 0; kind < 2; kind++) {
            boolean compact = kind == 1;
            RemoteViews view = new RemoteViews(context.getPackageName(), compact ? R.layout.widget_compact : R.layout.widget);
            int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
            AppUpdates.Release update = AppUpdates.available(context);
            view.setViewVisibility(R.id.update, update == null ? android.view.View.GONE : android.view.View.VISIBLE);
            view.setOnClickPendingIntent(R.id.update, PendingIntent.getActivity(context, 30, new Intent(context, UpdateActivity.class), flags));
            view.setTextViewText(R.id.weight, WeightHistory.todayLabel(context));
            view.setOnClickPendingIntent(R.id.weight, PendingIntent.getActivity(context, 20, new Intent(context, WeightActivity.class), flags));
            view.setTextViewText(R.id.status, status);
            view.setTextViewText(R.id.legend, android.text.Html.fromHtml(Dashboard.LEGEND));
            view.setOnClickPendingIntent(R.id.brand, PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), flags));
            view.setOnClickPendingIntent(R.id.status, PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), flags));
            for (Metric metric : Metric.values()) {
                int column = metric.ordinal();
                view.setTextViewText(Dashboard.SCORES[column], Dashboard.score(metric, points[column]));
                view.setContentDescription(Dashboard.SCORES[column], metric.title + " · " + metric.rule());
                view.setOnClickPendingIntent(Dashboard.SCORES[column], PendingIntent.getActivity(context, 310 + column,
                        new Intent(context, DailyHistoryActivity.class).putExtra("metric", column), flags));
                view.setTextViewText(Dashboard.HEADINGS[column], Dashboard.heading(metric, points[column]));
                view.setOnClickPendingIntent(Dashboard.HEADINGS[column], PendingIntent.getActivity(context, 400 + column,
                        new Intent(context, RecordHistoryActivity.class).putExtra("metric", column), flags));
                for (int i = 0; i < 6; i++) {
                    int button = Dashboard.BUTTONS[column][i];
                    view.setTextViewText(button, metric.labels[i]);
                    view.setContentDescription(button, metric.title + " · " + metric.labels[i]);
                    view.setTextColor(button, column == selectedMetric && i == selected && (progress < 0.26f || progress == 2)
                            ? Dashboard.COLORS[column] : 0xffe2e7ec);
                    Intent click = new Intent(context, HomeWidget.class).setAction("com.hunger.log.RECORD").putExtra("value", i).putExtra("metric", column);
                    view.setOnClickPendingIntent(button, PendingIntent.getBroadcast(context, 1000 + column * 6 + i, click, flags));
                }
            }
            int[] ids = manager.getAppWidgetIds(new ComponentName(context, compact ? CompactWidget.class : HomeWidget.class));
            boolean landscape = context.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
            for (int id : ids) {
                android.os.Bundle size = manager.getAppWidgetOptions(id);
                int width = size.getInt(landscape ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300);
                int height = size.getInt(landscape ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, compact ? 420 : 620);
                RemoteViews sized = view.clone();
                sized.setImageViewBitmap(R.id.plot, WidgetPlot.draw(points, width - Dashboard.padding(compact) * 2,
                        height - Dashboard.fixedHeight(compact), selectedMetric, progress));
                sized.setImageViewBitmap(R.id.effect, WidgetPlot.effect(points, width, height, selectedMetric, selected, progress, compact));
                manager.updateAppWidget(id, sized);
            }
        }
    }
}
