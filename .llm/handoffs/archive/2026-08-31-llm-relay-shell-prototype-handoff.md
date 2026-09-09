# Handoff: Two-LLM Shell Relay Prototype

**Date:** 2026-08-31  
**Status:** Completed and archived 2026-09-01
**Implementation:** Bourne shell in `rtayek/bin`, branch `archive/llm-relay`
**Verification:** 23 offline fixture and flow tests passed

## Executive Summary

Build the smallest useful experiment in which one LLM delegates a request to a
second LLM, examines the response, and returns either the accepted answer or an
explicit escalation to its caller. Use a three-word control language—`YES`,
`NO`, and `MAYBE`—that a Bourne-shell script can parse deterministically.

Do not add retries, concurrent workers, autonomous loops, databases, or Java in
the first version. Preserve every prompt, response, review, and final result as
a separate file.

## Location

Use the existing `bin` repository on a short-lived experimental branch:

```text
experiment/llm-relay
```

Suggested layout:

```text
llm-relay/
├── llm-relay.sh
├── prompts/
│   ├── delegate.md
│   └── review.md
└── tst/
    └── llm-relay-test.sh
```

Keep generated run directories out of Git initially because they may contain
private prompts, provider output, or machine-specific information.

## Actors

```text
Caller
  ↓ request
Supervisor LLM
  ↓ delegated prompt
Worker LLM
  ↑ worker response
Supervisor LLM
  ↓ accepted answer or escalation
Caller
```

The shell script is the deterministic mediator. The two LLMs do not invoke one
another directly.

The supervisor has two semantic responsibilities:

1. formulate or forward a clear request to the worker;
2. review the worker's response and classify it.

The caller may initially be the human running the script. Later it may be
another LLM or orchestration component.

## First Test Request

Use a deliberately simple request:

```text
Who is buried in Grant's Tomb?
```

A strong answer should recognize the wording and explain that Ulysses S. Grant
and Julia Dent Grant are entombed there.

Also test a simulated worker refusal such as:

```text
I cannot answer that request.
```

The refusal must be returned up the chain rather than discarded or rewritten
as success.

## Control Language

The supervisor's review must begin with exactly one of these tokens on the
first nonblank line:

```text
YES
NO
MAYBE
```

Meanings:

- `YES` — the worker response adequately answers the request; return the raw
  worker response to the caller.
- `NO` — the response is wrong, empty, refused, or otherwise unusable; return a
  failure report to the caller.
- `MAYBE` — the response requires caller judgment or clarification; return the
  request, response, and review explanation to the caller.

The reviewer may explain its decision after the first line:

```text
YES
The response correctly identifies both people entombed in Grant's Tomb.
```

The shell script parses only the first nonblank line. It must not attempt to
interpret the explanation.

Any other token, added punctuation, blank review, or malformed output becomes:

```text
INVALID
```

`INVALID` follows the same path as `MAYBE`: preserve everything and escalate to
the caller. Never guess what malformed control output meant.

## Review Prompt Contract

The supervisor review prompt should contain this contract:

```text
Evaluate whether the worker response adequately answers the original request.

Your first nonblank line must be exactly one of:

YES
NO
MAYBE

Do not add punctuation to that line. Explain your decision on later lines.
```

The prompt must include the original request and the worker's exact response,
clearly delimited.

## Saved Run Artifacts

Create one directory per run:

```text
run-2026-08-31-001/
├── 00-request.md
├── 01-delegated-prompt.md
├── 02-worker-response.md
├── 03-supervisor-review.md
├── 04-result.md
└── run-status.md
```

`run-status.md` should record:

- run identifier;
- supervisor and worker commands or provider names;
- start and completion timestamps;
- exit status of each external command;
- parsed decision token;
- final run state.

Do not automatically commit or push generated runs.

## Required Behavior

1. Accept a request from a file or standard input.
2. Ask the supervisor to produce the delegated prompt.
3. Send that saved prompt to the worker.
4. Preserve the worker's exact standard output and standard error separately.
5. If the worker exits unsuccessfully or produces no usable output, create a
   failure result and return it to the caller without invoking review.
6. Ask the supervisor to review a successful worker response.
7. Parse only the first nonblank review line.
8. Produce the final result according to `YES`, `NO`, `MAYBE`, or `INVALID`.
9. Exit nonzero for execution failure or malformed protocol output.

## Shell Constraints

- Use `/bin/sh` and portable Bourne/POSIX shell constructs.
- Do not use Bash arrays, `shopt`, or other Bash-only features.
- Pass substantial prompts through files or standard input, not one enormous
  quoted command-line argument.
- Quote every path expansion.
- Do not use `eval`.
- Treat nonzero provider exit status and empty output as failures.
- Keep provider commands configurable rather than embedding personal paths.
- Assume execution from Windows Git Bash, while keeping the script reasonably
  portable to Linux and WSL.

## Tiny Implementation Steps

1. Implement and test only the `YES`/`NO`/`MAYBE` parser using fixture files.
2. Add run-directory creation and artifact preservation.
3. Add fake supervisor and worker commands so the complete flow is testable
   without live providers.
4. Connect two installed, authenticated CLI providers, initially Claude and
   Codex if their noninteractive interfaces are suitable.
5. Run the Grant's Tomb success case.
6. Run refusal, ambiguous, empty-output, nonzero-exit, and malformed-token
   cases.

Keep each step independently testable. Do not design the future Java system
while implementing this experiment.

## Explicit Non-Goals

The first version does not include:

- automatic retries;
- more than one worker;
- more than one review;
- conversational loops;
- concurrency;
- persistent sessions;
- token or cost optimization;
- a database;
- ChatMap integration;
- automatic Git commits or pushes;
- Java.

## Definition of Done

- One command runs the complete supervisor → worker → supervisor flow.
- The first review line is parsed deterministically.
- `YES` returns the unmodified worker answer.
- `NO`, `MAYBE`, and `INVALID` return enough evidence for the caller to decide
  what happens next.
- Provider refusal and execution failure propagate upward without being hidden.
- Every material input and output is preserved in the run directory.
- Fixture tests cover all four parser outcomes and provider execution failure.
- No loop can continue indefinitely because the prototype performs exactly one
  delegation and one review.

## Later Decision

After the shell prototype works, evaluate whether the durable implementation
should remain a utility, move into ChatMap, or become a separate Java relay
project. That decision should be based on observed needs for retries, state,
concurrency, provider adapters, and lifecycle persistence—not speculation.
