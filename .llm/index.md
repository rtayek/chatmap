---
id: CM-IDX-01
type: context-dispatcher
project_id: CHATMAP
status: active
---
# Chat Map Context Index

This is the authoritative discovery registry for the Chat Map workspace. Agents MUST review these files to establish baseline constraints before executing compilation or code tasks.

## 🧱 Codebase Invariants
- **Philosophy:** Refer to `first-principles.md` for foundational architectural assumptions and core mathematical/semantic constraints.
- **Intent:** Refer to `design.md` for functional policies, JavaFX prompt screen handling rules, and menu bar interaction defaults.
- **Evolution:** Refer to `evo.md` for historical design context and past system evolution arcs.

## 📈 Active Context Window
- **Current State:** Read `working-context.md` for rolling project milestones, recent saving checkpoints, and next development hooks.
- **Session Lineage:** Consult the `handoffs/` directory to pull specific semantic logs from recent agent-to-agent (a2a) communication runs.

## 🚫 Excluded Directories
- **Transient Data:** `../.chatmap-local/` is strictly out of scope for agent reading or analysis. It contains raw, non-distilled conversation logs and execution data.
