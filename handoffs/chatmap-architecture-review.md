# ChatMap — High-Level Architectural Review
**Date:** 2026-08-17
**Head:** `b592801`
**Scope:** whole-system architecture — layering, dependency direction, ports,
composition, and enforcement. Not a line-level code review.

## One-paragraph verdict

ChatMap is a textbook hexagonal (ports-and-adapters) architecture, and — rare —
the boundaries are *enforced by an executable test* rather than merely intended.
The dependency rules hold in every direction I checked: the domain is pure,
the application layer depends only on its own ports, and infrastructure and
presentation sit correctly at the edges. This is a codebase whose structure will
resist erosion under multi-agent editing precisely because the erosion shows up
as a red test. The concerns are not structural; they're about a few ports
carrying infra-shaped types and one recent regression that slipped through
*because* the boundary test doesn't check behavior, only dependencies.

## The layering (concentric, dependencies point inward)

```
        presentation (cli, ui)  23 files
                 |
    app / composition root       8 files   <- the only place adapters are wired
                 |
        application  60 files
          - service/   (use cases / orchestration)
          - port/      (the hexagon's edges: 38 interfaces & DTOs)
          - model/, support/
                 |
           domain  16 files   <- pure, zero chatmap.* imports
                 ^
        infrastructure  55 files
          - persistence/sqlite, provider/web, ai, command,
            exporter, importer, handoff
          (implements the ports; depends inward, never outward)
```

## What's genuinely strong

**1. Dependency direction is correct and verified.**
- `domain` imports nothing from `chatmap.*` — it's a pure core. ✓
- `application` imports neither `infrastructure` nor `presentation` — it talks to
  the world only through `port` interfaces. ✓
- No upward dependencies anywhere (domain→app, persistence→app, etc.). ✓

**2. The architecture is executable.** `tst/chatmap/architecture/ArchitectureBoundaryTest`
enforces five invariants with zero external dependencies (no ArchUnit needed):
   - domain depends on no other ChatMap package
   - persistence depends on neither application-services, presentation, nor the
     composition root
   - application depends on neither infrastructure nor presentation
   - **JavaFX is confined to `presentation.ui` + `app`** — a real
     framework-containment rule, so the UI toolkit can't leak into use cases
   - presentation never constructs infrastructure adapters directly (must go
     through the composition root)

   This is the single most valuable architectural asset in the repo. It's why the
   structure has stayed clean across heavy parallel-agent editing.

**3. Ports are segregated by capability, not lumped into a god-DAO.** Persistence
is split into `ChatStore`, `MessageStore`, `ProjectStore`, `SearchStore`,
`SummaryStore`, `TagStore`, plus a separate `TransactionManager` — Interface
Segregation done properly. A service that only reads tags depends on `TagStore`
alone, not a 40-method repository. Same discipline in the importing/exporting/
provider ports.

**4. The composition root is isolated.** `app/` (8 files: `ApplicationBootstrap`,
`ServiceGraph`, `HandoffOrchestratorBootstrap`, etc.) is the *only* place where
concrete adapters meet interfaces. Wiring is centralized, so the rest of the
system is testable with fakes — which is exactly what the 460-test suite does.

**5. Provider & AI abstractions are properly polymorphic.** `ChatProvider`,
`AiBackend`/`AiProvider`, and the `ModelTarget`/`ProviderId` value objects let
ChatGPT/Claude/Gemini/CLI backends vary behind stable seams. Adding a fifth
provider is an infrastructure-only change.

## Architectural concerns (not structural failures — shape & enforcement gaps)

**A. Some ports carry infrastructure-shaped types.** `CommandResult` (exitCode /
stdout / stderr / timedOut) is a process-execution DTO that lives in
`application.port.command` and threads through application services (~16 refs in
the orchestrator alone). It's a *port* by location but an *adapter concern* by
shape — the application layer is reasoning in terms of OS-process results. Not a
dependency violation (so the boundary test stays green), but it's the kind of
leak that makes the application layer quietly aware of how work is executed.
Same smell, milder, with the raw `Map<String, Path>` project registry passed
into the orchestrator instead of a named domain-ish type. Both are known/deferred.

**B. The boundary test checks dependencies, not behavior — and that's exactly
where the recent regression slipped through.** `StandardCliBackend` was deleted
and reconstructed by an agent (`b592801`) in a form that compiles and passes
every architecture check, but silently dropped real capability (per-agent CLI
flags, the session-distinguishing `listSessions`). The architecture test can't
catch this because the *dependencies* are still correct — only the *behavior*
regressed. Takeaway: the structural enforcement is excellent and should be
matched by behavioral characterization tests on the load-bearing adapters
(`StandardCliBackend`, the web providers) so a "simplifying" rebuild fails loudly.

**C. `HandoffOrchestratorService` (439 lines) is the system's one gravity well.**
It polls, pulls, scans, creates worktrees, dispatches agents, captures output,
archives, commits, and pushes. It's coherent and well-tested, but it's doing
git-workflow orchestration *and* agent dispatch *and* result persistence. If any
class grows into a god-service, it's this one. Worth watching; a future split
(worktree lifecycle vs. task dispatch vs. inbox sync) may earn its keep.

## What I did NOT find (checked, clean)

- No layering violations in any direction.
- No framework leak (JavaFX confined; JDBC confined to persistence.sqlite).
- No god-DAO; no cyclic package dependencies surfaced by the import checks.
- No business logic in presentation (CLIs delegate to services).

## Recommendations, in priority order

1. **Add behavioral characterization tests to the load-bearing adapters**
   (`StandardCliBackend.commandFor`, `listSessions`, the web providers) so a
   compile-clean rebuild that drops behavior fails a test. This directly closes
   the gap that let `b592801` regress. Highest architectural ROI.
2. **First, restore what `b592801` dropped** — the per-agent flags and the
   audited `listSessions` (recoverable from the pre-deletion version).
3. **Consider a port-facing result type** that hides `CommandResult`'s process
   shape from the application layer (return an `AiResponse`/`AgentRun` the app
   understands; keep exit codes in the adapter). Addresses concern A.
4. **Keep an eye on `HandoffOrchestratorService`** for a future
   responsibility split; no action needed yet.

Net: architecturally this is in the top tier for a solo project — clean hexagon,
segregated ports, isolated composition, and *enforced* boundaries. The work to do
is behavioral safety-netting on the adapters, not structural change.
