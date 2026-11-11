package qa.home;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Minimal offline Home for the disposable emulator, independent of Google services. */
public final class FixtureHomeActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView label=new TextView(this);
        label.setText("MAX QA Home");label.setTextSize(28);
        label.setGravity(android.view.Gravity.CENTER);
        setContentView(label);
    }
}
