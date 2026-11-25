package com.un7kai.radio;

import android.content.Context;
import android.os.Build;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.ValueCallback;

import java.util.Locale;

public class WebSdrEngine extends WebView {

    private boolean pageReady = false;
    private long pendingFrequency = 7129100L;
    private String pendingMode = "LSB";

    public WebSdrEngine(Context context) {
        super(context);

        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
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
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                pageReady = true;
                applyTune();
            }
        });

        setBackgroundColor(0xFF000000);
    }

    public void connect(String url) {
        pageReady = false;

        if (url != null &&
            (url.startsWith("http://") ||
             url.startsWith("https://"))) {
            loadUrl(url);
        }
    }

    public void tune(long frequencyHz, String mode) {
        pendingFrequency = frequencyHz;
        pendingMode = mode == null ? "LSB" :
            mode.toUpperCase(Locale.US);

        if (pageReady) {
            applyTune();
        }
    }

    private void applyTune() {
        if (!pageReady) return;

        double frequencyKHz = pendingFrequency / 1000.0;

        String mode = pendingMode.toLowerCase(Locale.US);

        String js = String.format(
            Locale.US,
            "(function() {" +
            "if (typeof window.setfreq !== 'function' ||" +
            "typeof window.set_mode !== 'function') {" +
            "return 'API_NOT_FOUND';" +
            "}" +
            "window.set_mode('%s');" +
            "window.setfreq(%.3f);" +
            "return 'OK';" +
            "})()",
            mode,
            frequencyKHz
        );

        evaluateJavascript(js, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                // Результат выполнения JavaScript
            }
        });
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
