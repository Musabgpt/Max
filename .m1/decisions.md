# Decisions

- 2026-11-10: Empty upstream confirmed with git clone and git ls-remote. Build native
  Java Android on feature/max-assistant; preserve origin and do not push implicitly.
- Use downloaded local Qwen2.5 0.5B as the genuinely no-key option. NVIDIA optional.
- Respect Android permission/keyguard restrictions; notification inbox is not full email sync.
- Bundled M1 scripts are not exposed by the skill resource provider (read failed),
  so use the documented state schema with atomic local checkpoint writes.
- Developer authorization requires completing reversible build work without an extra
  design approval pause. Design/plan reflect implementer choices, not user approvals.
