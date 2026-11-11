package com.maxassistant.app.automation;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.text.InputType;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;

/** First-party browser. Page content is untrusted and receives no Java/native bridge. */
public final class BrowserActivity extends Activity {
    private WebView web;
    private EditText address;
    private TextView status;
    private static final String FIELDS = "Array.from(document.querySelectorAll('input,textarea')).filter(e=>!e.disabled&&!e.readOnly&&e.getClientRects().length&&(e.tagName==='TEXTAREA'||!e.type||['text','email','tel','search','url','number'].includes(e.type))&&!/password|credit|card|cvc|cvv|otp|one.?time|cc-/i.test([e.name,e.id,e.autocomplete].join(' ')))";
    private static final String SIGNATURE = "e=>JSON.stringify([e.name,e.id,e.type,e.getAttribute('aria-label'),e.placeholder])";

    public static void open(Context context, String url) {
        String checked = normalize(url);
        Intent intent = new Intent(context, BrowserActivity.class).putExtra("url", checked);
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    static String normalize(String input) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) return "https://www.google.com";
        if (!value.contains("://")) {
            if (value.contains(" ") || !value.contains(".")) return "https://www.google.com/search?q=" + Uri.encode(value);
            value = "https://" + value;
        }
        Uri uri = Uri.parse(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
            throw new IllegalArgumentException("المتصفح يقبل مواقع HTTPS فقط، دون بيانات دخول ضمن العنوان.");
        return uri.toString();
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.rgb(14, 20, 31));
        page.setPadding(10, 10, 10, 4);
        page.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(10 + insets.getSystemWindowInsetLeft(), 10 + insets.getSystemWindowInsetTop(),
                10 + insets.getSystemWindowInsetRight(), 4 + insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout toolbar = new LinearLayout(this);
        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextColor(Color.WHITE);
        address.setHintTextColor(Color.LTGRAY);
        address.setHint("https://example.com");
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        toolbar.addView(address, new LinearLayout.LayoutParams(0, -2, 1));
        Button go = new Button(this); go.setText("اذهب"); toolbar.addView(go);
        page.addView(toolbar);
        LinearLayout tools = new LinearLayout(this);
        Button back = new Button(this); back.setText("رجوع"); tools.addView(back, new LinearLayout.LayoutParams(0, -2, 1));
        Button fill = new Button(this); fill.setText("تعبئة حقل"); tools.addView(fill, new LinearLayout.LayoutParams(0, -2, 1));
        Button external = new Button(this); external.setText("خارج التطبيق"); tools.addView(external, new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(tools);
        status = new TextView(this); status.setTextColor(Color.LTGRAY); status.setTextSize(12);
        status.setText("راجع الحجز والدفع والإرسال بنفسك. لا تُقرأ حقول كلمات المرور أو الدفع.");
        page.addView(status);
        // Provider availability differs by device. Keep the catch confined to provider initialization.
        try {
            web = new WebView(this);
            WebSettings settings = web.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            settings.setJavaScriptCanOpenWindowsAutomatically(false);
            settings.setSupportMultipleWindows(false);
            settings.setMediaPlaybackRequiresUserGesture(true);
            settings.setSafeBrowsingEnabled(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        } catch (RuntimeException | LinkageError providerUnavailable) {
            if (web != null) {
                try { web.destroy(); } catch (RuntimeException | LinkageError cleanupError) { /* Partially initialized provider. */ }
            }
            web = null;
        }
        if (web != null) {
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int progress) { if (progress < 100) status.setText("تحميل الصفحة · " + progress + "%"); }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                try { normalize(request.getUrl().toString()); return false; }
                catch (IllegalArgumentException error) { toast("تم إيقاف رابط غير آمن أو يحتاج تطبيقًا خارجيًا."); return true; }
            }
            @Override public void onPageFinished(WebView view, String url) {
                address.setText(url);
                status.setText("راجع التفاصيل قبل الإرسال أو الدفع · " + Uri.parse(url).getHost());
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel(); status.setText("أوقفت الصفحة بسبب مشكلة في شهادة الاتصال.");
            }
        });
        page.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        } else {
            status.setText("المتصفح الداخلي غير متاح على هذا الجهاز.");
            TextView guidance = new TextView(this);
            guidance.setTextColor(Color.WHITE);
            guidance.setTextSize(18);
            guidance.setPadding(20, 32, 20, 20);
            guidance.setText("ثبّت أو فعّل Android System WebView أو Chrome المدعوم من متجر هاتفك، ثم افتح الصفحة مجددًا.\n\nيمكنك فتح الرابط في متصفح خارجي إذا كان مثبتًا. تعبئة النماذج داخل MAX تحتاج إلى WebView.");
            page.addView(guidance, new LinearLayout.LayoutParams(-1, 0, 1));
            fill.setEnabled(false);
            go.setText("افتح خارجيًا");
        }
        setContentView(page);
        page.requestApplyInsets();
        go.setOnClickListener(v -> { if (web == null) openExternal(); else navigate(address.getText().toString()); });
        address.setOnEditorActionListener((v, action, event) -> { if (web == null) openExternal(); else navigate(address.getText().toString()); return true; });
        back.setOnClickListener(v -> { if (web != null && web.canGoBack()) web.goBack(); else finish(); });
        fill.setOnClickListener(v -> showFields());
        external.setOnClickListener(v -> openExternal());
        String requestedUrl = state == null ? getIntent().getStringExtra("url") : state.getString("fallback_url", getIntent().getStringExtra("url"));
        if (web != null) {
            if (state == null || web.restoreState(state) == null) navigate(requestedUrl);
        } else {
            try { address.setText(normalize(requestedUrl)); }
            catch (IllegalArgumentException invalid) { address.setText("https://www.google.com"); toast(invalid.getMessage()); }
        }
    }

    private void openExternal() {
        try {
            String url = normalize(address.getText().toString());
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (IllegalArgumentException invalid) { toast(invalid.getMessage()); }
        catch (android.content.ActivityNotFoundException noBrowser) { toast("لا يوجد متصفح خارجي مثبت. ثبّت متصفحًا أو فعّل Android System WebView ثم حاول مجددًا."); }
        catch (SecurityException blocked) { toast("منع Android فتح المتصفح الخارجي. افتح الرابط يدويًا في متصفحك."); }
    }

    private void navigate(String url) {
        try { web.loadUrl(normalize(url)); }
        catch (IllegalArgumentException error) { toast(error.getMessage()); }
    }

    private void showFields() {
        final String origin = web.getUrl();
        if (origin == null || !origin.startsWith("https://")) return;
        final String token = java.util.UUID.randomUUID().toString();
        String enumerate = "(()=>{const fields=(" + FIELDS + ");window.__maxFormReview={token:" + JSONObject.quote(token) + ",url:location.href,items:fields.map(e=>({node:e,signature:(" + SIGNATURE + ")(e)}))};return JSON.stringify(fields.map((e,i)=>({index:i,label:e.getAttribute('aria-label')||e.placeholder||e.name||e.id||('حقل '+(i+1))})))})()";
        web.evaluateJavascript(enumerate, raw -> {
            try {
                Object decoded = new org.json.JSONTokener(raw).nextValue();
                JSONArray fields = new JSONArray(String.valueOf(decoded));
                if (fields.length() == 0) { toast("لا توجد حقول نصية متاحة. حقول كلمات المرور والدفع مستثناة."); return; }
                String[] labels = new String[Math.min(fields.length(), 100)];
                for (int i = 0; i < labels.length; i++) labels[i] = fields.getJSONObject(i).getString("label");
                new AlertDialog.Builder(this).setTitle("اختر الحقل · " + Uri.parse(origin).getHost())
                    .setItems(labels, (dialog, index) -> fillField(origin, token, index, labels[index])).setNegativeButton("إلغاء", null).show();
            } catch (Exception error) { toast("تعذّر قراءة حقول الصفحة."); }
        });
    }

    private void fillField(String expectedUrl, String token, int index, String label) {
        EditText input = new EditText(this); input.setHint("النص الذي تريد تعبئته");
        new AlertDialog.Builder(this).setTitle(label).setView(input)
            .setMessage("سيُملأ هذا الحقل فقط دون إرسال النموذج.")
            .setNegativeButton("إلغاء", null).setPositiveButton("تعبئة", (dialog, which) -> {
                if (!expectedUrl.equals(web.getUrl())) { toast("تغيّرت الصفحة. اختر الحقل مرة أخرى."); return; }
                String value = input.getText().toString();
                if (value.length() > 10000) { toast("النص طويل جدًا."); return; }
                String script = "(()=>{if(location.href!==" + JSONObject.quote(expectedUrl) + ")return false;const review=window.__maxFormReview;if(!review||review.token!==" + JSONObject.quote(token) + "||review.url!==location.href)return false;const item=review.items[" + index + "];if(!item)return false;const e=item.node;if(!e.isConnected||!(" + FIELDS + ").includes(e)||item.signature!==(" + SIGNATURE + ")(e))return false;const p=e.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;Object.getOwnPropertyDescriptor(p,'value').set.call(e," + JSONObject.quote(value) + ");delete window.__maxFormReview;e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));return true})()";
                web.evaluateJavascript(script, result -> toast("true".equals(result) ? "تمت التعبئة. راجع النص قبل إرسال النموذج." : "تغيّر الحقل؛ حاول مجددًا."));
            }).show();
    }

    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    @Override public void onBackPressed() { if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed(); }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putString("fallback_url", address.getText().toString()); if (web != null) web.saveState(out); }
    @Override protected void onPause() { if (web != null) web.onPause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if (web != null) web.onResume(); }
    @Override protected void onDestroy() { if (web != null) { web.stopLoading(); web.destroy(); } super.onDestroy(); }
}
