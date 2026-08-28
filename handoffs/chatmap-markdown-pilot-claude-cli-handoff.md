# ChatMap Markdown Pilot — Claude CLI Execution Handoff

**Repository:** `rtayek/chatmap`  
**Branch:** `feature/agents-markdown-pilot`  
**Recommended worker:** Claude CLI

## Purpose

Execute the analysis phase of ChatMap's agent-facing Markdown pilot. The canonical assignment is already stored in the repository:

```text
handoffs/chatmap-agents-markdown-pilot-handoff.md
```

This file is only a launch handoff. The repository handoff is authoritative; do not duplicate or reinterpret its full contents.

## 1. Task

Read the canonical pilot handoff completely and perform the investigation it specifies.

Inventory and classify ChatMap's tracked Markdown files, assess the proposed discovery chain and `.agents/llm/` structure, and produce the requested Markdown Pilot Report.

This phase is analysis only. Do not implement the proposed reorganization.

## 2. Context and files

Start with:

- `handoffs/chatmap-agents-markdown-pilot-handoff.md`;
- every tracked Markdown file in the repository;
- relevant Git history needed to distinguish current, working, transient, and historical material;
- root client-instruction files, human documentation, architecture/design material, handoffs, prompts, plans, and research notes.

The pilot is intended to provide evidence for the separate `dotmdfiles` project. It must test the proposed convention rather than assume that the convention is correct.

## 3. Tools

Use read-only repository inspection tools, especially:

- `git status`, `git branch --show-current`, `git log`, and `git ls-files`;
- filename and text search;
- direct reading of relevant Markdown files;
- Git history for ambiguous files.

Internet research is unnecessary unless a current client-discovery claim genuinely requires verification.

## 4. Constraints and permissions

- Work only on `feature/agents-markdown-pilot`.
- Begin by reporting the current branch, Git status, and exact canonical handoff filename.
- Stop if the branch is wrong or the working tree contains unexplained changes.
- Do not move, rename, rewrite, archive, or delete repository files.
- Do not create `.agents/`, `AGENTS.md`, `CLAUDE.md`, skills, registries, or empty directories during this phase.
- Do not convert ordinary handoffs or documentation into skills.
- Do not treat the earlier worker-lifecycle handoff as the current assignment.
- Preserve unrelated user work.
- Prefer a small number of evidence-backed recommendations over a comprehensive new framework.
- If the canonical handoff conflicts with this launch handoff, follow the canonical handoff and report the conflict.

## 5. Definition of done

Return a concise report containing:

1. Executive conclusion.
2. Markdown inventory grouped by semantic role.
3. Important files or groups classified by audience, persistence, discovery behavior, and recommended disposition.
4. Discovery problems found.
5. Proposed minimal discovery chain.
6. Proposed minimal tree containing only files ChatMap actually needs.
7. Files that should remain where they are.
8. Proposed moves or consolidations.
9. Decisions requiring Ray's judgment.
10. A small, reversible implementation plan, clearly separated from the analysis.
11. Explicit feedback on whether `.agents/llm/` is natural, redundant, or needs modification.
12. Feedback that can be returned to the `dotmdfiles` project.

Present the report in the CLI response. Do not modify the repository or save the report as a tracked file unless Ray separately authorizes that step.

## Final instruction

Read the canonical handoff first. Then inspect broadly enough to understand the repository, but stop after producing the analysis and proposal. Wait for Ray's review before implementing anything.
