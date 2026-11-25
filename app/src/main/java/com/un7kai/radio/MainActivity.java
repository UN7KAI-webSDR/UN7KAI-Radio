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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(5, 7, 9);
    private static final int PANEL = Color.rgb(25, 29, 33);
    private static final int GREEN = Color.rgb(0, 245, 55);
    private static final int CYAN = Color.rgb(0, 225, 235);
    private static final String DEFAULT_SERVER = "http://tulasdr.fvds.ru:8901/";

    private WebSdrEngine webSdrEngine;
    private TextView frequencyText;
    private TextView modeText;
    private TextView statusText;
    private TextView serverText;
    private String selectedServerUrl = DEFAULT_SERVER;
    private String selectedMode = "LSB";
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
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        buildInterface();
        connectSelectedServer();
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(BG);
        root.setPadding(5, 5, 5, 5);
        setContentView(root);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER);
        left.setBackgroundColor(PANEL);
        root.addView(left, new LinearLayout.LayoutParams(dp(76), -1));

        addLabel(left, "UN7KAI", GREEN, 17);
        addLabel(left, "MODE", CYAN, 11);
        for (String mode : new String[]{"LSB", "USB", "CW", "AM", "FM"}) {
            addButton(left, mode, v -> setMode(mode), GREEN);
        }
        addLabel(left, "BANDS", CYAN, 11);
        String[][] bands = {
                {"160", "1900000"}, {"80", "3700000"}, {"40", "7129100"},
                {"20", "14200000"}, {"15", "21200000"}, {"10", "28400000"},
                {"2m", "145500000"}, {"70cm", "433500000"}
        };
        for (String[] band : bands) {
            addButton(left, band[0], v -> setFrequency(Long.parseLong(band[1])), CYAN);
        }

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setBackgroundColor(BG);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -1, 1f);
        cp.setMargins(dp(5), 0, dp(5), 0);
        root.addView(center, cp);

        LinearLayout readout = new LinearLayout(this);
        readout.setOrientation(LinearLayout.VERTICAL);
        readout.setGravity(Gravity.CENTER);
        readout.setBackgroundColor(Color.BLACK);
        center.addView(readout, new LinearLayout.LayoutParams(-1, dp(65)));

        frequencyText = new TextView(this);
        frequencyText.setTextColor(GREEN);
        frequencyText.setTextSize(29);
        frequencyText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        frequencyText.setGravity(Gravity.CENTER);
        readout.addView(frequencyText);

        modeText = new TextView(this);
        modeText.setTextColor(CYAN);
        modeText.setTextSize(12);
        readout.addView(modeText);
        refreshReadout();

        webSdrEngine = new WebSdrEngine(this);
        webSdrEngine.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        center.addView(webSdrEngine);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setBackgroundColor(PANEL);
        center.addView(bottom, new LinearLayout.LayoutParams(-1, dp(112)));

        DialView dial = new DialView();
        bottom.addView(dial, new LinearLayout.LayoutParams(dp(112), dp(108)));

        LinearLayout tuneControls = new LinearLayout(this);
        tuneControls.setOrientation(LinearLayout.VERTICAL);
        tuneControls.setGravity(Gravity.CENTER);
        bottom.addView(tuneControls, new LinearLayout.LayoutParams(dp(105), -1));
        addButton(tuneControls, "− STEP", v -> changeFrequency(-stepHz), GREEN);
        addButton(tuneControls, "+ STEP", v -> changeFrequency(stepHz), GREEN);
        addButton(tuneControls, "STEP 100", v -> cycleStep(), CYAN);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER);
        right.setBackgroundColor(PANEL);
        root.addView(right, new LinearLayout.LayoutParams(dp(100), -1));

        addButton(right, "SDR\\nСЕРВЕРА", v -> showServerList(), CYAN);
        addButton(right, "POWER", v -> togglePower(), GREEN);
        addButton(right, "− FREQ", v -> changeFrequency(-stepHz * 10), GREEN);
        addButton(right, "+ FREQ", v -> changeFrequency(stepHz * 10), GREEN);
        addButton(right, "MAP", v ->
                Toast.makeText(this, "Карта серверов будет добавлена отдельно", Toast.LENGTH_SHORT).show(), CYAN);

        serverText = new TextView(this);
        serverText.setTextColor(Color.LTGRAY);
        serverText.setTextSize(9);
        serverText.setGravity(Gravity.CENTER);
        right.addView(serverText);

        statusText = new TextView(this);
        statusText.setTextColor(GREEN);
        statusText.setTextSize(10);
        statusText.setGravity(Gravity.CENTER);
        center.addView(statusText, new LinearLayout.LayoutParams(-1, dp(20)));
        updateStatus("ГОТОВ");
    }

    private void addLabel(LinearLayout parent, String text, int color, int size) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(color);
        label.setTextSize(size);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setGravity(Gravity.CENTER);
        label.setPadding(1, dp(4), 1, dp(4));
        parent.addView(label, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addButton(LinearLayout parent, String text, View.OnClickListener listener, int color) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(color);
        button.setTextSize(10);
        button.setAllCaps(false);
        button.setPadding(1, 0, 1, 0);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(35, 41, 45)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, 0, 1f);
        p.setMargins(dp(2), dp(2), dp(2), dp(2));
        parent.addView(button, p);
        button.setOnClickListener(listener);
    }

    private void setMode(String mode) {
        selectedMode = mode;
        refreshReadout();
        if (radioOn && webSdrEngine != null) webSdrEngine.tune(frequencyHz, selectedMode);
    }

    private void setFrequency(long hz) {
        frequencyHz = Math.max(1000L, hz);
        refreshReadout();
        if (radioOn && webSdrEngine != null) webSdrEngine.tune(frequencyHz, selectedMode);
        if (webSdrEngine != null) {
            View dial = findDialView();
            if (dial != null) dial.invalidate();
        }
    }

    private View findDialView() {
        ViewGroup root = (ViewGroup) findViewById(android.R.id.content);
        return findDialRecursive(root);
    }

    private View findDialRecursive(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof DialView) return child;
            if (child instanceof ViewGroup) {
                View found = findDialRecursive((ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void changeFrequency(long delta) {
        setFrequency(frequencyHz + delta);
    }

    private void cycleStep() {
        if (stepHz == 10) stepHz = 100;
        else if (stepHz == 100) stepHz = 1000;
        else if (stepHz == 1000) stepHz = 10000;
        else stepHz = 10;
        refreshReadout();
        Toast.makeText(this, "Шаг: " + stepHz + " Гц", Toast.LENGTH_SHORT).show();
    }

    private void refreshReadout() {
        if (frequencyText != null) {
            long khz = frequencyHz / 1000;
            long hz = frequencyHz % 1000;
            frequencyText.setText(String.format(Locale.US, "%,d.%03d kHz", khz, hz));
        }
        if (modeText != null) modeText.setText(selectedMode + "   STEP " + stepHz + " Hz");
    }

    private void connectSelectedServer() {
        if (webSdrEngine == null || !radioOn) return;
        webSdrEngine.connect(selectedServerUrl);
        webSdrEngine.tune(frequencyHz, selectedMode);
        updateStatus("ПОДКЛЮЧЕНИЕ...");
        if (serverText != null) serverText.setText(selectedServerUrl);
    }

    private void togglePower() {
        radioOn = !radioOn;
        if (radioOn) {
            connectSelectedServer();
            updateStatus("ВКЛЮЧЕНО");
        } else {
            if (webSdrEngine != null) webSdrEngine.disconnect();
            updateStatus("ВЫКЛЮЧЕНО");
        }
    }

    /*
     * Открывает официальный каталог классических WebSDR.
     * Страницу каталога не перестраиваем: это сохраняет её штатную навигацию
     * и позволяет выбрать любой доступный сервер.
     */
    private void showServerList() {
        final WebView directory = new WebView(this);
        WebSettings settings = directory.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportMultipleWindows(false);
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

                if (host.equalsIgnoreCase("websdr.org") ||
                        host.equalsIgnoreCase("www.websdr.org") ||
                        host.toLowerCase(Locale.US).endsWith(".websdr.org")) {
                    return false;
                }

                if ("http".equalsIgnoreCase(uri.getScheme()) ||
                        "https".equalsIgnoreCase(uri.getScheme())) {
                    selectedServerUrl = url;
                    dialog.dismiss();
                    if (radioOn) {
                        connectSelectedServer();
                    } else {
                        if (serverText != null) serverText.setText(selectedServerUrl);
                        updateStatus("СЕРВЕР ВЫБРАН");
                    }
                    return true;
                }
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handle(url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
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
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.88f);
            int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.88f);
            dialog.getWindow().setLayout(width, height);
        }
        directory.loadUrl("http://websdr.org/");
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
            float r = Math.min(getWidth(), getHeight()) * 0.39f;

            paint.setColor(Color.rgb(8, 10, 12));
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy, r + dp(8), paint);

            paint.setColor(Color.rgb(65, 72, 76));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(3));
            canvas.drawCircle(cx, cy, r, paint);

            paint.setColor(GREEN);
            paint.setStrokeWidth(dp(2));
            for (int i = 0; i < 31; i++) {
                double a = Math.toRadians(-135 + i * 9);
                float x1 = cx + (float) Math.cos(a) * (r - dp(5));
                float y1 = cy + (float) Math.sin(a) * (r - dp(5));
                float x2 = cx + (float) Math.cos(a) * (r - dp(13));
                float y2 = cy + (float) Math.sin(a) * (r - dp(13));
                canvas.drawLine(x1, y1, x2, y2, paint);
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(32, 38, 42));
            canvas.drawCircle(cx, cy, r - dp(17), paint);
            paint.setColor(CYAN);
            paint.setStrokeWidth(dp(4));
            double pointer = Math.toRadians(-135 + (frequencyHz % 1000000L) / 1000000.0 * 270.0);
            canvas.drawLine(cx, cy,
                    cx + (float) Math.cos(pointer) * (r - dp(23)),
                    cy + (float) Math.sin(pointer) * (r - dp(23)), paint);
            paint.setColor(GREEN);
            canvas.drawCircle(cx, cy, dp(5), paint);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(10));
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("TUNE", cx, getHeight() - dp(4), paint);
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
