# Android UI smoke verification

Device: disposable Android 8.0 / API 26 `default;x86_64` emulator, 540×960 at
240 dpi, software CPU/GPU emulation (no KVM). Final APK installed successfully:
`app/build/outputs/apk/debug/app-debug.apk`, SHA256
`d1c0e5ff1fbfc19d346485b28337d28c838714983b1a3f781ad0789f483a2353`.

The first software boot completed in approximately 147 seconds. No installation
or app code was changed for testing. ADB taps use bounds from UI XML, not image
coordinates. All input data is synthetic and local to this disposable emulator.

## Observed baseline checks

| Check | Result | Evidence |
|---|---|---|
| APK installation, MainActivity, Arabic onboarding/home | PASS | `screenshots/first-launch.png`, `screenshots/home.png` |
| Navigation through all five tabs | PASS | numbered UI XML captures |
| Add local memory and persist across force-stop/relaunch | PASS | `screenshots/memory.png`, `smoke-results.json` |
| Create task with future schedule | PASS | `screenshots/tasks.png` |
| Work → sleep → work mode toggle | PASS | `smoke-results.json` |
| Chat with missing local model shows actionable download/settings dialog | PASS | `screenshots/model-needed.png` |
| Rotate activity and return to portrait | PASS | `smoke-results.json` |
| Java/native fatal crash buffer after baseline | PASS, empty | `crash-logcat.txt` |
| Scheduled task processes while screen is off | PASS, controlled missing-model failure | `screen-off-power.txt`, `screenshots/background-result.png` |

The screen-off check confirmed `mWakefulness=Asleep` before the task was due.
The task then recorded `failed` with the expected Arabic message explaining that
the local model is not downloaded. This establishes execution and error handling
while the screen is off, rather than claiming successful model inference on this
emulator. Model generation and native Android loading have separate evidence below.

## Reproduce

With an already booted disposable emulator and installed Android platform tools:

```sh
python3 scripts/smoke-test.py --serial emulator-5554 \
  --apk app/build/outputs/apk/debug/app-debug.apk
python3 scripts/smoke-test.py --serial emulator-5554 --background-check
```

The optional background check advances the synthetic task's due-time fixture in
the debug application's SQLite database while the app is stopped, enables the
foreground session through the UI, turns the emulator screen off, and inspects
the resulting task record. It does not send mail/messages or download models.

## Scope limits

This emulator has no user's Gmail/WhatsApp accounts, NVIDIA key, or actual booking
accounts. Live mail, notification inline replies, NVIDIA quota/model access,
real purchases, and device-specific speech recognition were not exercised.
The API 26 run does not establish behavior on Android 14/15 battery/foreground
restrictions or a manufacturer's firmware. Verify these on a representative phone.
Actual local-model generation was verified separately by the host JNI harness;
the Android APK's x86_64 native load/error/cancel path was also executed separately.
See `model-runtime.md` and `android-native-runtime.md`.

The initial ADB input helper was corrected to re-read field bounds after the IME
shifted dialogs and to avoid dismissing a dialog when no keyboard was shown.
The final baseline results above were observed after that helper correction;
this did not require a product-code change.
