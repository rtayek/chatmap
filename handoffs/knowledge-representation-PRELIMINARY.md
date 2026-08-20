# Knowledge Representation — Preliminary Design Note

> **STATUS: PRELIMINARY.** Captures a representational decision that underlies BOTH
> flagship branches (semantic extraction AND facets). Settle before building either.
> Grounded in first-principles.md, principles 3, 7, 11.

## The core model: one node store + one typed-directed-edge store

Everything is nodes and typed directed edges. That's the whole representation.

### Nodes (Principle 3 — Knowledge is Atomic)
Small semantic units, one idea each:
- a fact, a decision, a constraint, a pattern, a question
- AND category/set values (e.g. "chatmap", "settled-decisions", "concurrency")

Category values are **just nodes**. There is no separate facet type, no separate
taxonomy schema. A "set" is a node like any other.

### Edges (Principle 11 — Knowledge Should Become Increasingly Connected)
Typed, directed: `(from_node, edge_type, to_node)`. One edge table.

Two families of edge_type, but structurally identical:
- **Classification / facet edges** — short, point at category nodes:
  `instance-of`, `in-project`, `has-status`, `has-topic`
- **Semantic edges** (the P11 list) — relate knowledge to knowledge:
  `supports`, `contradicts`, `refines`, `depends-upon`, `replaces`,
  `explains`, `references`

## Key insight: a facet IS a short edge

Tagging a node with a facet = drawing one edge to a category node.
- `decision-42 —[in-project]→ chatmap`   (a facet)
- `decision-42 —[has-status]→ settled`   (a facet)
- `decision-42 —[replaces]→ decision-17` (a semantic relation)

All edges. Facets and the knowledge graph are NOT two subsystems — they are the
same edge store with different edge types. This unifies the two flagship branches
at the data layer: extraction *produces* nodes+edges; facets *is* the
classification edges plus a UI to filter/traverse by them.

## "One or two or many" and "sets of sets" fall out for free

- **One facet** = one edge. **Many facets** = many edges from the same node.
  A node can be an instance of as many sets as apply — no multi-value-field
  machinery, just more edges. (This is why facets beat a rigid tree: a tree forces
  one parent; edges allow many.)
- **Sets of sets** = a category node with its own `instance-of` edge to a bigger
  category node. `settled-decisions —[instance-of]→ decision-types`. Nesting is
  emergent from edges, not a stored hierarchy.

## Guardrails (violating these is how this balloons — see the day's CommandBus/qwen incidents)

1. **No graph database. No RDF/triple-store/ontology framework.** A directed
   labeled graph here = two SQLite tables: a node table and an
   `(from, edge_type, to)` edge table. We already have SQLite. This is not a new
   subsystem. (Principle 13 — Simplicity; and the standing "skeptical of
   frameworks" stance.)
2. **Hierarchies are VIEWS, not storage** (Principle 11 explicit: "Hierarchies are
   useful views but are not the underlying structure"; reinforced by Principle 7 —
   Multiple Views Share One Semantic Model). Store the flat graph; DERIVE trees,
   outlines, filtered lists, documents as queries over it. Never store a tree.
3. **Category/set nodes get NO special scaffolding.** A facet value is a node; a
   tag is an `instance-of` edge. Do not build a first-class facet type with its
   own schema. The "very short directed graph" IS the implementation.

## Consequence for the roadmap

This unifies extraction and facets at the substrate: both are operations over one
node+edge store.
- **Extraction** (reconcile + compress) writes nodes and edges (including facet
  edges and semantic edges like `replaces`/`contradicts`).
- **Facets** = the `instance-of`-family edges + the filter/traverse UI over them.

So the two flagship branches should share this foundation, not each invent their
own storage. Design the node+edge store first; build extraction and facets on top.

## One-line summary

One node store (atomic knowledge + category nodes), one typed-directed-edge store;
facets are `instance-of`-type edges, semantic relations are the P11 edge types;
hierarchies, sets-of-sets, and all views are DERIVED, never stored; flat SQLite,
no graph DB, no framework.
