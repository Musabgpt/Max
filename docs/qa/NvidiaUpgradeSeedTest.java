package com.maxassistant.app.upgradetest;

import android.content.Context;
import android.test.InstrumentationTestCase;
import com.maxassistant.app.data.AppStore;
import com.maxassistant.app.data.SecureSecrets;

/** Runs inside the old debug APK process on a disposable emulator. */
public final class NvidiaUpgradeSeedTest extends InstrumentationTestCase {
    public void testSeedBeforeUpgrade() throws Exception {
        Context context=getInstrumentation().getTargetContext();
        assertEquals(1,context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionCode);
        SecureSecrets secrets=new SecureSecrets(context);
        secrets.setNvidiaKey("nvapi-upgrade-test-fixture-only");
        // Commit the same preference map, waiting for the encrypted write to persist.
        context.getSharedPreferences("max-secrets",Context.MODE_PRIVATE).edit().putBoolean("upgrade_probe",true).commit();
        assertEquals("nvapi-upgrade-test-fixture-only",secrets.getNvidiaKey());
        AppStore store=new AppStore(context);
        store.prefs().edit().putString("provider","nvidia").putString("nvidia_model","meta/llama-3.1-8b-instruct").commit();
        store.addMemory("upgrade-test","Preserve local memory during APK update");
        store.close();
    }
}
