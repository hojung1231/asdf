package com.hunger.log;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
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
        run(() -> {
            SocialClient client = new SocialClient(this);
            if (client.signedIn()) {
                JSONObject profile = client.profile();
                SocialStore.write(this, "profile", profile);
                main.post(() -> profileForm(profile));
            } else main.post(this::loginForm);
        });
    }

    private void loginForm() {
        if (isFinishing() || isDestroyed()) return;
        message.setText("이메일로 가입한 뒤 인증 메일을 확인하고 로그인하세요.");
        EditText email = field("이메일", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText password = field("비밀번호 · 8자 이상", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        action = new Button(this);
        action.setText("로그인");
        alternate = new Button(this);
        alternate.setText("회원가입");
        View.OnClickListener authenticate = view -> {
            boolean register = view == alternate;
            String secret = password.getText().toString();
            String address = email.getText().toString().trim();
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()) {
                email.setError("이메일 주소를 확인해 주세요."); return;
            }
            if (secret.length() < 8) { password.setError("8자 이상 입력하세요."); return; }
            run(() -> {
                SocialClient client = new SocialClient(this);
                if (client.authenticate(address, secret, register)) {
                    JSONObject profile = client.profile();
                    SocialStore.write(this, "profile", profile);
                    main.post(() -> { recreate(); });
                } else main.post(() -> {
                    password.setText("");
                    message.setText("인증 메일을 확인한 뒤 로그인하세요. 이미 가입했다면 로그인을 선택하세요.");
                });
            });
        };
        action.setOnClickListener(authenticate);
        alternate.setOnClickListener(authenticate);
        layout.addView(action);
        layout.addView(alternate);
    }

    private void profileForm(JSONObject profile) {
        if (isFinishing() || isDestroyed()) return;
        message.setText("친구에게 아래 코드를 알려주세요.");
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
        sharing.setText("내 코드를 알고 팔로우한 사람에게 현재 허기 상태와 기록 시각 공유");
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
        alternate.setText("로그아웃");
        alternate.setOnClickListener(view -> run(() -> {
            new SocialClient(this).logout();
            HomeWidget.refresh(this, "로그아웃", -1);
            main.post(this::recreate);
        }));
        layout.addView(alternate);
    }
}
