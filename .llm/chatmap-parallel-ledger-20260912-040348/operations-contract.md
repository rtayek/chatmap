# CM-PARALLEL-LEDGER-01 assignment contract

Repository: C:/Users/ray/eclipse-workspace/chatmap
Pinned commit: 1bb642d390059e955a548148a4bb3b546f1c3d2f
Run directory: C:/Users/ray/Downloads/chatmap-parallel-ledger-20260912-040348
Runtime: Codex native collaboration tools. ChatMap is the durable recorder.
Required reads: .llm/index.md first, AGENTS.md, CLAUDE.md, and the index-routed human.md, persona.md, first-principles.md, design.md, evo.md, working-context.md.
Read the named handoff .llm/handoffs/2026-09-12-CM-subagent-practice-and-ledger-experiment.md and its two required prior-evidence handoffs.
Use git show <pinned-commit>:<path> for code evidence; cite path and line numbers from that revision.
Permitted tools: read-only shell/Git, clock, and native collaboration messages. No network needed.
Excluded: .chatmap-local/, secrets, raw session logs, unrelated handoffs. Never read the database directly; inspect coordinator-produced ledger snapshots in this run directory.
Workers MUST NOT write files, run builds/tests/formatters, launch processes that outlive a tool call, create subagents, or modify shared state. Artifact persistence belongs to the coordinator.
These read-only and path restrictions are instructions, not a tool sandbox.
Output: plain ASCII; scope; verified facts with file:line evidence; recommendations labeled separately; uncertainty/disagreement; one next action; start/end UTC timestamps.
Definition of done: bounded evidence review returned to /root with no writes. Send a brief STARTED message with UTC after beginning; return a final report without waiting for other workers.
Failure: report the exact blocker and partial findings to /root; do not repair or invent evidence. Parent records terminal outcomes and synthesizes useful partial work.
Reports will be stored separately and hashed. Later findings can be appended as separately identified follow-up artifacts.

Role: operations evidence worker. Review run-contract.md, LedgerRun.java, timing/runtime snapshots available here, and relevant pinned lifecycle/persistence/path-resolution code. Examine isolation, timing, failure handling and artifact paths; distinguish procedural safeguards from enforced ones. Assess what evidence can prove concurrency and what remains unavailable. Focus on this actual experiment; no broad orchestration audit.

