# Feedback for dotmdfiles: ChatMap agent-Markdown pilot

Source: `handoffs/chatmap-agents-markdown-pilot-handoff.md` (canonical
assignment) and `handoffs/chatmap-markdown-pilot-claude-cli-handoff.md`
(launch handoff), run on `feature/agents-markdown-pilot`. Read-only
investigation, no reorganization implemented.

## What the pilot actually found

- **ChatMap's working discovery pattern is flat fan-out, not a chain.**
  `CLAUDE.md` references `agents.md`/`AGENTS.md`, `persona.md`, and
  `human.md` directly, in parallel, in one hop. The proposed
  `CLAUDE.md → AGENTS.md → .agents/llm/index.md` linear chain would replace
  a working flat structure with an extra hop that does no discovery work —
  `AGENTS.md` already holds terminal content (via symlink) for at least one
  client (Codex-style `AGENTS.md` readers), so turning it into a relay costs
  those readers an extra file open for content they currently get in one.
- **If a project-controlled dispatcher is added, prefer a parallel spoke
  over an inserted hop.** i.e. every client entry file references
  `.agents/llm/index.md` directly, the same way each already references
  `persona.md`/`human.md`, rather than routing through another entry file.
- **`.agents/skills/` vs `.agents/llm/` is a real, useful distinction** —
  auto-discovered/executable/instruction-bearing vs. project-controlled
  documentation. That's the part of the proposal worth keeping regardless of
  physical layout.
- **A live bug independent of the whole `.agents/` question:** ChatMap's
  `CLAUDE.md` referenced `@agents.md` (lowercase) while the tracked file is
  `AGENTS.md`. This resolves silently on Windows/Git Bash (case-insensitive
  lookup) and fails silently on a case-sensitive checkout (native Linux,
  case-sensitive WSL volumes, most CI). If this reference text originates
  from a `dotmdfiles` template, other consumer repos likely have the same
  latent bug, invisible to anyone testing only on Windows or macOS.
- **`AGENTS.md`, `persona.md`, and `human.md` in ChatMap are git symlinks**
  (mode `120000`) to an absolute path outside the repo
  (`C:/Users/ray/real-md-files/...`), not tracked copies. This means:
  - ChatMap's own git history has no content to diff/blame for its own
    behavioral instructions.
  - The repo's local `core.symlinks=false` means a fresh checkout under this
    same config writes these files as plain text containing the literal
    path string, not the real content — the *repo's own settings* would
    break its own instruction chain on re-checkout.
  - The absolute, single-user, single-OS target means the setup only works
    on this one machine/account; any other clone, contributor, or CI run
    gets a dangling link.
  - If `dotmdfiles` intends symlinking as the answer to the
    template/consumer drift problem, it should mandate relative paths (or a
    documented setup step) and `core.symlinks=true`, and say so explicitly —
    currently this assumption is silent and machine-specific.
- **Lifecycle tagging by filename/status-line convention already works
  informally** in ChatMap (`*-PRELIMINARY.md` status banners,
  `handoffs/archive/` for retired docs) without any new directory. Worth
  citing as a lightweight alternative to a formal `.agents/llm/index.md`
  dispatcher for projects that don't yet have enough operational Markdown to
  need one.

## What was not resolved (needs Ray's decision, not dotmdfiles' spec)

- Whether ChatMap's `agents.md`/`persona.md`/`human.md` should stay
  symlinks at all, given they're already fragile on this machine.
- Which of ChatMap's three independent architecture reviews (if any) is
  canonical — a ChatMap-local documentation problem, unrelated to the
  `.agents/` convention.

## Net recommendation for the general convention

Keep the skills/llm trust split. Loosen or drop the prescribed linear chain
in favor of parallel spokes from each client entry file. Add explicit
guidance on symlink portability (relative paths, `core.symlinks=true`) and
on `AGENTS.md` casing, since both produced silent, undetected failures in a
real pilot rather than a hypothetical one.
