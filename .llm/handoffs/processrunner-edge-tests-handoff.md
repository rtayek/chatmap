---
project: chatmap
agent: claude
branch: feature-processrunner-edge-tests
---

# Task: Port four missing process-execution edge-case tests into ProcessRunnerTest

## Context

`ProcessRunnerTest` (in `tst/chatmap/infrastructure/command/`) already covers the
recent additions well (truncation limits, tees, durable output paths, timeout,
missing executable, stdin, duration). MyClaw's older `CommandRunnerTest` covers a
few PROCESS-EXECUTION FUNDAMENTALS that ChatMap does NOT yet test. This task ports
ONLY those four missing cases — do not duplicate what ProcessRunnerTest already has.

The four gaps (verified by diffing the two suites):

1. **Arguments are not shell-interpreted.** An argument containing shell
   metacharacters must reach the child process verbatim, proving ProcessRunner
   uses an argv array (no shell). Security-relevant.
2. **Unicode round-trips.** Multi-byte UTF-8 (BMP + astral/emoji) survives capture
   intact (correct charset handling, no mojibake, no surrogate splitting).
3. **Embedded quotes and metacharacters survive.** Quotes, `;`, `&&`, `|`, `$VAR`,
   backticks in output/args are preserved literally.
4. **Simultaneous large stdout AND stderr do not deadlock.** Both streams filled
   with large output concurrently must both drain fully — the classic
   pipe-buffer-deadlock case. (Ties to the known finding about reader-drain
   blocking. ProcessRunnerTest has a `split-streams` helper mode already; this may
   be partially covered — CHECK: if an existing test already asserts both large
   streams drain without deadlock, SKIP case 4 and note it. Only add it if absent.)

## How to do it

ChatMap's `ProcessRunnerTest` already contains an inline test-helper program: a
`main(String[])` with a `switch` over modes (`normal`, `spawn`, `sleep`, `repeat`,
`utf8-tail`, `split-streams`) plus a `writeRepeated` helper. EXTEND that helper —
do not create a new one, do not import MyClaw's.

Add helper modes as needed:
- an `echoArg` mode that prints `args[1]` exactly (for cases 1 and 3),
- a `unicode` mode that prints a fixed multi-byte string (case 2),
- reuse/extend `split-streams` for case 4 if not already asserting no-deadlock.

Then add four `@Test` methods mirroring these (values are illustrative — match
ChatMap's existing assertion style and its `ProcessRunner.run(CommandRequest)`
API, NOT MyClaw's `runHelper`/`CommandResult` API):

- `argumentsAreNotShellInterpreted`: run the helper in `echoArg` mode with an
  argument like `one; echo BAD && $HOME \`date\` "quoted"` (include a newline).
  Assert stdout equals the argument verbatim.
- `unicodeOutputRoundTrips`: helper `unicode` mode. Assert stdout equals the
  expected snowman/kanji/emoji string exactly.
- `embeddedQuotesAndMetacharactersArePreserved`: helper `echoArg` with
  `"single' ; && || $PATH \`whoami\` < > |`. Assert exact-match stdout.
- `simultaneousLargeStdoutAndStderrDoNotDeadlock` (ONLY IF not already covered):
  helper emits e.g. 65536 chars to BOTH stdout and stderr. Assert exit 0 and both
  captured lengths equal what was emitted (i.e. neither stream was truncated by a
  deadlock/short-drain). Keep the size under the 4MB capture cap so truncation
  isn't the thing under test.

## Constraints

- TEST-ONLY change (plus extending the existing inline helper). Do NOT modify
  `ProcessRunner` production code — if a test reveals a real bug, STOP and report
  it rather than changing production here; that's a separate fix.
- Use ChatMap's actual API (`CommandRequest`/`CommandResult`/`ProcessRunner`),
  charset UTF-8, and its existing assertion conventions.
- Do NOT port MyClaw's other CommandRunnerTest cases (stdout/stderr capture, exit
  status, stdin, duration, timeout, missing-executable) — ProcessRunnerTest
  already covers those. Porting them would be duplication.
- Skip any of the four that turns out already covered; note which you skipped and
  why in the commit message.
- Naming: lowerCamelCase.
- `./gradlew check` passes.

## Validation

`./gradlew check` green with the new (up to four) tests. If case 4 was already
covered, the commit message says so. No production code changed.
