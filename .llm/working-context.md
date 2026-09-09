---
id: CM-CTX-V1
lifecycle: working
status: active
provenance: master-savepoint-commit-e085fa1
---
# Working Context

## Current Milestone: Manual Protocol Validation [2026-09-08]
We are executing a localized experiment to determine if a small collection of durable knowledge, operational rules, and working state can provide reliable agent context without ingesting the conversational archive.

## Immediate Action Items
- [ ] Craft one manual handoff file inside `.llm/handoffs/` tracking a closed sub-chat session.
- [ ] Move `first-principles.md` and `evo.md` into the local `.llm/` directory.
- [ ] Verify that an open agent session cleanly processes the decoupled `CLAUDE.md` and `AGENTS.md` root files.

## Open Statuses
- **Handoff Pruning:** Define the criteria for when a transient handoff is permanent enough to be squashed into `design.md` versus deleted.
- **Visualization:** Observe if the Obsidian local graph view accurately connects the updated front matter keys.
