package com.maxassistant.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** The API key stays on this device, encrypted by a non-exportable Android Keystore key. */
public final class SecureSecrets {
    private static final String ALIAS="max-assistant-secrets-v1";
    private final SharedPreferences prefs;
    public SecureSecrets(Context context){prefs=context.getApplicationContext().getSharedPreferences("max-secrets",Context.MODE_PRIVATE);}
    private static synchronized SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return generator.generateKey();
    }
    public void setNvidiaKey(String value){
        String trimmed=value==null?"":value.trim();if(trimmed.isEmpty()){clearNvidiaKey();return;}if(trimmed.length()>2000)throw new IllegalArgumentException("مفتاح API طويل جدًا");
        if(!trimmed.matches("[\\x21-\\x7E]+"))throw new IllegalArgumentException("الصق مفتاح API وحده دون مسافات أو أسطر إضافية");
        try{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());byte[] encrypted=cipher.doFinal(trimmed.getBytes(StandardCharsets.UTF_8));prefs.edit().putString("nvidia_cipher",Base64.encodeToString(encrypted,Base64.NO_WRAP)).putString("nvidia_iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).apply();}
        catch(Exception e){throw new IllegalStateException("تعذر حفظ المفتاح بشكل آمن على هذا الهاتف",e);}
    }
    public String getNvidiaKey(){
        String encoded=prefs.getString("nvidia_cipher","");if(encoded.isEmpty())return "";
        try{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(prefs.getString("nvidia_iv",""),Base64.NO_WRAP)));return new String(cipher.doFinal(Base64.decode(encoded,Base64.NO_WRAP)),StandardCharsets.UTF_8);}
        catch(Exception e){return "";}
    }
    public void clearNvidiaKey(){prefs.edit().remove("nvidia_cipher").remove("nvidia_iv").apply();}
}
