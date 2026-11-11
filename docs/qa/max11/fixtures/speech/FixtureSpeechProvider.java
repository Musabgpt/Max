package qa.speech;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

public final class FixtureSpeechProvider extends ContentProvider {
    public boolean onCreate() {return true;}
    @Override public Bundle call(String method,String arg,Bundle e) {
        SharedPreferences p=getContext().getSharedPreferences("fixture",0);
        if("reset".equals(method))p.edit().clear().commit();
        Bundle b=new Bundle();b.putInt("serviceCalls",p.getInt("serviceCalls",0));b.putInt("activityCalls",p.getInt("activityCalls",0));
        b.putString("language",p.getString("language",""));b.putString("activityLanguage",p.getString("activityLanguage",""));
        b.putBoolean("preferOffline",p.getBoolean("preferOffline",false));return b;
    }
    public Cursor query(Uri u,String[] p,String s,String[] a,String o) {return null;}
    public String getType(Uri u) {return null;}
    public Uri insert(Uri u,ContentValues v) {throw new UnsupportedOperationException();}
    public int delete(Uri u,String s,String[] a) {throw new UnsupportedOperationException();}
    public int update(Uri u,ContentValues v,String s,String[] a) {throw new UnsupportedOperationException();}
}
