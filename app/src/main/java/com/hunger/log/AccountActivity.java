package com.hunger.log;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;

import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;

public class AccountActivity extends Activity {
    private LinearLayout layout;
    private TextView message;
    private boolean busy;
    private Button action;
    private Button alternate;
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());

    interface Task { void run() throws Exception; }

    private void run(Task task) {
        if (busy) return;
        busy = true;
        if (action != null) action.setEnabled(false);
        if (alternate != null) alternate.setEnabled(false);
        SocialClient.EXECUTOR.execute(() -> {
            String error = null;
            try { task.run(); } catch (Exception failure) { error = failure.getMessage(); }
            final String result = error;
            main.post(() -> {
                busy = false;
                if (isFinishing() || isDestroyed()) return;
                if (action != null) action.setEnabled(true);
                if (alternate != null) alternate.setEnabled(true);
                if (result != null) message.setText(result);
            });
        });
    }

    private TextView label(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        layout.addView(view);
        return view;
    }

    private EditText field(String hint, int type) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setInputType(type);
        layout.addView(input);
        return input;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        android.widget.ScrollView page = new android.widget.ScrollView(this);
        page.addView(layout);
        setContentView(page);
        label("HUGGY 계정", 24);
        message = label("", 14);
        if (!SocialClient.configured()) {
            message.setText("친구 서버 연결을 준비 중입니다. 몸무게와 내 기록은 계속 휴대폰에 저장됩니다.");
            return;
        }
        action = new Button(this);
        action.setText("다시 확인");
        action.setOnClickListener(view -> recreate());
        layout.addView(action);
        run(() -> {
            SocialClient client = new SocialClient(this);
            if (client.signedIn()) {
                JSONObject profile = client.profile();
                SocialStore.write(this, "profile", profile);
                main.post(() -> profileForm(profile));
            } else main.post(this::loginForm);
        });
    }

    private void resetForm() {
        while (layout.getChildCount() > 2) layout.removeViewAt(2);
        action = null;
        alternate = null;
    }

    private void loginForm() {
        if (isFinishing() || isDestroyed()) return;
        resetForm();
        message.setText("닉네임으로 시작하세요. 계정은 이 기기에 보관되며 앱 삭제·데이터 삭제 시 복구할 수 없습니다.");
        EditText nickname = field("닉네임 · 24자 이내", InputType.TYPE_CLASS_TEXT);
        action = new Button(this);
        action.setText("닉네임으로 시작");
        action.setOnClickListener(view -> {
            String name = nickname.getText().toString().trim();
            if (name.isEmpty() || name.length() > 24) {
                nickname.setError("닉네임은 1~24자로 입력하세요.");
                return;
            }
            run(() -> {
                new SocialClient(this).createDeviceAccount(name);
                main.post(this::recreate);
            });
        });
        layout.addView(action);
    }
    private void profileForm(JSONObject profile) {
        if (isFinishing() || isDestroyed()) return;
        resetForm();
        message.setText("기기 계정 · 앱 삭제·데이터 삭제 시 계정이 사라집니다. 친구에게 아래 코드를 알려주세요.");
        TextView code = label("내 친구 코드" + (char) 10 + profile.optString("friend_code"), 18);
        code.setTextIsSelectable(true);
        Button share = new Button(this);
        share.setText("친구 코드 공유");
        share.setOnClickListener(view -> {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(android.content.Intent.EXTRA_TEXT, "HUGGY 친구 코드: " + profile.optString("friend_code"));
            startActivity(android.content.Intent.createChooser(intent, "친구 코드 공유"));
        });
        layout.addView(share);
        EditText name = field("닉네임 · 24자 이내", InputType.TYPE_CLASS_TEXT);
        name.setText(profile.optString("nickname"));
        CheckBox sharing = new CheckBox(this);
        sharing.setText("서로 승인한 친구에게 현재 허기 상태와 기록 시각 공유");
        sharing.setChecked(profile.optBoolean("sharing"));
        layout.addView(sharing);
        action = new Button(this);
        action.setText("설정 저장");
        action.setOnClickListener(view -> {
            String nickname = name.getText().toString().trim();
            boolean enabled = sharing.isChecked();
            if (nickname.isEmpty() || nickname.length() > 24) { name.setError("닉네임은 1~24자로 입력하세요."); return; }
            run(() -> {
                SocialClient client = new SocialClient(this);
                client.updateProfile(nickname, enabled);
                if (enabled) client.queueLatest(true);
                client.flush();
                client.snapshot();
                HomeWidget.refresh(this, "공유 설정 저장", -1);
                main.post(() -> message.setText("설정을 저장했습니다."));
            });
        });
        layout.addView(action);
        alternate = new Button(this);
        alternate.setText("기기 계정에서 나가기");
        alternate.setOnClickListener(view -> new android.app.AlertDialog.Builder(this)
                .setMessage("이 계정으로 다시 돌아올 수 없습니다. 기기 계정에서 나갈까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("나가기", (dialog, which) -> run(() -> {
                    new SocialClient(this).logout();
                    HomeWidget.refresh(this, "기기 계정 연결 해제", -1);
                    main.post(this::recreate);
                })).show());
        layout.addView(alternate);
    }
}
