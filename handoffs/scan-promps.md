### AI Handoff: Prompt Complexity Classification & Model Routing Strategy

### 1. Context Overview

* **Project Domain:** LLM Chat Capture, Retrieval, and Search Platform.
* **Target Tech Stack:** Java, Gradle, Eclipse, Bash, Git [Explicit Memory].
* **Available Models:** Claude Fable 5 (Monster), Claude Opus 5 (Workhorse), Claude Sonnet 5.6 (Lightweight Agentic).
* **Objective:** Build an automated pre-filtering router to scan incoming developer prompts. The router determines task complexity to seamlessly choose between low-cost, high-velocity models (Sonnet 5.6) and deep structural reasoning systems (Fable 5).

### 2. Structural Routing Pipeline

The following architectural diagram outlines how incoming prompts are evaluated against context footprints, linguistic intents, and environmental depth before hitting a target model. 

                  ┌──────────────────────────────┐
                  │    Incoming Developer Prompt │
                  └──────────────┬───────────────┘
                                 │
                                 ▼
                    [ 🔍 Prompt Classification ]
                 Extract Metrics & Analyze Keywords
                                 │
         ┌───────────────────────┴───────────────────────┐
         ▼                                               ▼
   [ Context Scope ]                               [ Intent Core ]
   • Single File/Snippet? (Light)                  • "Generate/Write/Fix"? (Light)
   • Multi-File/Full Repo? (Monster)               • "Refactor/Migrate/Audit"? (Monster)
         │                                               │
         └───────────────────────┬───────────────────────┘
                                 │
                                 ▼
                     [ ⚖️ Decision Matrix Engine ]
         Does it cross multi-file boundaries, alter Gradle
         build configurations, or introduce logical ambiguity?
                                 │
                ┌────────────────┴────────────────┐
                ▼                                 ▼
             [ YES ]                           [ NO ]
                │                                 │
                ▼                                 ▼
     🌋 CLAUDE FABLE 5 (Monster)       🏎️ CLAUDE SONNET 5.6 (Lightweight)
     • SWE-Bench Verified: ~96%         • SWE-Bench Verified: ~73%
     • Use for: Large architectural      • Use for: High-velocity CLI,
       refactors, cross-file tracking,    unit boilerplate, local syntax
       and ambiguous error tracing.       fixes, and immediate executions.

### 3. Core Technical Dimensions for Routing

### Context Footprint

* **Lightweight (Sonnet 5.6):** Targets an isolated loop, a standalone helper utility, or a localized logic snippet.
* **Monster (Fable 5):** Targets broad imports, multi-module cascades, and systems where modifying an interface requires synchronized edits across multiple packages.

### Action Verbs & Keywords

* **Lightweight:** High frequency of Write, Generate, Script, Fix syntax, Explain, Convert.
* **Monster:** High frequency of Refactor, Redesign, Migrate, Audit, Analyze architecture, Trace memory leak.

### Build & Lifecycle Complexity

* **Lightweight:** Execution scripts independent of build lifecycles or simple terminal tasks.
* **Monster:** Structural modifications requiring alignment with build.gradle scripts, project-wide dependency resolution, or multi-step compiler verification loops.

### 4. Next Incremental Step

Implement a micro-router within your platform. It should default to Sonnet 5.6 for raw typing velocity, parsing the prompt via a rapid keyword array or regex validator, and immediately escalates the state to Fable 5 the second a multi-file file boundary or structural refactoring constraint is flagged.
