# MAX1.1: connect user requests to real tools

User screenshots expose three independent defects: device requests reach a text-only
system prompt, requested HTML cannot run inside MAX, and null/unsupported speech
responses surface raw. This is continuation of the authorized Android assistant build.
Implementer decisions below use the existing native Java architecture.

## Device execution

Parse explicit Arabic/English open-app, Facebook post and WhatsApp message requests
into typed, bounded plans. Exact screenshot examples must work without an LLM call.
Review the concrete app/recipient/body before starting; opening a launcher does not
need accessibility. Multi-step drafting uses user-enabled accessibility, a visible
foreground session and trusted accessibility overlay with cancel. Final send/post
requires a separate user confirmation and revalidation of app, recipient/draft and
unlocked state. No execution of arbitrary LLM code, no permission/keyguard bypass,
no background resumption of an unconfirmed commit. Report observed actions without
claiming server-side posting/delivery. Unknown tasks may be interpreted by the model
only into the same allowlisted high-level plan schema, followed by review.

App discovery remains on-device through MAIN/LAUNCHER visibility, with known aliases
including Facebook/Lite and WhatsApp/Business. No screen snapshot is sent to a model.
Accessibility tasks stop on wrong app, ambiguous target, timeout, lock or cancellation.

## HTML

Recognize HTML-create-and-run requests before free-model readiness checks. The
calculator example has a complete built-in HTML implementation needing no key/model.
Other HTML can be generated with a separate code prompt and opened as a local
artifact. Add a preview button to saved generated replies and existing HTML answers.

Preview is an internal WebView sandbox: JS enabled, all network/file/content/native
bridges and permissions blocked. Code can be inspected/copied. Artifacts use validated
opaque IDs and bounded private files. Schema2 adds an artifact_id to messages using
non-destructive ALTER TABLE so old chat/memory/key data survives.

## Chat/voice

Remove the false blanket text-only persona; do not claim actions without tool results.
Reject null/undefined/empty sentinel replies. Never reveal private reasoning. A
documented length-truncated empty completion may get one bounded larger-budget retry.
Speech must not force offline Arabic; use a proper locale tag, fall back to the system
recognition activity on unsupported-language/unavailable errors, then show actionable
voice settings if no provider exists. Voice transcription remains a draft until Send.

## Validation

Meaningful parser/guard/response/voice-policy unit tests; compile/lint/signing checks.
Use an official SDK image with a working WebView to actually execute calculator JS
and verify network denial. Use isolated fake Facebook/WhatsApp apps for real Android
accessibility drafting/confirmation/cancel tests; never send a real post or message.
Preserve old APK signing identity, encrypted fixture key, database and memory across
the1.0.1→1.1.0 update. Clearly distinguish fixtures from real service/account testing.
