# Claude Code Auto-Memory Survey

Read-only reconnaissance of all 12 memory files across 5 projects under
`~/.claude/projects/*/memory/*.md`. No files modified, moved, or deleted.

## Per-file table

| Project | File | Frontmatter | Lines | Content type | Gist | Staleness signal | Naming |
|---|---|---|---|---|---|---|---|
| cjatmanager | `MEMORY.md` | none (plain `# Memory Index` + bullets) | 5 | index | Points at the 3 other cjatmanager files with one-line hooks | Only as stale as what it indexes | — |
| cjatmanager | `concurrency-design-settled.md` | full (name/description/metadata: node_type, type=project, originSessionId) | 26 | decision-log | Storage-layer `synchronized(conn)` locking replaced call-site `databaseLock`, as of commit a58a867 | **High** — references `chatmap.ui`/`chatmap.service` packages; current source tree has no such packages (it's `chatmap.application.service`/`chatmap.presentation.*`/`chatmap.infrastructure.*`), implying a hexagonal-architecture rename happened after this was written | kebab-case |
| cjatmanager | `prefers-bourne-shell.md` | full (type=feedback) | 15 | preference | Use Bash/POSIX sh, not PowerShell, on this Windows box | Low — a standing behavioral preference, not tied to code state | kebab-case |
| cjatmanager | `standing-review-findings.md` | full (type=project) | 58 | findings (+ decision-log elements) | Top structural problems + fix order; reports 3 big items DONE, 3 low-severity items remaining, plus a running "DONE this session" log | **High** — repeatedly references `chatmap.backend`, `chatmap.ui`, `chatmap.cli`, `chatmap.domain` packages ("chatmap.backend is owned by someone else... leave it alone"); none of these exist in the current tree, which uses `chatmap.infrastructure`/`chatmap.presentation`/`chatmap.application`. Heavily commit-hash- and session-anchored content — reads like a running scratch log more than a durable memory | kebab-case |
| dotmdfiles | `MEMORY.md` | none | 1 | index | Points at the one workflow file | Low | — |
| dotmdfiles | `workflow_chat_extraction.md` | full (type=user) | 19 | workflow (+ preference) | User's habit: distill each LLM chat into canonical `.md` files (architecture/design/patterns/working-context) instead of keeping transcripts; watch for unapplied updates | Low-medium — describes a durable habit, but cites a specific example (`miniReader`/`minireader-extract-1.md`) that could rot | **filename is snake_case but internal `name:` field is kebab-case** (`workflow-chat-extraction`) — mismatch |
| league-of-legends-discord-bot | `MEMORY.md` | none | 1 | index | Points at the one standalone-project file | Low | — |
| league-of-legends-discord-bot | `standalone-project.md` | full (type=project) | 10 | other (architecture fact) | Bot is pure Python + Discord token, no "openclaw" or other external service dependency; ranked-data fields are still placeholders | Low — simple factual claim about repo shape, easy to re-verify | kebab-case |
| myclaw | `MEMORY.md` | none | 2 | index | Points at both myclaw files; **already self-annotates** the second as "superseded in priority" | Low (it's doing staleness-flagging work itself) | — |
| myclaw | `project_switchboard_pivot.md` | full (type=project) | 24 | project-vision | myclaw is pivoting to become "Switchboard," a multi-window multi-session AI workbench (Session-centric, not model-centric); MVP scope, repo-rename plan, cross-repo mining plan | Low-medium — an active, self-declared "current overarching direction"; two dangling `[[wiki-links]]` (`desktop-accessibility-pass`, `myclaw-repo-layout`) to memories that don't exist in this file set | **filename snake_case, `name:` field kebab-case** (`project-switchboard-pivot`) — same mismatch pattern as dotmdfiles |
| myclaw | `project_web_desktop_vision.md` | full (type=project) | 21 | project-vision | Earlier staged web+desktop rollout plan (shared `PromptService`/`AiBackend` core, staged HTTP→browser→proxy→public rollout) | **Explicitly flagged stale by the sibling MEMORY.md** ("superseded in priority... not necessarily invalidated") and by the pivot memory itself | filename/`name:` mismatch, same pattern |
| util | `MEMORY.md` | **none** — the only file with a totally different shape: full prose doc with `##` headings and a Markdown table, no YAML frontmatter at all | 41 | other (session summary / stale redirect) | Describes `.md` template files (agents.md, persona.md, coding-style.md, etc.) that **were moved to `dotmdfiles`**; ends with "open Claude Code from `~/eclipse-workspace/dotmdfiles`" to continue | **High, self-declared** — the file says its own subject matter has relocated; the real current state lives in the `dotmdfiles` memory instead | — |

All files were readable; none empty.

## Synthesis

### 1. Types present

Revised from the handoff's proposed list — all 7 categories showed up, roughly:

- **index** — 4 (one per project's `MEMORY.md`, except `util`)
- **decision-log** — 1 clean (`concurrency-design-settled`), plus 1 hybrid (`standing-review-findings`, which is findings + a running decision/progress log)
- **findings** — 1 (`standing-review-findings`, hybrid as above)
- **preference** — 1 clean (`prefers-bourne-shell`); a second preference is embedded inside `workflow_chat_extraction` rather than standalone
- **project-vision** — 2 (`project_switchboard_pivot`, `project_web_desktop_vision`)
- **workflow** — 1 (`workflow_chat_extraction`)
- **other** — 2 (`standalone-project` — a one-off architecture fact that doesn't fit the other buckets cleanly; `util/MEMORY.md` — a full stale session-summary doc, not actually an index despite the filename)

So the taxonomy holds, but two files (`standing-review-findings`, `util/MEMORY.md`) don't fit one bucket cleanly — they're append-only running logs more than settled memory.

### 2. Most valuable to mine

- **`concurrency-design-settled.md`** — a clean, single-topic, still-plausibly-current architectural decision with clear "how to apply" guidance. Best shape for direct ingestion (would just need a staleness check against current package names first).
- **`project_switchboard_pivot.md`** — the most substantial, actively-maintained vision document in the set; explicitly cross-references and supersedes another memory, which is exactly the kind of relationship structure worth preserving if ChatMap models memory relationships.
- **`workflow_chat_extraction.md`** — describes a durable cross-project habit (not tied to one repo's code), so it ages better than the code-coupled files and could inform how ChatMap itself organizes "distilled knowledge" from chat history — which is close to ChatMap's own domain.
- **`prefers-bourne-shell.md`** — trivial content but a clean example of the "preference" type done right (frontmatter, Why/How-to-apply structure); useful as a schema reference even if the content itself is low-value.

### 3. Stale, trivial, or noise — skip candidates

- **`util/MEMORY.md`** — by its own text, obsolete; the real content lives in `dotmdfiles`. Skip, or treat only as a pointer/redirect.
- **`standing-review-findings.md`** — reads as a running scratch log tied to a package structure that no longer exists (`chatmap.backend`/`chatmap.ui`/`chatmap.cli`). High risk of actively misleading if ingested without verification against current code.
- **`concurrency-design-settled.md`** — same package-staleness concern as above (`chatmap.ui`/`chatmap.service`), despite being otherwise well-formed. Needs a "does this package still exist" check before trusting the "how to apply" section.
- **The 4 bare `MEMORY.md` index files** — trivially low information density on their own (1-5 lines of pointers); not worth ingesting as content, only as a map of what else exists.
- **`project_web_desktop_vision.md`** — already explicitly superseded by its sibling memory; low priority unless ChatMap wants historical/superseded-plan context specifically.

### 4. Consistency across projects

Noticeably inconsistent, and the inconsistency clusters by project/session rather than being random:

- **Frontmatter**: every file has the identical `name`/`description`/`metadata: {node_type, type, originSessionId}` shape **except** `util/MEMORY.md`, which has none at all and reads like an older, pre-schema format.
- **Filename vs. internal `name:` casing**: `cjatmanager` and `league-of-legends-discord-bot` use kebab-case filenames that match their `name:` field. `dotmdfiles` and `myclaw` use snake_case filenames (`workflow_chat_extraction.md`, `project_switchboard_pivot.md`, `project_web_desktop_vision.md`) while their internal `name:` fields are still kebab-case — a systematic mismatch isolated to those two projects, suggesting the naming convention changed between sessions/time periods rather than varying randomly.
- **Index file quality**: `myclaw/MEMORY.md` proactively annotates staleness ("superseded in priority") in the index itself — the most mature index of the four. The other three are bare pointer lists with no such curation.
- **`type` values seen**: `project` (5x), `feedback` (1x), `user` (1x), and none (util). Only a small vocabulary so far, but not obviously exhaustive.

### 5. Recommendation

**Target `decision-log` and `project-vision` files first** (the `concurrency-design-settled` / `project_switchboard_pivot` shape) — they have the richest structured content (Why + How-to-apply) and the clearest onward value for a tool like ChatMap that's explicitly in the business of distilling durable knowledge from AI sessions.

**Biggest obstacle: staleness that isn't self-flagged.** Two of the highest-value-looking files (`concurrency-design-settled`, `standing-review-findings`) reference a package structure (`chatmap.ui`, `chatmap.service`, `chatmap.backend`, `chatmap.cli`) that no longer matches the real source tree — and nothing in the file itself signals this; you'd only catch it by cross-checking against current code. By contrast, the *actually* stale-but-self-aware files (`project_web_desktop_vision`, `util/MEMORY.md`) are low-risk because they say so. Silent staleness is the dangerous case for ingestion — any pipeline should diff a memory's concrete claims (package names, file paths, commit hashes) against current repo state before trusting its "how to apply" guidance.
