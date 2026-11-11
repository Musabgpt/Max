package qa.max11;

import android.content.Context;
import android.database.Cursor;
import android.test.InstrumentationTestCase;
import com.maxassistant.app.data.AppStore;
import com.maxassistant.app.data.SecureSecrets;

/** Verifies the same Android Keystore and SQLite data after an install -r update. */
public final class Max11UpgradeVerifyTest extends InstrumentationTestCase {
    public void testEncryptedKeyMemoryAndLegacyChatSurvive() throws Exception {
        Context c=getInstrumentation().getTargetContext();
        assertEquals(3,c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionCode);
        assertEquals("nvapi-max11-local-fixture-only",new SecureSecrets(c).getNvidiaKey());
        assertTrue(c.getSharedPreferences("max-secrets",0).getBoolean("max11_upgrade_seed",false));
        try(AppStore store=new AppStore(c)) {
            assertEquals(2,store.getReadableDatabase().getVersion());
            assertEquals("settings-survive",store.prefs().getString("max11_upgrade_marker",""));
            boolean memory=false;
            for(AppStore.Memory m:store.getMemories())if("max11-upgrade-memory".equals(m.title))memory=true;
            assertTrue("Legacy memory preserved",memory);
            boolean user=false,html=false;
            for(AppStore.Message m:store.getMessages(500)) {
                if("max11-legacy-user-message".equals(m.content))user=true;
                if(m.content.contains("max11-legacy-html"))html=true;
            }
            assertTrue("Legacy user message preserved",user);assertTrue("Legacy HTML answer preserved",html);
            try(Cursor row=store.getReadableDatabase().rawQuery("SELECT artifact_id FROM messages WHERE content=?",new String[]{"max11-legacy-user-message"})) {
                assertTrue(row.moveToFirst());assertTrue("Legacy artifact stays unset",row.isNull(0)||row.getString(0).isEmpty());
            }
            boolean artifactColumn=false;
            try(Cursor columns=store.getReadableDatabase().rawQuery("PRAGMA table_info(messages)",null)) {
                while(columns.moveToNext())if("artifact_id".equals(columns.getString(1)))artifactColumn=true;
            }
            assertTrue("Schema2 artifact column exists",artifactColumn);
        }
    }
}
