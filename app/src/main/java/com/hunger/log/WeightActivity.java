package com.hunger.log;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class WeightActivity extends BaseActivity {
    private final java.util.Calendar date = java.util.Calendar.getInstance();
    private Button dateButton;
    private EditText input;
    private TextView message;

    private void updateDate() {
        dateButton.setText(WeightHistory.dateKey(date.getTimeInMillis()) + " · 날짜 변경");
        input.setText(WeightHistory.read(this).optString(WeightHistory.dateKey(date.getTimeInMillis()), ""));
        input.setSelection(input.length());
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        date.setTimeInMillis(getIntent().getLongExtra("date", System.currentTimeMillis()));
        if (state != null) date.setTimeInMillis(state.getLong("date", date.getTimeInMillis()));
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        TextView title = new TextView(this);
        title.setText("몸무게 기록 · kg");
        title.setTextSize(20);
        layout.addView(title);
        dateButton = new Button(this);
        dateButton.setOnClickListener(view -> {
            DatePickerDialog picker = new DatePickerDialog(this, (calendar, year, month, day) -> {
                date.set(year, month, day);
                updateDate();
            }, date.get(java.util.Calendar.YEAR), date.get(java.util.Calendar.MONTH),
                    date.get(java.util.Calendar.DAY_OF_MONTH));
            picker.getDatePicker().setMaxDate(System.currentTimeMillis());
            picker.show();
        });
        layout.addView(dateButton);
        input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("예: 72.5");
        input.setContentDescription("몸무게 킬로그램 입력");
        layout.addView(input);
        message = new TextView(this);
        message.setText("같은 날짜에 다시 저장하면 그날 몸무게를 수정합니다.");
        layout.addView(message);
        Button save = new Button(this);
        save.setText("저장");
        save.setOnClickListener(view -> {
            String value = WeightHistory.normalize(input.getText().toString());
            if (value == null) {
                input.setError("0보다 크고 1000 미만인 몸무게를 소수점 두 자리까지 입력하세요.");
                return;
            }
            if (WeightHistory.save(this, date.getTimeInMillis(), value)) {
                HomeWidget.refresh(this, "몸무게 " + value + " kg 저장", -1);
                finish();
            } else message.setText("저장하지 못했습니다. 다시 시도해 주세요.");
        });
        layout.addView(save);
        Button cancel = new Button(this);
        cancel.setText("취소");
        cancel.setOnClickListener(view -> finish());
        layout.addView(cancel);
        setContentView(layout);
        updateDate();
        if (state != null) input.setText(state.getString("input", ""));
        input.requestFocus();
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong("date", date.getTimeInMillis());
        state.putString("input", input.getText().toString());
        super.onSaveInstanceState(state);
    }
}
