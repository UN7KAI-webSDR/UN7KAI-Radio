package com.un7kai.radio;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.Response;
import okio.ByteString;
import android.webkit.WebView;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.app.Activity;
import android.os.Bundle;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Bitmap;
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
    private WebSocket waterfallSocket;
    private WebSdrEngine webSdrEngine;
    private WaterfallView waterfallView;
private boolean kiwiConnected = false;
    private android.media.AudioTrack audioTrack;
    private String selectedServerUrl = "";
    private int selectedServerIndex = 0;
    private int audioSampleRate = 12000;
    private boolean audioFlagShown = false;
private boolean audioStarted = false;
    private long lastAudioTime = 0;
private int audioFrameCount = 0;
    private boolean radioOn = true;
    private TextView frequency;
    private TextView bandLabel;
    private TextView status;
    private TextView modeLabel;
    private TextView stepLabel;

    private String selectedBand = "40 м";
    private String selectedMode = "LSB";

    private int step = 1000;
    private long frequencyHz = 7129100L;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        kiwiClient = new OkHttpClient.Builder().build();

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
    tuneKiwi();
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

       webSdrEngine = new WebSdrEngine(this);
        

webSdrEngine.connect(
    "http://tulasdr.fvds.ru:8901/"
);
LinearLayout.LayoutParams sp =
    new LinearLayout.LayoutParams(-1, 0, 1);

sp.setMargins(dp(4), dp(8), dp(4), dp(8));
center.addView(webSdrEngine, sp);

        DialView knob = new DialView(this);
        center.addView(knob,
            new LinearLayout.LayoutParams(dp(145), dp(145)));
step = 1000;

stepLabel = text("STEP: 1000 Hz", 13, true, CYAN);
center.addView(stepLabel);

stepLabel.setOnClickListener(v -> {
    if (step == 1000) {
        step = 500;
    } else if (step == 500) {
        step = 100;
    } else if (step == 100) {
        step = 50;
    } else if (step == 50) {
        step = 10;
    } else if (step == 10) {
        step = 1;
    } else {
        step = 1000;
    }

    stepLabel.setText("STEP: " + step + " Hz");
});
        
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
        Button powerButton = button("ВЫКЛЮЧИТЬ РАДИО");
right.addView(powerButton, buttonParams());

powerButton.setOnClickListener(v -> {
    if (radioOn) {
        radioOn = false;

        if (kiwiSocket != null) {
            kiwiSocket.close(1000, "Radio OFF");
            kiwiSocket = null;
        }
        if (waterfallSocket != null) {
    waterfallSocket.close(1000, "Radio OFF");
    waterfallSocket = null;
}

        stopAudio();
        powerButton.setText("ВКЛЮЧИТЬ РАДИО");
        status.setText("РАДИО ВЫКЛЮЧЕНО");

    } else {
        radioOn = true;
        powerButton.setText("ВЫКЛЮЧИТЬ РАДИО");

        if (!selectedServerUrl.isEmpty()) {
            connectKiwi(selectedServerUrl);
            connectWaterfall(selectedServerUrl);
        } else {
            showServerList();
        }
    }
});

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
    tuneKiwi();
}
   private void tuneKiwi() {
    if (kiwiSocket == null || !radioOn || !kiwiConnected) {
    return;
}

    String mode = selectedMode.toLowerCase(
        java.util.Locale.US
    );

    int low = -3000;
    int high = -300;

    if (mode.equals("usb")) {
        low = 300;
        high = 2700;
    } else if (mode.equals("lsb")) {
        low = -2700;
        high = -300;
    } else if (mode.equals("am")) {
        low = -4500;
        high = 4500;
    } else if (mode.equals("cw")) {
        low = 300;
        high = 800;
    } else if (mode.equals("fm")) {
        mode = "nfm";
        low = -6000;
        high = 6000;
    }

    String command = String.format(
        java.util.Locale.US,
        "SET mod=%s low_cut=%d high_cut=%d freq=%.3f",
        mode,
        low,
        high,
        frequencyHz / 1000.0
    );

    boolean sent = kiwiSocket.send(command);

    if (sent) {
        status.setText(
            "НАСТРОЙКА: " +
            String.format(
                java.util.Locale.US,
                "%.3f MHz",
                frequencyHz / 1000000.0
            )
        );
    } else {
        status.setText("ОШИБКА НАСТРОЙКИ SDR");
    }
}


    private void updateFrequency() {
        String raw = String.format(
            java.util.Locale.US, "%,d", frequencyHz)
            .replace(',', '.');
        frequency.setText(raw);
    }

   private void selectBand(String band) {
    selectedBand = band;

    if (band.equals("160 м")) frequencyHz = 1900000L;
    else if (band.equals("80 м")) frequencyHz = 3700000L;
    else if (band.equals("40 м")) frequencyHz = 7129100L;
    else if (band.equals("20 м")) frequencyHz = 14150000L;
    else if (band.equals("15 м")) frequencyHz = 21200000L;
    else if (band.equals("10 м")) frequencyHz = 28400000L;
    else if (band.equals("2 м")) frequencyHz = 145500000L;
    else if (band.equals("70 см")) frequencyHz = 433500000L;

    updateFrequency();

    bandLabel.setText(selectedBand + " • " + selectedMode);

    status.setText("ДИАПАЗОН: " + selectedBand);

    tuneKiwi();
}

   private void cycleStep() {
    if (step == 1000) step = 500;
    else if (step == 500) step = 100;
    else if (step == 100) step = 50;
    else if (step == 50) step = 10;
    else if (step == 10) step = 1;
    else step = 1000;

    stepLabel.setText("STEP: " + step + " Hz");
}
    private void showMessage(String message) {
        new android.app.AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show();
    }
private void startAudio() {
    int bufferSize = android.media.AudioTrack.getMinBufferSize(
        audioSampleRate,
        android.media.AudioFormat.CHANNEL_OUT_MONO,
        android.media.AudioFormat.ENCODING_PCM_16BIT
    );

    audioTrack = new android.media.AudioTrack(
        android.media.AudioManager.STREAM_MUSIC,
        audioSampleRate,
        android.media.AudioFormat.CHANNEL_OUT_MONO,
        android.media.AudioFormat.ENCODING_PCM_16BIT,
        bufferSize * 2,
        android.media.AudioTrack.MODE_STREAM
    );

    audioTrack.play();
    audioStarted = true;
}
    private void stopAudio() {
    if (audioTrack != null) {
        audioTrack.stop();
        audioTrack.release();
        audioTrack = null;
    }

    audioStarted = false;
}
   private void connectKiwi(String serverUrl) {
    status.setText("Подключение SDR...");

    try {
        android.net.Uri uri = android.net.Uri.parse(serverUrl);
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 8073;

        String wsUrl = "ws://" + host + ":" + port + "/"
                + System.currentTimeMillis() + "/SND";

        Request request = new Request.Builder()
                .url(wsUrl)
                .build();

        kiwiSocket = kiwiClient.newWebSocket(
            request,
            new WebSocketListener() {

                @Override
                public void onOpen(WebSocket ws, Response response) {
                    kiwiConnected = true;
                    ws.send("SET auth t=kiwi p=#");

                    new android.os.Handler(
                        android.os.Looper.getMainLooper()
                    ).postDelayed(() -> {
                        ws.send("SET compression=0");
                        ws.send("SET keepalive");
                        ws.send("SET agc=1 hang=0 thresh=-100 slope=6 decay=1000 manGain=50");

                        tuneKiwi();
                        ws.send("SET AR OK in=12000 out=12000");

                        startAudio();
                        status.setText("SDR CONNECTED");
                    }, 300);
                }

                @Override
                public void onMessage(WebSocket ws, ByteString data) {
                    byte[] frame = data.toByteArray();
                    
                    if (frame.length <= 10 ||
                        frame[0] != 'S' ||
                        frame[1] != 'N' ||
                        frame[2] != 'D') return;
                     lastAudioTime = System.currentTimeMillis();
audioFrameCount++;
                    if (audioFrameCount % 500 == 0) {
    runOnUiThread(() ->
        status.setText("SND FRAMES: " + audioFrameCount)
    );
}


                    int flags = frame[3] & 0xff;

                    if (!audioFlagShown) {
                        audioFlagShown = true;
                        runOnUiThread(() ->
                            status.setText("AUDIO FLAGS: " + flags)
                        );
                    }

                    if ((flags & 0x10) != 0 ||
                        (flags & 0x08) != 0) return;

                    int offset = 10;
                    int length = frame.length - offset;
                    length -= length % 2;

                    if (length <= 0 || audioTrack == null) return;

                    byte[] pcm = new byte[length];

                    if ((flags & 0x80) != 0) {
                        System.arraycopy(frame, offset, pcm, 0, length);
                    } else {
                        for (int i = 0; i < length; i += 2) {
                            pcm[i] = frame[offset + i + 1];
                            pcm[i + 1] = frame[offset + i];
                        }
                    }

                   int written = audioTrack.write(pcm, 0, length);

if (written < 0) {
    runOnUiThread(() ->
        status.setText("AUDIO WRITE ERROR: " + written)
    );
}
                }

                @Override
                public void onMessage(WebSocket ws, String message) {
                    runOnUiThread(() ->
                        status.setText("SDR: " + message)
                    );
                }

                @Override
                public void onFailure(
                    WebSocket ws,
                    Throwable t,
                    Response response
                ) {
                    kiwiConnected = false;
                    runOnUiThread(() ->
                        status.setText("SDR ERROR: " + t.getMessage())
                    );
                }

                @Override
                public void onClosed(WebSocket ws, int code, String reason) {
                    kiwiConnected = false;
                }
            }
        );

    } catch (Exception e) {
        status.setText("SDR ERROR: " + e.getMessage());
    }
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
                new InputStreamReader(connection.getInputStream())
            );

            StringBuilder json = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                json.append(line);
            }

            reader.close();

            JSONObject data = new JSONObject(json.toString());

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

            java.util.Collections.sort(
                allowed,
                (a, b) -> Integer.compare(
                    countryPriority(a),
                    countryPriority(b)
                )
            );

            runOnUiThread(() -> {
                if (allowed.isEmpty()) {
                    showMessage("Доступных серверов пока нет");
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

                int currentIndex = 0;

                if (!selectedServerUrl.isEmpty()) {
                    for (int i = 0; i < allowed.size(); i++) {
                        if (selectedServerUrl.equals(
                                allowed.get(i).optString("url", ""))) {
                            currentIndex = i;
                            break;
                        }
                    }
                }

                selectedServerIndex = currentIndex;

                android.widget.ArrayAdapter<String> adapter =
                    new android.widget.ArrayAdapter<>(
                        MainActivity.this,
                        android.R.layout.simple_list_item_1,
                        names
                    );

                android.app.AlertDialog dialog =
                    new android.app.AlertDialog.Builder(this)
                        .setTitle("KIWISDR — " + allowed.size() + " серверов")
                        .setAdapter(adapter, (d, which) -> {

                            selectedServerIndex = which;

                            JSONObject selected = allowed.get(which);

                            selectedServerUrl =
                                selected.optString("url", "");

                            stopAudio();

                            connectKiwi(selectedServerUrl);
                            connectWaterfall(selectedServerUrl);

                            status.setText(
                                "SDR: " +
                                selected.optString("name", "KiwiSDR")
                            );

                            showMessage(
                                "Выбран сервер:\n" + selectedServerUrl
                            );
                        })
                        .setNegativeButton("Закрыть", null)
                        .create();

                dialog.show();
                dialog.getListView().setSelection(selectedServerIndex);
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
    private int countryPriority(JSONObject s) {

    String place = (
        s.optString("loc", "") + " " +
        s.optString("name", "") + " " +
        s.optString("url", "")
    ).toLowerCase(java.util.Locale.ROOT);

    if (place.contains("казахстан") ||
        place.contains("kazakhstan")) return 0;

    if (place.contains("россия") ||
        place.contains("russia") ||
        place.contains("russian federation")) return 1;

    if (place.contains("украина") ||
        place.contains("ukraine")) return 2;

    if (place.contains("грузия") ||
        place.contains("georgia")) return 3;

    if (place.contains("армения") ||
        place.contains("armenia")) return 4;

    if (place.contains("узбекистан") ||
        place.contains("uzbekistan")) return 5;

    if (place.contains("кыргызстан") ||
        place.contains("киргизия") ||
        place.contains("kyrgyzstan")) return 6;

    if (place.contains("таджикистан") ||
        place.contains("tajikistan")) return 7;

    if (place.contains("туркменистан") ||
        place.contains("turkmenistan")) return 8;

    return 100;
}
private void connectWaterfall(String serverUrl) {
    try {
        if (waterfallSocket != null) {
            waterfallSocket.close(1000, "Switch SDR");
            waterfallSocket = null;
        }
        android.net.Uri uri =
            android.net.Uri.parse(serverUrl);

        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 8073;

        String wfUrl = "ws://" + host + ":" + port +
            "/" + System.currentTimeMillis() + "/W/F";

        Request request = new Request.Builder()
            .url(wfUrl)
            .build();

        waterfallSocket = kiwiClient.newWebSocket(
            request,
            new WebSocketListener() {

              @Override
public void onOpen(WebSocket ws, Response response) { 
                    ws.send("SET auth t=kiwi p=#");
                    ws.send("SET wf_comp=0");
                    ws.send("SET zoom=0 start=0");
                    ws.send("SET maxdb=-10 mindb=-110");
                    ws.send("SET wf_speed=4");
                }

                @Override
                public void onMessage(WebSocket ws, ByteString data) {
                    byte[] frame = data.toByteArray();

                    if (frame.length <= 16 ||
                        frame[0] != 'W' ||
                        frame[1] != '/' ||
                        frame[2] != 'F') return;

                   byte[] row = java.util.Arrays.copyOfRange(frame, 16, frame.length);

                    runOnUiThread(() -> {
                        if (waterfallView != null) {
                           waterfallView.addWaterfallRow(
    row, 0, row.length
);
                        }
                    });
                }

            @Override
public void onFailure(
    WebSocket ws,
    Throwable t,
    Response response
) {
    // Ошибка водопада не должна менять состояние радио
} 
            }
        );

    } catch (Exception e) {
        status.setText("WF ERROR: " + e.getMessage());
    }
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
private class WaterfallView extends View {

    private Bitmap bitmap;
    private Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private int[] pixels;
    private int wfWidth;
    private int wfHeight;

    public WaterfallView(android.content.Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
    }

    @Override
    protected void onSizeChanged(
            int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (w <= 0 || h <= 0) return;

        wfWidth = w;
        wfHeight = h;

        bitmap = Bitmap.createBitmap(
                w, h, Bitmap.Config.ARGB_8888);

        pixels = new int[w * h];
        java.util.Arrays.fill(pixels, Color.BLACK);
    }

    public void addWaterfallRow(
            byte[] data, int offset, int length) {

        if (bitmap == null || pixels == null ||
                data == null || length <= 0) return;

        System.arraycopy(
                pixels, 0,
                pixels, wfWidth,
                wfWidth * (wfHeight - 1));

        for (int x = 0; x < wfWidth; x++) {

            int index = offset +
                    (int)((long)x * length / wfWidth);

            int value = data[index] & 0xFF;

            int r = Math.min(255, value * 2);
            int g = Math.min(255, value * 3);
            int b = Math.min(255, 80 + value);

            pixels[x] = Color.rgb(r, g, b);
        }

        bitmap.setPixels(
                pixels, 0, wfWidth,
                0, 0, wfWidth, wfHeight);

        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (bitmap != null) {
            canvas.drawBitmap(
                    bitmap, null,
                    new android.graphics.Rect(
                            0, 0, getWidth(), getHeight()),
                    paint);
        }
    }
}
}
