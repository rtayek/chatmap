# FIRST_PRINCIPLES.md

# First Principles

This document defines the fundamental principles underlying ChatMap.

These principles should remain stable even as the implementation evolves.

Implementation decisions should be evaluated against these principles rather than the reverse.

---

# Principle 1

## Information is not Knowledge

Raw information has little value by itself.

Knowledge is information that has been interpreted, organized, and connected to other knowledge.

Therefore:

- storing information is insufficient
- retrieving information is insufficient
- understanding information is the objective

---

# Principle 2

## Conversations are Evidence

A conversation is not the final product.

It is evidence of reasoning.

Like laboratory notes, conversations record exploration, mistakes, hypotheses, and conclusions.

The enduring value lies primarily in the conclusions rather than the transcript.

Therefore:

Chats should be preserved.

Knowledge should be extracted.

---

# Principle 3

## Knowledge is Atomic

Knowledge should be represented as small semantic units.

Examples:

- one fact
- one decision
- one constraint
- one pattern
- one question

Large narrative documents are views built from many smaller knowledge objects.

Atomic knowledge is easier to:

- search
- review
- update
- reuse
- relate
- verify

---

# Principle 4

## Knowledge has Provenance

Every knowledge object should identify where it came from.

Possible sources include:

- AI conversation
- Git commit
- Markdown document
- research paper
- web page
- meeting note

Knowledge without provenance cannot easily be trusted.

---

# Principle 5

## Knowledge Evolves

Knowledge is rarely static.

A knowledge object may become:

- refined
- superseded
- corrected
- merged
- deprecated

The system should preserve this history rather than overwrite it.

---

# Principle 6

## Knowledge Exists Independent of Representation

The semantic content is primary.

Representations are secondary.

Possible representations include:

- JSON
- Markdown
- SQLite
- graph database
- HTML
- PDF

None of these formats defines the knowledge itself.

Changing storage technology should not require changing the semantic model.

---

# Principle 7

## Multiple Views Should Share One Semantic Model

One semantic model should produce many views.

Examples:

```text
Knowledge
      │
      ├── Markdown
      ├── JSON
      ├── Search
      ├── Project Handoff
      ├── Knowledge Graph
      └── Future Representations
```

Views should never become independent copies.

---

# Principle 8

## Extraction is a Transformation

The purpose of semantic extraction is not summarization.

It is transformation.

Conceptually:

```text
Conversation
        ↓
Semantic Understanding
        ↓
Knowledge Objects
```

A successful extraction preserves meaning while discarding conversational detail.

---

# Principle 9

## Deterministic Processing is Preferred

Whenever deterministic algorithms produce acceptable results, they should be preferred.

LLMs should be used where semantic understanding is required rather than where deterministic software is sufficient.

Examples:

Deterministic:

- parsing
- normalization
- indexing
- rendering
- searching
- validation

LLM:

- semantic extraction
- concept identification
- relationship discovery
- classification
- synthesis

---

# Principle 10

## Human Judgment Remains Authoritative

LLMs propose.

Humans decide.

Knowledge should move through stages such as:

```text
Extracted
        ↓
Reviewed
        ↓
Accepted
        ↓
Published
```

Human review establishes trust.

---

# Principle 11

## Knowledge Should Become Increasingly Connected

Isolated facts have limited value.

Value increases as relationships are discovered.

Examples:

- supports
- contradicts
- refines
- depends upon
- replaces
- explains
- references

Knowledge naturally forms a graph.

Hierarchies are useful views but are not the underlying structure.

---

# Principle 12

## Preserve the Original Sources

Semantic extraction should never destroy evidence.

Original material should remain available.

Users should always be able to trace:

Knowledge

↓

Extraction

↓

Conversation

↓

Original Messages

---

# Principle 13

## Simplicity is a Design Constraint

Architectural simplicity has long-term value.

Prefer:

- small abstractions
- explicit models
- composable transformations
- deterministic behavior
- understandable data structures

Avoid unnecessary complexity introduced solely by implementation technology.

---

# Principle 14

## The System Learns Incrementally

The system should improve over time.

New extractions should:

- refine existing knowledge
- identify duplicates
- strengthen confidence
- discover relationships
- identify inconsistencies

The knowledge base should become more coherent rather than merely larger.

---

# Principle 15

## The Goal is Understanding

The objective is not:

- more chats
- more files
- more summaries
- more databases

The objective is increasing understanding.

Everything else exists to support that goal.

---

# One Sentence Summary

ChatMap is in the business of mining scattered conversations for the durable, connected, reviewable knowledge buried inside them, while preserving the provenance of every idea.

---

# Principle 16

## Remain Open to External Architectures

ChatMap and MyClaw should remain open to incorporating useful ideas, components,
or architectural patterns from related agent projects.

If another project better satisfies the long-term goals, the system may move
toward adopting or integrating that project rather than continuing to develop
every capability itself.

This principle keeps the direction flexible without committing the project to
any particular external implementation, rewrite, or platform.

---

# Principle 17

## ChatMap is in the Business of Content Mining

Storing and organizing conversations is necessary, but it is substrate, not
the purpose.

The purpose is mining: finding and extracting the structure, decisions, and
connections buried inside scattered conversations.

This reframes earlier principles under one identity rather than a list of
unrelated features:

- extracting atomic knowledge units (Principle 3) is mining within a single
  conversation
- discovering relationships between knowledge (Principle 11) is mining across
  conversations
- recognizing that two chats are really one conversation, continued in a
  different tool, is also mining across conversations

Future work should be evaluated by which of two roles it plays:

```text
Substrate  — stores or organizes what has already been found
             (chats, messages, tags, facets, projects)

Capability — finds or extracts structure that was not already explicit
             (facet suggestion, cross-chat link detection, semantic
             extraction)
```

Substrate work is often a prerequisite for capability work, but it is not a
substitute for it. A growing pile of well-organized transcripts is not, by
itself, progress toward the mining goal.
