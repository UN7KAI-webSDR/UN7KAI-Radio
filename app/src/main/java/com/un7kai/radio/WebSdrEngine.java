package com.un7kai.radio;

import android.content.Context;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.os.Build;

import java.util.Locale;

public class WebSdrEngine extends WebView {

    private boolean pageReady = false;
    private long pendingDelta = 0;
    private String pendingMode = "lsb";

    private final String baseUrl =
        "http://tulasdr.fvds.ru:8901/";

    public WebSdrEngine(Context context) {
        super(context);

        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadsImagesAutomatically(true);

        if (Build.VERSION.SDK_INT >= 21) {
            settings.setMixedContentMode(
                WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            );
        }

        setWebChromeClient(new WebChromeClient());

        setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(
                WebView view, String url
            ) {
                pageReady = true;

                evaluateJavascript(
                    "set_mode('" + pendingMode + "');",
                    null
                );

                if (pendingDelta != 0) {
                    long d = pendingDelta;
                    pendingDelta = 0;
                    changeFrequency(d);
                }
            }
        });

        setBackgroundColor(0xFF000000);
    }

    public void connect(String url) {
        pageReady = false;
        loadUrl(url);
    }

    public void connectDefault(long frequencyHz, String mode) {
        String tune = String.format(
            Locale.US,
            "%.1f%s",
            frequencyHz / 1000.0,
            mode.toLowerCase(Locale.US)
        );

        connect(baseUrl + "?tune=" + tune);
    }

    public void changeFrequency(long deltaHz) {
        if (!pageReady) {
            pendingDelta += deltaHz;
            return;
        }

        int steps = (int)Math.round(deltaHz / 100.0);

        if (steps == 0) return;

        String direction = steps > 0 ? "+1" : "-1";
        int count = Math.abs(steps);

        String js =
            "for(var i=0;i<" + count + ";i++){freqstep(" +
            direction + ");} nominalfreq();";

        evaluateJavascript(js, null);
    }

    public void setMode(String mode) {
        pendingMode = mode.toLowerCase(Locale.US);

        if (pageReady) {
            evaluateJavascript(
                "set_mode('" + pendingMode + "'); nominalfreq();",
                null
            );
        }
    }

    public void setFrequency(long frequencyHz, String mode) {
        connectDefault(frequencyHz, mode);
    }

    public void disconnect() {
        pageReady = false;
        stopLoading();
        loadUrl("about:blank");
    }

    public void release() {
        pageReady = false;
        stopLoading();
        setWebChromeClient(null);
        setWebViewClient(null);
        removeAllViews();
        destroy();
    }
}
