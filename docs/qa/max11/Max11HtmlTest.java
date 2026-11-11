package qa.max11;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.webkit.WebSettings;
import android.webkit.WebView;
import com.maxassistant.app.preview.HtmlArtifactStore;
import com.maxassistant.app.preview.HtmlDocuments;
import com.maxassistant.app.preview.HtmlPreviewActivity;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Actual official WebView execution, not a host-side JS substitute or missing-provider fallback. */
public final class Max11HtmlTest extends Max11UiBase {
    Activity preview;
    WebView web;
    void open(String html) throws Exception {
        String id=new HtmlArtifactStore(target).save(html);
        assertEquals(HtmlDocuments.extract(html),new HtmlArtifactStore(target).read(id));
        preview=getInstrumentation().startActivitySync(new Intent(target,HtmlPreviewActivity.class)
            .putExtra("artifact_id",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();
        getInstrumentation().runOnMainSync(() -> web=preview.findViewById(target.getResources().getIdentifier("html_preview_webview","id",target.getPackageName())));
        assertNotNull("A real WebView must exist; provider fallback is not a pass",web);
        await("Document ready in WebView",()->"\"complete\"".equals(js("document.readyState")),30000);
    }
    String js(String script) throws Exception {
        AtomicReference<String> value=new AtomicReference<>();CountDownLatch complete=new CountDownLatch(1);
        getInstrumentation().runOnMainSync(() -> web.evaluateJavascript(script,v -> {value.set(v);complete.countDown();}));
        assertTrue("WebView JS callback",complete.await(20,TimeUnit.SECONDS));return value.get();
    }
    @Override protected void tearDown() throws Exception {
        if(preview!=null)getInstrumentation().runOnMainSync(() -> preview.finish());super.tearDown();
    }
    public void testCalculatorExecutesTwoPlusThreeTimesFour() throws Exception {
        open(HtmlDocuments.calculator());
        assertNotNull(WebView.getCurrentWebViewPackage());
        assertEquals("com.google.android.webview",WebView.getCurrentWebViewPackage().packageName);
        assertEquals("\"14\"",js("['2','+','3','*','4','='].forEach(function(k){document.querySelector('[data-key=\"'+k+'\"]').click()});document.querySelector('#display').textContent"));
        assertEquals("\"14\"",js("document.querySelector('#display').textContent"));
        screenshot("max11-calculator-actual-14");
        System.out.println("ACTUAL_WEBVIEW_CALCULATOR=2+3*4=14; provider="+WebView.getCurrentWebViewPackage().versionName);
    }
    public void testMainCalculatorCommandRunsBeforeModelReadinessCheck() throws Exception {
        launchMain();
        android.app.Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(HtmlPreviewActivity.class.getName(),null,false);
        try {
            int inputId=target.getResources().getIdentifier("assistant_input","id",target.getPackageName());
            int sendId=target.getResources().getIdentifier("assistant_send","id",target.getPackageName());
            getInstrumentation().runOnMainSync(() -> {
                android.widget.EditText input=main.findViewById(inputId);assertNotNull(input);
                input.setText("اكتب HTML لآلة حاسبة وشغلها");assertTrue(main.findViewById(sendId).performClick());
            });
            preview=monitor.waitForActivityWithTimeout(45000);assertNotNull("HTML command opens preview without downloaded model",preview);
            getInstrumentation().waitForIdleSync();
            getInstrumentation().runOnMainSync(() -> web=preview.findViewById(target.getResources().getIdentifier("html_preview_webview","id",target.getPackageName())));
            assertNotNull("Real preview WebView",web);
            await("Calculator DOM ready after main composer send",()->"true".equals(js("!!document.querySelector('#display')")),30000);
            assertEquals("\"14\"",js("['2','+','3','*','4','='].forEach(function(k){document.querySelector('[data-key=\"'+k+'\"]').click()});document.querySelector('#display').textContent"));
            screenshot("max11-main-command-calculator-14");
        } finally {getInstrumentation().removeMonitor(monitor);}
    }
    public void testSandboxBlocksNetworkFileContentAndNativeBridge() throws Exception {
        // The host run script supplies a local HTTP observation server through adb reverse.
        try(Socket socket=new Socket("127.0.0.1",8181)) {
            socket.getOutputStream().write("GET /native-control HTTP/1.0\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            assertTrue("Observation server is reachable outside WebView",socket.getInputStream().read()!=-1);
        }
        open("<!doctype html><html><body><p id='proof'>ready</p></body></html>");
        getInstrumentation().runOnMainSync(() -> {
            WebSettings s=web.getSettings();assertTrue(s.getJavaScriptEnabled());assertTrue(s.getBlockNetworkLoads());
            assertTrue(s.getBlockNetworkImage());assertFalse(s.getAllowFileAccess());assertFalse(s.getAllowContentAccess());
            assertFalse(s.getAllowFileAccessFromFileURLs());assertFalse(s.getAllowUniversalAccessFromFileURLs());
            assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW,s.getMixedContentMode());
        });
        assertEquals("\"undefined,undefined\"",js("typeof window.Android+','+typeof window.max"));
        assertEquals("\"undefined,undefined,undefined\"",js("typeof window.RTCPeerConnection+','+typeof window.WebTransport+','+typeof window.AudioContext"));
        js("window.__probe='pending';fetch('http://127.0.0.1:8181/blocked-fetch').then(function(){window.__probe='unexpected-success'}).catch(function(){window.__probe='blocked'});'started'");
        await("Remote fetch rejected",()->"\"blocked\"".equals(js("window.__probe")),15000);
        js("window.__file='pending';fetch('file:///data/data/com.maxassistant.app/shared_prefs/max-secrets.xml').then(function(){window.__file='unexpected-success'}).catch(function(){window.__file='blocked'});'started'");
        await("Private file request rejected",()->"\"blocked\"".equals(js("window.__file")),15000);
        js("window.__content='pending';fetch('content://qa.facebook.state/').then(function(){window.__content='unexpected-success'}).catch(function(){window.__content='blocked'});'started'");
        await("Content provider request rejected",()->"\"blocked\"".equals(js("window.__content")),15000);
        js("window.__image='pending';var image=new Image();image.onload=function(){window.__image='unexpected-success'};image.onerror=function(){window.__image='blocked'};image.src='http://127.0.0.1:8181/blocked-image';document.body.appendChild(image);'started'");
        await("Remote image rejected",()->"\"blocked\"".equals(js("window.__image")),15000);
        js("var link=document.createElement('a');link.href='http://127.0.0.1:8181/blocked-navigation';link.textContent='blocked';document.body.appendChild(link);link.click();'started'");
        SystemClock.sleep(300);
        assertEquals("\"https://max-preview.invalid/\"",js("location.href"));
        assertEquals("\"ready\"",js("document.querySelector('#proof').textContent"));
        System.out.println("ACTUAL_WEBVIEW_SANDBOX=fetch/file/content blocked; native observation server reachable");
    }
}
