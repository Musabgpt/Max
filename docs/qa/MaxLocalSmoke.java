import com.maxassistant.app.ai.LocalLlama;
public class MaxLocalSmoke {
 public static void main(String[] args) {
  String prompt="<|im_start|>system\nأنت مساعد عربي. أجب باختصار ولا تدّع تنفيذ إجراءات على الهاتف.<|im_end|>\n<|im_start|>user\nقل مرحبًا بالعربية وأضف رمز 🌍<|im_end|>\n<|im_start|>assistant\n";
  long started=System.currentTimeMillis();
  LocalLlama.prepare();
  String answer=LocalLlama.generate(args[0],prompt,48);
  if(answer==null||answer.trim().isEmpty())throw new AssertionError("Empty actual model output");
  System.out.println("ACTUAL_REPLY="+answer.trim());
  System.out.println("ELAPSED_MS="+(System.currentTimeMillis()-started));
  LocalLlama.prepare();
  try{LocalLlama.generate(args[0],prompt+"hello ".repeat(4000),8);throw new AssertionError("Long prompt accepted");}
  catch(IllegalStateException expected){System.out.println("LONG_INPUT_REJECTED="+expected.getMessage());}
  LocalLlama.prepare();LocalLlama.cancel();
  try{LocalLlama.generate(args[0],prompt,8);throw new AssertionError("Pre-call cancellation lost");}
  catch(IllegalStateException expected){if(!expected.getMessage().toLowerCase().contains("cancel"))throw expected;System.out.println("PRE_CALL_CANCEL_PRESERVED="+expected.getMessage());}
  System.out.println("HOST_JNI_SMOKE_PASS");
 }
}
