---
id: CM-EVO-01
lifecycle: durable
status: active
provenance: manually-authored
---
# Architectural Evolution

## Structural Trajectory
- **Context Management Pivot:** Transitioned from a generic template tracking framework to a focused semantic-compression engine.
- **Indirection Reduction:** Rejected long, nested discovery chains (`CLAUDE.md -> AGENTS.md -> .llm/index.md`) in favor of a flat fan-out discovery structure where independent tools route directly to the canonical `.llm/index.md` dispatcher.
- **Metadata Federation:** Separated document-level properties (YAML front matter) from system-level tool execution parameters (`map-manifest.json`), eliminating conflicting data authorities.
