# ChatMap Easy-Fixes Codex Handoff

## Assignment

Make the small, low-risk ChatMap improvements that do not require redesigning chat identity, provider provenance, database transactions, or semantic extraction.

Repository:

- <https://github.com/rtayek/chatmap>
- Default branch: `master`

This is a cleanup and usability pass, not an architectural change.

## Important Sequencing Rule

Another ChatMap task may already be changing provider identity, schema migration, import behavior, and transaction boundaries.

Before editing:

1. Run `git status --short --branch`.
2. Inspect recent commits and any uncommitted diff.
3. If architectural work is still in progress, do not overlap it.
4. Either wait until that work is complete or restrict this task to files that clearly do not conflict.

Do not discard, overwrite, stage, commit, push, or publish unrelated work.

## Engineering Preferences

- Java 25.
- Gradle command-line builds are authoritative.
- Eclipse is used as a plain Java project without Buildship.
- Code and tests are documentation.
- Prefer small deterministic changes.
- Keep documentation concise and nonduplicative.
- Preserve historical material rather than deleting it casually.
- Accessibility matters, especially readable font sizes and uncluttered controls.

## Baseline

The most recently reported baseline was:

- clean `master...origin/master`
- `gradlew.bat test` passed
- 133 tests, 0 failures

Verify the current baseline yourself because the repository may have changed since that report.

## Easy Fix 1: Make the Gradle Wrapper Executable

The repository previously stored `gradlew` with mode `100644`. That makes the documented `./gradlew` commands fail under Linux and WSL.

Fix the Git executable bit without changing the generated wrapper text:

```bash
git update-index --chmod=+x gradlew
```

Verify with:

```bash
git ls-files -s gradlew
```

The mode should be `100755`.

Do not regenerate the wrapper unless there is a separate demonstrated wrapper problem.

## Easy Fix 2: Audit the Plain-Eclipse Classpath

ChatMap intentionally avoids Buildship. Gradle copies dependencies to `lib/` and generates `.classpath` entries that refer to those jars.

Run the existing Eclipse-generation task using the platform-appropriate wrapper command. Then verify that every project-relative `lib/...jar` entry in `.classpath` actually exists.

Requirements:

- Do not hand-edit a generated `.classpath` entry if the generator is wrong.
- Fix `build.gradle.kts` generation logic when necessary.
- Preserve the plain-Eclipse workflow.
- Do not introduce the Buildship Gradle container.
- Keep Gradle builds authoritative.
- Note that committed JavaFX jars are currently Windows-specific; do not claim that the plain-Eclipse classpath is portable to WSL unless Linux JavaFX jars are deliberately supported.

Useful verification:

```bash
./gradlew eclipse
./gradlew test
```

On Windows Command Prompt or PowerShell, use `gradlew.bat` instead.

## Easy Fix 3: Make README Instructions Accurate

Keep `README.md` small. Update only the build/run information needed to avoid confusion.

It should state:

- Java 25 is required.
- Gradle wrapper commands are authoritative.
- Windows Command Prompt or PowerShell uses `gradlew.bat`.
- Windows Git Bash, Linux, and WSL use `./gradlew` after the executable-bit fix.
- `./gradlew eclipse` prepares the non-Buildship Eclipse classpath.
- The checked-in plain-Eclipse JavaFX jars currently target Windows.
- `run.sh`, if retained unchanged, is a Windows Git Bash helper rather than a portable Unix script.

Do not expand the README into an architecture document or user manual.

## Easy Fix 4: Correct Misleading Names and Comments

Review recently added UI labels, Javadocs, and comments for claims that are no longer true.

Known examples:

- **Get latest chat** historically meant “use the first provider that returns a chat,” not the newest chat across all providers.
- Some summary comments describe summarizing with the same backend that produced the chat, although the implementation uses Claude for summarization.
- `design.md` describes browser automation, live providers, and AI work as unimplemented non-goals even though optional implementations now exist.

Rules:

- Prefer wording that describes actual behavior.
- Do not change provider-selection behavior in this cleanup task.
- If the separate identity/provider-selection work has already corrected a label or behavior, keep that newer design.
- Do not rename public types merely to improve prose.
- Do not turn this into a broad comment-writing exercise.

An acceptable temporary UI label, only if still accurate, is:

```text
Import available chat
```

If explicit provider selection has already been implemented, use the label from that newer workflow instead.

## Easy Fix 5: Separate Obvious Historical Documents

The repository root contains several short session artifacts and old handoffs that are clearly not current design contracts.

Keep these current documents in the root:

- `README.md`
- `design.md`
- `vision.md`
- `first-principles.md`
- `semantic-extraction-handoff.md`

Move these obvious historical artifacts into `old-mds/` using `git mv`:

- `old-handoff.md`
- `chatmap-development-session.md`
- `chatmap-project-tag-ui.md`
- `exported-ChatMap-Development-Session.md`
- `note.txt`

Requirements:

- Preserve contents exactly during the move.
- Do not delete historical files.
- Do not merge or rewrite the large current documents during this task.
- Search for repository references to the moved paths and update only references that would otherwise break.
- Do not add another long index document. A one-paragraph `old-mds/README.md` is optional only if the directory is otherwise ambiguous.

If any listed file has become an active input to current code or tests, report that fact and do not move it blindly.

## Easy Fix 6: Small Font-Scaling Support

Add one small accessibility improvement to the JavaFX application, provided it does not conflict with work already underway in `ChatMapApp`.

Preferred scope:

- Add an application-wide font-size selector with a few large readable choices, such as 16, 20, 24, and 28 points.
- Apply the size to the application root so lists, controls, status text, and detail text scale together.
- Add keyboard shortcuts:
  - `Ctrl+=` or `Ctrl++`: increase size
  - `Ctrl+-`: decrease size
  - `Ctrl+0`: reset
- Keep the implementation local and simple.
- Do not add preference persistence, themes, custom CSS architecture, or a general settings subsystem.
- Ensure controls remain usable when the window is resized.

If this requires a substantial UI refactor, stop and report it rather than expanding the task.

Add focused tests for any extracted font-scale state or boundary logic. Do not introduce fragile JavaFX robot tests merely to test keyboard events.

## Easy Fix 7: Add a Minimal Test Workflow

If the repository still has no GitHub Actions test workflow, add one minimal workflow that:

- checks out the repository
- installs the required Java 25 distribution
- uses the committed Gradle wrapper
- runs the complete test task
- does not publish artifacts
- does not require secrets
- does not run browser/CDP live tests

Keep it small. Do not add release automation, dependency bots, code coverage services, or a CI matrix.

If Java 25 setup or the existing test suite is not reliable in the hosted environment, report the blocker and omit the workflow rather than adding a permanently red build.

## Files That Should Not Be Changed Here

Unless needed to resolve a direct compile failure caused by one of the easy fixes, do not modify:

- `src/chatmap/domain/Chat.java`
- `src/chatmap/domain/Source.java`
- `src/chatmap/importer/ImportedChat.java`
- `src/chatmap/service/ImportService.java`
- `src/chatmap/service/LiveChatFetchService.java`
- `src/chatmap/storage/schema.sql`
- database migration logic
- provider identity or content-hash logic
- transactional import/update behavior
- semantic knowledge-unit design

Those belong to the separate hardening task.

## Validation

Run the platform-appropriate equivalents of:

```bash
./gradlew test
./gradlew eclipse
git diff --check
git status --short
```

Also verify:

1. All tests pass.
2. Every `.classpath` jar path exists.
3. `gradlew` is stored as executable.
4. README commands match their stated shells.
5. Historical files were moved, not deleted.
6. Current Markdown links and file references are not broken.
7. Font scaling works at the smallest and largest offered sizes.
8. No database schema or import-identity behavior changed.

## Desired Report Back

Report:

- baseline and final test counts
- exact files changed or moved
- Gradle wrapper mode before and after
- any missing `.classpath` jars found and how generation was corrected
- the final README command summary
- the historical files moved
- the accessibility behavior added
- whether CI was added and its result
- anything deliberately skipped because it overlapped architectural work

Do not commit or push without explicit permission.
