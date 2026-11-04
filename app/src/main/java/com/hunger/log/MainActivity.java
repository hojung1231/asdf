package com.hunger.log;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final java.util.Map<String, PersonColumn> columns = new java.util.LinkedHashMap<>();
    private LinearLayout people;
    private LinearLayout friendContent;
    private android.widget.ScrollView friendScroll;
    private android.widget.ViewFlipper tabs;
    private RecordPane recordPane;
    private Button recordsTab;
    private Button friendsTab;
    private TextView message;
    private boolean loading;
    private boolean visible;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            render();
            if (SocialClient.configured()) remote(client -> {
                if (client.signedIn()) { client.queueLatest(false); client.flush(); client.snapshot(); }
            });
            handler.postDelayed(this, 15000);
        }
    };

    interface Task { void run(SocialClient client) throws Exception; }

    private void remote(Task task) {
        if (loading) return;
        loading = true;
        SocialClient.EXECUTOR.execute(() -> {
            String error = null;
            try { task.run(new SocialClient(this)); }
            catch (Exception failure) { error = failure.getMessage(); }
            String result = error;
            handler.post(() -> {
                loading = false;
                if (!visible || isFinishing() || isDestroyed()) return;
                message.setText(result == null ? "현재 공유 상태 · 약 15초마다 확인" : "연결 확인 필요 · " + result);
                render();
                HomeWidget.refresh(this, "HUGGY · 현재 상태", -1);
            });
        });
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff101317);
        getWindow().setNavigationBarColor(0xff101317);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xff101317);
        root.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.addView(text("HUGGY.", 23, 0xff69e8c0), new LinearLayout.LayoutParams(0, -2, 1));
        Button add = new Button(this);
        add.setText("＋ 친구");
        add.setTextSize(13);
        add.setOnClickListener(view -> { selectTab(1); follow(); });
        header.addView(add);
        Button menu = new Button(this);
        menu.setText("⋯");
        menu.setContentDescription("계정 및 기록 메뉴");
        menu.setOnClickListener(view -> showMenu(menu));
        header.addView(menu);
        root.addView(header);
        LinearLayout navigation = new LinearLayout(this);
        recordsTab = new Button(this);
        recordsTab.setText("내 기록");
        recordsTab.setOnClickListener(view -> selectTab(0));
        friendsTab = new Button(this);
        friendsTab.setText("함께 보기");
        friendsTab.setOnClickListener(view -> selectTab(1));
        navigation.addView(recordsTab, new LinearLayout.LayoutParams(0, dp(48), 1));
        navigation.addView(friendsTab, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(navigation);
        tabs = new android.widget.ViewFlipper(this);
        android.widget.ScrollView recordScroll = new android.widget.ScrollView(this);
        recordScroll.setFillViewport(true);
        recordPane = new RecordPane(this);
        recordScroll.addView(recordPane, new android.widget.ScrollView.LayoutParams(-1, -2));
        tabs.addView(recordScroll, new android.widget.ViewFlipper.LayoutParams(-1, -1));
        friendScroll = new android.widget.ScrollView(this);
        friendContent = new LinearLayout(this);
        friendContent.setOrientation(LinearLayout.VERTICAL);
        friendContent.addView(text("지금, 함께 어떤 상태인가요?", 21, 0xfff0f3f5));
        friendContent.addView(text("공은 마지막 기록 위치 · 친구를 눌러 팔로우 관리", 12, 0xffa6b0bb));
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
        people = new LinearLayout(this);
        people.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(people);
        friendContent.addView(scroll);
        message = text("", 12, 0xffa6b0bb);
        message.setPadding(0, dp(12), 0, dp(12));
        friendContent.addView(message);
        friendScroll.addView(friendContent);
        tabs.addView(friendScroll, new android.widget.ViewFlipper.LayoutParams(-1, -1));
        root.addView(tabs, new LinearLayout.LayoutParams(-1, 0, 1));
        friendContent.getViewTreeObserver().addOnGlobalLayoutListener(this::resizeFriendRails);
        setContentView(root);
        selectTab(state == null ? getIntent().getIntExtra("tab", 0) : state.getInt("tab", 0));
    }

    void selectTab(int tab) {
        int selected = tab == 1 ? 1 : 0;
        tabs.setDisplayedChild(selected);
        recordsTab.setTextColor(selected == 0 ? 0xff101317 : 0xffe2e7ec);
        friendsTab.setTextColor(selected == 1 ? 0xff101317 : 0xffe2e7ec);
        recordsTab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(selected == 0 ? 0xff69e8c0 : 0xff252d35));
        friendsTab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(selected == 1 ? 0xff69e8c0 : 0xff252d35));
        if (selected == 1) recordPane.stop();
        render();
    }

    private void resizeFriendRails() {
        if (friendScroll.getHeight() == 0 || columns.isEmpty()) return;
        int current = columns.values().iterator().next().rail.getLayoutParams().height;
        int fixed = friendContent.getHeight() - current;
        int target = Math.max(dp(160), friendScroll.getHeight() - fixed);
        if (target == current) return;
        for (PersonColumn column : columns.values()) {
            column.rail.setLayoutParams(new LinearLayout.LayoutParams(-1, target));
        }
    }

    @Override protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        selectTab(intent.getIntExtra("tab", 0));
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("tab", tabs.getDisplayedChild());
        super.onSaveInstanceState(state);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void render() {
        if (recordPane != null) recordPane.refresh();
        JSONArray members = SocialClient.cached(this).optJSONArray("members");
        JSONObject own = new JSONObject();
        try {
            if (members != null) for (int i = 0; i < members.length(); i++) {
                if (members.optJSONObject(i).optBoolean("own")) own = members.optJSONObject(i);
            }
            JSONArray history = History.read(this);
            JSONArray last = history.optJSONArray(history.length() - 1);
            if (last != null && last.optLong(0) >= own.optLong("recorded_at")) {
                own = new JSONObject(own.toString()).put("level", 6 - last.optInt(1)).put("recorded_at", last.optLong(0));
            }
            own.put("user_id", "self").put("nickname", "나").put("own", true);
        } catch (org.json.JSONException failure) { throw new IllegalStateException(failure); }
        java.util.Set<String> ids = new java.util.HashSet<>();
        updateColumn(own, ids);
        if (members != null) for (int i = 0; i < members.length(); i++) {
            JSONObject person = members.optJSONObject(i);
            if (!person.optBoolean("own")) updateColumn(person, ids);
        }
        java.util.Iterator<java.util.Map.Entry<String, PersonColumn>> iterator = columns.entrySet().iterator();
        while (iterator.hasNext()) {
            java.util.Map.Entry<String, PersonColumn> entry = iterator.next();
            if (!ids.contains(entry.getKey())) {
                entry.getValue().rail.stop();
                people.removeView(entry.getValue());
                iterator.remove();
            }
        }
        if (!SocialClient.configured()) message.setText("서버 연결 준비 중 · 가상 친구는 표시하지 않습니다.");
        else if (members == null) message.setText("계정 메뉴에서 로그인하고 친구 코드를 추가하세요.");
    }

    private void updateColumn(JSONObject person, java.util.Set<String> ids) {
        String id = person.optString("user_id");
        ids.add(id);
        PersonColumn column = columns.get(id);
        if (column == null) {
            column = new PersonColumn(person);
            columns.put(id, column);
            people.addView(column, new LinearLayout.LayoutParams(dp(112), -2));
        }
        column.update(person);
    }

    private void follow() {
        if (!SocialClient.configured()) {
            startActivity(new android.content.Intent(this, AccountActivity.class));
            return;
        }
        android.widget.EditText code = new android.widget.EditText(this);
        code.setHint("친구 코드 16자리");
        code.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        new android.app.AlertDialog.Builder(this).setTitle("친구 팔로우").setView(code)
                .setNegativeButton("취소", null).setPositiveButton("팔로우", (dialog, which) -> {
                    String value = code.getText().toString().trim();
                    if (!value.matches("[a-fA-F0-9]{16}")) { message.setText("친구 코드 16자리를 확인해 주세요."); return; }
                    remote(client -> {
                        client.rpc("huggy_follow", new JSONObject().put("p_code", value));
                        client.snapshot();
                    });
                }).show();
    }

    private void showMenu(View anchor) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "계정·공유 설정");
        popup.getMenu().add(0, 2, 1, WeightHistory.todayLabel(this));
        popup.getMenu().add(0, 3, 2, "매일의 점수·몸무게");
        popup.getMenu().add(0, 4, 3, "위젯 추가");
        popup.getMenu().add(0, 6, 4, "허기 기록 수정");
        popup.getMenu().add(0, 5, 5, "허기 기록 초기화");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) startActivity(new android.content.Intent(this, AccountActivity.class));
            if (item.getItemId() == 2) startActivity(new android.content.Intent(this, WeightActivity.class));
            if (item.getItemId() == 3) startActivity(new android.content.Intent(this, DailyHistoryActivity.class));
            if (item.getItemId() == 4) {
                new android.app.AlertDialog.Builder(this).setTitle("위젯 선택")
                        .setItems(new String[]{"HUGGY 기본", "HUGGY 미니"}, (dialog, which) ->
                                pinWidget(which == 0 ? HomeWidget.class : CompactWidget.class)).show();
            }
            if (item.getItemId() == 6) startActivity(new android.content.Intent(this, RecordHistoryActivity.class));
            if (item.getItemId() == 5) {
                new android.app.AlertDialog.Builder(this).setMessage("휴대폰의 허기 기록을 초기화할까요?")
                        .setNegativeButton("취소", null).setPositiveButton("초기화", (dialog, which) -> {
                            if (getSharedPreferences("history", 0).edit().remove("points").commit()) {
                                render();
                                HomeWidget.refresh(this, "내 기록 초기화 완료", -1);
                            }
                        }).show();
            }
            return true;
        });
        popup.show();
    }

    private void pinWidget(Class<?> provider) {
        android.appwidget.AppWidgetManager manager = android.appwidget.AppWidgetManager.getInstance(this);
        if (android.os.Build.VERSION.SDK_INT >= 26 && manager.isRequestPinAppWidgetSupported()) {
            manager.requestPinAppWidget(new android.content.ComponentName(this, provider), null, null);
        } else new android.app.AlertDialog.Builder(this).setMessage("홈 화면을 길게 누르고 HUGGY 위젯을 추가하세요.")
                .setPositiveButton("확인", null).show();
    }
    @Override protected void onResume() {
        super.onResume();
        visible = true;
        handler.removeCallbacks(refresh);
        refresh.run();
    }

    @Override protected void onPause() {
        visible = false;
        recordPane.stop();
        handler.removeCallbacks(refresh);
        for (PersonColumn column : columns.values()) column.rail.stop();
        super.onPause();
    }

    private class PersonColumn extends LinearLayout {
        private final TextView name;
        private final TextView state;
        private final TextView time;
        private final Rail rail;
        private JSONObject person;

        PersonColumn(JSONObject initial) {
            super(MainActivity.this);
            setOrientation(VERTICAL);
            setPadding(dp(6), dp(16), dp(6), dp(8));
            name = text("", 15, 0xfff0f3f5);
            name.setGravity(android.view.Gravity.CENTER);
            name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            addView(name, new LinearLayout.LayoutParams(-1, dp(44)));
            rail = new Rail(initial.optBoolean("own") ? 0xff69e8c0 : 0xffb59aff);
            addView(rail, new LinearLayout.LayoutParams(-1, dp(240)));
            state = text("", 12, 0xffe2e7ec);
            state.setGravity(android.view.Gravity.CENTER);
            addView(state, new LinearLayout.LayoutParams(-1, dp(60)));
            time = text("", 10, 0xffa6b0bb);
            time.setGravity(android.view.Gravity.CENTER);
            addView(time);
            setOnClickListener(view -> {
                if (person.optBoolean("own")) return;
                new android.app.AlertDialog.Builder(MainActivity.this).setTitle(person.optString("nickname"))
                        .setMessage(state.getText() + String.valueOf((char) 10) + time.getText())
                        .setNegativeButton("닫기", null).setPositiveButton("팔로우 취소", (dialog, which) ->
                                remote(client -> {
                                    client.rpc("huggy_unfollow", new JSONObject().put("p_user", person.getString("user_id")));
                                    client.snapshot();
                                })).show();
            });
        }

        void update(JSONObject value) {
            person = value;
            name.setText(person.optString("nickname"));
            int level = person.optInt("level", 0);
            rail.moveTo(level);
            state.setText(level < 1 ? "공유된 기록 없음" : level + " · " + History.LABELS[6 - level].substring(3));
            long recorded = person.optLong("recorded_at");
            time.setText(recorded <= 0 ? "" : "마지막 기록" + (char) 10 +
                    new java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.KOREA).format(new java.util.Date(recorded)));
        }
    }

    private class Rail extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int color;
        private float position = 1;
        private int target;
        private android.animation.ValueAnimator animation;
        Rail(int color) { super(MainActivity.this); this.color = color; }

        void stop() {
            if (animation != null) animation.cancel();
            if (target > 0) position = target;
        }

        void moveTo(int value) {
            if (value == target) return;
            if (animation != null) animation.cancel();
            target = value;
            if (value < 1) { invalidate(); return; }
            float from = position;
            animation = android.animation.ValueAnimator.ofFloat(from, value);
            animation.setDuration(650);
            animation.setInterpolator(new android.view.animation.DecelerateInterpolator());
            animation.addUpdateListener(frame -> { position = (float) frame.getAnimatedValue(); invalidate(); });
            animation.start();
        }

        @Override protected void onDraw(Canvas canvas) {
            float top = dp(18), bottom = getHeight() - dp(18), center = getWidth() / 2f;
            paint.setColor(0xff303a43);
            canvas.drawRoundRect(center - dp(3), top, center + dp(3), bottom, dp(3), dp(3), paint);
            paint.setTextSize(dp(10));
            for (int level = 1; level <= 6; level++) {
                float y = bottom - (bottom - top) * (level - 1) / 5;
                paint.setColor(0xff52616b);
                canvas.drawCircle(center, y, dp(2), paint);
                paint.setColor(0xffa6b0bb);
                canvas.drawText(String.valueOf(level), dp(5), y + dp(3), paint);
            }
            if (target < 1) return;
            float y = bottom - (bottom - top) * (position - 1) / 5;
            paint.setColor(color);
            paint.setAlpha(35);
            canvas.drawCircle(center, y, dp(18), paint);
            paint.setAlpha(100);
            canvas.drawCircle(center, y, dp(12), paint);
            paint.setColor(color);
            canvas.drawCircle(center, y, dp(8), paint);
            paint.setColor(0xfff0fff9);
            canvas.drawCircle(center - dp(2), y - dp(2), dp(2), paint);
        }
    }
}
