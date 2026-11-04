package com.hunger.log;

/** Keep custom screens outside system bars, cutouts and the keyboard on Android 15+. */
public class BaseActivity extends android.app.Activity {
    @Override protected void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        if (android.os.Build.VERSION.SDK_INT < 35) return;
        android.content.res.TypedArray theme = obtainStyledAttributes(new int[]{android.R.attr.windowIsFloating});
        boolean floating = theme.getBoolean(0, false);
        theme.recycle();
        if (floating) return;
        getWindow().getDecorView().setBackgroundColor(0xff101317);
        android.view.View content = findViewById(android.R.id.content);
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                    | android.view.WindowInsets.Type.displayCutout());
            android.graphics.Insets keyboard = insets.getInsets(android.view.WindowInsets.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));
            return android.view.WindowInsets.CONSUMED;
        });
        content.requestApplyInsets();
    }
}
