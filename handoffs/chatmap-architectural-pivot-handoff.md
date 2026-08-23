# ChatMap Architectural Pivot Handoff

## Context & North Star

ChatMap is pivoting from a monolithic multi-model harness into modular, independently-evolving subsystems. The north star is `evo.md` in the repo.

**The insight:** Everyone (Claude, ChatGPT, Gemini) is building agentic harnesses and tool-use APIs. You don't replicate that. Instead, ChatMap owns what only a local agent OS gateway can: **multi-model neutrality, local file custody, hardware primitives, and transparent audit trails.**

**The core win:** Let users talk to Claude, extract something, feed it to Gemini, get something back, feed *that* to a local model—all without vendor lock-in or friction. That's the job. Everything else is supporting infrastructure.

## The New Architecture

Three subsystems (others deferred):

### 1. **Vault** (opt-in persistence)
- SQLite chat storage (raw logs from 6 providers)
- Markdown workspaces (project context, coding styles, constraints)
- Local filesystem grounding (Windows path mapping)
- FTS5 search
- **Status:** Already exists; needs isolation into a standalone module
- **Timeline:** Refactor first (break circular deps), then evolve

### 2. **Router** (multi-model switching + extraction)
- The spine of the system
- Talk to model A, extract output, feed to model B, extract again, feed to model C
- Chain arbitrary sequences of LLM calls across providers
- Manage session state so continuity isn't lost
- **Status:** Skeleton only (interfaces, no logic)
- **Timeline:** Build after Vault is isolated

### 3. **Gateway** (sandbox + primitives)
- Java sandbox that blocks external LLM calls from escaping your workspace
- Micro-utilities: zip/unzip, local disk ops
- Model decides *what*; ChatMap executes mechanically
- **Status:** Stubbed; minimal
- **Timeline:** Implement as Router needs it

### Deferred (not Day 1)
- **Mesh Net:** P2P sync across instances. Nice-to-have for collaboration; not essential for core routing job.
- **Semantic extraction:** Agentic downstream task. External agent spins up, parses chats, outputs .md. ChatMap doesn't do this.
- **Knowledge graphing:** Same—external agent work.
- **UI ornament:** Sparse, testable CLI-like starting point. Menu restructuring, prompt mode, etc. come after Router is solid.

## Immediate Work: Vault Isolation

See `vault-isolation-handoff.md` for detailed execution plan.

**Goal:** Extract Vault into a standalone, clean-API module so:
1. Router can call it without dragging UI/orchestration baggage
2. Vault can evolve independently (different storage backends later)
3. Agents can work on Router while Vault stabilization completes

**Scope:** 5–7 hours of focused agent work (audit, extract, break deps, test, stub Router skeleton).

**Phases:**
1. Create `vault` package with clean API surface
2. Move all SQLite access into Vault
3. Break circular dependencies in ChatMapController
4. Write Vault tests (100% coverage of public API)
5. Stub Router skeleton (empty interfaces)
6. Document what's parked and why

**Success criteria:**
- [ ] All SQLite access goes through VaultAPI
- [ ] No circular dependencies
- [ ] Vault tests pass
- [ ] Router skeleton compiles
- [ ] ChatMapController uses VaultAPI instead of reaching into SQLite

## Post-Vault Work (Future Handoffs)

Once Vault is isolated and tested:

1. **Router implementation** — multi-model switching, extraction logic, chain execution
2. **Gateway primitives** — sandbox enforcement, zip/unzip, disk ops
3. **Session state management** — continuity across provider switches
4. **Integration tests** — Router + Vault + Gateway working together

Each becomes a separate, testable handoff.

## Architecture Decisions (Locked In)

- **Router is the spine** — not Vault, not UI. Router orchestrates everything.
- **Vault is opt-in** — not every workflow needs storage. Fire-and-forget is valid.
- **Gateway is minimal** — just enough to expose local primitives safely.
- **No Mesh Day 1** — defer until you know you need it.
- **Agents handle downstream** — semantic extraction, knowledge graphing are external.

## Who Should Do What

### Vault Isolation (Current)
**Best:** Claude Code CLI or Codex
- Narrow, architectural, well-scoped
- Java architecture work
- Structured handoff (phases defined)

**Not:** Antigravity (opaque memory), qwen-2.5-72b (retired from refactor duty)

### Router Implementation (Next)
**Best:** Claude Code CLI or Codex (whichever didn't do Vault)
- Multi-model switching logic
- Extraction/chaining primitives
- Well-defined interface (Router skeleton waiting)

### Gateway Primitives (After Router)
**Best:** Either agent
- Straightforward Java utilities
- Sandbox validation (critical—needs review)
- Zip/unzip, disk ops

## Known Constraints & Patterns

- **Low vision:** All UI decisions filtered through accessibility (sparse layouts preferred)
- **Voice dictation heavy:** Expect transcription artifacts in notes/comments
- **Multi-agent review discipline:** Every diff must be reviewed before pushing. Revert aggressively with `git reset --hard` if broken.
- **Git workflow:** Use `git apply [patch]` + manual add/commit, not `git am` (known to fail in Ray's environment)
- **Verify in fresh clone:** Test patches in a clean checkout before considering them done
- **Scene graph bugs:** UI elements created but not attached to visible graph. Always verify attachment.
- **Session continuation pattern:** Validate session IDs flow through correctly; don't hardcode `sessionId=null`

## Key Files

- **evo.md** — the north star vision (read first)
- **vault-isolation-handoff.md** — detailed execution plan for Vault isolation
- **Current codebase:** `C:\Users\ray\eclipse-workspace\cjatmanager` (local); `https://github.com/rtayek/chatmap` (remote)

## Notes for Agents

- Read `evo.md` first to understand why we're doing this
- The handoff is terse intentionally; ask for clarification before assuming
- Commit frequently and clearly (decision: why, open: what's uncertain)
- Test in isolation before integrating
- No "improvements" while refactoring—just isolate
- Every diff gets reviewed; expect feedback

---

**Summary:** ChatMap is shifting focus from "build everything ourselves" to "be the irreplaceable local anchor that lets agents switch models freely." Vault isolation is the first step. Router is the real product. Everything else serves those two.

