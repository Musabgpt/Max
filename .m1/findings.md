# Verified findings and scope

- Upstream Musabgpt/Max was empty (`git clone` warning + `git ls-remote origin`).
- First APK build passed, followed by independent review and a second final build
  after lifecycle, privacy, scheduler and browser corrections.
- Composition commands such as Arabic inbox reply drafts must stay chat; regression
  covered by CommandRouterTest. Explicit screen fill requires an explicit field prefix.
- UI and service work-mode defaults now agree. TaskRunner rechecks stop/sleep between
  tasks, recovers interrupted statuses under its process guard, and closes DB helpers.
- UI destruction cancels only its own AI request, not unrelated background inference.
- Accessibility screen reads/actions return to the reviewed external target and check
  its package; browser fills pin the reviewed DOM node rather than a changing index.
- Android backup/device-transfer exclusions cover app records, keys, browser state
  and model files. NVIDIA credential strings are validated before HTTP headers.
- Final APK SHA256: d1c0e5ff1fbfc19d346485b28337d28c838714983b1a3f781ad0789f483a2353.
- JVM 8/8, lint 0 errors/7 warnings, signed APK and 16KB zip alignment verified.
- Actual pinned-model generation verified on host JNI. Android x86_64/API26 loaded
  the actual APK native code and propagated a missing-model error without crashing.

## Explicit delivery limits

Automatic permission granting, locked-screen UI control and work with power switched
off are not available to an ordinary Android app. Full mailbox synchronization and
WhatsApp message history are not implemented: this release captures opted-in new
notifications and offers reviewed replies. Booking is a browser search/manual checkout
flow. The local model is small and downloaded once; NVIDIA quotas depend on its
provider, and authenticated NVIDIA use needs the user's key. Voice recognition needs
a compatible installed Android speech service. No real user messages or purchases
were performed during development. These limits are stated in the app and README.
