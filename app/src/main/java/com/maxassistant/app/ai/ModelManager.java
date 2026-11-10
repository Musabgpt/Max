package com.maxassistant.app.ai;

import android.content.Context;
import android.os.StatFs;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.HttpsURLConnection;

/** One-click, key-free on-device model download, pinned and verified before use. */
public final class ModelManager {
    public static final String MODEL_NAME="Qwen2.5 0.5B Instruct · Q4_K_M";
    public static final long MODEL_BYTES=491400032L;
    public static final String MODEL_SHA256="74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db";
    public static final String MODEL_URL="https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/9217f5db79a29953eb74d5343926648285ec7e67/qwen2.5-0.5b-instruct-q4_k_m.gguf";
    private static final AtomicBoolean DOWNLOADING=new AtomicBoolean(false);
    private final File directory;
    private volatile boolean cancelled;
    private volatile HttpsURLConnection active;
    public interface Progress { void onProgress(long downloaded,long total); }
    public ModelManager(Context context){directory=new File(context.getApplicationContext().getFilesDir(),"models");}
    public File getModelFile(){return new File(directory,"qwen2.5-0.5b-instruct-q4_k_m.gguf");}
    private File marker(){return new File(directory,"qwen2.5-0.5b-instruct-q4_k_m.verified");}
    public boolean isReady(){return getModelFile().isFile()&&getModelFile().length()==MODEL_BYTES&&marker().isFile();}
    public boolean isDownloading(){return DOWNLOADING.get();}
    public void cancel(){cancelled=true;HttpsURLConnection c=active;if(c!=null)c.disconnect();}
    public void deleteModel(){if(DOWNLOADING.get())throw new IllegalStateException("أوقف التنزيل أولًا");marker().delete();getModelFile().delete();}
    public void download(Progress progress) throws IOException {
        if(isReady()){if(progress!=null)progress.onProgress(MODEL_BYTES,MODEL_BYTES);return;}
        if(!DOWNLOADING.compareAndSet(false,true))throw new IOException("تنزيل النموذج جارٍ بالفعل");
        cancelled=false;File part=new File(directory,"model.part");
        try{
            if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("تعذر إنشاء مجلد النموذج");
            if(new StatFs(directory.getAbsolutePath()).getAvailableBytes()<MODEL_BYTES+64L*1024*1024)throw new IOException("تحتاج إلى نحو 560 ميغابايت مساحة متاحة لتنزيل النموذج");
            URL url=new URL(MODEL_URL);HttpsURLConnection connection=null;
            for(int redirects=0;redirects<8;redirects++){
                if(cancelled)throw new IOException("تم إلغاء التنزيل");
                if(!"https".equalsIgnoreCase(url.getProtocol()))throw new IOException("رابط تنزيل غير آمن");
                connection=(HttpsURLConnection)url.openConnection();active=connection;connection.setConnectTimeout(30000);connection.setReadTimeout(45000);connection.setInstanceFollowRedirects(false);connection.setRequestProperty("Accept-Encoding","identity");
                int code=connection.getResponseCode();
                if(code>=300&&code<400){String location=connection.getHeaderField("Location");connection.disconnect();if(location==null)throw new IOException("رابط التنزيل غير صالح");url=new URL(url,location);connection=null;continue;}
                if(code!=200)throw new IOException("خادم النموذج أعاد HTTP "+code+". حاول لاحقًا.");
                long expected=connection.getContentLengthLong();if(expected>0&&expected!=MODEL_BYTES)throw new IOException("حجم النموذج لا يطابق الإصدار المثبت");break;
            }
            if(connection==null)throw new IOException("تحويلات كثيرة في رابط التنزيل");
            MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(Exception e){throw new IOException(e);}
            long count=0,lastProgress=0;
            try(BufferedInputStream in=new BufferedInputStream(connection.getInputStream());FileOutputStream out=new FileOutputStream(part)){
                byte[] buffer=new byte[128*1024];int read;
                while((read=in.read(buffer))!=-1){
                    if(cancelled)throw new IOException("تم إلغاء التنزيل");count+=read;if(count>MODEL_BYTES)throw new IOException("تجاوز التنزيل الحجم المتوقع");
                    out.write(buffer,0,read);digest.update(buffer,0,read);
                    long now=System.currentTimeMillis();if(progress!=null&&now-lastProgress>250){progress.onProgress(count,MODEL_BYTES);lastProgress=now;}
                }out.getFD().sync();
            }
            if(cancelled)throw new IOException("تم إلغاء التنزيل");
            if(count!=MODEL_BYTES||!MODEL_SHA256.equals(hex(digest.digest())))throw new IOException("فشل التحقق من سلامة النموذج. أعد المحاولة.");
            marker().delete();if(getModelFile().exists()&&!getModelFile().delete())throw new IOException("تعذر استبدال النموذج");if(!part.renameTo(getModelFile()))throw new IOException("تعذر حفظ النموذج");
            try(FileOutputStream out=new FileOutputStream(marker())){out.write(MODEL_SHA256.getBytes(StandardCharsets.US_ASCII));out.getFD().sync();}
            if(progress!=null)progress.onProgress(MODEL_BYTES,MODEL_BYTES);
        }finally{HttpsURLConnection c=active;active=null;if(c!=null)c.disconnect();if(part.exists())part.delete();DOWNLOADING.set(false);}
    }
    private static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte x:bytes)b.append(String.format(java.util.Locale.ROOT,"%02x",x&255));return b.toString();}
}
