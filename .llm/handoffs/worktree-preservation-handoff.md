---
project: chatmap
agent: claude
branch: feature-preserve-dirty-worktrees
---

# Task: Stop destroying an agent's uncommitted work when a handoff fails

## Background / why

When the orchestrator runs an agent in a git worktree and something fails after
the agent has edited files, the agent's work is force-deleted instead of being
kept for recovery. Two separate defects combine to cause this:

### Defect A — preservation is gated on the wrong failure
In `HandoffOrchestratorService` (the per-task run method), a local
`preserveWorktree` flag starts `false` and is set `true` in exactly ONE place:
the `catch (GitWorkspaceManager.WorktreeCommitFailedException)` block. Every
other failure after the agent starts -- agent exits nonzero, agent times out
(`LlmBackendExecutionException`), a `git status`/`git add` failure, any other
exception after edits -- returns via `recordFailure(...)` with `preserveWorktree`
still `false`, so the `finally` block calls `gitManager.removeWorktree(...)` and
the edits are gone.

### Defect B — `removeWorktree` ignores git's result and deletes anyway
`GitWorkspaceManager.removeWorktree` runs `git worktree remove --force` but does
NOT check the returned `CommandResult`, then unconditionally calls
`fileStore.deleteRecursively(worktree)`. So even when git refuses or fails to
remove the worktree (e.g. the Windows "Permission denied" teardown we've seen),
the directory is recursively deleted regardless.

## The fix

The correct policy: **once the agent has started, preserve the worktree if it has
uncommitted changes, regardless of why the run failed. Only remove a worktree
that is clean (or that never got dirty).** A committed-and-pushed success removes
the worktree as today.

`GitWorkspaceManager.hasChanges(Path worktree)` already exists (runs
`git status --porcelain`) -- use it to make the decision.

### Part 1 — gate cleanup on dirtiness, not on the exception type
In the per-task method's `finally` (or just before removal), replace the
"preserve only on commit failure" logic with:

- On the success path (agent committed and pushed): remove the worktree as now.
- On ANY failure path after the worktree was created: check
  `gitManager.hasChanges(worktree)`. If it has changes, PRESERVE it (log a WARN
  naming the path for manual recovery, and include "preserved at <path>" in the
  failure detail so it syncs back to the phone). If it's clean, remove it.
- Guard `hasChanges` itself: if the worktree add failed (early return, worktree
  never usable), don't call `hasChanges` on a nonexistent worktree. Preserve the
  existing "return before worktree exists" paths untouched.
- If `hasChanges` itself throws (git status failing on a broken worktree), treat
  that as "cannot prove clean" and PRESERVE rather than delete -- fail safe.

Keep the existing explicit `WorktreeCommitFailedException` message ("preserved at
... for manual recovery") -- that path stays preserved; it's now one case of the
general rule.

### Part 2 — make `removeWorktree` respect git's result
In `GitWorkspaceManager.removeWorktree`: capture the `CommandResult` from
`git worktree remove --force`. If it failed (nonzero exit), do NOT
`deleteRecursively`; log a WARN with the git stderr and leave the directory in
place (git knows about it; force-deleting behind git's back is what corrupts the
`.git/worktrees` metadata and has caused the index inconsistencies we've seen).
Only `deleteRecursively` as a fallback when git reports success but the directory
somehow remains. This keeps `git worktree` metadata and the on-disk directory in
agreement.

## Tests to update (these currently assert the destructive behavior)

Two existing tests encode the bug and MUST be updated to reflect the corrected
policy. Both use a stubbed agent that makes NO file edits, so their worktrees are
CLEAN -- meaning after the fix they should STILL see removal (clean worktrees are
still cleaned up). Verify they pass as-is under the new logic; if the fake models
a clean `git status --porcelain` (empty output) they will:

- `HandoffOrchestratorServiceTest.agentFailureWritesFailureReportAndLeavesOriginalFileInPlace`
  (~line 400): stubs `claude -p` to exit 1 with no edits. Worktree is clean, so
  `git worktree remove --force` should still run. Ensure the fake's
  `git status --porcelain` for this worktree returns empty so `hasChanges` is
  false. Assertion at ~line 415 can stay.
- The other `git worktree remove --force` assertion (~line 117): same reasoning.

## Tests to ADD (the actual regression guards -- this is the point)

- **Dirty agent failure preserves the worktree:** stub the agent to exit nonzero
  AND stub `git status --porcelain` for the worktree to return non-empty (dirty).
  Assert the worktree is NOT force-removed, the failure detail contains
  "preserved", and the result is a failure. This is the exact scenario that's
  currently broken.
- **Dirty timeout preserves:** same but simulate the timeout path
  (`LlmBackendExecutionException` with a timed-out `CommandResult`).
- **`removeWorktree` respects a failed git removal:** unit-test
  `GitWorkspaceManager.removeWorktree` with a `git worktree remove` that returns
  nonzero -> assert `deleteRecursively` is NOT called and it logs/leaves the dir.
- **Clean success still removes:** guard against over-preserving -- a successful
  committed run still removes the worktree.

## Constraints

- Confined to `HandoffOrchestratorService` and `GitWorkspaceManager` (plus the
  named tests). Don't change the success/commit/push flow.
- Don't swallow exceptions; preserve-on-failure must still return a
  `recordFailure` result with a clear message.
- `./gradlew check` must pass. Note: existing tests hard-coding `codex.cmd` may
  fail on Linux independently of this change (separate known CI issue) -- do not
  "fix" those here.

## Validation

`./gradlew check` passes; the new preserve-on-dirty tests are present and green;
the two updated tests still pass under the clean-worktree reasoning.
