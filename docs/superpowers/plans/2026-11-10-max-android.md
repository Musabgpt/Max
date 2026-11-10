# Max Android Implementation Plan

**Goal:** Deliver a usable Arabic Android assistant APK from the empty Max repository.
**Architecture:** Native Java UI and SQLite, isolated AI providers, reviewed phone tools,
notification inbox, user-controlled background scheduler and local llama.cpp inference.
**Tech Stack:** Java17, Android SDK35, Gradle, CMake/JNI, llama.cpp.
**Spec:** docs/superpowers/specs/2026-11-10-max-android-design.md

## Global constraints

- minSdk26, targetSdk35; Arabic RTL; no embedded credentials.
- No permission bypass, lock-screen bypass, automatic purchases or silent sends.
- Free local model requires one visible 491 MB verified download.

## Review focus

- Disabled permissions and locked phone must yield actionable errors.
- Malformed URLs and webpage content must not invoke privileged app APIs.
- Cancelled downloads/network failures must preserve valid model files.
- Restarted scheduler must not duplicate actions or start a microphone.
- Provider credentials and private inbox must not leak into logs/backups/cloud prompts.

## Tasks

- [x] Build tooling: pinned Gradle/AGP/SDK/llama, JNI cancellation and packaging.
- [x] Data and AI: SQLite models, encrypted secrets, verified model downloader,
      local/NVIDIA providers, deterministic command parsing and meaningful tests.
- [x] Tools: accessibility, reviewed browser/forms, notification replies, service/scheduling.
- [x] Product UI: onboarding, chat and voice, tasks, inbox, memory, settings and permissions.
- [x] Integrate, independent review, tests/lint, assemble and inspect APK, attempt emulator QA.

Observed validation: JVM 8/8, lint 0 errors/7 warnings, signed and aligned APK,
actual host inference, Android native load and 9 emulator checks including screen-off
background failure handling. Physical ARM64 execution and authenticated provider/app
accounts remain untested. GitHub CI setup needed explicit platform-tools because the
action's historical default requests removed package tools; corrected run is pending.

## Ownership and interfaces

Root owns MainActivity/resources and documentation. build_tooling owns Gradle/native,
core_ai owns ai/data, android_services owns automation/service/manifest/xml.
AppStore nested models are Message, Task, Memory and InboxItem; AiRepository.reply(String)
runs on a worker and does not persist messages. All AI and download work stays off main.
ActionExecutor.preview(Activity,action,value) reviews tools before dispatch.
Independent tasks run concurrently; integration and final verification run sequentially.
