package qa.speech;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.widget.TextView;
import java.util.ArrayList;

/** System recognition-activity contract fixture; emits a transcript, not a send. */
public final class FixtureSpeechActivity extends Activity {
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        TextView label=new TextView(this);label.setText("FIXTURE — recognition activity");setContentView(label);
        getSharedPreferences("fixture",0).edit().putInt("activityCalls",getSharedPreferences("fixture",0).getInt("activityCalls",0)+1)
            .putString("activityLanguage",getIntent().getStringExtra(RecognizerIntent.EXTRA_LANGUAGE)).commit();
        new Handler(getMainLooper()).postDelayed(() -> {
            ArrayList<String> results=new ArrayList<>();results.add("افتح واتساب");
            setResult(RESULT_OK,new Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS,results));finish();
        },300);
    }
}
