package com.un7kai.radio;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Locale;

public class WebSdrEngine extends WebView {

    private boolean pageReady = false;
    private boolean released = false;
    private String serverUrl = "";
    private long pendingFrequency = 7129100L;
    private String pendingMode = "LSB";

    public WebSdrEngine(Context context) {
        super(context);

        setBackgroundColor(Color.BLACK);
        setFocusable(true);
        setFocusableInTouchMode(true);

        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        setMixedContentModeCompat();

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookies.setAcceptThirdPartyCookies(this, true);
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
    }

    private void setMixedContentModeCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }
    }

    public void connect(String url) {
        if (released || url == null || url.trim().isEmpty()) return;

        serverUrl = url.trim();
        pageReady = false;

        String tunedUrl = buildTunedUrl(serverUrl);
        loadUrl(tunedUrl);
    }

    public void tune(long frequencyHz, String mode) {
        if (released) return;

        pendingFrequency = Math.max(1000L, frequencyHz);
        if (mode != null && !mode.trim().isEmpty()) {
            pendingMode = mode.trim().toUpperCase(Locale.US);
        }

        if (pageReady) {
            applyTune();
        }
    }

    private String buildTunedUrl(String baseUrl) {
        String clean = baseUrl;
        int hash = clean.indexOf('#');
        if (hash >= 0) clean = clean.substring(0, hash);

        String separator = clean.contains("?") ? "&" : "?";
        return clean + separator + "tune=" + tuneParameter();
    }

    private String tuneParameter() {
        double khz = pendingFrequency / 1000.0;
        return String.format(Locale.US, "%.3f", khz) + modeParameter();
    }

    private String modeParameter() {
        String mode = pendingMode.toLowerCase(Locale.US);
        if ("LSB".equalsIgnoreCase(mode)) return "lsb";
        if ("USB".equalsIgnoreCase(mode)) return "usb";
        if ("CW".equalsIgnoreCase(mode)) return "cw";
        if ("AM".equalsIgnoreCase(mode)) return "am";
        if ("FM".equalsIgnoreCase(mode)) return "fm";
        return "usb";
    }

    private void applyTune() {
        if (released || !pageReady) return;

        final double frequencyKhz = pendingFrequency / 1000.0;
        final String mode = modeParameter();

        String js =
                "(function() {" +
                "var f=" + String.format(Locale.US, "%.3f", frequencyKhz) + ";" +
                "var m='" + mode + "';" +
                "try {" +
                " if (typeof setfreqif === 'function') { setfreqif(f.toFixed(3)); }" +
                " else if (typeof setfreqif_fut === 'function') { setfreqif_fut(f.toFixed(3)); }" +
                " else {" +
                "  var x=document.querySelector('form[name=\\\"freqform\\\"] input[name=\\\"frequency\\\"]');" +
                "  if(x){x.value=f.toFixed(3); x.dispatchEvent(new Event('change',{bubbles:true}));" +
                "   var form=x.form; if(form && typeof form.requestSubmit==='function') form.requestSubmit();" +
                "  }" +
                " }" +
                " var selectors=document.querySelectorAll('select');" +
                " for(var i=0;i<selectors.length;i++){" +
                "  var s=selectors[i];" +
                "  for(var j=0;j<s.options.length;j++){" +
                "   var o=s.options[j]; var v=(o.value+' '+o.text).toLowerCase();" +
                "   if(v.indexOf(m)>=0 && /(mode|demod|mod)/i.test(s.name+' '+s.id+' '+s.className)){" +
                "    s.selectedIndex=j; s.dispatchEvent(new Event('change',{bubbles:true})); break;" +
                "   }" +
                "  }" +
                " }" +
                " if(typeof setmode==='function') setmode(m);" +
                " if(typeof setmodeif==='function') setmodeif(m);" +
                "} catch(e) {}" +
                "})();";

        evaluateJavascript(js, null);
    }

    public void disconnect() {
        pageReady = false;
        stopLoading();
        loadUrl("about:blank");
    }

    public void release() {
        if (released) return;
        released = true;
        pageReady = false;
        stopLoading();
        setWebChromeClient(null);
        setWebViewClient(new WebViewClient());
        loadUrl("about:blank");
        removeAllViews();
        destroy();
    }
}
