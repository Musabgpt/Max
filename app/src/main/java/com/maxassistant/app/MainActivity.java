package com.maxassistant.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.*;
import android.widget.*;

import com.maxassistant.app.ai.*;
import com.maxassistant.app.data.*;
import com.maxassistant.app.automation.*;
import com.maxassistant.app.service.*;

import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;

/** User-facing control surface. All AI/network work runs off the UI thread. */
public final class MainActivity extends Activity {
    private static final int BG=0xff10151f, CARD=0xff1b2331, TEXT=0xffeff3fa,
            MUTED=0xff9daec3, MINT=0xff79e1c3, BLUE=0xff91b7ff;
    private AppStore store;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private LinearLayout root,content,nav,chatList;
    private ScrollView page;
    private EditText input;
    private TextView status;
    private boolean busy,ttsReady;
    private volatile boolean downloading;
    private String tab="home";
    private ModelManager downloadManager;
    private AiRepository activeAi;
    private SpeechRecognizer speech;
    private TextToSpeech tts;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        store=new AppStore(this);
        if(!store.prefs().contains("work_mode"))store.prefs().edit().putString("work_mode","work").apply();
        tts=new TextToSpeech(this,result->{ttsReady=result==TextToSpeech.SUCCESS;if(ttsReady)tts.setLanguage(Locale.forLanguageTag("ar"));});
        if(state!=null)tab=state.getString("tab","home");
        render();
        if(!store.prefs().getBoolean("onboarding_done",false))main.post(this::onboarding);
    }

    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("tab",tab);}
    @Override protected void onResume(){super.onResume();if(store!=null&&"settings".equals(tab))render();}
    @Override protected void onStop(){super.onStop();if(speech!=null)speech.cancel();}
    @Override protected void onDestroy(){
        if(speech!=null)speech.destroy();
        if(tts!=null){tts.stop();tts.shutdown();}
        if(downloadManager!=null)downloadManager.cancel();
        if(activeAi!=null)activeAi.cancel();worker.shutdownNow();main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(4),0,dp(4));return v;}
    private TextView title(String value){TextView v=text(value,25,TEXT);v.setTypeface(null,Typeface.BOLD);return v;}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private void space(LinearLayout parent,int size){View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(size)));}
    private LinearLayout card(LinearLayout parent){LinearLayout c=column();c.setBackground(shape(CARD,20));c.setPadding(dp(16),dp(14),dp(16),dp(14));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);parent.addView(c,p);return c;}
    private Button button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(TEXT);b.setBackground(shape(0xff2b374b,13));b.setPadding(dp(12),dp(7),dp(12),dp(7));b.setMinHeight(dp(44));b.setMinimumHeight(dp(44));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(8);b.setLayoutParams(p);b.setOnClickListener(v->action.run());return b;}
    private Button primary(String label,Runnable action){Button b=button(label,action);b.setTextColor(BG);b.setBackground(shape(MINT,13));b.setTypeface(null,Typeface.BOLD);return b;}
    private EditText field(String hint,boolean multiline){EditText f=new EditText(this);f.setHint(hint);f.setHintTextColor(MUTED);f.setTextColor(TEXT);f.setTextSize(15);f.setBackground(shape(0xff121a27,12));f.setPadding(dp(13),dp(12),dp(13),dp(12));f.setSingleLine(!multiline);f.setInputType(InputType.TYPE_CLASS_TEXT|(multiline?InputType.TYPE_TEXT_FLAG_MULTI_LINE:0));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(9);f.setLayoutParams(p);return f;}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private String now(long millis){return DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT,new Locale("ar")).format(new Date(millis));}
    private void open(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException ex){toast("لا يوجد متصفح مثبت لفتح الرابط.");}}
    private void safeSettings(Intent intent){try{startActivity(intent);}catch(RuntimeException e){toast("هذه الصفحة غير متاحة في إصدار الهاتف.");}}

    private void render(){
        root=column();root.setBackgroundColor(BG);root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        // API 35 edge-to-edge: keep all controls inside system-bar and keyboard insets.
        root.setOnApplyWindowInsetsListener((v,insets)->{
            v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;
        });
        setContentView(root);root.requestApplyInsets();
        LinearLayout header=row();header.setPadding(dp(20),dp(12),dp(20),dp(12));
        TextView logo=text("✦  MAX",25,MINT);logo.setTypeface(null,Typeface.BOLD);logo.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);header.addView(logo,new LinearLayout.LayoutParams(0,-2,1));
        Button mode=button("sleep".equals(store.prefs().getString("work_mode","work"))?"☾ وضع النوم":"● وضع العمل",this::toggleMode);mode.setTextSize(12);header.addView(mode,new LinearLayout.LayoutParams(-2,dp(42)));root.addView(header);
        page=new ScrollView(this);page.setFillViewport(true);content=column();content.setPadding(dp(20),dp(6),dp(20),dp(14));page.addView(content);root.addView(page,new LinearLayout.LayoutParams(-1,0,1));
        switch(tab){case "tasks":tasks();break;case "inbox":inbox();break;case "memory":memory();break;case "settings":settings();break;default:home();}
        nav=row();nav.setBackgroundColor(CARD);String[] keys={"home","tasks","inbox","memory","settings"};String[] labels={"✦\nمساعدي","◷\nمهامي","▤\nالوارد","◇\nذاكرتي","⚙\nالإعدادات"};
        for(int i=0;i<keys.length;i++){final String key=keys[i];TextView n=text(labels[i],12,tab.equals(key)?MINT:MUTED);n.setGravity(Gravity.CENTER);n.setPadding(dp(2),dp(10),dp(2),dp(10));n.setContentDescription(labels[i].replace('\n',' '));n.setOnClickListener(v->{tab=key;render();});nav.addView(n,new LinearLayout.LayoutParams(0,dp(66),1));}root.addView(nav);
    }

    private void home(){
        content.addView(text("مساحتك لإنجاز يومك",12,MINT));content.addView(title("أهلًا، أنا ماكس"));
        content.addView(text("فكّر بصوتك. وخلي الباقي عليّ.",15,MUTED));space(content,12);
        LinearLayout summary=card(content);status=text(providerStatus(),13,MINT);summary.addView(status);
        summary.addView(text("أوامرك وذكرياتك على جهازك. اختر ما تشاركه مع النموذج.",12,MUTED));
        LinearLayout quick=row();addQuick(quick,"↗ متصفح",()->askUrl());addQuick(quick,"▤ الهاتف",this::tools);addQuick(quick,"✉ بريد",this::composeEmail);content.addView(quick);space(content,12);
        chatList=column();content.addView(chatList);
        List<AppStore.Message> messages=store.getMessages(60);
        if(messages.isEmpty()){
            LinearLayout intro=card(chatList);intro.addView(text("بماذا أساعدك اليوم؟",20,TEXT));intro.addView(text("جرّب: «افتح واتساب»، «افتح موقع https://example.com»، أو اطلب مني كتابة مسودة رد.",14,MUTED));
            intro.addView(button("✈ البحث عن رحلة",()->travel("flight")));intro.addView(button("⇄ البحث عن قطار",()->travel("train")));intro.addView(button("+ جدولة مهمة",this::newTask));
        }else for(AppStore.Message m:messages)bubble(m.role,m.content);
        if(busy)chatList.addView(text("ماكس يفكّر…",14,MINT));
        LinearLayout compose=column();compose.setPadding(0,dp(8),0,0);input=field("اكتب طلبك هنا…",true);input.setMaxLines(5);compose.addView(input);
        LinearLayout controls=row();Button send=primary(busy?"جارٍ التفكير…":"إرسال ↑",this::send);send.setEnabled(!busy);controls.addView(send,new LinearLayout.LayoutParams(0,dp(48),1));Button mic=button("◉ صوت",this::listen);LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(95),dp(48));mp.setMarginStart(dp(8));controls.addView(mic,mp);compose.addView(controls);
        if(busy)compose.addView(button("إيقاف التوليد",()->{if(activeAi!=null)activeAi.cancel();toast("تم طلب الإيقاف.");}));content.addView(compose);
        main.post(()->{if(page!=null)page.fullScroll(View.FOCUS_DOWN);});
    }

    private void bubble(String role,String message){LinearLayout c=card(chatList);c.setBackground(shape("user".equals(role)?0xff25374a:CARD,16));c.addView(text("user".equals(role)?"أنت":"✦ ماكس",12,"user".equals(role)?BLUE:MINT));TextView body=text(message,16,TEXT);body.setTextIsSelectable(true);c.addView(body);}
    private void addQuick(LinearLayout parent,String label,Runnable run){Button b=button(label,run);b.setTextSize(12);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(46),1);p.setMargins(dp(3),0,dp(3),0);parent.addView(b,p);}
    private String providerStatus(){if("nvidia".equals(store.prefs().getString("provider","local")))return "◉ NVIDIA · "+(NvidiaClient.AUTO_MODEL.equals(NvidiaClient.normalizeChoice(store.prefs().getString("nvidia_model",NvidiaClient.AUTO_MODEL)))?"اختيار تلقائي":"نموذج مخصص");return new ModelManager(this).isReady()?"● نموذج محلي · جاهز بدون إنترنت":"◌ النموذج المحلي يحتاج تنزيلًا أول مرة";}

    private void send(){
        if(busy)return;String message=input.getText().toString().trim();if(message.isEmpty())return;
        if(message.length()>6000){toast("الطلب طويل. اختصره إلى 6000 حرف.");return;}
        CommandRouter.Command command=CommandRouter.parse(message);
        if(command!=null){store.addMessage("user",message);store.addMessage("assistant","سأعرض الإجراء للمراجعة: "+command.summary+". التنفيذ يعتمد على الصلاحيات وحالة الهاتف.");render();ActionExecutor.preview(this,command.action,command.value);return;}
        if("local".equals(store.prefs().getString("provider","local"))&&!new ModelManager(this).isReady()){
            new AlertDialog.Builder(this).setTitle("النموذج غير محمّل بعد").setMessage("نزّل النموذج المجاني مرة واحدة (~491 ميغابايت)، أو أضف مفتاح NVIDIA في الإعدادات.").setPositiveButton("تنزيل النموذج",(d,w)->downloadModel()).setNegativeButton("الإعدادات",(d,w)->{tab="settings";render();}).show();return;
        }
        store.addMessage("user",message);busy=true;activeAi=new AiRepository(getApplicationContext());final AiRepository request=activeAi;render();
        worker.execute(()->{
            try{String answer=request.reply(message);store.addMessage("assistant",answer);main.post(()->{if(!isDestroyed()){busy=false;activeAi=null;render();if(store.prefs().getBoolean("speak_replies",false))speak(answer);}});}
            catch(Exception ex){main.post(()->{if(!isDestroyed()){busy=false;activeAi=null;store.addMessage("assistant","تعذّر إكمال الطلب: "+error(ex));render();}});}
        });
    }
    private String error(Exception e){String s=e.getMessage();return s==null?"خطأ غير متوقع. راجع الإعدادات وحاول مجددًا.":s;}
    private void speak(String value){if(ttsReady)tts.speak(value,TextToSpeech.QUEUE_FLUSH,null,"max-reply");else toast("محرك الصوت غير جاهز. ثبّت صوتًا عربيًا من إعدادات الهاتف.");}

    private void listen(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},11);return;}
        if(!SpeechRecognizer.isRecognitionAvailable(this)){toast("لا توجد خدمة تعرّف على الكلام. ثبّت أو فعّل خدمة صوتية في الهاتف.");return;}
        if(speech!=null)speech.destroy();speech=SpeechRecognizer.createSpeechRecognizer(this);
        speech.setRecognitionListener(new RecognitionListener(){
            public void onReadyForSpeech(Bundle p){toast("أنا أستمع…");}public void onBeginningOfSpeech(){}public void onRmsChanged(float v){}public void onBufferReceived(byte[] b){}public void onEndOfSpeech(){}public void onPartialResults(Bundle b){}public void onEvent(int t,Bundle b){}
            public void onError(int code){toast(code==SpeechRecognizer.ERROR_NO_MATCH?"لم ألتقط الكلام. حاول مجددًا.":"تعذّر التعرف على الصوت ("+code+"). قد تتطلب خدمة الهاتف الإنترنت.");}
            public void onResults(Bundle data){ArrayList<String> r=data.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(r!=null&&!r.isEmpty()){if(!"home".equals(tab)){tab="home";render();}input.setText(r.get(0));input.setSelection(input.length());}}
        });
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ar");i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);speech.startListening(i);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==11&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)listen();else if(request==11)toast("يمكنك استخدام الكتابة دون الميكروفون.");}

    private void tools(){String[] labels={"فتح موقع","فتح واتساب","فتح Gmail","قراءة النص الظاهر على الشاشة","كتابة في الحقل المحدد","الضغط على نص","رجوع","تمرير لأسفل"};new AlertDialog.Builder(this).setTitle("أدوات الهاتف").setItems(labels,(d,n)->{
        switch(n){case 0:askUrl();break;case 1:ActionExecutor.preview(this,"open_app","com.whatsapp");break;case 2:ActionExecutor.preview(this,"open_app","com.google.android.gm");break;case 3:ActionExecutor.readScreen(this,snapshot->{if(!isDestroyed())new AlertDialog.Builder(this).setTitle("النص المتاح").setMessage(snapshot).setPositiveButton("إغلاق",null).show();});break;case 4:askText("النص المراد كتابته","fill");break;case 5:askText("النص المراد الضغط عليه","click");break;case 6:ActionExecutor.preview(this,"back","");break;default:ActionExecutor.preview(this,"scroll","");}
    }).setNegativeButton("إغلاق",null).show();}
    private void askUrl(){EditText url=field("https://example.com",false);url.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);new AlertDialog.Builder(this).setTitle("تصفح وأنجز").setView(url).setPositiveButton("فتح",(d,w)->ActionExecutor.preview(this,"open_url",url.getText().toString().trim())).setNegativeButton("إلغاء",null).show();}
    private void askText(String title,String action){EditText value=field(title,true);new AlertDialog.Builder(this).setTitle(title).setView(value).setMessage("افتح التطبيق وحدد الحقل قبل العودة للمساعد. يتطلب تفعيل خدمة الوصول.").setPositiveButton("مراجعة",(d,w)->ActionExecutor.preview(this,action,value.getText().toString())).setNegativeButton("إلغاء",null).show();}
    private void travel(String action){EditText value=field("من، إلى، التاريخ وعدد المسافرين",true);new AlertDialog.Builder(this).setTitle(action.equals("flight")?"ابحث عن رحلة طيران":"ابحث عن رحلة قطار").setMessage("سأفتح نتائج البحث. راجع السعر والتفاصيل وأكمل الحجز والدفع بنفسك على موقع الشركة.").setView(value).setPositiveButton("بحث",(d,w)->ActionExecutor.preview(this,action,value.getText().toString())).setNegativeButton("إلغاء",null).show();}
    private void composeEmail(){LinearLayout c=column();c.setPadding(dp(20),0,dp(20),0);EditText to=field("البريد الإلكتروني للمستلم",false);to.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);EditText subject=field("الموضوع",false),body=field("نص الرسالة",true);c.addView(to);c.addView(subject);c.addView(body);new AlertDialog.Builder(this).setTitle("مسودة بريد").setView(c).setPositiveButton("مراجعة في تطبيق البريد",(d,w)->{try{org.json.JSONObject j=new org.json.JSONObject();j.put("to",to.getText().toString().trim());j.put("subject",subject.getText().toString());j.put("body",body.getText().toString());ActionExecutor.preview(this,"email",j.toString());}catch(Exception ex){toast("تعذّر إنشاء المسودة.");}}).setNegativeButton("إلغاء",null).show();}

    private void tasks(){content.addView(text("أنجز في الوقت المناسب",12,MINT));content.addView(title("مهامي"));content.addView(text("المهام المجدولة تولّد نصًا أو تذكيرًا. إجراءات الشاشة والإرسال تتطلب مراجعتك وفتح القفل.",14,MUTED));content.addView(primary("+ مهمة جديدة",this::newTask));space(content,16);
        List<AppStore.Task> list=store.getTasks();if(list.isEmpty())empty("جدول يومك بهدوء","أضف تذكيرًا أو اطلب مسودة تُجهّز في موعد محدد.");
        for(AppStore.Task task:list){LinearLayout c=card(content);c.addView(text(task.title,18,TEXT));c.addView(text(now(task.scheduledAt)+" · "+taskStatus(task.status),12,MINT));c.addView(text(task.prompt,14,MUTED));if(task.result!=null&&!task.result.isEmpty()){TextView result=text(task.result,14,TEXT);result.setTextIsSelectable(true);c.addView(result);}c.addView(button("حذف المهمة",()->new AlertDialog.Builder(this).setTitle("حذف المهمة؟").setPositiveButton("حذف",(d,w)->{store.deleteTask(task.id);TaskService.schedule(this);render();}).setNegativeButton("إلغاء",null).show()));}
    }
    private String taskStatus(String s){switch(s){case "pending":return "مجدولة";case "running":return "قيد التنفيذ";case "completed":return "مكتملة";case "failed":return "تعذّر التنفيذ";case "waiting_unlock":return "بانتظار فتح القفل";default:return s;}}
    private void newTask(){LinearLayout c=column();c.setPadding(dp(20),0,dp(20),0);EditText name=field("عنوان المهمة",false),prompt=field("الطلب: مثل جهز مسودة بريد، أو تذكير",true);c.addView(name);c.addView(prompt);final Calendar at=Calendar.getInstance();at.add(Calendar.MINUTE,10);Button date=button("الموعد: "+now(at.getTimeInMillis()),()->new DatePickerDialog(this,(p,y,m,d)->{at.set(y,m,d);new TimePickerDialog(this,(t,h,min)->{at.set(Calendar.HOUR_OF_DAY,h);at.set(Calendar.MINUTE,min);at.set(Calendar.SECOND,0);at.set(Calendar.MILLISECOND,0);},at.get(Calendar.HOUR_OF_DAY),at.get(Calendar.MINUTE),true).show();},at.get(Calendar.YEAR),at.get(Calendar.MONTH),at.get(Calendar.DAY_OF_MONTH)).show());
        date.setOnClickListener(v->new DatePickerDialog(this,(p,y,m,d)->{at.set(y,m,d);new TimePickerDialog(this,(t,h,min)->{at.set(Calendar.HOUR_OF_DAY,h);at.set(Calendar.MINUTE,min);at.set(Calendar.SECOND,0);at.set(Calendar.MILLISECOND,0);date.setText("الموعد: "+now(at.getTimeInMillis()));},at.get(Calendar.HOUR_OF_DAY),at.get(Calendar.MINUTE),true).show();},at.get(Calendar.YEAR),at.get(Calendar.MONTH),at.get(Calendar.DAY_OF_MONTH)).show());c.addView(date);c.addView(text("الموعد تقريبي حسب Android. وضع النوم يؤجل التنفيذ. لا يتم إرسال رسائل أو شراء تذاكر تلقائيًا.",12,MUTED));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("مهمة جديدة").setView(c).setPositiveButton("حفظ",null).setNegativeButton("إلغاء",null).create();dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{String n=name.getText().toString().trim(),p=prompt.getText().toString().trim();if(n.isEmpty()||p.isEmpty()){toast("أدخل العنوان والطلب.");return;}if(at.getTimeInMillis()<=System.currentTimeMillis()){toast("اختر موعدًا في المستقبل.");return;}store.addTask(n,p,at.getTimeInMillis());TaskService.schedule(this);if(store.prefs().getBoolean("bg_enabled",false))TaskService.start(this);dialog.dismiss();tab="tasks";render();toast("تمت الجدولة. فعّل العمل بالخلفية لتنفيذ المهمة.");}));dialog.show();}

    private void inbox(){content.addView(text("ما تسمح بمشاركته فقط",12,MINT));content.addView(title("الوارد"));content.addView(text("إشعارات واتساب وGmail وOutlook التي تصل بعد تفعيل الوصول. لا تمثل كامل سجل الرسائل أو صندوق البريد.",14,MUTED));if(!InboxService.enabled(this)){content.addView(primary("تفعيل الوصول للإشعارات",()->safeSettings(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))));}
        content.addView(button("تحديث",this::render));space(content,10);List<AppStore.InboxItem> items=store.getInbox(60);if(items.isEmpty())empty("الوارد هادئ الآن","بعد التفعيل، ستظهر الإشعارات الجديدة هنا. لن تُرسل للنموذج إلا إذا طلبت مسودة لها.");
        for(AppStore.InboxItem item:items){LinearLayout c=card(content);c.addView(text(item.packageName.contains("whatsapp")?"WhatsApp":"البريد",12,MINT));c.addView(text(item.title,18,TEXT));c.addView(text(item.text,15,TEXT));c.addView(text(now(item.receivedAt),11,MUTED));c.addView(button("اكتب مسودة رد بالذكاء الاصطناعي",()->draftReply(item)));if(item.notificationKey!=null&&InboxService.canReply(item.notificationKey))c.addView(button("رد على الإشعار",()->reply(item)));}
        if(!items.isEmpty())content.addView(button("مسح سجل الإشعارات",()->new AlertDialog.Builder(this).setTitle("مسح السجل المحلي؟").setPositiveButton("مسح",(d,w)->{store.clearInbox();render();}).setNegativeButton("إلغاء",null).show()));
    }
    private void draftReply(AppStore.InboxItem item){new AlertDialog.Builder(this).setTitle("مشاركة الرسالة مع النموذج؟").setMessage("سيُستخدم محتوى هذه الرسالة لصياغة مسودة. عند اختيار NVIDIA يخرج النص من الهاتف إلى المزود.").setPositiveButton("إعداد مسودة",(d,w)->{tab="home";render();input.setText("اكتب مسودة رد مناسبة دون إرسالها على الرسالة التالية من "+item.title+":\n"+item.text);}).setNegativeButton("إلغاء",null).show();}
    private void reply(AppStore.InboxItem item){EditText body=field("راجع واكتب ردك هنا",true);new AlertDialog.Builder(this).setTitle("رد إلى "+item.title).setView(body).setPositiveButton("مراجعة الإرسال",(d,w)->{if(body.getText().toString().trim().isEmpty()){toast("اكتب نص الرد أولًا.");return;}InboxService.replyReviewed(this,item.notificationKey,body.getText().toString());}).setNegativeButton("إلغاء",null).show();}

    private void memory(){content.addView(text("تذكّر ما يهمك",12,MINT));content.addView(title("ذاكرتي"));content.addView(text("محفوظة داخل التطبيق على هاتفك. تُضاف للنموذج المحلي، أو لـNVIDIA عند اختياره، لتخصيص إجاباته.",14,MUTED));content.addView(primary("+ إضافة ذكرى",this::newMemory));space(content,16);List<AppStore.Memory> list=store.getMemories();if(list.isEmpty())empty("ابدأ بالتفاصيل الصغيرة","احفظ اسمك أو أسلوب الرد المفضل. تجنب كلمات المرور وأرقام البطاقات.");for(AppStore.Memory memory:list){LinearLayout c=card(content);c.addView(text(memory.title,18,TEXT));c.addView(text(memory.content,15,MUTED));c.addView(button("حذف",()->new AlertDialog.Builder(this).setTitle("حذف الذكرى؟").setPositiveButton("حذف",(d,w)->{store.deleteMemory(memory.id);render();}).setNegativeButton("إلغاء",null).show()));}}
    private void newMemory(){LinearLayout c=column();c.setPadding(dp(20),0,dp(20),0);EditText title=field("عنوان، مثل: تفضيلاتي",false),body=field("ما الذي تريدني أن أتذكره؟",true);c.addView(title);c.addView(body);AlertDialog d=new AlertDialog.Builder(this).setTitle("ذكرى جديدة").setView(c).setPositiveButton("حفظ",null).setNegativeButton("إلغاء",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(b->{if(title.length()==0||body.length()==0){toast("أدخل العنوان والمحتوى.");return;}store.addMemory(title.getText().toString(),body.getText().toString());d.dismiss();render();}));d.show();}
    private void empty(String title,String body){LinearLayout c=card(content);c.addView(text(title,20,TEXT));c.addView(text(body,14,MUTED));}

    private void settings(){
        content.addView(text("كل شيء تحت تحكمك",12,MINT));content.addView(title("الإعدادات"));space(content,10);
        LinearLayout model=card(content);model.addView(text("النموذج",19,TEXT));model.addView(text(providerStatus(),13,MINT));String activeModel=store.prefs().getString("nvidia_active_model","");if(!activeModel.isEmpty())model.addView(text("نموذج NVIDIA المستخدم: "+activeModel,12,MUTED));String modelNotice=store.prefs().getString("nvidia_model_notice","");if(!modelNotice.isEmpty())model.addView(text(modelNotice,12,MINT));
        model.addView(button("مجاني على الهاتف · Qwen 0.5B",()->{store.prefs().edit().putString("provider","local").apply();render();}));model.addView(text("تنزيل واحد ~491 MB. يعمل دون إنترنت بعده، وقدرته اللغوية والاستدلالية محدودة. يُنصح بذاكرة RAM لا تقل عن 3 GB.",12,MUTED));model.addView(button(downloading?"عرض تقدم التنزيل":new ModelManager(this).isReady()?"✓ النموذج محمّل":"تنزيل النموذج المجاني",this::downloadModel));model.addView(button("إعداد NVIDIA",this::nvidiaSetup));
        LinearLayout permissions=card(content);permissions.addView(text("صلاحيات الهاتف",19,TEXT));permissions.addView(text("Android يطلب موافقتك لكل صلاحية. لا يمكن منحها تلقائيًا أو التحكم بشاشة مقفلة.",13,MUTED));permissions.addView(button((MaxAccessibilityService.isEnabled(this)?"✓ ":"")+"خدمة الوصول · التحكم بالشاشة",this::accessibilityConsent));permissions.addView(button((InboxService.enabled(this)?"✓ ":"")+"قراءة الإشعارات والرد عليها",()->notificationConsent()));if(Build.VERSION.SDK_INT>=33)permissions.addView(button("السماح بإشعارات ماكس",()->requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},12)));permissions.addView(button("السماح بالميكروفون",()->requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},13)));
        LinearLayout background=card(content);background.addView(text("العمل والنوم",19,TEXT));background.addView(text("العمل بالخلفية يستخدم إشعارًا ظاهرًا. يمكن تجهيز نصوص والتذكير والشاشة مطفأة؛ تنفيذ إجراءات التطبيقات ينتظر فتح القفل. إيقاف التطبيق قسريًا أو قيود البطارية قد يوقف المهام.",13,MUTED));boolean enabled=store.prefs().getBoolean("bg_enabled",false);background.addView(button(enabled?"إيقاف العمل بالخلفية":"تشغيل العمل بالخلفية",()->{if(enabled){store.prefs().edit().putBoolean("bg_enabled",false).apply();TaskService.stop(this);}else{store.prefs().edit().putBoolean("bg_enabled",true).apply();TaskService.start(this);}render();}));background.addView(button("تبديل وضع النوم / العمل",this::toggleMode));background.addView(button("إعدادات استهلاك البطارية",()->safeSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))));
        LinearLayout voice=card(content);voice.addView(text("الصوت",19,TEXT));Switch speak=new Switch(this);speak.setText("قراءة الإجابات بصوت مسموع");speak.setTextColor(TEXT);speak.setChecked(store.prefs().getBoolean("speak_replies",false));speak.setOnCheckedChangeListener((b,value)->store.prefs().edit().putBoolean("speak_replies",value).apply());voice.addView(speak);voice.addView(text("التسجيل يبدأ بالضغط على زر الصوت فقط. توفر العربية والعمل دون إنترنت يعتمدان على خدمة الصوت في هاتفك.",12,MUTED));
        LinearLayout privacy=card(content);privacy.addView(text("بياناتك",19,TEXT));privacy.addView(text("لا نسخ احتياطي لبيانات التطبيق. المحادثات والذاكرة والوارد محليًا. NVIDIA يستقبل طلب المحادثة والذكريات عند اختياره، ولا يُرسل سجل الإشعارات تلقائيًا.",13,MUTED));privacy.addView(button("مسح المحادثة",()->new AlertDialog.Builder(this).setTitle("مسح المحادثة؟").setPositiveButton("مسح",(d,w)->{store.clearMessages();toast("تم مسح المحادثة.");}).setNegativeButton("إلغاء",null).show()));privacy.addView(button("حذف مفتاح NVIDIA",()->new AlertDialog.Builder(this).setTitle("حذف المفتاح المحفوظ؟").setPositiveButton("حذف",(d,w)->{new SecureSecrets(this).clearNvidiaKey();store.prefs().edit().putString("provider","local").apply();render();}).setNegativeButton("إلغاء",null).show()));privacy.addView(text("MAX · الإصدار 1.0.1\nمساعد قابل للتطوير؛ خدمات الحجوزات والبريد تتطلب حساباتك لدى مزوديها.",12,MUTED));
    }

    private void accessibilityConsent(){new AlertDialog.Builder(this).setTitle("السماح بالتحكم بالشاشة").setMessage("تسمح خدمة الوصول لماكس بقراءة نص الشاشة والضغط والكتابة أثناء الإجراءات التي تطلبها وتراجعها. لا يقرأ الحقول السرية ولا يفتح قفل الجهاز. يمكنك تعطيلها في أي وقت من إعدادات Android. بعض الهواتف تتطلب السماح بالإعدادات المقيّدة من معلومات التطبيق أولًا.").setPositiveButton("فتح إعدادات الوصول",(d,w)->safeSettings(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).setNegativeButton("إلغاء",null).show();}
    private void notificationConsent(){new AlertDialog.Builder(this).setTitle("الوصول إلى الإشعارات").setMessage("يمكن لهذا الإذن الاطلاع على الإشعارات. ماكس يحتفظ محليًا بإشعارات WhatsApp وGmail وOutlook فقط، ويتيح الرد عندما يوفّره التطبيق. لا يُرسل محتواها للنموذج تلقائيًا. يمكنك إيقاف الإذن ومسح السجل.").setPositiveButton("فتح إعدادات الإشعارات",(d,w)->safeSettings(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))).setNegativeButton("إلغاء",null).show();}
    private void toggleMode(){boolean sleep=!"sleep".equals(store.prefs().getString("work_mode","work"));store.prefs().edit().putString("work_mode",sleep?"sleep":"work").apply();TaskService.schedule(this);if(store.prefs().getBoolean("bg_enabled",false))TaskService.start(this);render();toast(sleep?"وضع النوم: المهام مؤجلة حتى العودة للعمل.":"وضع العمل: يمكن تنفيذ المهام المسموح بها.");}

    private void onboarding(){LinearLayout c=column();c.setPadding(dp(24),dp(8),dp(24),dp(8));c.addView(text("✦ MAX",30,MINT));c.addView(text("مساعدك، على هاتفك",22,TEXT));c.addView(text("محادثة وصوت، ذاكرة محلية، متصفح، ومهام مجدولة.\n\nابدأ بلا حساب بنموذج مجاني يُنزّل مرة واحدة (~491 MB)، أو استخدم NVIDIA بمفتاح تُدخله مرة واحدة. حدود المجانية والتوفر يحددها المزود.\n\nالصلاحيات اختيارية وتُفعّل بموافقتك. الردود والحجوزات تحتاج مراجعتك. لا يعمل التحكم بالشاشة أثناء قفل الهاتف.",15,MUTED));new AlertDialog.Builder(this).setView(c).setPositiveButton("ابدأ مجانًا",(d,w)->{store.prefs().edit().putBoolean("onboarding_done",true).putString("provider","local").apply();downloadModel();}).setNeutralButton("إعداد NVIDIA",(d,w)->{store.prefs().edit().putBoolean("onboarding_done",true).apply();nvidiaSetup();}).setNegativeButton("استكشاف التطبيق",(d,w)->store.prefs().edit().putBoolean("onboarding_done",true).apply()).setCancelable(false).show();}

    private void nvidiaSetup(){LinearLayout c=column();c.setPadding(dp(20),0,dp(20),0);c.addView(text("1. افتح NVIDIA وسجّل حسابًا للحصول على مفتاح API.\n2. الصق المفتاح هنا مرة واحدة. يُحفظ مشفّرًا على الهاتف.\nالحصص المجانية وتوفر النماذج قد يتغيران حسب المزود.",14,MUTED));c.addView(button("فتح صفحة NVIDIA للحصول على المفتاح ↗",()->open("https://build.nvidia.com/")));EditText key=field("nvapi-…",false);key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);c.addView(key);EditText model=field("النموذج: auto للاختيار التلقائي",false);model.setText(NvidiaClient.normalizeChoice(store.prefs().getString("nvidia_model",NvidiaClient.AUTO_MODEL)));c.addView(model);c.addView(button("اختيار نموذج تلقائيًا",()->model.setText(NvidiaClient.AUTO_MODEL)));c.addView(text("auto يختار نموذج دردشة من قائمة NVIDIA الحالية، ويستبدله عند إيقافه. اسم نموذج مخصص اختياري.",12,MUTED));c.addView(text("اترك المفتاح فارغًا لاستخدام المفتاح المحفوظ. لا تشاركه في المحادثة.",12,MUTED));ScrollView form=new ScrollView(this);form.addView(c);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("NVIDIA · إعداد مرة واحدة").setView(form).setPositiveButton("حفظ واستخدام",null).setNegativeButton("إلغاء",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(b->{try{SecureSecrets secrets=new SecureSecrets(this);String value=key.getText().toString().trim();if(!value.isEmpty())secrets.setNvidiaKey(value);if(secrets.getNvidiaKey()==null||secrets.getNvidiaKey().isEmpty()){toast("أدخل مفتاح API أولًا.");return;}String modelId=NvidiaClient.normalizeChoice(model.getText().toString());store.prefs().edit().putString("provider","nvidia").putString("nvidia_model",modelId).remove("nvidia_active_model").remove("nvidia_model_notice").apply();key.setText("");dialog.dismiss();render();testNvidia();}catch(Exception ex){toast("تعذّر حفظ المفتاح بأمان: "+error(ex));}}));dialog.show();}
    private void testNvidia(){toast("جارٍ التحقق من الاتصال بـNVIDIA…");worker.execute(()->{try{String result=new AiRepository(getApplicationContext()).testNvidiaConnection();main.post(()->{if(!isDestroyed())new AlertDialog.Builder(this).setTitle("اختبار NVIDIA").setMessage(result).setPositiveButton("حسنًا",null).show();});}catch(Exception ex){main.post(()->{if(!isDestroyed())new AlertDialog.Builder(this).setTitle("تعذّر الاتصال").setMessage(error(ex)+"\nراجع المفتاح واسم النموذج وحصة المزود.").setPositiveButton("حسنًا",null).show();});}});}

    private void downloadModel(){
        if(new ModelManager(this).isReady()){toast("النموذج المحلي جاهز للاستخدام.");return;}
        if(downloading){toast("التنزيل جارٍ. أبقِ التطبيق مفتوحًا حتى يكتمل.");return;}
        new AlertDialog.Builder(this).setTitle("تنزيل النموذج المجاني").setMessage("سيُنزّل ملف بحجم 491 ميغابايت من Hugging Face ويُفحص قبل استخدامه. يلزم قرابة 650 MB مساحة فارغة. استخدم Wi-Fi وأبقِ التطبيق مفتوحًا. لا يحتاج حسابًا أو مفتاح API.").setPositiveButton("تنزيل",(d,w)->{
            downloading=true;downloadManager=new ModelManager(getApplicationContext());
            ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);LinearLayout c=column();c.setPadding(dp(20),dp(16),dp(20),dp(16));TextView progress=text("بدء التنزيل…",14,TEXT);c.addView(progress);c.addView(bar);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("تجهيز ماكس على هاتفك").setView(c).setNegativeButton("إلغاء التنزيل",(x,y)->downloadManager.cancel()).setCancelable(false).create();dialog.show();
            worker.execute(()->{try{downloadManager.download((done,total)->main.post(()->{if(!isDestroyed()){bar.setProgress(total>0?(int)(done*100/total):0);progress.setText((done/1_000_000)+" / "+(total/1_000_000)+" MB"+(total>0&&done>=total?" · فحص الملف…":""));}}));main.post(()->{downloading=false;if(!isDestroyed()){dialog.dismiss();store.prefs().edit().putString("provider","local").apply();render();toast("النموذج جاهز. يمكنك المحادثة دون إنترنت.");}});}catch(Exception ex){main.post(()->{downloading=false;if(!isDestroyed()){dialog.dismiss();toast("لم يكتمل التنزيل: "+error(ex));}});}});
        }).setNegativeButton("لاحقًا",null).show();
    }
}
