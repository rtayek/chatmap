---
project: chatmap
agent: claude
branch: feature-model-channel-prototype
---

# Task: Prototype the Model × Channel enum composition (with derived Source), in anExperiment

## Context / goal

We're redesigning how ChatMap represents "which LLM, reached how." The insight:
a Model (the intelligence: Claude, Gemini, llama3, qwen) and a Channel (the
transport: cli, web, api, ollama server) are INDEPENDENT, many-to-many axes. The
same model can be reached through multiple channels (Claude via cli AND web), and
the same channel carries many models (the api channel carries Claude, Gemini,
etc). A ModelTarget is a curated, valid (model, channel) pairing.

This task builds a self-contained PROTOTYPE in the `anExperiment` package to prove
the structure and the Source-derivation logic in isolation. It must NOT touch the
real ChatMap `ModelTarget`, `ProviderId`, `Source`, or any provider yet — the real
port is a separate follow-up (noted at the end). Keeping it isolated lets us
validate the shape before disturbing the load-bearing enum that four providers and
the persistence layer depend on.

## Starting point

An earlier sketch (`anExperiment/Enums.java`) already has:
- `enum Model { m1, m2, m3 }`
- `enum Channel { c1, c2, c3 }`
- `enum ModelTarget` composing them via constructor: `m1c1(Model.m1, Channel.c1)`,
  with `public final Model model; public final Channel channel;`
- A `static {}` block using `Map<Model, EnumSet<Channel>>` (`byModel`) that both
  rejects duplicate (model, channel) pairings and answers `channelsFor(Model)`.

Build on that file.

## What to add in this task

### 1. Source, derived from Channel (NOT stored on ModelTarget)

Add a small `enum Source` to the prototype representing provenance, e.g.
`s1, s2, s3` (stand-ins for the real claudeCliPrompt / ollamaPrompt / etc).

Key decision (already made): each ModelTarget's Source is DERIVED, not passed as a
constructor arg. In the real system today, Source is a pure function of the
CHANNEL alone (every ollama target -> ollamaPrompt, every claudeCli target ->
claudeCliPrompt, regardless of model). So model the derivation as a
`Channel -> Source` lookup:

- Put the mapping ON `Channel` as a field: `Channel(Source source)` with
  `c1(Source.s1), c2(Source.s2), c3(Source.s3)` and a `public Source source()`
  accessor. This makes Channel the single owner of the channel->source fact.
- Give `ModelTarget` a derived accessor: `public Source source() { return channel.source(); }`
  — no stored Source field, so it can never drift from the channel.

(Design note for the reader: we intentionally key the derivation on channel, not
on the (model, channel) pair, because the real data shows Source depends only on
the channel. If that ever stops being true — a model that changes provenance
within the same channel — the derivation would move to a
`Map<Pair<Model,Channel>, Source>` lookup instead. Do NOT build that now; keep it
channel-keyed and add a comment saying so.)

### 2. Reverse lookup for symmetry

Add `modelsFor(Channel)` backed by a `Map<Channel, EnumSet<Model>>` (`byChannel`),
built in the same `static {}` block, so both directions of the many-to-many are
queryable ("which channels reach this model" AND "which models does this channel
carry"). Reuse the same duplicate-detection (EnumSet.add returning false) so the
guard covers both maps consistently.

### 3. Demonstrate in main()

Extend `main` to print:
- every ModelTarget with its model, channel, and DERIVED source
- `channelsFor(m)` for each Model
- `modelsFor(c)` for each Channel

Add at least one ModelTarget where a single model pairs with two channels (e.g.
`m1c1`, `m1c2`) so the shared-model case is visible in the output.

### 4. A tiny test (JUnit, in the test tree mirroring anExperiment)

- `channelsFor(m1)` returns exactly the channels m1 is paired with.
- `ModelTarget.m1c1.source()` equals `Channel.c1.source()` (derivation works).
- Adding a duplicate pairing triggers the load-time guard — since you can't easily
  add an enum constant in a test, instead unit-test the guard logic by asserting
  no duplicates exist in the current values (i.e. `values().length` equals the
  number of distinct (model, channel) pairs). This locks in the invariant.

## Constraints

- Everything stays under `anExperiment` (prod) and the mirroring test package.
  Do NOT import or modify anything under `chatmap.*`.
- Keep `Model`/`Channel` values as simple stand-ins (m1.., c1..) — this proves
  structure, not real model names. The real names come in the port phase.
- `public final` fields are fine for the prototype (matches the existing sketch);
  don't bother converting to accessors except where noted (`Channel.source()`,
  `ModelTarget.source()`).
- `./gradlew check` passes (the prototype + its test compile and run green).

## Follow-up (NOT part of this task — note it in the commit body)

Once the prototype shape is confirmed, a later handoff will port it into the real
`chatmap.application.port.llm.ModelTarget`: rename the axis currently called
`ProviderId` to reflect channel-vs-model, introduce a real `Model` type, recut the
real ModelTarget rows as (model, channel) pairings, derive `Source` from the
channel (replacing the stored Source column), retype `providerModelName` usage in
the four providers, and fix the OllamaProvider "default" handling. That port is
review-before-merge because it touches persistence-adjacent code.

## Validation

`./gradlew check` passes; running `anExperiment.Enums` prints targets with derived
sources and both directional lookups; the invariant test is green.
