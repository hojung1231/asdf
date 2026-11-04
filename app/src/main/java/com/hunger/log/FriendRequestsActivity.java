package com.hunger.log;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

public class FriendRequestsActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout rows;
    private TextView status;
    private boolean visible, loading;
    private final Runnable refresh = new Runnable() {
        @Override public void run() { fetch(null); handler.postDelayed(this, 15000); }
    };
    interface Action { void run(SocialClient client) throws Exception; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff101317);
        getWindow().setNavigationBarColor(0xff101317);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xff101317);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        root.setPadding(padding, padding, padding, padding);
        Button back = new Button(this);
        back.setText("‹ 함께 보기");
        back.setOnClickListener(v -> finish());
        root.addView(back);
        root.addView(label("친구 요청", 24));
        root.addView(label("승인하면 서로 친구가 됩니다. 현재 상태는 공유를 켠 친구끼리 볼 수 있습니다.", 14));
        status = label("불러오는 중…", 13);
        root.addView(status);
        Button retry = new Button(this);
        retry.setText("새로고침");
        retry.setOnClickListener(v -> fetch(null));
        root.addView(retry);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }
    private TextView label(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(0xffe2e7ec);
        view.setPadding(0, 12, 0, 12);
        return view;
    }
    private void fetch(Action action) {
        if (loading) return;
        loading = true;
        enableActions(rows, false);
        status.setText(action == null ? "요청을 확인하고 있습니다…" : "처리 중…");
        SocialClient.EXECUTOR.execute(() -> {
            JSONObject snapshot = null;
            String error = null;
            try {
                SocialClient client = new SocialClient(this);
                if (!client.signedIn()) throw new java.io.IOException("계정 메뉴에서 닉네임으로 먼저 시작해 주세요.");
                if (action != null) action.run(client);
                snapshot = client.friendshipSnapshot();
            } catch (Exception failure) { error = failure.getMessage(); }
            JSONObject result = snapshot;
            String failure = error;
            handler.post(() -> {
                loading = false;
                if (!visible || isFinishing() || isDestroyed()) return;
                enableActions(rows, true);
                if (failure != null) { status.setText("연결 확인 필요 · " + failure); return; }
                status.setText("약 15초마다 확인 · 승인 후 서로의 목록에 등록됩니다.");
                render(result);
                HomeWidget.refresh(this, "HUGGY · 친구 갱신", -1);
            });
        });
    }
    private void enableActions(android.view.View view, boolean enabled) {
        if (view instanceof Button) view.setEnabled(enabled);
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) enableActions(group.getChildAt(i), enabled);
        }
    }
    private void render(JSONObject snapshot) {
        rows.removeAllViews();
        JSONArray requests = snapshot.optJSONArray("requests");
        int count = requests == null ? 0 : requests.length();
        for (boolean incoming : new boolean[]{true, false}) {
            rows.addView(label(incoming ? "받은 요청" : "보낸 요청 · 승인 대기", 18));
            int shown = 0;
            for (int i = 0; i < count; i++) {
                JSONObject request = requests.optJSONObject(i);
                if (request == null || request.optBoolean("incoming") != incoming) continue;
                shown++;
                String user = request.optString("user_id");
                rows.addView(label(request.optString("nickname"), 17));
                LinearLayout actions = new LinearLayout(this);
                if (incoming) {
                    Button accept = new Button(this);
                    accept.setText("승인");
                    accept.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff69e8c0));
                    accept.setOnClickListener(v -> fetch(client -> client.respondFriend(user, true)));
                    actions.addView(accept, new LinearLayout.LayoutParams(0, -2, 1));
                }
                Button decline = new Button(this);
                decline.setText(incoming ? "거절" : "요청 취소");
                decline.setOnClickListener(v -> fetch(client -> {
                    if (incoming) client.respondFriend(user, false);
                    else client.rpc("huggy_unfollow", new JSONObject().put("p_user", user));
                }));
                actions.addView(decline, new LinearLayout.LayoutParams(0, -2, 1));
                rows.addView(actions);
            }
            if (shown == 0) rows.addView(label("요청이 없습니다.", 14));
        }
    }
    @Override protected void onResume() { super.onResume(); visible = true; handler.removeCallbacks(refresh); refresh.run(); }
    @Override protected void onPause() { visible = false; handler.removeCallbacks(refresh); super.onPause(); }
}
