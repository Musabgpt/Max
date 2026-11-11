package qa.max11;

import android.content.Context;
import android.test.InstrumentationTestCase;
import com.maxassistant.app.data.AppStore;
import com.maxassistant.app.data.SecureSecrets;

/** Seeds only fixture data in the old production v1.0.1 APK on a disposable emulator. */
public final class Max11UpgradeSeedTest extends InstrumentationTestCase {
    public void testSeedOldSchemaAndEncryptedKey() throws Exception {
        Context c=getInstrumentation().getTargetContext();
        assertEquals(2,c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionCode);
        SecureSecrets secrets=new SecureSecrets(c);secrets.setNvidiaKey("nvapi-max11-local-fixture-only");
        c.getSharedPreferences("max-secrets",0).edit().putBoolean("max11_upgrade_seed",true).commit();
        assertEquals("nvapi-max11-local-fixture-only",secrets.getNvidiaKey());
        try(AppStore store=new AppStore(c)) {
            assertEquals(1,store.getWritableDatabase().getVersion());
            store.prefs().edit().putBoolean("onboarding_done",true).putString("provider","local")
                .putString("max11_upgrade_marker","settings-survive").commit();
            store.addMemory("max11-upgrade-memory","Local fixture memory survives v1.0.1 to v1.1.0");
            store.addMessage("user","max11-legacy-user-message");
            store.addMessage("assistant","<!doctype html><html><body>max11-legacy-html</body></html>");
            assertEquals(2,store.getMessages(500).size());
        }
    }
}
