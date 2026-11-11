package com.maxassistant.app.ai;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** NVIDIA text generation and bounded recovery when a model is withdrawn. */
public final class NvidiaClient {
    public static final String AUTO_MODEL="auto";
    public static final String LEGACY_MODEL="meta/llama-3.1-8b-instruct";
    public static final String DEFAULT_MODEL="nvidia/nemotron-3.5-lightning-30b-a3b";
    public static final String MODELS_URL="https://integrate.api.nvidia.com/v1/models";
    public static final String CHAT_URL="https://integrate.api.nvidia.com/v1/chat/completions";
    private static final int MAX_RESPONSE=2*1024*1024;
    private static final List<String> PREFERRED=Arrays.asList(DEFAULT_MODEL,
        "nvidia/nemotron-3.5-bolt-e4b", "nvidia/nemotron-nano-3-30b-a3b",
        "qwen/qwen3-next-80b-a3b-instruct", "openai/gpt-oss-20b");
    private final String key;
    private final Transport transport;
    private volatile boolean cancelled;
    public interface Transport {
        Response send(String method,String url,String key,String body) throws IOException;
        default void cancel() { }
    }
    public static final class Response {
        public final int status;
        public final String body;
        public Response(int status,String body){this.status=status;this.body=body;}
    }
    public static final class Result {
        public final String text,model;
        public final boolean switched;
        public Result(String text,String model,boolean switched){this.text=text;this.model=model;this.switched=switched;}
    }
    public NvidiaClient(String key){this(key,new HttpsTransport());}
    public NvidiaClient(String key,Transport transport){this.key=key==null?"":key.trim();this.transport=transport;}

    public Result complete(String requested,String lastModel,JSONArray messages) throws IOException {
        return complete(requested,lastModel,messages,1024);
    }
    public Result complete(String requested,String lastModel,JSONArray messages,int maxTokens) throws IOException {
        checkCancelled();validateKey();
        String choice=normalizeChoice(requested);
        if(!AUTO_MODEL.equals(choice)&&!validId(choice))throw new IOException("معرّف نموذج NVIDIA غير صالح. اختر auto للاختيار التلقائي.");
        if(messages==null||messages.length()==0)throw new IOException("اكتب رسالة أولًا.");
        List<String> available=null;
        String model=choice;
        if(AUTO_MODEL.equals(choice)){
            try{available=loadModels();}catch(IOException unavailable){checkCancelled();}
            model=available==null?(chatModel(lastModel)?lastModel:DEFAULT_MODEL):choose(available,lastModel,Collections.emptySet());
            if(model==null)throw new IOException("لا تتوفر نماذج دردشة مناسبة في قائمة NVIDIA الآن. استخدم النموذج المحلي أو حاول لاحقًا.");
        }
        boolean switched=requested!=null&&LEGACY_MODEL.equals(requested.trim());
        Set<String> attempted=new HashSet<>();
        int budget=Math.max(32,Math.min(8192,maxTokens));
        // At most two inference requests: withdrawn model OR an explicitly length-
        // truncated empty answer. Authentication, quota and network errors do not replay.
        for(int attempt=0;attempt<2;attempt++){
            checkCancelled();
            if(model.startsWith("openai/gpt-oss-"))budget=Math.max(4096,budget);
            Response response=send("POST",CHAT_URL,key,payload(model,messages,budget));
            if(response.status==200){
                String text=answer(response.body);
                if(ReplyText.isUsable(text))return new Result(ReplyText.require(text),model,switched);
                if(attempt==0&&lengthTruncated(response.body)&&budget<8192){budget=Math.min(8192,Math.max(4096,budget*2));continue;}
                throw new IOException("لم يرجع النموذج جوابًا نهائيًا. اختصر الطلب أو اختر نموذجًا آخر.");
            }
            if((response.status!=404&&response.status!=410)||attempt==1)throw statusError(response.status);
            attempted.add(model);
            if(available==null){
                try{available=loadModels();}catch(IOException unavailable){checkCancelled();throw statusError(response.status);}
            }
            String replacement=choose(available,"",attempted);
            if(replacement==null)throw statusError(response.status);
            model=replacement;switched=true;
        }
        throw new IOException("تعذّر اختيار نموذج NVIDIA متاح.");
    }

    public void cancel(){cancelled=true;transport.cancel();}
    private void checkCancelled() throws IOException {
        if(cancelled||Thread.currentThread().isInterrupted())throw new IOException("تم إيقاف الطلب");
    }
    private void validateKey() throws IOException {
        if(key.isEmpty()||key.length()>2000)throw new IOException("أضف مفتاح NVIDIA في الإعدادات مرة واحدة.");
        for(int i=0;i<key.length();i++)if(key.charAt(i)<=32||key.charAt(i)>126)throw new IOException("تنسيق مفتاح NVIDIA غير صالح. الصق المفتاح دون فراغات أو أسطر إضافية.");
    }
    public static String normalizeChoice(String model){
        String value=model==null?"":model.trim();
        return value.isEmpty()||LEGACY_MODEL.equals(value)||"auto".equalsIgnoreCase(value)||"تلقائي".equals(value)?AUTO_MODEL:value;
    }
    private static boolean validId(String value){
        return value!=null&&value.length()<=200&&value.matches("[A-Za-z0-9][A-Za-z0-9._-]*/[A-Za-z0-9][A-Za-z0-9._-]*");
    }
    private static boolean chatModel(String id){
        if(!validId(id)||LEGACY_MODEL.equals(id))return false;
        String name=id.toLowerCase(Locale.ROOT);
        for(String excluded:new String[]{"embed","rerank","retriev","reward","guard","safety","parse","vision","-vl","omni","riva","translate"})if(name.contains(excluded))return false;
        return PREFERRED.contains(id)||name.contains("instruct")||name.startsWith("nvidia/nemotron-")||name.startsWith("openai/gpt-oss-");
    }
    private static String choose(List<String> models,String previous,Set<String> attempted){
        if(chatModel(previous)&&models.contains(previous)&&!attempted.contains(previous))return previous;
        for(String preferred:PREFERRED)if(models.contains(preferred)&&!attempted.contains(preferred))return preferred;
        List<String> sorted=new ArrayList<>(models);Collections.sort(sorted);
        for(String id:sorted)if(chatModel(id)&&!attempted.contains(id))return id;
        return null;
    }
    private List<String> loadModels() throws IOException {
        // The catalog is public: never attach the user's key to metadata requests.
        Response response=send("GET",MODELS_URL,null,null);
        if(response.status!=200)throw new IOException("تعذّر تحديث قائمة نماذج NVIDIA.");
        try{
            JSONArray data=new JSONObject(response.body).getJSONArray("data");
            List<String> ids=new ArrayList<>();
            for(int i=0;i<data.length()&&i<2000;i++){
                JSONObject entry=data.optJSONObject(i);String id=entry==null?"":entry.optString("id","");
                if(validId(id)&&!ids.contains(id))ids.add(id);
            }
            return ids;
        }catch(JSONException e){throw new IOException("قائمة نماذج NVIDIA غير صالحة.",e);}
    }
    private static String payload(String model,JSONArray messages,int budget) throws IOException {
        try{
            JSONObject request=new JSONObject().put("model",model).put("messages",messages)
                .put("max_tokens",model.startsWith("openai/gpt-oss-")?Math.max(4096,budget):budget)
                .put("temperature",0.4).put("stream",false);
            if(model.startsWith("nvidia/nemotron-3.5-")||model.startsWith("nvidia/nemotron-nano-3-"))
                request.put("chat_template_kwargs",new JSONObject().put("enable_thinking",false));
            if(model.startsWith("openai/gpt-oss-"))request.put("reasoning_effort","low");
            return request.toString();
        }catch(JSONException e){throw new IOException("تعذّر إنشاء طلب NVIDIA.",e);}
    }
    private static String answer(String body) throws IOException {
        try{
            Object content=new JSONObject(body).getJSONArray("choices").getJSONObject(0).getJSONObject("message").opt("content");
            if(content instanceof String)return ReplyText.clean((String)content);
            if(content instanceof JSONArray){
                StringBuilder finalText=new StringBuilder();JSONArray parts=(JSONArray)content;
                for(int i=0;i<parts.length();i++){
                    JSONObject part=parts.optJSONObject(i);if(part==null)continue;
                    String type=part.optString("type","");Object text=part.opt("text");
                    if(("text".equals(type)||"output_text".equals(type))&&text instanceof String)finalText.append(text);
                }
                return ReplyText.clean(finalText.toString());
            }
            return "";
        }catch(JSONException e){throw new IOException("رد NVIDIA غير صالح. حاول مجددًا لاحقًا.",e);}
    }
    private static boolean lengthTruncated(String body){
        try{return "length".equals(new JSONObject(body).getJSONArray("choices").getJSONObject(0).optString("finish_reason",""));}
        catch(JSONException invalid){return false;}
    }
    private Response send(String method,String url,String apiKey,String body) throws IOException {
        checkCancelled();
        try{
            Response response=transport.send(method,url,apiKey,body);checkCancelled();
            if(response==null||response.body==null||response.body.length()>MAX_RESPONSE)throw new IOException("Invalid response size");
            return response;
        }catch(IOException|RuntimeException error){
            checkCancelled();
            // Do not show transport exceptions, response bodies or header values;
            // an invalid-header exception can otherwise include the credential.
            throw new IOException("تعذّر الاتصال بـ NVIDIA. تحقق من الإنترنت وحاول مجددًا.",error);
        }
    }
    private static IOException statusError(int code){
        switch(code){
            case 401:return new IOException("رفض NVIDIA مفتاح API (401). تحقق من المفتاح المحفوظ في الإعدادات.");
            case 403:return new IOException("منع NVIDIA الوصول (403). فعّل Public API Endpoints في حسابك وتحقق من السماح للنموذج. لا تحذف مفتاحك بسبب هذا الخطأ.");
            case 404:case 410:return new IOException("النموذج لم يعد متاحًا لدى NVIDIA (HTTP "+code+"). تعذّر تشغيل بديل متاح؛ حاول لاحقًا أو استخدم النموذج المحلي.");
            case 429:return new IOException("وصلت إلى حد استخدام NVIDIA (429). انتظر أو استخدم النموذج المحلي.");
            case 400:case 422:return new IOException("لم يقبل NVIDIA النموذج أو صيغة الطلب (HTTP "+code+"). اختر auto في الإعدادات أو حدّث اسم النموذج.");
            default:return new IOException("خدمة NVIDIA غير متاحة الآن (HTTP "+code+"). حاول لاحقًا.");
        }
    }

    private static final class HttpsTransport implements Transport {
        private volatile HttpsURLConnection active;
        private volatile boolean cancelled;
        @Override public void cancel(){cancelled=true;HttpsURLConnection current=active;if(current!=null)current.disconnect();}
        @Override public Response send(String method,String url,String key,String body) throws IOException {
            if(cancelled)throw new IOException("Cancelled");
            if(!MODELS_URL.equals(url)&&!CHAT_URL.equals(url))throw new IOException("Invalid provider endpoint");
            HttpsURLConnection connection=(HttpsURLConnection)new URL(url).openConnection();active=connection;
            try{
                if(cancelled)throw new IOException("Cancelled");
                connection.setConnectTimeout(20000);connection.setReadTimeout("GET".equals(method)?20000:90000);
                connection.setInstanceFollowRedirects(false);connection.setRequestMethod(method);
                connection.setRequestProperty("Accept","application/json");
                if(key!=null)connection.setRequestProperty("Authorization","Bearer "+key);
                if(body!=null){
                    connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
                    byte[] data=body.getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(data.length);
                    try(OutputStream out=connection.getOutputStream()){out.write(data);}
                }
                int status=connection.getResponseCode();
                InputStream stream=status>=400?connection.getErrorStream():connection.getInputStream();
                return new Response(status,stream==null?"":read(stream));
            }finally{active=null;connection.disconnect();}
        }
        private static String read(InputStream input) throws IOException {
            try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] bytes=new byte[8192];int n;
                while((n=in.read(bytes))!=-1){if(out.size()+n>MAX_RESPONSE)throw new IOException("Response too large");out.write(bytes,0,n);}
                return new String(out.toByteArray(),StandardCharsets.UTF_8);
            }
        }
    }
}
