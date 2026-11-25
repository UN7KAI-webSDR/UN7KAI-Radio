package com.un7kai.radio;

import android.content.Context;
import android.os.Build;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class WebSdrEngine extends WebView {

    public WebSdrEngine(Context context) {
        super(context);

        WebSettings s = getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadsImagesAutomatically(true);

        if (Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(
                WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            );
            CookieManager.getInstance()
                .setAcceptThirdPartyCookies(this, true);
        }

        setWebChromeClient(new WebChromeClient());
        setWebViewClient(new WebViewClient());
        setBackgroundColor(0xFF000000);
    }

    public void connect(String url) {
        if (url == null) return;

        url = url.trim();

        if (url.startsWith("http://") ||
            url.startsWith("https://")) {
            loadUrl(url);
        }
    }

    public void disconnect() {
        stopLoading();
        loadUrl("about:blank");
    }

    public void release() {
        stopLoading();
        setWebChromeClient(null);
        setWebViewClient(null);
        removeAllViews();
        destroy();
    }
}
