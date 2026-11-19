package com.un7kai.radio;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.content.Intent;
import android.net.Uri;

public class MainActivity extends Activity {
    private LinearLayout root;
    private TextView status;
    private String selectedBand = "КВ — 40 м";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(18, 38, 66));
        getWindow().setNavigationBarColor(Color.rgb(18, 38, 66));

        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(24));
        root.setBackgroundColor(Color.rgb(242, 246, 250));
        scroll.addView(root);

        TextView title = text("UN7KAI RADIO", 26, true, Color.rgb(18, 38, 66));
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView subtitle = text("Интернет-приём радиолюбительских диапазонов", 14, false, Color.DKGRAY);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(6), 0, dp(18));
        root.addView(subtitle);

        addSection("КОРОТКИЕ ВОЛНЫ (КВ)");
        addBand("160 м  •  1,8 МГц");
        addBand("80 м  •  3,5 МГц");
        addBand("40 м  •  7 МГц");
        addBand("20 м  •  14 МГц");
        addBand("15 м  •  21 МГц");
        addBand("10 м  •  28 МГц");

        addSection("УКВ / ДЦВ");
        addBand("2 м  •  144–146 МГц");
        addBand("70 см  •  430–440 МГц");

        addSection("СПУТНИКОВЫЙ ПРИЁМ");
        addBand("Радиолюбительские спутники");

        status = text("Выбран диапазон: " + selectedBand, 15, true, Color.rgb(18, 38, 66));
        status.setPadding(0, dp(18), 0, dp(10));
        root.addView(status);

        Button open = button("Найти интернет-SDR приёмники");
        open.setOnClickListener(v -> openDirectory());
        root.addView(open);

        TextView note = text(
            "Примечание: телефон использует удалённые SDR-приёмники через интернет. " +
            "Наличие приёма зависит от диапазона, возможностей конкретного приёмника и его доступности. " +
            "КВ-приёмники KiwiSDR/WebSDR обычно не покрывают диапазоны 2 м и 70 см.",
            13, false, Color.DKGRAY);
        note.setPadding(0, dp(16), 0, 0);
        root.addView(note);

        setContentView(scroll);
    }

    private void addSection(String label) {
        TextView t = text(label, 16, true, Color.rgb(18, 38, 66));
        t.setPadding(0, dp(18), 0, dp(8));
        root.addView(t);
    }

    private void addBand(String label) {
        Button b = button(label);
        b.setOnClickListener(v -> {
            selectedBand = label;
            status.setText("Выбран диапазон: " + selectedBand);
        });
        root.addView(b);
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        p.setMargins(0, dp(4), 0, dp(4));
        b.setLayoutParams(p);
        return b;
    }

    private TextView text(String value, int size, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private void openDirectory() {
        String url = "https://www.websdr.org/";
        if (selectedBand.contains("144") || selectedBand.contains("430") || selectedBand.contains("спутники")) {
            url = "https://www.google.com/search?q=online+VHF+UHF+SDR+receiver+" +
                  Uri.encode(selectedBand);
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            status.setText("Не удалось открыть браузер. Проверьте подключение к интернету.");
        }
    }

    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
