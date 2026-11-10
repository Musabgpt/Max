# Local model runtime verification

Observed 2026-11-10 on Linux x86_64 with JDK 17. This is a host JNI integration check, not an Android device benchmark. Both Android ABI builds are verified separately by the Android build.

- Model: Qwen/Qwen2.5-0.5B-Instruct-GGUF, qwen2.5-0.5b-instruct-q4_k_m.gguf
- Revision: `9217f5db79a29953eb74d5343926648285ec7e67`
- Actual downloaded size: `491400032` bytes
- Actual SHA-256: `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db` (matched Hugging Face LFS metadata and app pin)
- Engine: repository-pinned llama.cpp b4514, same `app/src/main/cpp/maxllama.cpp` JNI implementation, compiled for Linux against JDK headers and CPU backend.
- Harness: [MaxLocalSmoke.java](MaxLocalSmoke.java). It checks actual Arabic+emoji generation, excessive-prompt rejection, and cancellation between `prepare()` and native `generate()` entry.

Observed output (process exit 0):

```text
ACTUAL_REPLY=مرحبًا بالعربية 🌍
ELAPSED_MS=1814
LONG_INPUT_REJECTED=Local prompt is too long; shorten your message or use NVIDIA
PRE_CALL_CANCEL_PRESERVED=Generation cancelled
HOST_JNI_SMOKE_PASS
```

Temporary host files/model were removed after verification to recover Android emulator disk space. No provider key was used. NVIDIA authenticated generation remains unverified without a user API key.

Other core checks: `CommandRouterTest` passed all eight JUnit 4 tests, including the actual inbox reply-composition prefix remaining chat rather than screen-fill; Java core sources compiled with JDK 17 against Android API 35.
