---
id: CM-IDX-01
lifecycle: durable
status: active
provenance: manually-authored
---
# Chat Map Context Index

This is the canonical entry point for the Chat Map repository. Agents MUST read all documents listed below to establish a reliable baseline context before executing code verification or task loops.

## 🧱 Durable Knowledge
- **Philosophy:** See `first-principles.md` for core token-density and context-handling equations.
- **Intent:** See `design.md` for multi-LLM routing, JavaFX prompt screens, and object responsibilities.
- **Trajectory:** See `evo.md` for past structural design pivots and architectural reasoning.

## 📈 Working State
- **Current Action:** See `working-context.md` for active milestones and open operational timelines.
- **Lineage:** Consult the `handoffs/` folder for transient state transfers between active session runs.

## 🚫 Operational Boundaries
- **Walled Off:** Agents MUST NOT open, parse, or index any contents inside the `../.chatmap-local/` directory unless the immediate task contains an explicit human authorization referencing a specific file ID.
