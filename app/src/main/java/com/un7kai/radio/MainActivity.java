package com.un7kai.radio;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.webkit.WebView;
import android.webkit.WebChromeClient;

public class MainActivity extends Activity {
    private final int BG = Color.rgb(5, 7, 9);
    private final int PANEL = Color.rgb(25, 29, 33);
    private final int GREEN = Color.rgb(0, 245, 55);
    private final int CYAN = Color.rgb(0, 225, 235);
    private final int MUTED = Color.rgb(145, 151, 158);

    private LinearLayout root;
    private TextView frequency;
    private TextView bandLabel;
    private TextView status;
    private TextView modeLabel;
    private TextView stepLabel;
    private TextView sMeter;
    private String selectedBand = "40 м";
    private String selectedMode = "LSB";
    private int step = 100;
    private long frequencyHz = 7129100L;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(16));
        root.setBackgroundColor(BG);
        scroll.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        TextView brand = text("UN7KAI", 18, true, Color.rgb(245, 225, 60));
        TextView remote = text("  RADIO  •  WEB SDR", 12, true, CYAN);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(remote);
        root.addView(header);

        frequency = text("7.129.100", 48, true, GREEN);
        frequency.setGravity(Gravity.CENTER);
        frequency.setPadding(0, dp(14), 0, dp(2));
        frequency.setSingleLine(true);
        frequency.setTextScaleX(0.94f);
        root.addView(frequency);

        bandLabel = text("40 METERS     •     LSB", 14, true, CYAN);
        bandLabel.setGravity(Gravity.CENTER);
        bandLabel.setPadding(0, 0, 0, dp(12));
        root.addView(bandLabel);

        addSectionTitle("S-METER");
        sMeter = text("▰ ▰ ▰ ▰ ▰ ▰ ▰ ▰ ▰ ▰ ▰ ▰ ▰ ▰ ▰", 17, true, GREEN);
        sMeter.setGravity(Gravity.CENTER);
        sMeter.setPadding(0, dp(4), 0, dp(12));
        root.addView(sMeter);

        addSectionTitle("ПРИЁМНИК / SDR");
        TextView spectrum = text("Спектр и водопад появятся при подключении\nк SDR-сервису с передачей этих данных.", 13, false, MUTED);
        spectrum.setGravity(Gravity.CENTER);
        spectrum.setBackground(panelBackground(Color.rgb(13, 21, 25), 10, Color.rgb(45, 65, 68)));
        spectrum.setPadding(dp(10), dp(22), dp(10), dp(22));
        root.addView(spectrum, matchWrap());

        LinearLayout tuneRow = new LinearLayout(this);
        tuneRow.setGravity(Gravity.CENTER);
        tuneRow.setPadding(0, dp(18), 0, dp(14));
        Button down = button("−\nDOWN", 15, PANEL, Color.WHITE);
        Button knob = button("◉\nTUNING", 20, Color.rgb(37, 40, 44), GREEN);
        Button up = button("+\nUP", 15, PANEL, Color.WHITE);
        tuneRow.addView(down, new LinearLayout.LayoutParams(0, dp(112), 1));
        tuneRow.addView(knob, new LinearLayout.LayoutParams(0, dp(132), 1.25f));
        tuneRow.addView(up, new LinearLayout.LayoutParams(0, dp(112), 1));
        root.addView(tuneRow);
        down.setOnClickListener(v -> changeFrequency(-step));
        up.setOnClickListener(v -> changeFrequency(step));
        knob.setOnClickListener(v -> changeFrequency(step));

        addSectionTitle("РЕЖИМ РАБОТЫ");
        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        String[] modeNames = {"LSB", "USB", "CW", "AM", "FM"};
        for (String m : modeNames) {
            Button b = smallButton(m);
            modes.addView(b, new LinearLayout.LayoutParams(0, dp(46), 1));
            b.setOnClickListener(v -> {
                selectedMode = m;
                updateLabels();
            });
        }
        root.addView(modes);
        modeLabel = text("Режим: LSB", 13, true, GREEN);
        modeLabel.setPadding(0, dp(6), 0, dp(4));
        root.addView(modeLabel);

        addSectionTitle("РАДИОЛЮБИТЕЛЬСКИЕ ДИАПАЗОНЫ");
        String[][] bands = {
            {"160 м", "1.8 MHz"}, {"80 м", "3.5 MHz"}, {"40 м", "7 MHz"},
            {"20 м", "14 MHz"}, {"15 м", "21 MHz"}, {"10 м", "28 MHz"},
            {"2 м", "144 MHz"}, {"70 см", "430 MHz"}
        };
        LinearLayout bandGrid = new LinearLayout(this);
        bandGrid.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < bands.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = i; j < Math.min(i + 2, bands.length); j++) {
                final String band = bands[j][0];
                Button b = smallButton(bands[j][0] + "\n" + bands[j][1]);
                row.addView(b, new LinearLayout.LayoutParams(0, dp(58), 1));
                b.setOnClickListener(v -> selectBand(band));
            }
            bandGrid.addView(row);
        }
        root.addView(bandGrid);

        addSectionTitle("НАСТРОЙКИ");
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        Button stepButton = smallButton("STEP\n100 Hz");
        Button bwButton = smallButton("BW\nФИЛЬТР");
        Button sqlButton = smallButton("SQL\nШУМОПОДАВЛЕНИЕ");
        controls.addView(stepButton, new LinearLayout.LayoutParams(0, dp(58), 1));
        controls.addView(bwButton, new LinearLayout.LayoutParams(0, dp(58), 1));
        controls.addView(sqlButton, new LinearLayout.LayoutParams(0, dp(58), 1));
        root.addView(controls);
        stepLabel = text("Шаг настройки: 100 Hz", 13, false, MUTED);
        stepLabel.setPadding(0, dp(6), 0, dp(2));
        root.addView(stepLabel);
        stepButton.setOnClickListener(v -> cycleStep());
        bwButton.setOnClickListener(v -> showMessage("Полоса пропускания настраивается на стороне SDR-сервиса."));
        sqlButton.setOnClickListener(v -> showMessage("SQL доступен, если его поддерживает выбранный SDR-сервис."));

        Button open = button("НАЙТИ ИНТЕРНЕТ-SDR ПРИЁМНИКИ", 15, Color.rgb(20, 105, 40), Color.WHITE);
        LinearLayout.LayoutParams openParams = matchWrap();
        openParams.topMargin = dp(14);
        root.addView(open, openParams);
        open.setOnClickListener(v -> openDirectory());

        status = text("Выбран диапазон: " + selectedBand, 13, true, CYAN);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(12), 0, dp(4));
        root.addView(status);

        TextView note = text("Телефон принимает сигнал через удалённые интернет-SDR.\nS-метр, спектр, водопад и точная настройка зависят от возможностей выбранного сервиса.", 12, false, MUTED);
        note.setGravity(Gravity.CENTER);
        note.setPadding(dp(8), dp(10), dp(8), dp(4));
        root.addView(note);

        setContentView(scroll);
    }

    private void addSectionTitle(String label) {
        TextView t = text(label, 12, true, CYAN);
        t.setPadding(dp(2), dp(13), 0, dp(6));
        root.addView(t);
    }

    private void selectBand(String band) {
        selectedBand = band;
        if (band.equals("160 м")) frequencyHz = 1900000;
        else if (band.equals("80 м")) frequencyHz = 3700000;
        else if (band.equals("40 м")) frequencyHz = 7129100;
        else if (band.equals("20 м")) frequencyHz = 14150000;
        else if (band.equals("15 м")) frequencyHz = 21200000;
        else if (band.equals("10 м")) frequencyHz = 28400000;
        else if (band.equals("2 м")) frequencyHz = 145500000;
        else if (band.equals("70 см")) frequencyHz = 433500000;
        updateFrequency();
        updateLabels();
        status.setText("Выбран диапазон: " + selectedBand);
    }

    private void changeFrequency(long delta) {
        frequencyHz = Math.max(1000, frequencyHz + delta);
        updateFrequency();
    }

    private void updateFrequency() {
        String raw = String.format(java.util.Locale.US, "%,d", frequencyHz).replace(',', '.');
        frequency.setText(raw);
    }

    private void updateLabels() {
        bandLabel.setText(selectedBand.toUpperCase() + "     •     " + selectedMode);
        modeLabel.setText("Режим: " + selectedMode);
    }

    private void cycleStep() {
        if (step == 100) step = 500;
        else if (step == 500) step = 1000;
        else step = 100;
        stepLabel.setText("Шаг настройки: " + step + " Hz");
    }

    private void showMessage(String message) {
        new android.app.AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show();
    }

    private Button button(String label, int size, int background, int foreground) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(size);
        b.setAllCaps(false);
        b.setTextColor(foreground);
        b.setGravity(Gravity.CENTER);
        b.setBackground(panelBackground(background, 14, Color.rgb(75, 80, 85)));
        b.setPadding(dp(4), dp(4), dp(4), dp(4));
        return b;
    }

    private Button smallButton(String label) {
        Button b = button(label, 13, PANEL, GREEN);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1);
        p.setMargins(dp(2), dp(2), dp(2), dp(2));
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

    private GradientDrawable panelBackground(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
    }
private void openDirectory() {
    LinearLayout container = new LinearLayout(this);
    container.setOrientation(LinearLayout.VERTICAL);
    container.setBackgroundColor(BG);

    WebView webView = new WebView(this);
    webView.setBackgroundColor(BG);
    webView.getSettings().setJavaScriptEnabled(true);
    webView.getSettings().setDomStorageEnabled(true);
    webView.setWebChromeClient(new WebChromeClient());

    webView.loadUrl("http://sdr.22dx.ru:8901");

    container.addView(webView, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
    ));

    setContentView(container);
}
   private void openDirectory() {
    LinearLayout container = new LinearLayout(this);
    container.setOrientation(LinearLayout.VERTICAL);
    container.setBackgroundColor(BG);

    WebView webView = new WebView(this);
    webView.setBackgroundColor(BG);
    webView.getSettings().setJavaScriptEnabled(true);
    webView.getSettings().setDomStorageEnabled(true);
    webView.setWebChromeClient(new WebChromeClient());

    webView.loadUrl("http://sdr.22dx.ru:8901");

    container.addView(webView, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
    ));

    setContentView(container);
}
    

    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
