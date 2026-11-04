package com.hunger.log;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class HomeWidget extends AppWidgetProvider {
    private static final int[] BUTTONS = {R.id.b0, R.id.b1, R.id.b2, R.id.b3, R.id.b4};

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        refresh(context, "허기 기록 · 그래프 열기", -1);
    }

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if ("com.hunger.log.RECORD".equals(intent.getAction())) {
            int value = intent.getIntExtra("value", -1);
            if (value >= 0 && value < BUTTONS.length) {
                refresh(context, History.record(context, value), value);
            }
        }
    }

    static void refresh(Context context, String status, int selected) {
        RemoteViews view = new RemoteViews(context.getPackageName(), R.layout.widget);
        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        view.setTextViewText(R.id.status, status);
        view.setOnClickPendingIntent(R.id.status, PendingIntent.getActivity(context, 0,
                new Intent(context, MainActivity.class), flags));
        for (int i = 0; i < BUTTONS.length; i++) {
            view.setTextViewText(BUTTONS[i], (i == selected ? "✓ " : "") + History.LABELS[i]);
            Intent click = new Intent(context, HomeWidget.class).setAction("com.hunger.log.RECORD");
            click.putExtra("value", i);
            view.setOnClickPendingIntent(BUTTONS[i], PendingIntent.getBroadcast(context, i, click, flags));
        }
        AppWidgetManager.getInstance(context).updateAppWidget(new ComponentName(context, HomeWidget.class), view);
    }
}
