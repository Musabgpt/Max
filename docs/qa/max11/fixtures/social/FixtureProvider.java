package qa.fixture;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/** Test-only oracle: reads counters and simulates user edits in the local fixture. */
public final class FixtureProvider extends ContentProvider {
    public boolean onCreate() { return true; }
    @Override public Bundle call(String method,String arg,Bundle extras) {
        SharedPreferences p=getContext().getSharedPreferences("fixture",0);
        if ("reset".equals(method)) p.edit().clear().commit();
        if ("draft".equals(method)) {
            p.edit().putString("draft",arg).commit();
            FixtureActivity a=FixtureActivity.current.get();
            if(a!=null) a.runOnUiThread(() -> {if(a.editor!=null)a.editor.setText(arg);});
        }
        if ("recipient".equals(method)) {
            p.edit().putString("recipient",arg).commit();
            FixtureActivity a=FixtureActivity.current.get();
            if(a!=null) a.runOnUiThread(() -> a.chat(arg));
        }
        Bundle b=new Bundle();
        for (String key:new String[]{"draft","committedDraft","recipient","committedRecipient","stage"})b.putString(key,p.getString(key,""));
        b.putInt("commitCount",p.getInt("commitCount",0));b.putInt("shareCount",p.getInt("shareCount",0));return b;
    }
    public Cursor query(Uri u,String[] p,String s,String[] a,String o) {return null;}
    public String getType(Uri u) {return null;}
    public Uri insert(Uri u,ContentValues v) {throw new UnsupportedOperationException();}
    public int delete(Uri u,String s,String[] a) {throw new UnsupportedOperationException();}
    public int update(Uri u,ContentValues v,String s,String[] a) {throw new UnsupportedOperationException();}
}
