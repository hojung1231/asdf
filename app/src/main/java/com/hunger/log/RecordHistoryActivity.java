package com.hunger.log;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import org.json.JSONArray;

public class RecordHistoryActivity extends BaseActivity {
    private ListView list;
    private JSONArray points;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        TextView title = new TextView(this);
        title.setText("허기 기록 수정");
        title.setTextSize(22);
        layout.addView(title);
        TextView help = new TextView(this);
        help.setText("기록을 누르면 허기 단계를 수정합니다. 기록 시각은 유지됩니다.");
        layout.addView(help);
        list = new ListView(this);
        TextView empty = new TextView(this);
        empty.setText("아직 저장한 허기 기록이 없습니다.");
        layout.addView(empty);
        list.setEmptyView(empty);
        layout.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        list.setOnItemClickListener((parent, view, row, id) -> {
            int index = points.length() - 1 - row;
            JSONArray point = points.optJSONArray(index);
            long time = point.optLong(0);
            int[] selected = {point.optInt(1)};
            String titleText = new java.text.SimpleDateFormat("MM/dd HH:mm 기록", java.util.Locale.KOREA)
                    .format(new java.util.Date(time));
            new android.app.AlertDialog.Builder(this).setTitle(titleText)
                    .setSingleChoiceItems(History.LABELS, selected[0], (dialog, which) -> selected[0] = which)
                    .setNegativeButton("취소", null)
                    .setPositiveButton("수정 저장", (dialog, which) -> {
                        boolean saved = History.edit(this, index, time, selected[0]);
                        android.widget.Toast.makeText(this, saved ? "기록을 수정했습니다." : "기록이 바뀌었거나 저장하지 못했습니다. 다시 선택해 주세요.",
                                android.widget.Toast.LENGTH_SHORT).show();
                        if (saved) HomeWidget.refresh(this, "허기 기록 수정", -1);
                        reload();
                    }).show();
        });
        setContentView(layout);
    }

    @Override protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        points = History.read(this);
        java.util.List<String> labels = new java.util.ArrayList<>();
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.KOREA);
        for (int i = points.length() - 1; i >= 0; i--) {
            JSONArray point = points.optJSONArray(i);
            labels.add(format.format(new java.util.Date(point.optLong(0))) + (char) 10
                    + History.LABELS[point.optInt(1)]);
        }
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels));
    }
}
