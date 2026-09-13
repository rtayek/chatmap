# Handoff: .llm Metadata Pilot Validator

Date: 2026-09-13
Repo: rtayek/chatmap
Branch: master
Commits: 8d61c0f (validator), 6a36016 (deferred note), 8254e6f (agenda item 7)

## Work completed

A deterministic, read-only validator for the `.llm` metadata pilot now exists.
It checks only the rules already declared in `.llm/manifest.json` and never
creates, modifies, moves, or deletes project files. Any failure yields a
nonzero exit.

Files added:
- `src/chatmap/metadata/ProjectMetadataValidator.java` - core logic; parses
  the manifest with Gson, returns a `Result` (failures plus counts).
- `src/chatmap/metadata/ValidateProjectMetadataCli.java` - prints a PASS
  summary or lists failures; exits nonzero on any failure.
- `tst/chatmap/metadata/ProjectMetadataValidatorTest.java` - 13 tests over
  temporary fixtures.
- `build.gradle.kts` - new `validateProjectMetadata` task (verification group).

## Checks enforced

- entrypoint exists; every `required_documents` path exists; handoff directory
  exists.
- each `metadata_pilot.documents` file: valid UTF-8, no BOM, LF not CRLF,
  begins with YAML front matter, has one closing `---`, has nonblank `id`,
  `lifecycle`, `status`, `provenance`, and no duplicate front-matter keys.
- ids are unique across pilot documents.
- lifecycle and status appear in their manifest allowlists.
- pilot documents and the entrypoint are listed in `required_documents`.

Front matter is parsed as the existing flat `key: value` format with a small
hand-written reader. No YAML library or other new dependency was added.

## Verification

- `./gradlew validateProjectMetadata`
  -> PASS: project metadata valid (6 pilot documents, 8 required paths)
- `./gradlew test --tests chatmap.metadata.ProjectMetadataValidatorTest`
  -> 13 passed, 0 failed
- `./gradlew check` -> BUILD SUCCESSFUL (checkstyle, PMD, SpotBugs, jacoco)
- `git status --short` -> clean; no generated files left behind

Note: Gradle runs intermittently hit Windows file locks on `build/` outputs
("Unable to delete" / "Failed to clean up stale outputs"). `./gradlew --stop`
and clearing the stale directory resolved it each time; final runs were clean.

## Boundaries respected

Did not touch `findSessionByAssignment`, Agent Client, scheduling, the Claude
SessionStart hook, filename renaming, A2A production wiring, or semantic
evaluation.

## Open question

The manifest's `validation` block (`encoding`, `bom`, `line_endings`,
`require_unique_ids`, `require_paths_exist`) is currently descriptive only; the
validator enforces those rules unconditionally and the flags happen to agree
with that strict behavior, so the block is decorative. Recorded under
working-context Deferred: decide whether to make the flags per-check toggles or
drop the block. Recommendation is to leave enforcement strict-by-default and
wire a single flag only if a real case needs it.
