package com.hunger.log;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RemoteViews;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public class LayoutTest {
    @org.junit.Before public void keepLayoutTestsOffline() {
        AppUpdates.prefs(org.robolectric.RuntimeEnvironment.getApplication()).edit()
                .clear().putLong("attempt", System.currentTimeMillis()).commit();
    }

    @Test public void updateButtonAppearsOnlyForANewerVersionInBothWidgetSizes() throws Exception {
        android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
        for (int code : new int[]{BuildConfig.VERSION_CODE - 1, BuildConfig.VERSION_CODE, BuildConfig.VERSION_CODE + 1}) {
            org.json.JSONObject release = new org.json.JSONObject().put("versionCode", code)
                    .put("versionName", "2.3").put("url", AppUpdates.BASE + "download/huggy-v2.3/HUGGY-2.3.apk")
                    .put("sha256", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
            AppUpdates.prefs(context).edit().putString("release", release.toString()).commit();
            for (int layout : new int[]{R.layout.widget, R.layout.widget_compact}) {
                android.widget.FrameLayout parent = new android.widget.FrameLayout(context);
                View root = android.view.LayoutInflater.from(context).inflate(layout, parent, false);
                AppUpdates.bind(context, root);
                measure(root, 250, layout == R.layout.widget ? 520 : 320);
                assertEquals(code > BuildConfig.VERSION_CODE ? View.VISIBLE : View.GONE, root.findViewById(R.id.update).getVisibility());
                assertTrue(root.findViewById(R.id.brand).getMeasuredWidth() > 0);
                assertTrue(root.findViewById(R.id.plot).getMeasuredHeight() > 0);
            }
        }
    }
    private void measure(View view, int width, int height) {
        float density = view.getResources().getDisplayMetrics().density;
        int pixelsWide = Math.round(width * density);
        int pixelsHigh = Math.round(height * density);
        view.measure(View.MeasureSpec.makeMeasureSpec(pixelsWide, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(pixelsHigh, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, pixelsWide, pixelsHigh);
    }

    @Test public void miniWidgetCanRenderAndKeepAllSixButtonsInside() {
        android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
        RemoteViews remote = new RemoteViews(context.getPackageName(), R.layout.widget_compact);
        ViewGroup widget = (ViewGroup) remote.apply(context, null);
        measure(widget, 300, 320);
        ImageView plot = widget.findViewById(R.id.plot);
        assertTrue(plot.getMeasuredHeight() >= 40);
        for (int id : new int[]{R.id.b0, R.id.b1, R.id.b2, R.id.b3, R.id.b4, R.id.b5}) {
            View button = widget.findViewById(id);
            Rect bounds = new Rect(0, 0, button.getWidth(), button.getHeight());
            widget.offsetDescendantRectToMyCoords(button, bounds);
            assertTrue(bounds.top >= 0);
            assertTrue(bounds.bottom <= widget.getHeight());
            assertTrue(button.getMeasuredWidth() > 0);
        }
    }

    @Test public void appUsesViewportAndKeepsInputAccessibleAtDifferentSizes() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            ViewGroup content = activity.findViewById(android.R.id.content);
            View root = content.getChildAt(0);
            for (int[] size : new int[][]{{320, 568}, {412, 892}, {800, 360}}) {
                measure(root, size[0], size[1]);
                RecordPane pane = activity.findViewById(R.id.plot).getParent() instanceof RecordPane
                        ? (RecordPane) activity.findViewById(R.id.plot).getParent() : findPane(root);
                assertNotNull(pane);
                assertTrue(pane.getMeasuredHeight() >= pane.getResources().getDisplayMetrics().density * 520);
                assertTrue(activity.findViewById(R.id.plot).getMeasuredHeight() > 0);
                assertTrue(activity.findViewById(R.id.b5).getMeasuredHeight() > 0);
                activity.selectTab(1);
                measure(root, size[0], size[1]);
                activity.selectTab(0);
            }
        }
    }

    private RecordPane findPane(View root) {
        if (root instanceof RecordPane) return (RecordPane) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                RecordPane found = findPane(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
}
