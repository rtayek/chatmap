# Handoff: ChatMap Agent-Facing Markdown Pilot

> **Status: completed.** The pilot found that flat discovery works better for
> ChatMap than a forced linear chain, while the trust distinction between
> auto-discovered skills and other agent-facing material remains useful. The
> assignment and its result are archived together.

## Project

`ChatMap`

## Purpose

Use ChatMap as the second real-world pilot for organizing agent-facing Markdown files.

The first pilot, in the `dotfiles` repository, produced this principle:

> **Standardize discovery, not organization.**

New evidence from the Agent Skills specification and client implementation guidance suggests that `.agents/skills/` is becoming a cross-client discovery convention for skill packages. This pilot should test whether ChatMap's other LLM-operational material fits naturally beside that convention under `.agents/llm/`.

This is an investigation and limited pilot—not authorization for a broad documentation rewrite.

---

# Assignment Inputs

## 1. Task

Inspect ChatMap's existing Markdown files and evaluate this proposed discovery model:

```text
CLAUDE.md
    ↓
AGENTS.md
    ↓
.agents/llm/index.md
```

with the possible project-controlled structure:

```text
.agents/
├── skills/                 # Agent Skills discovery convention
└── llm/                    # Other agent/LLM-operational material
    ├── index.md            # Project-controlled context dispatcher
    ├── working-context.md  # Current operational state, if useful
    ├── handoffs/           # Compressed transfers between chats/workers
    └── prompts/            # Reusable prompts, only if genuinely needed
```

Determine whether this model improves discovery and reduces ambiguity in ChatMap without imposing unnecessary structure.

Do not assume every shown file or directory is required. Empty ceremonial directories should not be created.

## 2. Context and Files

Start with:

- All tracked Markdown files in the ChatMap repository.
- Existing root instruction files such as `AGENTS.md`, `CLAUDE.md`, or equivalents.
- Existing handoffs, working-context files, plans, architecture files, design files, and research notes.
- `README.md` and any human-facing `docs/` material.
- Relevant Git history when needed to determine whether a file is current, historical, generated, or obsolete.

Background distinctions:

### Agent Skills standard

A skill is a directory containing `SKILL.md`, with optional scripts, references, assets, and other resources. The specification defines the package format; clients define discovery locations.

`.agents/skills/` is an emerging cross-client discovery convention at both project and user scope. It should be treated as a location with special loading behavior—not as an arbitrary documentation folder.

### Other agent-facing Markdown

Handoffs, working context, prompts, project instructions, and semantic state are not automatically Agent Skills. They may fit under `.agents/llm/`, where `.agents/llm/index.md` acts as the project-controlled dispatcher.

### Human-facing documentation

`README.md`, durable architecture/design documentation, user guides, and other material intended for humans should remain in their ordinary project locations unless there is a concrete reason to move them.

### Semantic-state principle

Markdown should primarily preserve project-specific information that cannot be reliably reconstructed from source code, tests, repository structure, general engineering knowledge, or Git history.

Git should preserve chronology. Canonical Markdown should preserve durable meaning. Working context should preserve only the small amount of current operational state needed to continue effectively.

## 3. Tools

Use ordinary read-only repository inspection tools, including:

- `git status`, `git log`, `git ls-files`, and `git diff`;
- file search and text search;
- direct reading of relevant Markdown files;
- repository tests only if a small pilot change is later authorized and the change could affect tooling or builds.

Internet research is not required unless a claim about a current client convention must be verified.

## 4. Constraints and Permissions

- Inventory and classify before proposing moves.
- Do not reorganize files merely to make the tree look uniform.
- Do not move human-facing documentation into `.agents/` solely because agents also read it.
- Do not turn handoffs, context files, or instructions into `SKILL.md` packages unless they genuinely describe a reusable task capability.
- Do not place research specimens or downloaded skills in active discovery paths.
- Do not create empty directories.
- Do not duplicate content between provider-specific entry files.
- Keep provider-specific root files thin whenever practical.
- Preserve existing working behavior unless a change is explicitly justified.
- Treat `.agents/skills/` as potentially auto-discovered and executable/instruction-bearing content.
- Note trust, precedence, collision, and portability concerns when relevant.
- Do not introduce a registry, database, schema, installer, or management application during this pilot.
- Do not perform a broad rewrite or migration without presenting the proposal first.
- Preserve unrelated user changes in the working tree.

## 5. Definition of Done

The investigation is complete when it produces a concise report containing:

1. An inventory of ChatMap's Markdown files, grouped by semantic role.
2. For each important file or group:
   - current location;
   - primary audience;
   - semantic role;
   - loading/discovery behavior, if any;
   - persistence: durable, working, transient, or historical;
   - recommended disposition: keep, move, merge, archive, delete later, or investigate.
3. A proposed minimal discovery chain for ChatMap.
4. A proposed tree showing only directories and files that ChatMap actually needs.
5. A list of files that should remain where they are.
6. A list of ambiguities or decisions requiring the user's judgment.
7. A small, reversible implementation plan, separated from the analysis.
8. Explicit feedback on whether `.agents/llm/` feels natural or redundant in real use.

No repository mutation is required to satisfy this definition of done. The user should be able to review the proposal before authorizing implementation.

---

# Classification Model

Use semantic role rather than filename alone. Candidate roles include:

| Role | Purpose | Typical persistence |
|---|---|---|
| Client entry point | Satisfy a tool's expected filename and route onward | Durable |
| Context dispatcher | Tell agents which project context to load and when | Durable |
| Skill package | Reusable task capability activated on demand | Durable/versioned |
| Project instructions | Repository-specific behavioral constraints | Durable |
| Architecture/design | Preserve project-specific intent and invariants | Durable |
| Working context | Describe current direction, next work, and unresolved state | Frequently updated |
| Handoff | Transfer compressed state between chats, workers, or phases | Transient or archived |
| Prompt/command | Provide an explicitly invoked reusable operation | Durable if reused |
| Plan/specification | Describe intended work or required behavior | Lifecycle-dependent |
| Research note | Preserve evidence or unresolved investigation | Temporary or durable |
| Historical artifact | Preserve archaeology but not guide current work | Archived |
| Human documentation | Explain the project to maintainers or users | Durable |

A file may have more than one audience, but identify its primary role and explain any mixed responsibility.

---

# Questions the Pilot Should Answer

- Can a new agent reliably find the right starting context without reading every Markdown file?
- Is `CLAUDE.md → AGENTS.md → .agents/llm/index.md` a useful chain for ChatMap?
- Does `.agents/llm/` clarify the distinction between skills and other operational material?
- Would `.agents/index.md` be simpler, or would that blur standardized skill discovery with project-defined context?
- Which existing ChatMap files are durable semantic state, and which are merely old handoffs or chronology?
- Which documents primarily serve humans and should remain outside `.agents/`?
- Are any existing files accidentally acting as active instructions when they should be historical specimens?
- Are there duplicated or conflicting instructions?
- Which client-specific entry files are genuinely required?
- What should happen when ChatMap eventually acquires project-local skills?

---

# Expected Report Shape

```markdown
# ChatMap Markdown Pilot Report

## Executive conclusion

## Current inventory

## Semantic classification

## Discovery problems found

## Proposed minimal structure

## Files that should not move

## Proposed moves or consolidations

## Decisions required from the user

## Small reversible implementation plan

## Feedback to the dotmdfiles project
```

Keep the report focused. Prefer a small number of well-supported recommendations over a comprehensive new documentation framework.

---

# Intended Outcome

This pilot should produce evidence for the `dotmdfiles` project, not merely tidy ChatMap.

The result should help determine whether the emerging general model is sound:

```text
client-required entry point
        ↓
shared project instructions
        ↓
project-controlled context dispatcher
        ↓
project-specific organization
```

with this tentative physical arrangement:

```text
.agents/skills/   = cross-client discoverable skill packages
.agents/llm/      = project-defined operational Markdown
```

If ChatMap exposes weaknesses in that arrangement, report them plainly. The purpose of the pilot is to improve the convention, not validate it by assumption.
