package com.maxassistant.app.preview;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.ClientCertRequest;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.HttpAuthHandler;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.maxassistant.app.R;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/** Runs user HTML in an internal offline WebView, with the original code always available. */
public final class HtmlPreviewActivity extends Activity {
    private static final String EXTRA_ARTIFACT = "artifact_id";
    private static final String PREVIEW_ORIGIN = "https://max-preview.invalid/";
    private static final String CSP = "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; "
        + "img-src data:; font-src 'none'; connect-src 'none'; frame-src 'none'; child-src 'none'; "
        + "object-src 'none'; media-src 'none'; worker-src 'none'; manifest-src 'none'; "
        + "base-uri 'none'; form-action 'none'; navigate-to 'none'";
    // WebRTC STUN and locally synthesized audio are outside ordinary resource loads.
    // Freeze these APIs before untrusted code; no child window/frame can obtain originals.
    private static final String BLOCK_EXTRA_APIS = "<script>(()=>{'use strict';"
        + "for(const name of ['RTCPeerConnection','webkitRTCPeerConnection','mozRTCPeerConnection',"
        + "'WebTransport','WebSocket','EventSource','Worker','SharedWorker','AudioContext',"
        + "'webkitAudioContext','OfflineAudioContext','webkitOfflineAudioContext','speechSynthesis']){"
        + "try{Object.defineProperty(window,name,{value:undefined,writable:false,configurable:false});}catch(e){}}"
        + "for(const name of ['sendBeacon','getUserMedia','webkitGetUserMedia','mozGetUserMedia']){"
        + "try{Object.defineProperty(navigator,name,{value:undefined,writable:false,configurable:false});}catch(e){}}"
        + "})();</script>";
    private String artifactId;
    private String html;
    private WebView web;
    private TextView status;
    private ScrollView codePane;
    private Button previewButton;
    private Button codeButton;
    private boolean codeVisible;

    public static void open(Context context, String artifactId) {
        if (artifactId == null || !artifactId.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
            throw new IllegalArgumentException("معرّف المعاينة غير صالح.");
        Intent intent = new Intent(context, HtmlPreviewActivity.class).putExtra(EXTRA_ARTIFACT, artifactId);
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        artifactId = state == null ? getIntent().getStringExtra(EXTRA_ARTIFACT) : state.getString(EXTRA_ARTIFACT);
        codeVisible = state != null && state.getBoolean("code_visible", false);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        page.setBackgroundColor(Color.rgb(16, 21, 31));
        int padding = dp(12);
        page.setPadding(padding, padding, padding, padding);
        page.setOnApplyWindowInsetsListener((view, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                android.graphics.Insets keyboard = insets.getInsets(WindowInsets.Type.ime());
                left = bars.left; top = bars.top; right = bars.right; bottom = Math.max(bars.bottom, keyboard.bottom);
            } else {
                left = insets.getSystemWindowInsetLeft(); top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight(); bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(padding + left, padding + top, padding + right, padding + bottom);
            return insets;
        });
        TextView title = new TextView(this);
        title.setText("معاينة HTML محلية");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setPadding(0, 0, 0, dp(8));
        page.addView(title);

        LinearLayout toolbar = new LinearLayout(this);
        Button back = button("رجوع"); back.setOnClickListener(view -> finish());
        previewButton = button("المعاينة"); previewButton.setId(R.id.html_preview_show);
        codeButton = button("الكود"); codeButton.setId(R.id.html_preview_show_code);
        Button copy = button("نسخ"); copy.setId(R.id.html_preview_copy);
        for (Button control : new Button[]{back, previewButton, codeButton, copy})
            toolbar.addView(control, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        page.addView(toolbar);
        status = new TextView(this);
        status.setId(R.id.html_preview_status);
        status.setTextColor(Color.rgb(181, 197, 217));
        status.setTextSize(13);
        status.setPadding(0, dp(6), 0, dp(10));
        status.setText("تعمل هذه الصفحة محليًا. الروابط والاتصالات وأذونات الجهاز محظورة.");
        page.addView(status);

        FrameLayout content = new FrameLayout(this);
        page.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        codePane = new ScrollView(this);
        codePane.setFillViewport(true);
        TextView code = new TextView(this);
        code.setId(R.id.html_preview_code);
        code.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        code.setTextDirection(View.TEXT_DIRECTION_LTR);
        code.setTextColor(Color.rgb(223, 235, 250));
        code.setBackgroundColor(Color.rgb(11, 17, 27));
        code.setTypeface(Typeface.MONOSPACE);
        code.setTextSize(12);
        code.setPadding(padding, padding, padding, padding);
        code.setTextIsSelectable(true);
        code.setHorizontallyScrolling(false);
        codePane.addView(code, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(page);
        page.requestApplyInsets();

        try {
            html = new HtmlArtifactStore(this).read(artifactId);
            code.setText(html);
        } catch (IOException invalid) {
            status.setText(invalid.getMessage());
            code.setText("لم أتمكن من فتح ملف المعاينة. ارجع إلى المحادثة وافتح الكود أو أنشئ المعاينة مجددًا.");
            copy.setEnabled(false);
            codeVisible = true;
        }
        if (html != null) createWebView(content);
        content.addView(codePane, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        previewButton.setOnClickListener(view -> showCode(false));
        codeButton.setOnClickListener(view -> showCode(true));
        copy.setOnClickListener(view -> {
            if (html == null) return;
            try {
                ClipboardManager clipboard = getSystemService(ClipboardManager.class);
                if (clipboard == null) throw new IllegalStateException("Clipboard unavailable");
                clipboard.setPrimaryClip(ClipData.newPlainText("HTML · MAX", html));
                Toast.makeText(this, "تم نسخ كود HTML", Toast.LENGTH_SHORT).show();
            } catch (RuntimeException failure) {
                Toast.makeText(this, "تعذّر النسخ. يمكنك تحديد الكود ونسخه من هذه الشاشة.", Toast.LENGTH_LONG).show();
            }
        });
        showCode(codeVisible || web == null);
    }

    @SuppressWarnings("deprecation")
    private void createWebView(FrameLayout content) {
        try {
            web = new WebView(this);
            web.setId(R.id.html_preview_webview);
            web.setContentDescription("معاينة HTML محلية");
            WebSettings settings = web.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);
            settings.setBlockNetworkLoads(true);
            settings.setBlockNetworkImage(true);
            settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
            settings.setDomStorageEnabled(false);
            settings.setDatabaseEnabled(false);
            settings.setSaveFormData(false);
            settings.setGeolocationEnabled(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            settings.setJavaScriptCanOpenWindowsAutomatically(false);
            settings.setSupportMultipleWindows(false);
            settings.setMediaPlaybackRequiresUserGesture(true);
            settings.setSafeBrowsingEnabled(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
            // Never add a JavascriptInterface or change global cookie preferences.
            web.setDownloadListener((url, agent, disposition, mime, size) -> blocked());
            web.setWebChromeClient(new WebChromeClient() {
                @Override public void onPermissionRequest(PermissionRequest request) { request.deny(); }
                @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                    callback.invoke(origin, false, false);
                }
                @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                    callback.onReceiveValue(null); blocked(); return true;
                }
                @Override public boolean onCreateWindow(WebView view, boolean dialog, boolean userGesture, android.os.Message result) {
                    blocked(); return false;
                }
                @Override public void onShowCustomView(View view, CustomViewCallback callback) { callback.onCustomViewHidden(); }
                @Override public boolean onJsAlert(WebView view, String url, String message, android.webkit.JsResult result) {
                    result.cancel(); return true;
                }
                @Override public boolean onJsConfirm(WebView view, String url, String message, android.webkit.JsResult result) {
                    result.cancel(); return true;
                }
                @Override public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, android.webkit.JsPromptResult result) {
                    result.cancel(); return true;
                }
            });
            web.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { blocked(); return true; }
                @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { blocked(); return true; }
                @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) { return denied(); }
                @Override public WebResourceResponse shouldInterceptRequest(WebView view, String url) { return denied(); }
                @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                    if (!PREVIEW_ORIGIN.equals(url) && !"about:blank".equals(url)) { view.stopLoading(); blocked(); }
                }
                @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) { handler.cancel(); }
                @Override public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) { handler.cancel(); }
                @Override public void onReceivedClientCertRequest(WebView view, ClientCertRequest request) { request.cancel(); }
                @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                    releaseWebView();
                    fallback("توقّف محرك المعاينة. أعد فتحها لتشغيل الصفحة؛ الكود متاح للنسخ.");
                    return true;
                }
            });
            content.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            // The policy is parsed before every byte of untrusted HTML. Additional policies
            // inside the document cannot relax it. Inline scripts/styles remain useful.
            String secured = "<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<meta http-equiv=\"Content-Security-Policy\" content=\"" + CSP + "\">"
                + "<meta http-equiv=\"X-DNS-Prefetch-Control\" content=\"off\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" + BLOCK_EXTRA_APIS + "</head><body>"
                + html + "</body></html>";
            web.loadDataWithBaseURL(PREVIEW_ORIGIN, secured, "text/html", "UTF-8", null);
        } catch (RuntimeException | LinkageError unavailable) {
            releaseWebView();
            fallback("لتشغيل المعاينة فعّل أو حدّث Android System WebView أو Chrome من إعدادات الجهاز، ثم أعد فتحها. الكود متاح للنسخ.");
        }
    }

    private static WebResourceResponse denied() {
        return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked",
            Collections.singletonMap("Cache-Control", "no-store"),
            new ByteArrayInputStream("Blocked in local HTML preview".getBytes(StandardCharsets.UTF_8)));
    }

    private void blocked() {
        if (status != null) status.setText("تم حظر رابط أو طلب خارجي. تظل المعاينة محلية بدون أذونات للجهاز.");
    }

    private void fallback(String message) {
        if (status != null) status.setText(message);
        codeVisible = true;
        if (codePane != null) showCode(true);
    }

    private void showCode(boolean show) {
        codeVisible = show || web == null;
        if (web != null) web.setVisibility(codeVisible ? View.GONE : View.VISIBLE);
        codePane.setVisibility(codeVisible ? View.VISIBLE : View.GONE);
        previewButton.setEnabled(web != null && codeVisible);
        codeButton.setEnabled(!codeVisible);
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(13);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        return button;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString(EXTRA_ARTIFACT, artifactId);
        state.putBoolean("code_visible", codeVisible);
        super.onSaveInstanceState(state);
    }
    @Override protected void onResume() { super.onResume(); if (web != null) web.onResume(); }
    @Override protected void onPause() { if (web != null) web.onPause(); super.onPause(); }
    @Override protected void onDestroy() { releaseWebView(); super.onDestroy(); }

    private void releaseWebView() {
        WebView previous = web;
        web = null;
        if (previous == null) return;
        try {
            if (previous.getParent() instanceof ViewGroup) ((ViewGroup) previous.getParent()).removeView(previous);
            previous.stopLoading();
            previous.setDownloadListener(null);
            previous.setWebChromeClient(null);
            previous.setWebViewClient(new WebViewClient());
            previous.destroy();
        } catch (RuntimeException | LinkageError providerError) { /* A failed provider can also reject cleanup. */ }
    }
}
