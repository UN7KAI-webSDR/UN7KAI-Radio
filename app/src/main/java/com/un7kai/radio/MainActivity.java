package com.un7kai.radio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {

    private static final int BG = Color.rgb(5, 7, 9);
    private static final int PANEL = Color.rgb(20, 25, 29);
    private static final int PANEL_LIGHT = Color.rgb(34, 41, 46);
    private static final int GREEN = Color.rgb(0, 245, 75);
    private static final int CYAN = Color.rgb(0, 220, 235);
    private static final int WHITE = Color.rgb(225, 232, 235);

    private static final String DEFAULT_SERVER = "http://tulasdr.fvds.ru:8901/";

    private WebSdrEngine webSdrEngine;
    private TextView frequencyText;
    private TextView modeText;
    private TextView statusText;
    private TextView serverText;
    private TextView qthText;
    private TextView distanceText;
    private TextView stepText;
    private DialView dialView;

    private String selectedServerUrl = DEFAULT_SERVER;
    private String selectedMode = "LSB";
    private String userLocator = "LO";

/*
 * Classic WebSDR servers do not publish one universal machine-readable
 * locator field. Enter the selected server locator when prompted.
 */
    private String serverLocator = "";

    private long frequencyHz = 7129100L;
    private long stepHz = 100L;
    private boolean radioOn = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        buildInterface();
        connectSelectedServer();
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(4), dp(4), dp(4), dp(4));
        setContentView(root);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER);
        left.setBackgroundColor(PANEL);
        root.addView(left, new LinearLayout.LayoutParams(dp(82), -1));

        addLabel(left, "UN7KAI", GREEN, 17);
        addLabel(left, "RECEIVER", CYAN, 9);
        addLabel(left, "MODE", CYAN, 10);

        for (final String mode : new String[]{"LSB", "USB", "CW", "AM", "FM"}) {
            addButton(left, mode, v -> setMode(mode), GREEN, 0);
        }

        addLabel(left, "BANDS", CYAN, 10);
        String[][] bands = {
                {"160m", "1900000"}, {"80m", "3700000"},
                {"40m", "7129100"}, {"20m", "14200000"},
                {"15m", "21200000"}, {"10m", "28400000"},
                {"2m", "145500000"}, {"70cm", "433500000"}
        };
        for (final String[] band : bands) {
            addButton(left, band[0],
                    v -> setFrequency(Long.parseLong(band[1])), CYAN, 0);
        }

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setBackgroundColor(BG);
        LinearLayout.LayoutParams centerParams =
                new LinearLayout.LayoutParams(0, -1, 1f);
        centerParams.setMargins(dp(4), 0, dp(4), 0);
        root.addView(center, centerParams);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(Color.BLACK);
        center.addView(header, new LinearLayout.LayoutParams(-1, dp(65)));

        LinearLayout readout = new LinearLayout(this);
        readout.setOrientation(LinearLayout.VERTICAL);
        readout.setGravity(Gravity.CENTER);
        header.addView(readout, new LinearLayout.LayoutParams(0, -1, 1f));

        frequencyText = new TextView(this);
        frequencyText.setTextColor(GREEN);
        frequencyText.setTextSize(27);
        frequencyText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        frequencyText.setGravity(Gravity.CENTER);
        readout.addView(frequencyText);

        modeText = new TextView(this);
        modeText.setTextColor(CYAN);
        modeText.setTextSize(11);
        modeText.setGravity(Gravity.CENTER);
        readout.addView(modeText);
        refreshReadout();

        LinearLayout headerButtons = new LinearLayout(this);
        headerButtons.setOrientation(LinearLayout.VERTICAL);
        headerButtons.setGravity(Gravity.CENTER);
        header.addView(headerButtons, new LinearLayout.LayoutParams(dp(92), -1));
        addButton(headerButtons, "QTH LOCATOR", v -> showQthDialog(), CYAN, dp(27));
        addButton(headerButtons, "SERVER QTH", v -> showServerLocatorDialog(), GREEN, dp(27));

        webSdrEngine = new WebSdrEngine(this);
        webSdrEngine.setBackgroundColor(Color.BLACK);
        center.addView(webSdrEngine, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setBackgroundColor(PANEL);
        center.addView(bottom, new LinearLayout.LayoutParams(-1, dp(112)));

        dialView = new DialView();
        bottom.addView(dialView, new LinearLayout.LayoutParams(dp(112), dp(108)));

        LinearLayout tuneControls = new LinearLayout(this);
        tuneControls.setOrientation(LinearLayout.VERTICAL);
        tuneControls.setGravity(Gravity.CENTER);
        bottom.addView(tuneControls, new LinearLayout.LayoutParams(dp(104), -1));
        addButton(tuneControls, "− STEP", v -> changeFrequency(-stepHz), GREEN, 0);
        addButton(tuneControls, "+ STEP", v -> changeFrequency(stepHz), GREEN, 0);
        stepText = addButton(tuneControls, "STEP 100", v -> cycleStep(), CYAN, 0);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(5), 0, dp(5), 0);
        bottom.addView(info, new LinearLayout.LayoutParams(0, -1, 1f));

        qthText = addInfoLine(info, "QTH: " + userLocator, CYAN);
        distanceText = addInfoLine(info, "DIST: —", GREEN);
        addInfoLine(info, "WEBSDR RECEIVER", WHITE);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER);
        right.setBackgroundColor(PANEL);
        root.addView(right, new LinearLayout.LayoutParams(dp(102), -1));

        addButton(right, "WEBSDR\\nSERVERS", v -> showServerList(), CYAN, 0);
        addButton(right, "POWER", v -> togglePower(), GREEN, 0);
        addButton(right, "− 10 STEP", v -> changeFrequency(-stepHz * 10), GREEN, 0);
        addButton(right, "+ 10 STEP", v -> changeFrequency(stepHz * 10), GREEN, 0);
        addButton(right, "MAP / QTH", v -> showQthDialog(), CYAN, 0);

        serverText = new TextView(this);
        serverText.setTextColor(WHITE);
        serverText.setTextSize(8);
        serverText.setGravity(Gravity.CENTER);
        serverText.setPadding(dp(2), dp(3), dp(2), dp(3));
        right.addView(serverText, new LinearLayout.LayoutParams(-1, -2));

        statusText = new TextView(this);
        statusText.setTextColor(GREEN);
        statusText.setTextSize(10);
        statusText.setGravity(Gravity.CENTER);
        center.addView(statusText, new LinearLayout.LayoutParams(-1, dp(19)));

        updateStatus("ГОТОВ");
        updateLocationInfo();
    }

    private TextView addInfoLine(LinearLayout parent, String text, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(10);
        view.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        parent.addView(view, new LinearLayout.LayoutParams(-1, dp(25)));
        return view;
    }

    private void addLabel(LinearLayout parent, String text, int color, int size) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(color);
        label.setTextSize(size);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setGravity(Gravity.CENTER);
        label.setPadding(1, dp(3), 1, dp(3));
        parent.addView(label, new LinearLayout.LayoutParams(-1, -2));
    }

    private Button addButton(LinearLayout parent, String text,
                             View.OnClickListener listener, int color, int fixedHeight) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(color);
        button.setTextSize(9);
        button.setAllCaps(false);
        button.setPadding(dp(1), 0, dp(1), 0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(PANEL_LIGHT));

        LinearLayout.LayoutParams params;
        if (fixedHeight > 0) {
            params = new LinearLayout.LayoutParams(-1, fixedHeight);
        } else {
            params = new LinearLayout.LayoutParams(-1, 0, 1f);
        }
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        parent.addView(button, params);
        button.setOnClickListener(listener);
        return button;
    }

    private void setMode(String mode) {
        selectedMode = mode;
        refreshReadout();
        if (radioOn && webSdrEngine != null) {
            webSdrEngine.tune(frequencyHz, selectedMode);
        }
    }

    private void setFrequency(long hz) {
        frequencyHz = Math.max(1000L, hz);
        refreshReadout();
        if (radioOn && webSdrEngine != null) {
            webSdrEngine.tune(frequencyHz, selectedMode);
        }
        if (dialView != null) dialView.invalidate();
    }

    private void changeFrequency(long delta) {
        setFrequency(frequencyHz + delta);
    }

    private void cycleStep() {
        if (stepHz == 10) stepHz = 50;
        else if (stepHz == 50) stepHz = 100;
        else if (stepHz == 100) stepHz = 500;
        else if (stepHz == 500) stepHz = 1000;
        else if (stepHz == 1000) stepHz = 5000;
        else stepHz = 10;

        refreshReadout();
        Toast.makeText(this, "Шаг: " + stepHz + " Гц", Toast.LENGTH_SHORT).show();
    }

    private void refreshReadout() {
        if (frequencyText != null) {
            long khz = frequencyHz / 1000L;
            long hz = frequencyHz % 1000L;
            frequencyText.setText(String.format(Locale.US, "%,d.%03d kHz", khz, hz));
        }
        if (modeText != null) {
            modeText.setText(selectedMode + "     STEP " + stepHz + " Hz");
        }
        if (stepText != null) stepText.setText("STEP " + stepHz);
    }

    private void connectSelectedServer() {
        if (webSdrEngine == null || !radioOn) return;
        webSdrEngine.connect(selectedServerUrl);
        updateStatus("ЗАГРУЗКА WebSDR...");
        if (serverText != null) serverText.setText(selectedServerUrl);
    }

    private void togglePower() {
        radioOn = !radioOn;
        if (radioOn) {
            connectSelectedServer();
            updateStatus("ВКЛЮЧЕНИЕ");
        } else {
            if (webSdrEngine != null) webSdrEngine.disconnect();
            updateStatus("ПРИЁМ ВЫКЛЮЧЕН");
        }
    }

    private void showServerList() {
        final WebView directory = new WebView(this);
        WebSettings settings = directory.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportMultipleWindows(false);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        directory.setWebChromeClient(new WebChromeClient());

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("КЛАССИЧЕСКИЕ WebSDR — ВЫБОР СЕРВЕРА")
                .setView(directory)
                .setNegativeButton("ЗАКРЫТЬ", (d, w) -> d.dismiss())
                .create();

        WebViewClient client = new WebViewClient() {
            private boolean handle(String url) {
                if (url == null) return false;
                Uri uri = Uri.parse(url);
                String host = uri.getHost();
                if (host == null) return false;

                String lowerHost = host.toLowerCase(Locale.US);
                if (lowerHost.equals("websdr.org") ||
                        lowerHost.equals("www.websdr.org") ||
                        lowerHost.endsWith(".websdr.org")) {
                    return false;
                }

                String scheme = uri.getScheme();
                if ("http".equalsIgnoreCase(scheme) ||
                        "https".equalsIgnoreCase(scheme)) {
                    selectedServerUrl = url;
                    serverLocator = "";
                    dialog.dismiss();
                    if (radioOn) {
                        connectSelectedServer();
                    } else {
                        if (serverText != null) serverText.setText(selectedServerUrl);
                        updateStatus("СЕРВЕР ВЫБРАН");
                    }
                    showServerLocatorDialog();
                    return true;
                }
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handle(url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view,
                                                     WebResourceRequest request) {
                return handle(request.getUrl().toString());
            }
        };

        directory.setWebViewClient(client);
        dialog.setOnDismissListener(d -> {
            directory.stopLoading();
            directory.destroy();
        });
        dialog.show();

        if (dialog.getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90f);
            int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.90f);
            dialog.getWindow().setLayout(width, height);
        }
        directory.loadUrl("https://www.websdr.org/");
    }

    private void showQthDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Например: LO станция");
        input.setText(userLocator);
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
                .setTitle("ВАШ QTH-ЛОКАТОР")
                .setMessage("Введите Maidenhead locator (например, LO или LOxxaa).")
                .setView(input)
                .setNegativeButton("ОТМЕНА", null)
                .setPositiveButton("СОХРАНИТЬ", (dialog, which) -> {
                    String value = input.getText().toString().trim().toUpperCase(Locale.US);
                    if (!isValidLocator(value)) {
                        Toast.makeText(this, "Неверный QTH-локатор", Toast.LENGTH_LONG).show();
                        return;
                    }
                    userLocator = value;
                    updateLocationInfo();
                })
                .show();
    }

    private void showServerLocatorDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("QTH сервера, если известен");
        input.setText(serverLocator);
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
                .setTitle("QTH-ЛОКАТОР WebSDR СЕРВЕРА")
                .setMessage("Укажите локатор сервера из его описания. Если неизвестен — оставьте поле пустым.")
                .setView(input)
                .setNegativeButton("ПРОПУСТИТЬ", (dialog, which) -> {
                    serverLocator = "";
                    updateLocationInfo();
                })
                .setPositiveButton("СОХРАНИТЬ", (dialog, which) -> {
                    String value = input.getText().toString().trim().toUpperCase(Locale.US);
                    if (value.isEmpty()) {
                        serverLocator = "";
                    } else if (isValidLocator(value)) {
                        serverLocator = value;
                    } else {
                        Toast.makeText(this, "Неверный локатор сервера", Toast.LENGTH_LONG).show();
                        return;
                    }
                    updateLocationInfo();
                })
                .show();
    }

    private boolean isValidLocator(String locator) {
        if (locator == null || locator.length() < 2 ||
                locator.length() > 10 || locator.length() % 2 != 0) {
            return false;
        }

        for (int i = 0; i < locator.length(); i++) {
            char c = locator.charAt(i);
            if (i < 2) {
                if (c < 'A' || c > 'R') return false;
            } else if (i < 4) {
                if (c < '0' || c > '9') return false;
            } else if (i < 6) {
                if (c < 'A' || c > 'X') return false;
            } else if (i < 8) {
                if (c < '0' || c > '9') return false;
            } else if (i < 10) {
                if (c < 'A' || c > 'X') return false;
            }
        }
        return true;
    }

    private double[] locatorCenter(String locator) {
        String s = locator.toUpperCase(Locale.US);
        double lon = -180.0;
        double lat = -90.0;
        double lonSize = 20.0;
        double latSize = 10.0;

        for (int i = 0; i < s.length(); i += 2) {
            int field = i / 2;
            char a = s.charAt(i);
            char b = s.charAt(i + 1);

            if (field == 0) {
                lon += (a - 'A') * lonSize;
                lat += (b - 'A') * latSize;
            } else if (field == 1) {
                lonSize /= 10.0;
                latSize /= 10.0;
                lon += (a - '0') * lonSize;
                lat += (b - '0') * latSize;
            } else if (field == 2) {
                lonSize /= 24.0;
                latSize /= 24.0;
                lon += (a - 'A') * lonSize;
                lat += (b - 'A') * latSize;
            } else if (field == 3) {
                lonSize /= 10.0;
                latSize /= 10.0;
                lon += (a - '0') * lonSize;
                lat += (b - '0') * latSize;
            } else if (field == 4) {
                lonSize /= 24.0;
                latSize /= 24.0;
                lon += (a - 'A') * lonSize;
                lat += (b - 'A') * latSize;
            }
        }

        return new double[]{lat + latSize / 2.0, lon + lonSize / 2.0};
    }

    private double distanceKm(String first, String second) {
        double[] a = locatorCenter(first);
        double[] b = locatorCenter(second);
        double lat1 = Math.toRadians(a[0]);
        double lat2 = Math.toRadians(b[0]);
        double dLat = Math.toRadians(b[0] - a[0]);
        double dLon = Math.toRadians(b[1] - a[1]);

        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371.0 * 2.0 * Math.atan2(Math.sqrt(h), Math.sqrt(1.0 - h));
    }

    private void updateLocationInfo() {
        if (qthText != null) qthText.setText("QTH: " + userLocator);
        if (distanceText != null) {
            if (isValidLocator(userLocator) && isValidLocator(serverLocator)) {
                distanceText.setText(String.format(Locale.US, "DIST: %.0f km",
                        distanceKm(userLocator, serverLocator)));
            } else {
                distanceText.setText("DIST: НЕТ ДАННЫХ");
            }
        }
    }

    private void updateStatus(String value) {
        if (statusText != null) statusText.setText(value);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private class DialView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float lastX;
        private boolean dragging;

        DialView() {
            super(MainActivity.this);
            setBackgroundColor(PANEL);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float r = Math.min(getWidth(), getHeight()) * 0.37f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(7, 10, 12));
            canvas.drawCircle(cx, cy, r + dp(8), paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(3));
            paint.setColor(Color.rgb(75, 87, 92));
            canvas.drawCircle(cx, cy, r, paint);

            paint.setColor(GREEN);
            paint.setStrokeWidth(dp(2));
            for (int i = 0; i <= 30; i++) {
                double angle = Math.toRadians(-135 + i * 9);
                float x1 = cx + (float) Math.cos(angle) * (r - dp(4));
                float y1 = cy + (float) Math.sin(angle) * (r - dp(4));
                float x2 = cx + (float) Math.cos(angle) * (r - dp(12));
                float y2 = cy + (float) Math.sin(angle) * (r - dp(12));
                canvas.drawLine(x1, y1, x2, y2, paint);
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(32, 39, 43));
            canvas.drawCircle(cx, cy, r - dp(17), paint);

            paint.setColor(CYAN);
            paint.setStrokeWidth(dp(4));
            double pointer = Math.toRadians(
                    -135 + (frequencyHz % 1000000L) / 1000000.0 * 270.0);
            canvas.drawLine(cx, cy,
                    cx + (float) Math.cos(pointer) * (r - dp(23)),
                    cy + (float) Math.sin(pointer) * (r - dp(23)), paint);

            paint.setColor(GREEN);
            canvas.drawCircle(cx, cy, dp(5), paint);

            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(9));
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("TUNING", cx, getHeight() - dp(3), paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = event.getX();
                    dragging = true;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (dragging) {
                        float dx = event.getX() - lastX;
                        lastX = event.getX();
                        long delta = Math.round(dx * stepHz * 2.0);
                        if (delta != 0) changeFrequency(delta);
                        invalidate();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    performClick();
                    return true;
            }
            return true;
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }
    }

    @Override
    protected void onDestroy() {
        if (webSdrEngine != null) {
            webSdrEngine.release();
            webSdrEngine = null;
        }
        super.onDestroy();
    }
}
