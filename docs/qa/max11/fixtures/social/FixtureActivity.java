package qa.fixture;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.ref.WeakReference;

/** Native, local-only fake social application. No network permission or account. */
public final class FixtureActivity extends Activity {
    static WeakReference<FixtureActivity> current = new WeakReference<>(null);
    SharedPreferences state;
    LinearLayout layout;
    EditText editor;
    boolean whatsapp;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        current = new WeakReference<>(this);
        state = getSharedPreferences("fixture", MODE_PRIVATE);
        whatsapp = getPackageName().equals("com.whatsapp");
        if (whatsapp && Intent.ACTION_SEND.equals(getIntent().getAction())) contacts();
        else feed();
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        if (whatsapp && Intent.ACTION_SEND.equals(intent.getAction())) contacts(); else feed();
    }
    void root(String stage) {
        editor = null;
        state.edit().putString("stage",stage).commit();
        layout = new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24,36,24,24);setContentView(layout);
        label("FIXTURE ONLY — local " + (whatsapp ? "WhatsApp" : "Facebook"));
    }
    TextView label(String text) {
        TextView v=new TextView(this);v.setText(text);v.setTextSize(20);layout.addView(v);return v;
    }
    Button button(String text,View.OnClickListener action) {
        Button b=new Button(this);b.setText(text);b.setOnClickListener(action);layout.addView(b);return b;
    }
    int id(String name) { return getResources().getIdentifier(name,"id",getPackageName()); }
    void feed() {
        root("feed");
        if (whatsapp) button("مروة",v -> chat("مروة"));
        else {
            button("بم تفكر؟",v -> compose());
            button("مشاركة",v -> state.edit().putInt("shareCount",state.getInt("shareCount",0)+1).commit());
        }
    }
    void contacts() {
        root("contacts");label("إرسال إلى");
        EditText search=new EditText(this);search.setId(id("search_src_text"));search.setHint("بحث");layout.addView(search);
        button("مروة",v -> chat("مروة"));button("عمر",v -> chat("عمر"));
    }
    void editor(String text) {
        editor=new EditText(this);editor.setId(id("entry"));editor.setHint("اكتب هنا");
        editor.setText(text);editor.setMinLines(3);layout.addView(editor);
        state.edit().putString("draft",text).commit();
        editor.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
            public void onTextChanged(CharSequence s,int start,int before,int count) { state.edit().putString("draft",s.toString()).commit(); }
            public void afterTextChanged(Editable e) { }
        });
    }
    void compose() {
        root("composer");label("إنشاء منشور");editor("");button("نشر",v -> commit());
    }
    void chat(String recipient) {
        root("chat");state.edit().putString("recipient",recipient).commit();
        TextView header=label(recipient);header.setId(id("conversation_contact_name"));
        editor(getIntent().getStringExtra(Intent.EXTRA_TEXT)==null ? "" : getIntent().getStringExtra(Intent.EXTRA_TEXT));
        button("إرسال",v -> commit());
    }
    void commit() {
        state.edit().putInt("commitCount",state.getInt("commitCount",0)+1)
            .putString("committedDraft",state.getString("draft",""))
            .putString("committedRecipient",state.getString("recipient","")).commit();
        label("تم حفظ الالتزام محليًا — FIXTURE");
    }
}
