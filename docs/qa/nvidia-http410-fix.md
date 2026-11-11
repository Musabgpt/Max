# NVIDIA HTTP410 repair — Android1.0.1

The user's screenshot showed a failed NVIDIA request for “Hi” with HTTP410.
The shipped default was `meta/llama-3.1-8b-instruct`.

## Root cause and live evidence

The official model metadata endpoint returned410 without a credential and stated
that this model reached end of life on2026-08-26. The official
[model page](https://build.nvidia.com/meta/llama-3_1-8b-instruct/playground) also marks
its hosted endpoint deprecated. Raw public metadata is retained in
`nvidia-live-probe.json`; no user's key was used.

The public `/v1/models` catalog returned200 and84model IDs. The current preferred
model `nvidia/nemotron-3.5-lightning-30b-a3b` returned200 from its metadata endpoint;
its [official page](https://build.nvidia.com/nvidia/nemotron-3.5-lightning-30b-a3b)
shows a free hosted endpoint. Listing/metadata success is not proof of a particular
account's authorization or quota.

## Resulting behavior

- A saved obsolete default is interpreted as `auto`, keeping the encrypted key.
- Automatic selection uses the live NVIDIA catalog and filters embedding, safety,
  reranking and other non-chat endpoints. A previously working model is retained
  when present, with current known chat models preferred for a first request.
- If an explicit model returns404/410, one replacement request is allowed within
  NVIDIA using the same conversation. Other errors never replay the prompt.
-401,403 and429 remain distinct;403 explains Public API Endpoints/account access.
- Metadata requests omit the credential. Inference uses fixed HTTPS NVIDIA URLs,
  refuses redirects and sanitizes transport/parser errors before showing them.
- Nemotron thinking is disabled for concise final answers; private reasoning is
  never substituted for a missing final answer.
- Settings default to `auto`, show the last successful model and preserve advanced
  manual choice. VersionCode2/versionName1.0.1 installs over the original APK.

## Observed checks

- TDD:12new NvidiaClient tests failed against an unimplemented client, then passed.
  With8existing command-router tests,20JVM tests pass.
- Android lint:0errors,8warnings; debug APK build, v2signature and16KB alignment pass.
- Old/new signing certificate SHA256 matches:
  `f646305489684b9f2647dbe5ad5336bf8a3a21ff7a3c939eb5e936cc3df2e418`.
- A framework Instrumentation test seeded a dummy key through the real Android
  Keystore and a memory record on installed versionCode1:1test passed.
- `adb install -r` installed versionCode2 without uninstalling.2instrumentation
  tests then passed: the saved key decrypted unchanged, memory remained, legacy
  model choice normalized to auto, and410recovery ran against Android's actual
  JSON implementation with deterministic transport.
- UI check passed: upgraded settings display `auto`, never display the saved key,
  and produce no fatal crash. See `nvidia-v101-ui/`.
- No live authenticated NVIDIA inference was executed: the user's key remains on
  their phone. The shipped client was tested with simulated HTTP responses plus
  live public catalog/deprecation metadata.

An initial standalone `app_process` upgrade probe lacked AndroidKeyStoreProvider
initialization and could not establish a key fixture. It was replaced with normal
framework Instrumentation, which runs inside the target application process.
The passing upgrade checks are in `nvidia-upgrade-seed.txt` and
`nvidia-upgrade-verify.txt`; supported test sources are alongside those reports.
