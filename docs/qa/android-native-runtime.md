# Android native runtime smoke check

Observed 2026-11-10 using the built APK on emulator-5554, Android API 26 / x86_64. Process exited 0. No application UI, settings, or app data were modified.

- APK SHA-256: `d1c0e5ff1fbfc19d346485b28337d28c838714983b1a3f781ad0789f483a2353`
- Loaded the APK's real `LocalLlama` Java class through ART and its x86_64 `libmaxllama.so` plus `libc++_shared.so` extracted from that APK.
- `prepare()` and `cancel()` JNI calls succeeded.
- Native backend initialization succeeded on Android.
- A deliberately missing model produced the expected Java `IllegalStateException`, with no native crash.
- Harness: [AndroidNativeSmoke.java](AndroidNativeSmoke.java), compiled with JDK 17 and SDK 35 `d8 --min-api 26`.

Invocation after copying harness dex, APK, and native libraries to `/data/local/tmp/max-native-smoke`:

```sh
CLASSPATH=/data/local/tmp/max-native-smoke/harness.dex:/data/local/tmp/max-native-smoke/max.apk LD_LIBRARY_PATH=/data/local/tmp/max-native-smoke app_process -Djava.library.path=/data/local/tmp/max-native-smoke /system/bin AndroidNativeSmoke
```

Observed output:

```text
ANDROID_NATIVE_SMOKE_BEGIN
ANDROID_ABI=x86_64
ANDROID_API=26
APK_NATIVE_PREPARE_OK
APK_NATIVE_CANCEL_OK
register_backend: registered backend CPU (1 devices)
register_device: registered device CPU (Android virtual processor)
gguf_init_from_file: failed to open GGUF file '/data/local/tmp/max-native-smoke/model-does-not-exist.gguf'
llama_model_load: error loading model: llama_model_loader: failed to load model from /data/local/tmp/max-native-smoke/model-does-not-exist.gguf

llama_model_load_from_file_impl: failed to load model
SAFE_MISSING_MODEL_EXCEPTION=java.lang.IllegalStateException: Cannot load model; download may be incomplete or memory insufficient
ANDROID_NATIVE_SMOKE_PASS
```

This verifies Android x86_64 dynamic linking, JNI entrypoints, backend initialization, and failure propagation at minSdk 26. Actual text generation was separately verified on the host with the pinned model ([model-runtime.md](model-runtime.md)); full Android model generation and physical ARM64 execution were not exercised by this check.
