package com.maxassistant.app.upgradetest;

import android.content.Context;
import android.test.InstrumentationTestCase;
import com.maxassistant.app.ai.NvidiaClient;
import com.maxassistant.app.data.AppStore;
import com.maxassistant.app.data.SecureSecrets;
import org.json.JSONArray;

/** Real Android Keystore/update checks; inference transport below is deterministic. */
public final class NvidiaUpgradeVerifyTest extends InstrumentationTestCase {
    public void testSavedKeyAndMemorySurviveUpdate() throws Exception {
        Context context=getInstrumentation().getTargetContext();
        assertEquals(2,context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionCode);
        assertEquals("nvapi-upgrade-test-fixture-only",new SecureSecrets(context).getNvidiaKey());
        AppStore store=new AppStore(context);
        boolean found=false;for(AppStore.Memory memory:store.getMemories())if("upgrade-test".equals(memory.title))found=true;
        assertTrue(found);
        assertEquals("auto",NvidiaClient.normalizeChoice(store.prefs().getString("nvidia_model","")));
        store.close();
    }
    public void testGoneRecoveryWithAndroidJson() throws Exception {
        final int[] calls={0};
        NvidiaClient.Transport transport=new NvidiaClient.Transport(){
            public NvidiaClient.Response send(String method,String url,String key,String body){
                int step=calls[0]++;
                if(step==0)return new NvidiaClient.Response(410,"gone");
                if(step==1){assertEquals("GET",method);assertNull(key);return new NvidiaClient.Response(200,"{\"data\":[{\"id\":\"nvidia/nemotron-3.5-lightning-30b-a3b\"}]}");}
                return new NvidiaClient.Response(200,"{\"choices\":[{\"message\":{\"content\":\"أهلًا\"}}]}");
            }
        };
        NvidiaClient.Result result=new NvidiaClient("nvapi-test-fixture-only",transport).complete("example/gone-instruct","",new JSONArray("[{\"role\":\"user\",\"content\":\"Hi\"}]"));
        assertEquals("أهلًا",result.text);assertTrue(result.switched);assertEquals(3,calls[0]);
    }
}
