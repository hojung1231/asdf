package com.hunger.log;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class HomeWidget extends AppWidgetProvider {
    private static final int[] BUTTONS = {R.id.b0, R.id.b1, R.id.b2, R.id.b3, R.id.b4, R.id.b5};

    private static final android.os.Handler FRAMES = new android.os.Handler(android.os.Looper.getMainLooper());

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        refresh(context, "HUGGY · 그래프 열기", -1);
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                                     int id, android.os.Bundle options) {
        refresh(context, "HUGGY · 그래프 열기", -1);
    }

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if ("com.hunger.log.RECORD".equals(intent.getAction())) {
            int value = intent.getIntExtra("value", -1);
            if (value >= 0 && value < BUTTONS.length) {
                int before = History.read(context).length();
                String status = History.record(context, value);
                boolean saved = History.read(context).length() > before;
                animate(context, status, value, saved, goAsync());
            }
        }
    }

    private static int animationId;
    private static boolean flying;

    private static void animate(Context context, String status, int selected, boolean saved,
                                PendingResult pending) {
        if (flying && !saved) {
            if (pending != null) pending.finish();
            return;
        }
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
                        refresh(context, status, -1, 2);
                    } else {
                        float progress = saved ? frame / 14f : 2;
                        refresh(context, status, selected, progress);
                    }
                } finally {
                    if (frame == frames && pending != null) pending.finish();
                }
            }, i * 55L);
        }
    }

    static void refresh(Context context, String status, int selected) {
        if (selected < 0) refresh(context, status, -1, 2);
        else animate(context, status, selected, false, null);
    }
    private static void refresh(Context context, String status, int selected, float progress) {
        RemoteViews view = new RemoteViews(context.getPackageName(), R.layout.widget);
        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        org.json.JSONArray points = History.read(context);
        view.setContentDescription(R.id.status, status + " · 그래프 열기");
        view.setOnClickPendingIntent(R.id.status, PendingIntent.getActivity(context, 0,
                new Intent(context, MainActivity.class), flags));
        for (int i = 0; i < BUTTONS.length; i++) {
            view.setTextViewText(BUTTONS[i], (i == selected ? "✓ " : "") + History.LABELS[i]);
            Intent click = new Intent(context, HomeWidget.class).setAction("com.hunger.log.RECORD");
            click.putExtra("value", i);
            view.setOnClickPendingIntent(BUTTONS[i], PendingIntent.getBroadcast(context, i, click, flags));
        }
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, HomeWidget.class));
        boolean landscape = context.getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        for (int id : ids) {
            android.os.Bundle size = manager.getAppWidgetOptions(id);
            int width = size.getInt(landscape ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
                    : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300);
            int height = size.getInt(landscape ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                    : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 350);
            float textScale = Math.max(0.65f, Math.min(1.35f, 300f * height / (350f * Math.max(1, width))));
            RemoteViews sized = view.clone();
            sized.setImageViewBitmap(R.id.plot, WidgetPlot.draw(points, status, selected, progress, textScale));
            manager.updateAppWidget(id, sized);
        }
    }
}
