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
                measure(root, 250, layout == R.layout.widget ? 620 : 420);
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

    @Test public void widgetsShowAllEighteenButtonsAndAFullWidthGraph() {
        android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
        for (boolean compact : new boolean[]{false, true}) {
            RemoteViews remote = new RemoteViews(context.getPackageName(), compact ? R.layout.widget_compact : R.layout.widget);
            ViewGroup widget = (ViewGroup) remote.apply(context, null);
            measure(widget, 250, compact ? 420 : 620);
            ImageView plot = widget.findViewById(R.id.plot);
            float density = context.getResources().getDisplayMetrics().density;
            assertTrue(plot.getMeasuredHeight() >= 40 * density);
            assertEquals(widget.getWidth() - Math.round(2 * Dashboard.padding(compact) * density), plot.getMeasuredWidth());
            Rect graph = bounds(widget, plot);
            assertEquals(Math.round(Dashboard.plotTop(compact) * density), graph.top);
            assertEquals(widget.getHeight() - Math.round(Dashboard.fixedHeight(compact) * density), graph.height());
            int previousBottom = -1;
            for (int row = 0; row < 6; row++) {
                int previousRight = -1, top = -1;
                for (int column = 0; column < 3; column++) {
                    View button = widget.findViewById(Dashboard.BUTTONS[column][row]);
                    Rect area = bounds(widget, button);
                    assertTrue(area.top >= 0 && area.bottom <= widget.getHeight());
                    assertTrue(area.left > previousRight && area.width() > 0);
                    if (top >= 0) assertEquals(top, area.top);
                    assertTrue(area.top > previousBottom);
                    previousRight = area.right; top = area.top;
                    float expectedX = Dashboard.padding(compact) * density
                            + (widget.getWidth() - 2 * Dashboard.padding(compact) * density) * (column + .5f) / 3;
                    float expectedY = widget.getHeight() - Dashboard.originBottom(compact) * density + row * Dashboard.rowHeight(compact) * density;
                    assertEquals(expectedX, area.exactCenterX(), 2);
                    assertEquals(expectedY, area.exactCenterY(), 2);
                }
                previousBottom = bounds(widget, widget.findViewById(Dashboard.BUTTONS[0][row])).bottom;
            }
        }
    }
    private Rect bounds(ViewGroup root, View child) {
        Rect area = new Rect(0, 0, child.getWidth(), child.getHeight());
        root.offsetDescendantRectToMyCoords(child, area);
        return area;
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
                assertTrue(pane.getMeasuredHeight() >= pane.getResources().getDisplayMetrics().density * 620);
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

    @Test public void simultaneousColumnsRecordAndReviseWithoutChangingOtherHistories() {
        android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
        long timestamp = History.todayStart() + 1000;
        org.json.JSONArray original = new org.json.JSONArray().put(new org.json.JSONArray().put(timestamp).put(3));
        context.getSharedPreferences("history", 0).edit().putString("points", original.toString()).putLong("social_change", 1234).commit();
        context.getSharedPreferences("history_focus", 0).edit().clear().commit();
        context.getSharedPreferences("history_stress", 0).edit().clear().commit();
        Metric.HUNGER.select(context);
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            activity.findViewById(R.id.f0).performClick();
            activity.findViewById(R.id.f5).performClick();
            org.json.JSONArray focus = History.read(context, Metric.FOCUS);
            assertEquals(1, focus.length());
            assertEquals(5, focus.optJSONArray(0).optInt(1));
            assertTrue(History.edit(context, Metric.FOCUS, 0, focus.optJSONArray(0).optLong(0), 2));
            assertEquals(2, History.read(context, Metric.FOCUS).optJSONArray(0).optInt(1));
            activity.findViewById(R.id.s0).performClick();
            assertEquals(1, History.read(context, Metric.STRESS).length());
            assertEquals(original.toString(), History.read(context).toString());
            assertEquals(1234, context.getSharedPreferences("history", 0).getLong("social_change", 0));
            assertEquals(History.LABELS[0], ((android.widget.TextView) activity.findViewById(R.id.b0)).getText().toString());
            assertEquals(Metric.FOCUS.labels[0], ((android.widget.TextView) activity.findViewById(R.id.f0)).getText().toString());
            assertEquals(Metric.STRESS.labels[0], ((android.widget.TextView) activity.findViewById(R.id.s0)).getText().toString());
            for (int[] column : Dashboard.BUTTONS) for (int id : column) assertEquals(View.VISIBLE, activity.findViewById(id).getVisibility());
        }
    }

    @Test public void friendRequestsSeparateIncomingApprovalFromOutgoingCancellation() throws Exception {
        try (ActivityController<FriendRequestsActivity> controller = Robolectric.buildActivity(FriendRequestsActivity.class).create()) {
            FriendRequestsActivity activity = controller.get();
            org.json.JSONArray requests = new org.json.JSONArray()
                    .put(new org.json.JSONObject().put("user_id", "a").put("nickname", "요한").put("incoming", true))
                    .put(new org.json.JSONObject().put("user_id", "b").put("nickname", "길동").put("incoming", false));
            java.lang.reflect.Method render = FriendRequestsActivity.class.getDeclaredMethod("render", org.json.JSONObject.class);
            render.setAccessible(true);
            render.invoke(activity, new org.json.JSONObject().put("requests", requests));
            ViewGroup content = activity.findViewById(android.R.id.content);
            View root = content.getChildAt(0);
            measure(root, 320, 568);
            java.util.List<String> actions = new java.util.ArrayList<>();
            collectButtons(root, actions);
            assertEquals(1, java.util.Collections.frequency(actions, "승인"));
            assertEquals(1, java.util.Collections.frequency(actions, "거절"));
            assertEquals(1, java.util.Collections.frequency(actions, "요청 취소"));
            render.invoke(activity, new org.json.JSONObject().put("requests", new org.json.JSONArray()));
            actions.clear();
            collectButtons(root, actions);
            assertFalse(actions.contains("승인"));
            assertFalse(actions.contains("거절"));
            assertFalse(actions.contains("요청 취소"));
        }
    }

    private void collectButtons(View view, java.util.List<String> actions) {
        if (view instanceof android.widget.Button) actions.add(((android.widget.Button) view).getText().toString());
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collectButtons(group.getChildAt(i), actions);
        }
    }

    @Test @Config(sdk = 35) public void android15KeepsContentOutsideBarsAndKeyboard() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            View content = activity.findViewById(android.R.id.content);
            android.view.WindowInsets insets = new android.view.WindowInsets.Builder()
                    .setInsets(android.view.WindowInsets.Type.statusBars(), android.graphics.Insets.of(0, 24, 0, 0))
                    .setInsets(android.view.WindowInsets.Type.navigationBars(), android.graphics.Insets.of(0, 0, 0, 32))
                    .setInsets(android.view.WindowInsets.Type.ime(), android.graphics.Insets.of(0, 0, 0, 200))
                    .build();
            content.dispatchApplyWindowInsets(insets);
            assertEquals(24, content.getPaddingTop());
            assertEquals(200, content.getPaddingBottom());
            android.view.WindowInsets closed = new android.view.WindowInsets.Builder(insets)
                    .setInsets(android.view.WindowInsets.Type.ime(), android.graphics.Insets.NONE).build();
            content.dispatchApplyWindowInsets(closed);
            assertEquals(32, content.getPaddingBottom());
        }
    }
}
