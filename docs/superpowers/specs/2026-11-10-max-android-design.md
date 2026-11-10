# Max Android assistant

Build the requested Arabic Android APK in the user's empty Musabgpt/Max repository.
Implementation choices are made autonomously under the user's build instruction.

## Design

Native Java Android application, Android 8+ (API 26), target API 35. Arabic RTL,
dark interface with chat, task scheduling, inbox, local memory, and setup pages.
No account or backend is needed. Chat and memory stay in private SQLite storage.
The optional NVIDIA key is encrypted using Android Keystore and excluded from backup.

The free provider runs Qwen2.5 0.5B locally through pinned llama.cpp. Its 491 MB
GGUF download is automatic after a clear user download action, verified by SHA256.
The small model has limited reasoning quality. NVIDIA is optional: show the provider's
key page during first setup, save the supplied key once, and explain provider quotas.
No embedded shared API keys or unsupported promises of perpetual free cloud access.

## Tools and Android constraints

Explicit commands can open allowed apps/sites, read accessible screen text, fill a
focused input, navigate, and search flights/trains. Review dialogs precede UI actions.
A built-in browser supports browsing and form filling without bypassing site login,
CAPTCHA, or payment confirmation. Flight and train flows open booking searches;
the app does not claim that a ticket has been purchased.

Notification access captures Gmail/Outlook and WhatsApp notifications after the user
enables it. This is recent notification content, not complete inbox/history access.
Inline replies use a live notification's RemoteInput after review. Email composition
opens the installed email app. No user communications are sent during development.

Permissions always use Android's official user consent screens. Accessibility cannot
unlock the phone, read passwords, or grant permissions automatically. Background
tasks use a visible foreground service, a work/sleep switch, and scheduled reminders;
availability depends on Android battery restrictions. Screen operations wait for
unlock. Speech recognition is user initiated, TTS optional; no boot-time microphone.

## Approaches considered

Native Android supports accessibility, notifications, foreground services and local
inference directly. A web wrapper cannot provide these reliably. A hosted-agent-only
app requires accounts/API configuration and cannot meet the free local option.
Native Java with JNI was selected by the implementer.

## Acceptance

Produce an actual debug APK, source and repeatable build instructions. Compile,
run meaningful command/scheduling tests and Android lint, inspect packaged manifest,
and attempt emulator smoke testing. Report any unavailable hardware validation and
unimplemented integrations honestly. Keep durable evidence under .m1 and docs.
