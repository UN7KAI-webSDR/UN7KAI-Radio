package com.un7kai.radio;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.Response;
import android.webkit.WebView;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.app.Activity;
import android.os.Bundle;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Paint;
import android.graphics.Canvas;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MainActivity extends Activity {

    private final int BG = Color.rgb(5, 7, 9);
    private final int PANEL = Color.rgb(25, 29, 33);
    private final int GREEN = Color.rgb(0, 245, 55);
    private final int CYAN = Color.rgb(0, 225, 235);
    private final int MUTED = Color.rgb(145, 151, 158);

    private LinearLayout root;
    private WebView sdrWebView;
    private OkHttpClient kiwiClient;
private WebSocket kiwiSocket;
private boolean kiwiConnected = false;
    private String selectedServerUrl = "";
    private TextView frequency;
    private TextView bandLabel;
    private TextView status;
    private TextView modeLabel;
    private TextView stepLabel;

    private String selectedBand = "40 м";
    private String selectedMode = "LSB";

    private int step = 100;
    private long frequencyHz = 7129100L;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        );

        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        // Основная горизонтальная компоновка
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(dp(8), dp(6), dp(8), dp(6));
        root.setBackgroundColor(BG);
       // ЛЕВАЯ ПАНЕЛЬ
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER);
        root.addView(left, new LinearLayout.LayoutParams(
            0, -1, 1.2f));

        TextView brand = text("UN7KAI", 23, true,
            Color.rgb(245, 225, 60));
        left.addView(brand);
        left.addView(text("SDR RADIO", 13, true, CYAN));

        addTitle(left, "РЕЖИМ");

        String[] modes = {"LSB", "USB", "CW", "AM", "FM"};

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        left.addView(modeRow);

        for (String m : modes) {
            Button b = button(m);
            modeRow.addView(b,
                new LinearLayout.LayoutParams(0, dp(42), 1));
            b.setOnClickListener(v -> {
                selectedMode = m;
                modeLabel.setText("Режим: " + m);
                bandLabel.setText(selectedBand + " • " + m);
            });
        }

        addTitle(left, "ДИАПАЗОНЫ");

        String[] bands = {
            "160 м", "80 м", "40 м", "20 м",
            "15 м", "10 м", "2 м", "70 см"
        };

        for (int i = 0; i < bands.length; i += 4) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            left.addView(row);

            for (int j = i; j < i + 4; j++) {
                final String band = bands[j];
                Button b = button(band);
                row.addView(b,
                    new LinearLayout.LayoutParams(0, dp(42), 1));
                b.setOnClickListener(v -> selectBand(band));
            }
        }
    // ЦЕНТРАЛЬНАЯ ПАНЕЛЬ
        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        root.addView(center,
            new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 2.5f));

        frequency = text("7.129.100", 34, true, GREEN);
        frequency.setGravity(Gravity.CENTER);
        center.addView(frequency);

        bandLabel = text("40 м • LSB", 14, true, CYAN);
        center.addView(bandLabel);

        TextView spectrum = text(
            "〰  SPECTRUM / WATERFALL  〰",
            15, true, GREEN);
        spectrum.setGravity(Gravity.CENTER);
        spectrum.setBackground(panelBackground(
            Color.rgb(10, 22, 25), 8, CYAN));
        LinearLayout.LayoutParams sp =
            new LinearLayout.LayoutParams(
                -1, 0, 1);
        sp.setMargins(dp(4), dp(8), dp(4), dp(8));
        center.addView(spectrum, sp);

        DialView knob = new DialView(this);
        center.addView(knob,
            new LinearLayout.LayoutParams(dp(145), dp(145)));

        stepLabel = text("STEP: 100 Hz", 13, true, CYAN);
        center.addView(stepLabel);

        modeLabel = text("Режим: LSB", 12, true, GREEN);
        center.addView(modeLabel);
       knob.setOnTouchListener(new View.OnTouchListener() {
            float lastAngle;
            float accumulated;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                float x = e.getX() - v.getWidth() / 2f;
                float y = e.getY() - v.getHeight() / 2f;
                float angle = (float) Math.toDegrees(
                    Math.atan2(y, x));

                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    lastAngle = angle;
                    accumulated = 0;
                    return true;
                }

                if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    float delta = angle - lastAngle;

                    if (delta > 180) delta -= 360;
                    if (delta < -180) delta += 360;

                    lastAngle = angle;
                    accumulated += delta;

                    v.setRotation(v.getRotation() + delta);

                    int ticks = (int)(accumulated / 5f);

                    if (ticks != 0) {
                        changeFrequency((long) ticks * step);
                        accumulated -= ticks * 5f;
                    }
                    return true;
                }
                return true;
            }
        }); 
        // ПРАВАЯ ПАНЕЛЬ
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER);
        root.addView(right,
            new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1.1f));

        addTitle(right, "НАСТРОЙКИ");

        Button stepButton = button("STEP");
        right.addView(stepButton, buttonParams());
        stepButton.setOnClickListener(v -> cycleStep());

        Button bwButton = button("BW FILTER");
        right.addView(bwButton, buttonParams());
        bwButton.setOnClickListener(v ->
            showMessage("Настройка BW"));

        Button sqlButton = button("SQL");
        right.addView(sqlButton, buttonParams());
        sqlButton.setOnClickListener(v ->
            showMessage("Настройка SQL"));

        Button serverButton = button("SDR СЕРВЕРА");
        right.addView(serverButton, buttonParams());
        serverButton.setOnClickListener(v -> showServerList());

        Button mapButton = button("MAP");
        right.addView(mapButton, buttonParams());
        mapButton.setOnClickListener(v ->
            showMessage("Карта SDR"));

        status = text("UN7KAI READY", 12, true, GREEN);
        right.addView(status);

        // ЗАВЕРШЕНИЕ КОМПОНОВКИ
        setContentView(root);
    }
    private void addTitle(LinearLayout layout, String title) {
        TextView t = text(title, 12, true, CYAN);
        t.setGravity(Gravity.CENTER);
        layout.addView(t);
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(11);
        b.setTextColor(GREEN);
        b.setAllCaps(false);
        b.setPadding(2, 0, 2, 0);
        b.setBackground(panelBackground(PANEL, 9, CYAN));
        return b;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams p =
            new LinearLayout.LayoutParams(-1, dp(42));
        p.setMargins(dp(2), dp(2), dp(2), dp(2));
        return p;
    }

    private TextView text(String value, int size,
                          boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private GradientDrawable panelBackground(
            int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private int dp(int value) {
        return (int)(value *
            getResources().getDisplayMetrics().density + 0.5f);
    }

    private void changeFrequency(long delta) {
        frequencyHz = Math.max(1000, frequencyHz + delta);
        updateFrequency();
    }

    private void updateFrequency() {
        String raw = String.format(
            java.util.Locale.US, "%,d", frequencyHz)
            .replace(',', '.');
        frequency.setText(raw);
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
        bandLabel.setText(selectedBand + " • " + selectedMode);
        status.setText("UN7KAI • " + selectedBand);
    }

    private void cycleStep() {
        if (step == 100) step = 500;
        else if (step == 500) step = 1000;
        else step = 100;

        stepLabel.setText("STEP: " + step + " Hz");
    }

    private void showMessage(String message) {
        new android.app.AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show();
    }

    private void showServerList() {

    new Thread(() -> {
        HttpURLConnection connection = null;

        try {
            URL url = new URL(
                "https://cdn.aethersdr.com/kiwi.json"
            );

            connection = (HttpURLConnection)
                url.openConnection();

            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                    connection.getInputStream()
                )
            );

            StringBuilder json = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                json.append(line);
            }

            reader.close();

            JSONObject data =
                new JSONObject(json.toString());

            if (data.optInt("schema", -1) != 1) {
                throw new Exception("Неизвестная версия каталога");
            }

            JSONArray all = data.getJSONArray("receivers");
            java.util.ArrayList<JSONObject> allowed =
                new java.util.ArrayList<>();

            for (int i = 0; i < all.length(); i++) {
                JSONObject s = all.getJSONObject(i);

                if (!s.has("ext_api") ||
                    s.optInt("ext_api", 0) <= 0 ||
                    s.optBoolean("offline", true) ||
                    s.optBoolean("flagged", true) ||
                    s.optString("url", "").isEmpty()) {
                    continue;
                }

                allowed.add(s);
            }

            runOnUiThread(() -> {
                if (allowed.isEmpty()) {
                    showMessage("Разрешённых серверов пока нет");
                    return;
                }

                String[] names = new String[allowed.size()];

                for (int i = 0; i < allowed.size(); i++) {
                    JSONObject s = allowed.get(i);

                    names[i] =
                        s.optString("name", "KiwiSDR") +
                        "\n" +
                        s.optString("loc", "Место не указано") +
                        " | Пользователи: " +
                        s.optInt("users", 0) + "/" +
                        s.optInt("users_max", 0);
                }

                new android.app.AlertDialog.Builder(this)
                    .setTitle("KIWISDR — " + allowed.size() +
                              " разрешённых серверов")
                    .setItems(names, (dialog, which) -> {
    JSONObject selected = allowed.get(which);

    String serverUrl = selected.optString("url", "");

    status.setText(
        "SDR: " + selected.optString("name", "KiwiSDR")
    );

    showMessage("Выбран сервер:\n" + serverUrl);
})
                    .setNegativeButton("Закрыть", null)
                    .show();
            });

        } catch (Exception e) {
            runOnUiThread(() ->
                showMessage("Ошибка каталога: " + e.getMessage())
            );

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

    }).start();
}

private void openSdrTest() {

    if (sdrWebView == null) {
        sdrWebView = new WebView(this);

        WebSettings settings = sdrWebView.getSettings();
        settings.setJavaScriptEnabled(true);
       settings.setUserAgentString(
    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
); 
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        sdrWebView.setWebChromeClient(
            new WebChromeClient()
        );

        root.addView(sdrWebView,
    new LinearLayout.LayoutParams(
        -1, -1
    )
);
    }

    sdrWebView.loadUrl(
        "http://sdr.22dx.ru:8901/"
    );

    status.setText("SDR БАРНАУЛ ЗАГРУЖЕН");
}
    private class DialView extends View {
        private final Paint paint = new Paint(
            Paint.ANTI_ALIAS_FLAG);

        DialView(android.content.Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float r = Math.min(cx, cy) * 0.95f;

            paint.setShader(new RadialGradient(
                cx, cy, r,
                new int[]{
                    Color.LTGRAY,
                    Color.DKGRAY,
                    Color.BLACK
                },
                null, Shader.TileMode.CLAMP));

            canvas.drawCircle(cx, cy, r, paint);
            paint.setShader(null);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.LTGRAY);

            for (int i = 0; i < 72; i++) {
                canvas.save();
                canvas.rotate(i * 5, cx, cy);
                canvas.drawLine(
                    cx, cy - r + dp(3),
                    cx, cy - r + dp(13), paint);
                canvas.restore();
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(30, 32, 35));
            canvas.drawCircle(cx, cy, r - dp(17), paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(5));
            paint.setColor(Color.LTGRAY);
            canvas.drawCircle(cx, cy, r - dp(20), paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(
                cx - dp(12), cy - dp(12), r * 0.65f,
                new int[]{
                    Color.rgb(100, 103, 108),
                    Color.rgb(35, 37, 40),
                    Color.BLACK
                },
                null, Shader.TileMode.CLAMP));

            canvas.drawCircle(cx, cy, r - dp(27), paint);
            paint.setShader(null);

            paint.setColor(Color.rgb(65, 68, 72));
            canvas.drawCircle(cx, cy, r * 0.36f, paint);

           paint.setColor(Color.rgb(15, 17, 20));
            canvas.drawCircle(cx, cy, r * 0.30f, paint);
        }
    }
}
