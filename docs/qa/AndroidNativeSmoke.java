import com.maxassistant.app.ai.LocalLlama;
public final class AndroidNativeSmoke {
 public static void main(String[] args) {
  System.out.println("ANDROID_NATIVE_SMOKE_BEGIN");
  System.out.println("ANDROID_ABI="+android.os.Build.SUPPORTED_ABIS[0]);
  System.out.println("ANDROID_API="+android.os.Build.VERSION.SDK_INT);
  LocalLlama.prepare();
  System.out.println("APK_NATIVE_PREPARE_OK");
  LocalLlama.cancel();
  System.out.println("APK_NATIVE_CANCEL_OK");
  LocalLlama.prepare();
  try {
   LocalLlama.generate("/data/local/tmp/max-native-smoke/model-does-not-exist.gguf","<|im_start|>user\nمرحبا 🌍<|im_end|>\n<|im_start|>assistant\n",8);
   throw new AssertionError("Missing model unexpectedly succeeded");
  } catch (IllegalStateException expected) {
   if(!expected.getMessage().contains("Cannot load model"))throw expected;
   System.out.println("SAFE_MISSING_MODEL_EXCEPTION="+expected.getClass().getName()+": "+expected.getMessage());
  }
  System.out.println("ANDROID_NATIVE_SMOKE_PASS");
 }
}
