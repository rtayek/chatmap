---
id: CHATMAP-HANDOFF-2026-09-27-01
lifecycle: working
status: active
provenance: chat-handoff
---

# Handoff: Agent Instructions, Skills, and ChatMap Responsibilities

## Purpose

Share the new dotmdfiles direction with the ChatMap project and identify its
implications for ChatMap's continuity, provenance, and worker-lifecycle model.

This is an architectural handoff, not an instruction to change ChatMap code
immediately.

ChatMap repository: `https://github.com/rtayek/chatmap`

## Background

The current cross-project instruction model uses this discovery chain:

```text
CLAUDE.md -> AGENTS.md -> .llm/index.md -> human.md, persona.md,
project documents, working context, and sometimes handoffs
```

Experience with multiple LLM clients suggests that this chain is too fragile.
Agents do not reliably follow every link, interpret directory-depth rules, or
distinguish current authority from historical evidence.

The accepted new direction is:

```text
CLAUDE.md -> self-contained AGENTS.md -> exact project documents when required
```

`AGENTS.md` will combine the important content currently distributed across
`AGENTS.md`, `human.md`, `persona.md`, and the human-readable routing portion of
`index.md`. Its shared portion will be standardized; its final project-context
portion will be repository-owned.

## What This Means for ChatMap

ChatMap should treat instructions, skills, assignments, state, and historical
evidence as related but distinct objects.

### Governing instructions

`AGENTS.md` is the eagerly loaded governing contract. It contains permissions,
authority, human constraints and preferences, persona, skill policy, and a
short project map.

### Skills

A skill is an on-demand capability or workflow, not the complete identity of an
agent. Each skill has a `SKILL.md` plus optional scripts, references, and
assets.

The effective behavior of a worker can be modeled as:

```text
model
+ governing instructions
+ persona
+ permissions
+ tools
+ assignment
+ selected skill names and versions
+ working state
```

This is directly relevant to ChatMap's existing assignment model. The five
assignment inputs remain useful:

1. Task
2. Context and files
3. Tools
4. Constraints and permissions
5. Definition of done

Selected skills and their versions should eventually be recorded as part of
the assignment context or execution provenance.

### Project knowledge

Substantial durable documents such as design, first principles, evolution, and
implementation notes may remain under `.llm/`. They should be loaded only when
`AGENTS.md` or the current assignment names them explicitly.

### Working state and historical evidence

`working-context.md` remains mutable current state. Handoffs and archives remain
historical evidence. Neither should silently override governing instructions or
durable project decisions.

## Formal Language and Metadata

Use BCP 14 terminology in normative instruction documents. Capitalized `MUST`,
`MUST NOT`, `SHOULD`, `SHOULD NOT`, and `MAY` have the meanings defined by RFC
2119 and RFC 8174.

Use the formats according to their roles:

- Markdown: human-readable rules, design, decisions, context, and handoffs.
- YAML front matter: metadata about an individual Markdown document or skill.
- JSON: machine-consumed manifests, schemas, or interchange data.
- Bourne shell: deterministic operations and deployment scripts.
- SQLite: a rebuildable catalog, ledger, or query layer when scale warrants it.

The authoritative semantic content should remain in Git-tracked Markdown and
skill packages. ChatMap may index it, but its database should not silently
become a competing source of truth.

## Indexes and Manifests

The human-readable `.llm/index.md` is expected to disappear from the mandatory
cross-project entry set. Its routing function moves into the project-context
section of `AGENTS.md`.

A machine-readable manifest may still be useful later, but only when a real
consumer exists. Possible consumers include:

- validation of document IDs, lifecycle, status, and provenance;
- recording the expected shared-instruction revision;
- enumerating installed project skills and versions;
- detecting missing or stale deployed copies;
- seeding ChatMap's searchable catalog.

Do not make a JSON manifest authoritative for prose rules, and do not create
one merely for architectural symmetry.

## Skills at Scale

There may eventually be a very large number of skills. Loading all skill bodies
is neither necessary nor desirable.

Use three populations:

1. A small personal active set.
2. A small repository-specific active set.
3. A large central library that is searchable but not automatically installed.

The central `skills` repository owns reusable skill packages. Client-specific
install locations may contain ordinary deployed copies. The user prefers
ordinary files rather than Windows symlinks.

ChatMap could eventually help answer:

- Which skills exist?
- Where did each skill come from?
- Which version was installed or used?
- Which worker assignment selected it?
- Which scripts, references, and tools did it depend on?
- What artifact did the skill-assisted worker produce?
- Can an earlier result be reproduced from the recorded assignment and skill
  revisions?

These are catalog, provenance, and continuity questions. ChatMap should not
become the runtime skill loader or a general agent orchestrator merely because
it records those facts.

## Architectural Boundary

Preserve the established invariant:

```text
Workers own execution; ChatMap owns continuity.
```

The revised ownership model is:

- System owns cross-project architectural decisions and project registry.
- dotmdfiles owns the shared `AGENTS.md` template and deployment conventions.
- skills owns reusable capability packages.
- individual repositories own their project-context suffix and durable design.
- ChatMap records assignments, workers, artifacts, lifecycle, provenance,
  decisions requiring attention, and handoff continuity.

ChatMap may validate or index external material, but should not silently mutate
the authoritative instruction or skill sources.

## Candidate Future Evidence-Producing Experiment

Do not begin this experiment until the consolidated dotmdfiles format has a
stable draft.

Once ready, run one bounded ChatMap experiment:

1. Create an assignment that names one exact skill and skill revision.
2. Record the governing `AGENTS.md` revision.
3. Run the worker and record its tools, constraints, artifacts, and outcome.
4. Retire the worker and create a successor assignment from a semantic handoff.
5. Verify that ChatMap can answer which instructions and skill revision shaped
   the result without loading the entire skill library.

This would produce evidence about reproducibility and semantic continuity. It
would not prove general LLM reliability or truthfulness.

## Decisions Still Needed

- Whether skill identity is stored directly on assignments or through a
  normalized assignment-skill relation.
- Whether skill versions use Git commit IDs, content hashes, declared semantic
  versions, or more than one identifier.
- Whether the first catalog is generated JSON, SQLite, or simply a filesystem
  scan cached in ChatMap.
- Which provenance fields are required before a worker session can start.
- How to represent governing-instruction revisions for non-Git workers.

These questions belong in ChatMap design work after the dotmdfiles ADR and
combined `AGENTS.md` draft exist.

## Next Chat Opening Request

Review this handoff against ChatMap's current `.llm/design.md`,
`.llm/working-context.md`, and worker-lifecycle implementation. Identify the
smallest schema or experiment needed to record governing-instruction and skill
provenance. Do not change code until the dotmdfiles consolidated format is
stable enough to supply one concrete test case.
