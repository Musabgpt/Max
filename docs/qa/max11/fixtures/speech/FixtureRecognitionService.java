package qa.speech;

import android.content.Intent;
import android.os.RemoteException;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;

/** Deterministic unsupported-language fixture, never records audio. */
public final class FixtureRecognitionService extends RecognitionService {
    @Override protected void onStartListening(Intent intent,Callback callback) {
        getSharedPreferences("fixture",0).edit().putInt("serviceCalls",getSharedPreferences("fixture",0).getInt("serviceCalls",0)+1)
            .putString("language",intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
            .putBoolean("preferOffline",intent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,false)).commit();
        try {callback.error(12);} catch(RemoteException e) {throw new IllegalStateException(e);}
    }
    @Override protected void onStopListening(Callback callback) { }
    @Override protected void onCancel(Callback callback) { }
}
