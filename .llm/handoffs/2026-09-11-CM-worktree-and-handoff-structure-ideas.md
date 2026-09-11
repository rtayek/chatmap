# Handoff: ideas worth adopting from agency-agents review

Source: read-only review of github.com/msitarzewski/agency-agents on
2026-09-11. Not a tool to install -- see notes below -- but two specific
ideas are worth pulling into ChatMap's own worker/handoff design.

---

## 1. Git worktrees as the parallel-write mechanism

**Problem this solves:** multiple workers (whether separate CLI instances
or separate LLM sessions) operating on the same ChatMap checkout at the
same time risk stepping on each other's commits, or worse, each other's
working-tree state.

**Idea:** each worker gets its own git worktree off a dedicated branch,
rather than all workers sharing one working directory:

    git worktree add ../worker-frontend feat/worker-frontend
    git worktree add ../worker-backend feat/worker-backend

Each worktree is a full, independent working directory checked out from
the same repository, so two workers can build, run, and commit at the same
time without touching each other's uncommitted state. Merges back to the
integration branch happen deliberately, not as a side effect of two
workers committing to the same branch simultaneously.

**Relevance to existing code:** this is a git-level answer to a gap noted
in an earlier review pass -- ChatMap already has `WorkerLifecycleChain`,
`WorkerAssignment`, and related domain classes for modeling multiple
workers, but nothing yet addresses how those workers avoid colliding on
the filesystem/repo when actually running concurrently. Worktrees are the
missing piece, not a replacement for the existing domain model.

**Open:** whether worktree creation/cleanup should be something
HandoffWatcher or a worker-lifecycle component manages automatically per
WorkerAssignment, or stays a manual step for now.

## 2. Structured handoff fields, including explicit acceptance criteria

**Problem this solves:** current handoffs vary a lot in structure --
compare the free-form narrative style of most existing
`.llm/handoffs/*.md` files against the more explicit template style seen
in this review's own output. Neither is wrong, but nothing currently
requires a handoff to state what "done" looks like.

**Idea:** borrow the shape, not the presentation, of agency-agents' handoff
template:

- Metadata: From, To, Task reference, Priority, Timestamp (most of this
  already exists informally in current handoffs)
- Current state: what already exists, specifically
- Deliverable request: what is needed, stated as a concrete artifact
- Acceptance criteria: explicit, checkable conditions for "this is done" --
  this is the piece current ChatMap handoffs generally lack
- Quality expectations: what evidence of completion looks like (tests
  passing, a specific command's output, etc.)

**What NOT to adopt:** the source repo is saturated with emoji and em-dash
house style throughout its templates and persona files. None of that
should be carried over -- ASCII-only stays the rule, no exceptions, same as
every other handoff in this project.

## 3. Lower-priority note

The source repo's `academic/`, `engineering/`, `specialized/`, etc.
directories are a catalog of role-specific system prompts (232 personas).
If ChatMap or the worker-lifecycle work ever needs a starting point for
defining what a given worker's role/persona should look like, this catalog
is a source of drafts to adapt -- stripped of emoji/em-dash style -- rather
than something to reference at runtime.

## Scope note

This is a design-ideas handoff, not an implementation task. No code
changes were made. Whether and how to act on items 1 and 2 is open.
