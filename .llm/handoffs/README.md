# Handoffs

Handoffs transfer bounded work between chats, workers, or phases. They are not
the project's canonical current state.

## Delivery

For ordinary interactive work, ask a chat or agent with repository write
access to save the handoff directly in the destination repository's
`.llm/handoffs/` directory, commit it, and return links to the file and commit.
The request should name the repository and destination path explicitly.

When source and destination projects differ, prefer a self-identifying name:

```text
handoff-<from-project>-to-<to-project>-<subject>-<date>.md
```

A watcher, shared inbox repository, or automatic router is not required for
ordinary interactive handoffs. Those remain optional facilities for unattended
intake.

Use the root documents in this order:

1. `first-principles.md`
2. `design.md`
3. `implementation-notes.md`
4. `working-context.md`

Keep an assignment in this directory while it is active or awaiting a decision.
Move it to `handoffs/archive/` when it is completed, superseded, or retained
only as research/history. Preliminary designs should say so explicitly.

The archive preserves chronology and evidence. Archived documents do not
override the root documents or current code and tests.
