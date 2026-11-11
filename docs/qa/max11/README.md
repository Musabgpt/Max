# MAX1.1 Android validation

This directory contains reproducible test inputs and observed evidence for a disposable
Android emulator. The Facebook, WhatsApp and speech packages are local fixtures, with
no network permission, service accounts, real posts, or real messages.

The HTML gate requires a working official WebView provider and actual JavaScript
execution. Missing-provider fallback is not a passing HTML preview result.

Use instrumentation's `getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)`
throughout accessibility tests. The standalone `uiautomator dump` command can suppress
the MAX accessibility service and invalidate a run.

QA source ownership: `docs/qa/max11/` and `scripts/qa_tooling/`. Product builds and signing
are performed by the integrating agent; the fixture builder does not run Gradle.

The official API30 Google APIs image supplies `com.google.android.webview83.0.4103.106`.
This host uses TCG without KVM. Unused GMS, Google Search/Velvet, Wellbeing and GSF
were disabled in the disposable emulator after background ANRs/system-server restarts;
the official WebView remains enabled. Vulkan is disabled, with SwiftShader GLES.
The small `qa.home` fixture is the default Home during tests; this avoids the Pixel
Launcher ANR dialog after Google services are disabled. The original blocked attempt
is retained under `run-device-positive/` and is not counted as a passing app test.
No platform/framework/WebView code or allowlist was patched. Earlier environment
failures are not counted as application test results.

Reproduce using the installed SDK/JDK (builders accept alternate paths):

```bash
python3 scripts/qa_tooling/build_fixture_apks.py
python3 scripts/qa_tooling/build_instrumentation.py --seed
python3 scripts/qa_tooling/build_instrumentation.py
```

For API26, rebuild only the speech fixture with
`python3 scripts/qa_tooling/build_fixture_apks.py --only speech --legacy-speech-binding`.
Its framework binds directly to RecognitionService, before the modern system broker;
requiring the modern signature bind permission prevents the intended error12 callback.
This changes only the no-network fixture. The production APK and microphone permission
flow are unchanged.

For the upgrade gate, install the original APK from tag `v1.0.1-preview.1` on the
disposable emulator, install the seed instrumentation, and run
`qa.max11.Max11UpgradeSeedTest`. The seed expects versionCode2/schema1 and inserts
only a dummy encrypted key, a fixture memory, a preference marker and two messages.
Then install the new production APK with `pm install -r`, without clearing data,
and replace the seed instrumentation APK with `verify/max11-test.apk`.

Install the four no-network fixture APKs. For this TCG environment, select
`qa.home/qa.home.FixtureHomeActivity` with `cmd package set-home-activity` and stop
the Pixel Launcher before running the UI cases. Then run:

```bash
python3 scripts/qa_tooling/run_instrumentation.py --serial emulator-5554 \
  --output docs/qa/max11/run
```

The localhost observer must receive a native-control request and must receive no
request from the HTML preview. The HTML tests require real WebView JavaScript to
compute `2+3*4=14`, including dispatch through the chat composer. Device tests verify
review, local drafting, cancellation, stale/double confirmation, edited body, changed
recipient, Home/wrong app and lock guards. The speech fixture first returns error12,
then an actual recognition Activity result, which must stay an unsent draft.

These fixtures prove Android integration and guard behavior. They do not establish
compatibility with every live Facebook/WhatsApp UI, microphone hardware, real account
delivery, authenticated NVIDIA quotas or physical ARM64 performance.

Accepted results are indexed in `acceptance.json`:15 distinct scenarios plus the
old-version seed on APK SHA1f984e82. API30 supplied the upgrade and real HTML checks;
API26 supplied10 reviewed-device scenarios and speech error12 fallback. The API26
lock fixture initially left a stale PIN bouncer; credential cleanup now refreshes the
nonsecure keyguard. Only the four blocked cases were rerun after repair. A stronger
voice-input visibility assertion also passed on the unchanged APK; its preselected
directory name `run-voice-visible-red` does not mean the run failed.

The API30 sandbox's DOM/settings assertions passed. Its interrupted combined run did
not retain a complete host observer archive; no independent full-run zero-request
receipt is claimed. Earlier failed/interrupted logs remain available alongside the
passing per-case records so environment repairs are reviewable.
