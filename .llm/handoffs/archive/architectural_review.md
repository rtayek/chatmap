# ChatMap Architectural Review

Based on a deep scan of the codebase and recent commits, here is a comprehensive review of the project's architecture, code health, and your newly proposed semantic extraction design.

## 1. Architectural Patterns (Hexagonal / Ports & Adapters)

The codebase exhibits a highly disciplined adherence to Hexagonal Architecture.

- **Domain (`chatmap.domain`)**: 
  - **Status**: Excellent. The domain layer is completely pure. `ArchitectureBoundaryTest` successfully enforces that it has zero outbound dependencies to other `chatmap.*` packages.
- **Application (`chatmap.application.port`, `chatmap.application.service`)**:
  - **Status**: Strong. Use cases and business orchestration (like `HandoffOrchestratorService`) are well-separated from implementation details. The recent introduction of `ProjectRegistry` and `GitOutcome` further solidified the boundary by preventing infrastructure details (like generic Maps or raw process `CommandResult`s) from leaking into the domain logic.
- **Infrastructure (`chatmap.infrastructure`)**:
  - **Status**: Good separation. Adapters for SQLite, web scraping (CDP), and process execution implement the application ports cleanly. 
- **Presentation (`chatmap.presentation`) & Composition Root (`chatmap.app`)**:
  - **Status**: The CLI and UI boundaries correctly delegate to application services. `ServiceGraph.java` cleanly centralizes dependency injection.

**Feedback**: It is rare to see this level of boundary enforcement in a Java codebase without heavy frameworks. The custom `ArchitectureBoundaryTest` is a brilliant, lightweight way to keep the structure honest.

## 2. Code Health & Static Analysis

- **Static Analysis**: The build is fully integrated with Checkstyle, PMD, and SpotBugs. The fact that `./gradlew check` passes cleanly (especially after our recent exception narrowing and deduplication tasks) indicates a very high baseline of code quality.
- **Class Sizes**: A scan of the largest classes (`ChatRepository` ~383 lines, `GeminiWebAdapter` ~380 lines, `Database` ~364 lines) shows they are still within reasonable limits for infrastructure adapters. The web adapters are naturally larger due to CDP boilerplate, but hoisting the JSON helpers to `CdpTranscriptAdapter` was the right move.
- **Test Coverage**: The distribution of tests (`infrastructure` has 35 tests, `application` has 19) is decent, though the application services could potentially benefit from more unit testing as complex orchestration logic grows.

## 3. Review of the Semantic Extraction Design

Your new design doc (`semantic-extraction-design-PRELIMINARY.md`) is well-reasoned. Here is a review of how it fits into the current architecture and answers to your open questions:

**Design Strengths:**
1. **Separation of Concerns (Mode A vs Mode B)**: Splitting the problem into "Reconcile" (staleness detection) and "Compress" (distillation) is the right call. Reconciliation is deterministic and diff-based, while compression is generative.
2. **Propose, Don't Auto-file**: This aligns perfectly with the CLI's existing philosophy. Modifying memory files silently is how systems become confidently wrong.
3. **Pragmatic Targeting**: Choosing to parse only Markdown (Claude Code, Codex) and intentionally ignoring undocumented binary/protobuf formats (Antigravity/Ollama) is a great scoping decision for the MVP.

**Architectural Fit for Implementation:**
- This feature maps cleanly to a new `chatmap.application.service.SemanticExtractionService`.
- Reading the external `.claude` or `.codex` memory files should be hidden behind a new port (e.g., `chatmap.application.port.memory.ExternalMemorySource`), with infrastructure adapters for `ClaudeCodeMemoryAdapter` and `CodexMemoryAdapter`.
- The diffing engine (comparing claims to the codebase) can leverage the existing `SearchService` or `GitWorkspaceManager`.

**Feedback on Open Questions:**
- *1. Reconcile output: annotate in place or emit a triage report?* 
  **Recommendation**: Emit a separate triage report. Annotating in place requires a robust Markdown parser/writer that preserves exact formatting, which is notoriously brittle. A separate `reconciliation-report.md` is safer and fits the "propose" principle better.
- *2. What counts as a concrete claim?*
  **Recommendation**: Start strictly with file paths, class/interface names, and package names. These can be deterministically verified via `fd` or `grep` (or their Java equivalents). Prose assertions are too fuzzy for a V1 diff.
- *3. Per-file, per-project, or per-chat?*
  **Recommendation**: Per-project. A memory file in `~/.claude/projects/*/memory/` is inherently scoped to the project. Running it at the project level gives the LLM the correct context boundary.

---
**Summary**: The codebase is in excellent shape structurally. The semantic extraction feature is scoped well and has a clear path for integration into the existing Hexagonal Architecture. Let me know if you want to start building the `ExternalMemorySource` port or the MVP for Mode A!
