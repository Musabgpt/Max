package com.maxassistant.app.voice;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Locale;

/** User-initiated speech only. A transcript never sends or executes a command. */
public final class VoiceInputController {
    public static final int REQUEST_MIC=501, REQUEST_SYSTEM_SPEECH=502;
    public interface Host {void onText(String text);void onListening(boolean listening);}
    private final Activity activity;
    private final Host host;
    private SpeechRecognizer recognizer;
    private boolean active;
    private int session;

    public VoiceInputController(Activity activity,Host host){this.activity=activity;this.host=host;}
    public void startOrStop(){
        if(activity.isFinishing()||activity.isDestroyed())return;
        if(active&&recognizer!=null){recognizer.stopListening();host.onListening(false);return;}
        if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQUEST_MIC);return;
        }
        cancel();
        if(!SpeechRecognizer.isRecognitionAvailable(activity)){systemFallback();return;}
        final int token=++session;active=true;host.onListening(true);
        try{
            recognizer=SpeechRecognizer.createSpeechRecognizer(activity);
            recognizer.setRecognitionListener(new RecognitionListener(){
                public void onReadyForSpeech(Bundle data){if(current(token))toast("أنا أستمع…");}
                public void onBeginningOfSpeech(){}public void onRmsChanged(float rms){}public void onBufferReceived(byte[] data){}
                public void onEndOfSpeech(){if(current(token))host.onListening(false);}
                public void onPartialResults(Bundle data){}public void onEvent(int type,Bundle data){}
                public void onError(int error){
                    if(!current(token))return;
                    active=false;host.onListening(false);
                    if(SpeechPolicy.shouldUseSystemFallback(error))systemFallback();else help(error);
                }
                public void onResults(Bundle results){
                    if(!current(token))return;
                    active=false;host.onListening(false);
                    deliver(results==null?null:results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION));
                }
            });
            recognizer.startListening(recognitionIntent());
        }catch(RuntimeException unavailable){active=false;host.onListening(false);systemFallback();}
    }
    private boolean current(int token){return token==session&&active&&!activity.isDestroyed();}
    private Intent recognitionIntent(){
        Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,SpeechPolicy.language(Locale.getDefault()));
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,false);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,"تحدث بالعربية — سيظهر النص للمراجعة قبل الإرسال");
        return intent;
    }
    private void systemFallback(){
        // Destroy the failed service before opening the system/provider voice UI.
        // Incrementing session suppresses its delayed cancellation/error callbacks.
        cancel();
        try{activity.startActivityForResult(recognitionIntent(),REQUEST_SYSTEM_SPEECH);}
        catch(ActivityNotFoundException|SecurityException unavailable){help(12);}
    }
    private void deliver(ArrayList<String> values){
        if(values==null||values.isEmpty()||values.get(0)==null||values.get(0).trim().isEmpty()){toast(SpeechPolicy.errorText(7));return;}
        host.onText(values.get(0).trim());
    }
    public boolean onActivityResult(int request,int result,Intent data){
        if(request!=REQUEST_SYSTEM_SPEECH)return false;
        host.onListening(false);
        if(result==Activity.RESULT_OK)deliver(data==null?null:data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS));
        return true;
    }
    public boolean onPermissionResult(int request,int[] results){
        if(request!=REQUEST_MIC)return false;
        if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)startOrStop();else help(9);
        return true;
    }
    public void cancel(){
        active=false;session++;host.onListening(false);
        if(recognizer!=null){try{recognizer.cancel();recognizer.destroy();}catch(RuntimeException ignored){}recognizer=null;}
    }
    public void destroy(){cancel();}
    public void openSettings(){
        try{activity.startActivity(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS));}
        catch(ActivityNotFoundException absent){
            try{activity.startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));}
            catch(ActivityNotFoundException ignored){toast("افتح إعدادات الهاتف ثم اللغة والإدخال لاختيار خدمة صوت تدعم العربية.");}
        }
    }
    private void help(int error){
        if(activity.isFinishing()||activity.isDestroyed())return;
        new AlertDialog.Builder(activity).setTitle("خدمة التعرف على الصوت")
            .setMessage(SpeechPolicy.errorText(error)+"\n\nيمكنك متابعة الكتابة. لا يلزم مفتاح NVIDIA للصوت.")
            .setPositiveButton("إعدادات الصوت",(dialog,which)->openSettings())
            .setNegativeButton("إغلاق",null).show();
    }
    private void toast(String value){Toast.makeText(activity,value,Toast.LENGTH_LONG).show();}
}
