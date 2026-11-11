# Integration review

Reviewed source covers MainActivity dispatch/draft lifecycle, typed plans and app
resolution, final-commit state/window evidence, HTML document storage and sandbox,
provider output handling, voice cancellation/fallback and schema2 migration.

- Exact Facebook screenshot command and calculator command are recognized locally,
  before model readiness checks. Ordinary quoted instructions/draft requests remain chat.
- Generated device plans pass a strict four-field schema and visible review. No model
  text is interpreted as taps, shell commands or arbitrary Android actions.
- Final commit requires the same unlocked, active, focused app/window, recipient,
  text and field identity after approval. Consent is consumed once before the click;
  failures never automatically replay an external send/post.
- HTML has a private opaque identifier, bounded storage and explicit clearing. The
  preview has no file/content access, native bridge, network, child frames or permissions.
- Missing/sentinel provider answers are controlled failures; private reasoning is not
  surfaced. Review found GPT's retry budget remained4096 despite a larger logical
  budget; the new regression failed before the fix and all69 JVM tests pass afterward.
- Speech results populate a draft. Unsupported-language errors open one system voice
  Activity, without forcing offline recognition; errors do not resend a request.
- Schema migration adds the artifact column without dropping tables. Android framework
  instrumentation verifies the real encrypted fixture key and memory/chat continuity.

QA-only review fixed a case-sensitive `PIN set` assumption and moved its assertion
inside finally-protected credential cleanup. No live social account, user key, message
delivery or booking purchase was used as a test. Actual service UI variation and
physical ARM64 behavior remain outside this fixture validation.
