# Android UI smoke verification

Device: disposable Android 8.0 / API 26 `default;x86_64` emulator, 540×960 at
240 dpi, software CPU/GPU emulation (no KVM). Baseline APK installed successfully:
`app/build/outputs/apk/debug/app-debug.apk`, SHA256
`d1c0e5ff1fbfc19d346485b28337d28c838714983b1a3f781ad0789f483a2353`.

The browser-provider correction was then rebuilt as APK SHA256
`9bb6c764f5a131b476324597e9f2f5ed16ae9c29e48c9c6333015570abb93542`.
The final build also includes browser system-bar insets and has SHA256
`46fd8641a66e1411734dab7e5fedf440110a407361e83072637841f4dbb22a6d`.
Its installed `/data/app/.../base.apk` hash was checked through ADB and matched.
The baseline behavior is unchanged outside `BrowserActivity`; targeted regression
testing uses this corrected APK, with the original crash kept as evidence below.

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
python3 scripts/smoke-test.py --serial emulator-5554 --browser-check \
  --apk app/build/outputs/apk/debug/app-debug.apk
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

## Browser provider regression

An additional reviewed HTTPS browser launch exposed a real product defect on
this minimal AOSP image, which has no installed WebView provider: constructing
`WebView` threw `MissingWebViewPackageException` and crashed the app.
Evidence is preserved in `browser-before-fix-crash.txt` and
`webview-provider-state.txt`. This is distinct from the passing baseline's empty
crash buffer. The provider-unavailable fallback was implemented and the affected path was
retested successfully on the final APK.


## Final browser regression result (2026-11-11)

The final installed APK passed four focused checks:

| Check | Observed result |
| --- | --- |
| Reviewed HTTPS launch without a WebView provider | Actionable Arabic guidance; form-fill disabled; no MAX crash |
| External browser unavailable | Controlled message; fallback remains usable |
| Browser rotation and Back | Requested URL retained; Back returns to MAX |
| Crash buffer after corrected path | Empty |

The original regression helper assumed there was no external URL handler. Package
Manager and logcat instead identified `org.chromium.webview_shell`, which crashed
independently because this image has no registered WebView provider. The fixture
now temporarily disables that exact shell, verifies no URL handler resolves, and
restores its enabled state in `finally`. Earlier failure evidence remains in
`history/`; the intermediate lost activity context was restored before the final
full focused run. No application change was needed for these fixture corrections.

Final results contain 13 accepted checks: the nine baseline checks on unchanged
home/background code, plus four browser checks on the final APK. Normal WebView
page loading/form filling remains unverified on this provider-less image.

GitHub Actions run 42928447171 passed on app source commit
`dee44ee6575b257b5182844c166531c1f7878fa8`. Only QA helpers, reports and delivery
metadata change afterward. The app/module/build files are identical to that
successful run; no duplicate application build is needed for a documentation commit.
