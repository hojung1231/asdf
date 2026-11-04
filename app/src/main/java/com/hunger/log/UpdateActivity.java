package com.hunger.log;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class UpdateActivity extends BaseActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private Button action;
    private AppUpdates.Release release;
    private boolean busy;
    private boolean awaitingPermission;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(0xff101317);
        status = new TextView(this);
        status.setTextColor(0xffe2e7ec);
        status.setTextSize(18);
        root.addView(status);
        action = new Button(this);
        action.setOnClickListener(v -> { if (release == null) check(); else download(); });
        root.addView(action);
        TextView note = new TextView(this);
        note.setText("기록과 계정을 유지하며 업데이트합니다.\n설치 화면에서 업데이트를 눌러 주세요.\n처음에는 HUGGY의 ‘이 출처 허용’ 설정이 필요합니다.");
        note.setTextColor(0xffa6b0bb);
        root.addView(note);
        setContentView(root);
        check();
    }
    private void display(String message, String label, boolean enabled) {
        if (isDestroyed() || isFinishing()) return;
        status.setText(message);
        action.setText(label);
        action.setEnabled(enabled);
    }
    private void check() {
        display("새 버전을 확인하고 있습니다…", "확인 중", false);
        AppUpdates.WORK.execute(() -> {
            try {
                AppUpdates.Release found = AppUpdates.fetch(this);
                handler.post(() -> {
                    release = found.code > BuildConfig.VERSION_CODE ? found : null;
                    display(release == null ? "현재 최신 버전입니다 · " + BuildConfig.VERSION_NAME : "HUGGY " + found.name + " 업데이트가 있습니다.", release == null ? "다시 확인" : "다운로드 및 업데이트", true);
                    HomeWidget.refresh(this, "HUGGY · 업데이트 확인", -1);
                });
            } catch (Exception error) { handler.post(() -> display("확인하지 못했습니다. " + error.getMessage(), "다시 확인", true)); }
        });
    }
    private java.io.File apk() { return new java.io.File(new java.io.File(getCacheDir(), "updates"), "huggy.apk"); }

    private void download() {
        if (busy) return;
        busy = true;
        display("업데이트를 내려받고 있습니다…", "다운로드 중", false);
        AppUpdates.Release selected = release;
        AppUpdates.WORK.execute(() -> {
            try {
                java.io.File file = apk();
                if (!file.isFile() || !AppUpdates.matches(file, selected.sha)) {
                    file.getParentFile().mkdirs();
                    java.net.HttpURLConnection connection = AppUpdates.connect(selected.url);
                    try (java.io.InputStream in = connection.getInputStream(); java.io.OutputStream out = new java.io.FileOutputStream(file)) {
                        byte[] buffer = new byte[32768]; int count; long total = 0;
                        while ((count = in.read(buffer)) != -1) {
                            total += count;
                            if (total > 100 * 1024 * 1024) throw new java.io.IOException("파일 크기가 올바르지 않습니다.");
                            out.write(buffer, 0, count);
                        }
                    } finally { connection.disconnect(); }
                }
                if (!AppUpdates.matches(file, selected.sha)) { file.delete(); throw new java.io.IOException("파일 검증에 실패했습니다. 다시 시도해 주세요."); }
                android.content.pm.PackageManager pm = getPackageManager();
                android.content.pm.PackageInfo archive = pm.getPackageArchiveInfo(file.getPath(), android.content.pm.PackageManager.GET_SIGNATURES);
                android.content.pm.PackageInfo installed = pm.getPackageInfo(getPackageName(), android.content.pm.PackageManager.GET_SIGNATURES);
                if (archive == null || !getPackageName().equals(archive.packageName) || archive.versionCode != selected.code
                        || !java.util.Arrays.equals(archive.signatures, installed.signatures)) {
                    file.delete(); throw new java.io.IOException("기존 앱과 서명이 맞지 않아 설치를 중단했습니다.");
                }
                handler.post(() -> { busy = false; if (!isDestroyed() && !isFinishing()) install(); });
            } catch (Exception error) {
                handler.post(() -> { busy = false; display("업데이트 실패 · " + error.getMessage(), "다시 시도", true); });
            }
        });
    }
    private void install() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                awaitingPermission = true;
                display("설정에서 ‘이 출처 허용’을 켜고 돌아와 주세요.", "설치 다시 시도", true);
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:" + getPackageName())));
                return;
            }
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".updates", apk());
            startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
            display("설치 화면에서 업데이트를 눌러 주세요.", "설치 다시 열기", true);
        } catch (Exception error) { display("설치 화면을 열지 못했습니다. " + error.getMessage(), "다시 시도", true); }
    }
    @Override protected void onResume() {
        super.onResume();
        if (awaitingPermission) {
            awaitingPermission = false;
            if (android.os.Build.VERSION.SDK_INT >= 26 && getPackageManager().canRequestPackageInstalls()) download();
        }
    }
}
