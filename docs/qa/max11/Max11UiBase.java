package qa.max11;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import com.maxassistant.app.MainActivity;
import com.maxassistant.app.automation.AutomationService;
import com.maxassistant.app.data.AppStore;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/** Actual Android UI access, preserving the target accessibility service. */
public abstract class Max11UiBase extends InstrumentationTestCase {
    UiAutomation ui;
    Context target;
    Activity main;
    @Override protected void setUp() throws Exception {
        super.setUp();target=getInstrumentation().getTargetContext();
        ui=getInstrumentation().getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        AccessibilityServiceInfo info=ui.getServiceInfo();info.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS|AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;ui.setServiceInfo(info);
        try(AppStore store=new AppStore(target)) {store.prefs().edit().putBoolean("onboarding_done",true).putString("provider","local").commit();}
        shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard");shell("input keyevent KEYCODE_MENU");
    }
    @Override protected void tearDown() throws Exception {
        if(target!=null)AutomationService.cancel(target);
        if(main!=null)getInstrumentation().runOnMainSync(() -> main.finish());
        super.tearDown();
    }
    Activity launchMain() {
        main=getInstrumentation().startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();return main;
    }
    String shell(String command) throws Exception {
        try(ParcelFileDescriptor fd=ui.executeShellCommand(command);FileInputStream in=new FileInputStream(fd.getFileDescriptor());ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");
        }
    }
    void await(String reason,Callable<Boolean> predicate,long timeout) throws Exception {
        long end=SystemClock.uptimeMillis()+timeout;
        while(SystemClock.uptimeMillis()<end) {if(predicate.call())return;SystemClock.sleep(150);}
        fail(reason+"; current UI:\n"+tree());
    }
    List<AccessibilityNodeInfo> roots() {
        List<AccessibilityNodeInfo> roots=new ArrayList<>();
        for(AccessibilityWindowInfo w:ui.getWindows()) {AccessibilityNodeInfo r=w.getRoot();if(r!=null)roots.add(r);}
        AccessibilityNodeInfo active=ui.getRootInActiveWindow();if(active!=null)roots.add(active);return roots;
    }
    AccessibilityNodeInfo find(String value,boolean description) {
        for(AccessibilityNodeInfo root:roots()) {AccessibilityNodeInfo n=findIn(root,value,description);if(n!=null)return n;}
        return null;
    }
    AccessibilityNodeInfo findIn(AccessibilityNodeInfo n,String value,boolean description) {
        if(n==null)return null;
        CharSequence text=description?n.getContentDescription():n.getText();
        if(n.isVisibleToUser()&&text!=null&&value.contentEquals(text))return n;
        for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo found=findIn(n.getChild(i),value,description);if(found!=null)return found;}
        return null;
    }
    void click(String text) throws Exception {
        await("Missing clickable text "+text,()->find(text,false)!=null,15000);
        AccessibilityNodeInfo n=find(text,false);while(n!=null&&!n.isClickable())n=n.getParent();
        assertNotNull(n);assertTrue("Click "+text,n.performAction(AccessibilityNodeInfo.ACTION_CLICK));
    }
    void clickDescription(String description) throws Exception {
        await("Missing control "+description,()->find(description,true)!=null,15000);
        assertTrue(find(description,true).performAction(AccessibilityNodeInfo.ACTION_CLICK));
    }
    Bundle state(String kind) {return target.getContentResolver().call(android.net.Uri.parse("content://qa."+kind+".state"),"state",null,null);}
    void fixture(String kind,String method,String arg) {target.getContentResolver().call(android.net.Uri.parse("content://qa."+kind+".state"),method,arg,null);}
    String tree() {StringBuilder b=new StringBuilder();for(AccessibilityNodeInfo r:roots())dump(r,b,0);return b.toString();}
    void dump(AccessibilityNodeInfo n,StringBuilder b,int depth) {
        if(n==null||depth>24||b.length()>14000)return;
        if(n.isVisibleToUser())b.append(n.getPackageName()).append(" | ").append(n.getViewIdResourceName()).append(" | ").append(n.getText()).append(" | ").append(n.getContentDescription()).append('\n');
        for(int i=0;i<n.getChildCount();i++)dump(n.getChild(i),b,depth+1);
    }
    void screenshot(String name) throws Exception {
        android.graphics.Bitmap bitmap=ui.takeScreenshot();assertNotNull("Android screenshot",bitmap);
        File destination=new File(target.getExternalFilesDir(null),name+".png");
        try(FileOutputStream stream=new FileOutputStream(destination)) {assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream));}
        bitmap.recycle();
    }
}
