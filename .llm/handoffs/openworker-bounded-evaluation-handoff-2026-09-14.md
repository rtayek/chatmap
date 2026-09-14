# OpenWorker Bounded Evaluation Handoff

Date: 2026-09-14

## Recipient

Claude Code, working with Ray in the ChatMap repository and on Ray's Windows workstation.

## Purpose

Evaluate OpenWorker as an external agent runtime or harness that might execute work while ChatMap preserves continuity, provenance, lifecycle evidence, and durable semantic knowledge.

This is an evaluation only. Do not integrate OpenWorker into ChatMap, copy OpenWorker code into ChatMap, add its dependencies to ChatMap, or redesign ChatMap as a general orchestrator.

## Project Context

ChatMap's long-term purpose is to extract durable, connected, reviewable semantic knowledge from conversations, keep it current, and preserve provenance and history. ChatMap may provide bounded coordination facilities, but it is not intended to become a general scheduler, router, permissions system, or agent harness.

The working distinction to test is:

- OpenWorker may own agent execution, tools, approvals, and delivery of work.
- ChatMap may own durable continuity, lifecycle records, provenance, artifacts, and later semantic acceptance.
- A2A is a possible protocol between agents; do not assume OpenWorker implements it.
- Agent Client is a possible portable Java-to-CLI adapter; it is not equivalent to a complete agent runtime.

Official starting points:

- [OpenWorker website](https://openworker.com/)
- [OpenWorker source repository](https://github.com/andrewyng/openworker)

OpenWorker currently describes itself as an open-source, local-first desktop coworker that can use cloud models or Ollama, work with files and tools, produce artifacts, and gate consequential actions for human approval. Treat these as claims to verify, not as experiment results.

## Normal ChatMap Discovery

Before beginning:

1. Follow the repository's normal discovery chain: `CLAUDE.md` to `AGENTS.md` to `.llm/index.md`.
2. Read the authoritative current-state documents selected by the index.
3. Read only handoffs directly relevant to A2A, worker lifecycle, parallel recording, Agent Client, semantic evaluation, and this assignment.
4. Do not automatically read or index `.chatmap-local/` or any other excluded raw-data directory.
5. Report the exact ChatMap commit examined and whether the checkout was clean. Do not discard or overwrite Ray's local changes.

## Safety and Isolation Rules

1. Keep the OpenWorker checkout, installation, configuration, logs, and experiment output outside the ChatMap repository.
2. Use a new disposable experiment directory with a clear date-stamped name.
3. Start with local Ollama only. Do not enter cloud API keys, connect GitHub, Slack, email, calendars, cloud accounts, or other external services.
4. Grant access only to a disposable, read-only copy or worktree pinned to a recorded ChatMap commit. Do not point OpenWorker at Ray's active checkout if a safe copy can be used.
5. Do not give OpenWorker access to `.git` credentials, secret stores, home-directory-wide files, `.chatmap-local/`, or unrelated repositories.
6. Do not enable unattended or blanket auto-approval. Observe approval behavior under the default or most restrictive practical policy.
7. Do not modify either ChatMap's schema or OpenWorker's source.
8. Do not install system-wide packages, bypass Windows security warnings, disable security controls, or alter workstation configuration without Ray's explicit approval.
9. Never expose or record secret values. Redact them if they appear unexpectedly.
10. If a step requires a GUI action that Claude cannot perform, give Ray one precise action at a time, wait for the result, and continue from the evidence Ray supplies. Do not pretend the action was performed.

## Assignment

Perform the work in five phases. Complete as much as can be done safely. A documented blocker is a valid result.

### Phase 1: Establish the Exact Product and Revision

1. Inspect the official website and source repository.
2. Record:
   - repository URL;
   - exact OpenWorker commit or release tested;
   - license;
   - beta or release status;
   - supported Windows installation path;
   - high-level architecture and runtime dependencies;
   - Ollama support;
   - artifact, transcript, approval, persistence, scheduling, MCP, and export facilities;
   - whether any documented A2A support actually exists.
3. Separate direct observations, documented claims, and inferences.
4. Note discrepancies between the website, README, releases, and observed application. Do not resolve discrepancies by guessing.

### Phase 2: Prepare a Safe Runnable Experiment

1. Prefer the least invasive supported Windows route.
2. Before downloading, installing, or running untrusted binaries, show Ray:
   - the exact source and version;
   - whether the binary is signed;
   - the intended destination;
   - the permissions and network access it may use.
3. If Ray approves installation, configure OpenWorker with a local Ollama model only. Record the exact model identifier and relevant settings.
4. Create a disposable read-only ChatMap checkout or copy pinned to a recorded commit.
5. Capture a before-state inventory and `git status` for the disposable checkout.

If a safe runnable setup cannot be completed, stop the execution portion and produce a source-based readiness report. Do not improvise around security or platform blockers.

### Phase 3: Run One Bounded Successful Task

Give OpenWorker this task, adjusted only for the exact disposable path:

> Examine this pinned ChatMap checkout read-only. Explain the boundary around `WorkerLifecycleService`, identify one confirmed lifecycle limitation with source evidence, and produce a Markdown artifact named `openworker-chatmap-readonly-report.md`. Cite file paths and distinguish observed facts from inference. Do not edit the checkout, install dependencies, access the network, or inspect excluded raw-data directories.

During the run, observe and preserve evidence for:

- what files and directories OpenWorker was granted;
- which model was used;
- planning or step decomposition;
- every tool or command request;
- every approval prompt and Ray's response;
- transcript or event history;
- final task state;
- generated artifact location, bytes, and SHA-256 hash;
- whether the task survives application close and reopen;
- before-and-after `git status` of the disposable ChatMap checkout.

Evaluate the report for source accuracy. OpenWorker producing a file is not proof that its semantic claims are correct.

### Phase 4: Run One Bounded Failure or Blocked Task

Test failure preservation without destructive behavior. Prefer one of these methods:

1. Ask OpenWorker to analyze a deliberately nonexistent file and require it not to fabricate contents; or
2. Ask it to perform a harmless command needed for the task, then have Ray deny that approval.

Record which method was used. Verify whether OpenWorker:

- enters a clear blocked, input-required, cancelled, or failed state;
- preserves the reason and partial work;
- avoids claiming success;
- keeps successful prior artifacts intact;
- retains the transcript and approval decision after restart;
- offers a comprehensible continuation or retry path.

Do not manufacture a failure by corrupting repositories, killing system processes, removing user data, or changing security settings.

### Phase 5: Assess the ChatMap Boundary

Without writing integration code, map the observed OpenWorker evidence to ChatMap's existing concepts:

| OpenWorker evidence | Candidate ChatMap concept |
| --- | --- |
| requested outcome | assignment |
| execution instance | worker session |
| progress or terminal state | lifecycle transition |
| approval request and decision | decision/provenance evidence |
| generated file | artifact with hash and source path |
| transcript or summary | handoff or session evidence |
| retry or continuation | successor session or continuation link |

Determine:

1. Can ChatMap record an OpenWorker run using its existing public application services and schema?
2. Which required facts are available through supported export, files, logs, or APIs?
3. Which facts exist only in OpenWorker's private storage or UI?
4. Is there a stable integration seam, or would recording require brittle scraping or direct database access?
5. Does OpenWorker expose A2A, MCP, a local API, CLI, event stream, or export format useful to ChatMap?
6. Would Agent Client add value alongside OpenWorker, or would it solve a separate CLI-only problem?
7. What is the smallest future experiment that could record one OpenWorker run into an isolated ChatMap home without changing either product?

## Required Deliverable

Create one Markdown report named:

`openworker-bounded-evaluation-report-2026-09-14.md`

The report must contain:

1. Executive verdict: `PROMISING`, `INCONCLUSIVE`, or `NOT A FIT`.
2. Exact versions, commits, model, platform, and paths used.
3. Installation and permission observations.
4. Successful-task evidence and semantic accuracy review.
5. Failed/blocked-task evidence.
6. Persistence, transcript, approval, artifact, and export findings.
7. A2A and MCP findings, clearly separated.
8. Mapping to ChatMap's existing lifecycle ledger.
9. Comparison of OpenWorker, Agent Client, A2A, and ChatMap.
10. Risks, unknowns, and any claims not independently verified.
11. Recommendation for the smallest next step.
12. A complete inventory of evidence files with sizes and SHA-256 hashes.
13. Commands run and whether each changed any state.

Keep raw evidence outside the ChatMap repository. The report may identify its absolute location on Ray's machine. Do not copy large transcripts, binaries, application databases, secrets, or third-party source trees into ChatMap.

## Repository Changes

Default to no ChatMap code changes.

After the report is complete:

1. Show Ray the report and concise verdict.
2. Propose, but do not automatically make, any documentation update to `.llm/working-context.md`.
3. Do not commit or push unless Ray explicitly authorizes it after reviewing the result.
4. If Ray asks to preserve the report in ChatMap, place only the distilled report in the appropriate handoff location and follow the repository's naming, metadata, validation, and archiving rules.

## Acceptance Criteria

The experiment is complete only if the report makes clear:

- what was actually run versus merely inspected;
- whether OpenWorker left the test checkout unchanged;
- whether success, failure, approvals, transcripts, and artifacts persisted;
- whether the successful report was semantically accurate;
- whether evidence can be exported or observed without private-storage scraping;
- whether an isolated ChatMap recorder appears feasible without turning ChatMap into an orchestrator;
- what should happen next, including a justified recommendation to stop if appropriate.

The experiment is not a production adoption decision. A successful demo does not authorize connectors, unattended work, production repositories, schema changes, or OpenWorker integration.
