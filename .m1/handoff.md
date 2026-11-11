# MAX Android1.0.1 delivery complete

Latest user issue: NVIDIA HTTP410 on Hi. Root cause was the shipped obsolete
meta/llama-3.1-8b-instruct hosted endpoint (public metadata confirms EOL2026-08-26).

Update:https://raw.githubusercontent.com/Musabgpt/Max/v1.0.1-preview.1/apk/MaxAssistant-v1.apk
Release:https://github.com/Musabgpt/Max/releases/tag/v1.0.1-preview.1
SHA256:2d70d15b1d744c846de76790335826de8a48ac83b70775ef08bc8fabde23d1a4

NvidiaClient now selects live catalog chat models, migrates the old default to
auto and retries a model-unavailable404/410 once within NVIDIA. Other failures
do not resend the prompt. Stored API key/SQLite paths remain unchanged.

Verified20JVM tests, lint0errors8warnings, same signing certificate as1.0.0,
3Android instrumentation checks across genuine install-r upgrade (encrypted
key+memory retained), actual Android JSON recovery, settings UI, and GitHub CI
42934336266 on application source8d1e89d. Pinned public APK digest matched.
No real user key was requested or used; authenticated NVIDIA inference still
needs the user to retry on their device. Old general feature limits remain in
README. No job/approval pending. Future changes should preserve signing identity
and inspect the release and .m1 state before retrying publication.
