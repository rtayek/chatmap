# Handoff: ChatMap Session Review - 2026-09-16

Purpose: retire this chat (long, and Ray is near his session limit); resume
in a fresh chat. Supersedes handoff-chatmap-session-review-2026-09-10.md as
the current state-of-play document -- that one is now historical.

Repo state at time of writing: origin/master, HEAD at the commit that added
the metadata validator and caller-chain-escalation verification (see
working-context.md Active Agenda items 6-7 for the exact findings text --
copied in full below since it is dense and easy to lose).

## What happened this session (since the 2026-09-10 retirement)

1. **Design-doc fixes landed.** `.llm/design.md`'s Import section now
   cross-references `implementation-notes.md`'s provider system (was
   previously silently incomplete). The ArchUnit/layer-boundary Deferred
   item, the `findSessionByAssignment` LIMIT-1 retry bug (found, fixed,
   tested -- `ORDER BY id DESC LIMIT 1`), and several other small doc items
   all landed via the patch-file workflow this session established.

2. **Handoff naming convention resolved and is now enforced by tooling.**
   `.llm/handoffs/README.md` documents direct-delivery-by-agent as the
   default (no watcher/router needed for ordinary handoffs). A real script,
   `sync-handoffs.sh`, routes files from Ray's Downloads folder into the
   right project's `.llm/handoffs/` by parsing a `-TO-<CODE>-` segment
   against a known-project-codes table (`SYS`, `CM`, `DMF`, `DF`). Two
   gotchas learned the hard way, worth remembering: (a) the glob only
   matches filenames containing the literal word "handoff" -- renaming a
   file to fix its code can accidentally un-match it if "handoff" gets
   dropped; (b) `-TO-<CODE>-` is for genuine cross-project routing only,
   not for naming a recipient tool/worker (e.g. "Claude Code" is not a
   project). Same-project handoffs should just end in `-handoff.md` with no
   `-TO-` segment, matching the real files already in the repo (e.g.
   `2026-09-13-CM-metadata-validator-handoff.md`).

3. **Handoff pruning done.** `.llm/handoffs/` was archived from 95+ files
   down to under 40 active, with a new `.llm/handoffs/archive/` holding 62+
   files (git-mv'd, so `git log --follow` still finds history). Done in two
   passes: a conservative claude.ai-proposed batch of 31, then Claude Code
   independently extended it using its own judgment. A handful of items
   were explicitly NOT archived because they looked possibly still-active
   (`semantic-evaluation-handoff.md`, `vault-isolation-handoff.md`,
   `myclaw_to_chatmap_merge_plan.md`, `capture-agent-output-handoff.md`,
   `a2a-experiment-runbook.md` -- the last one specifically because
   `README.md` references it by name).

4. **`.llm/human.md`, `.llm/persona.md`, and `AGENTS.md` are now symlinks**,
   pointing at `C:/Users/ray/eclipse-workspace/dotmdfiles/real/` (moved
   there mid-session from an earlier, since-abandoned
   `C:/Users/ray/real-md-files/` location). Centralizes shared guidance
   across Ray's projects. Known limitation, deliberately accepted for now:
   absolute Windows path, not portable to a second machine, CI, or a fresh
   clone. Ray's plan is to move the real files into a System project and
   have System scan dependents for valid pointers -- **this is a plan, not
   yet done.** **Known doc-sync gap:** the Deferred-list note about this in
   `working-context.md` still says the old `real-md-files` path; a patch to
   fix it was drafted this session but never confirmed applied -- check
   before assuming it is current.

5. **Caller-chain escalation (Active Agenda item 6) was VERIFIED, not
   fixed.** This was the single most-stuck item across many sessions
   (traces back to the "Agency Agents" document discussion). Finding: not
   queryable today. Caller identity is recorded only through one overloaded
   `predecessorSessionId` edge (means both "who called me" and "who I
   succeed," can't represent fan-in). Decision *raising* is recorded
   (`question`/`reason` fields), but decision *resolution* is not (no
   resolver identity, no resolved-at-level, no link back to the raised
   decision). Fixing this is a real architectural change requiring Ray's
   go-ahead -- explicitly not done unilaterally. Full finding text is
   preserved verbatim in `working-context.md` item 6; do not re-derive it,
   read it there.

6. **Semantic evaluation (item 7) resolved: REFINE, not Stop or Expand.**
   A ten-case frozen corpus was run and independently audited by a second,
   separate process (no LLM used in the audit itself) --
   VERIFIED WITH QUALIFICATIONS. 7/10 semantic accuracy, format 10/10 (or
   9/10 under a stricter scoring rule). The three failures cluster on one
   real pattern: negation, decision-vs-rejected, and disputed-attribution
   all fail to populate machine-readable control fields even though the
   underlying content is captured correctly. Next step (not yet started):
   add worked examples for these three patterns plus a deterministic
   post-parse check, re-run the same frozen corpus, then author a larger
   independently-written corpus.

7. **Metadata validator built** (`ProjectMetadataValidator.java`,
   `ValidateProjectMetadataCli.java`, 13 tests), wired into
   `./gradlew check`, deliberately no new YAML dependency added. Resolves
   the old item 7 (nee item 6) decision -- this is done, not just decided.

8. **Two new Active Agenda items added:** item 5 (evaluate "Agent Client"
   by Mark Pollack as a common CLI adapter across Claude/Codex/Gemini CLI --
   investigation only, explicit BSL-license caution) and item 11
   (investigate continuous real-time capture for the CDP web providers,
   since today's `latestChat()` is single-snapshot-only -- explicitly framed
   as continuity, not harness, so it doesn't cross the Deferred
   scheduler/harness line).

9. **A prior mistake of mine, corrected on the record:** I initially told
   Ray a `wasNull()`-ordering defect in `WorkerLifecycleRepository` was a
   false alarm. It was not -- `rs.getLong("id")` was evaluated before
   `rs.wasNull()` was checked, so the null-guard was silently checking the
   wrong column. Claude Code found and fixed this correctly (same commit as
   the failure-reason-preservation fix, `transitionWithFailure()`). Worth
   remembering as a real gotcha pattern for any future ResultSet-reading
   code review, not just this one instance.

10. **OpenWorker (Andrew Ng / Rohit Prasad, MIT, openworker.com)
    evaluation ran to completion. Verdict: INCONCLUSIVE, not PROMISING and
    not NOT-A-FIT.** Full report:
    `.llm/handoffs/openworker-bounded-evaluation-report-2026-09-14.md` --
    read that in full before doing anything further with OpenWorker, do not
    rely on this summary alone. Key points:
    - The integration seam is real and good: OpenWorker's journal and team
      board are genuine hash-chained on-disk SQLite (not UI-only), mapping
      cleanly onto ChatMap's own lifecycle-ledger concepts. Notably, this
      revealed ChatMap's own lifecycle events are NOT hash-chained today --
      a real gap OpenWorker's design doesn't have, worth considering later.
    - But no genuinely successful task run was observed. The local model
      (`qwen2.5:7b` via Ollama) fabricated an entire analysis (invented
      method names, invented line numbers, none matching the real 241-line
      source file) AND fabricated the claim that it wrote the output file
      (directory was empty, no write in the audit log). This happened even
      after a real, separate configuration bug (Ollama defaulting to a
      4096-token context, causing a 144x tool-call loop) was correctly
      diagnosed and fixed. The fabrication is a model-capability problem,
      not a config problem.
    - Recommended next step, NOT YET DONE, Ray's call: one more bounded run
      using a capable model (Ray's Claude API key) instead of the local
      model, to get a genuine successful-run-plus-failure-plus-persistence
      test. Explicitly costs sending the (non-secret) pinned ChatMap
      snapshot to a cloud provider -- disclosed, not assumed acceptable.
    - OpenWorker is currently still installed on Ray's machine
      (`C:\Users\ray\AppData\Local\OpenWorker\`), and Ollama was left
      running with `OLLAMA_CONTEXT_LENGTH=16384` (transient, reverts on
      Ollama restart) as of this session's end -- confirm current state
      before assuming either is still true.
    - **Separately discovered, unresolved: Ollama was observed running
      CPU-only on Ray's machine**, despite having an RTX 4060 Ti. Not yet
      diagnosed. Ray had Claude Code separately investigating this as this
      session ended.
    - Hardware research done this session (not yet acted on): DeepSeek's
      14B distill (`deepseek-r1:14b`) is a realistic fit for a 16GB 4060 Ti
      (~8-9GB at Q4) and would likely outperform `qwen2.5:7b` for the
      OpenWorker retry. Kimi K2 is definitively infeasible on this hardware
      (~1T-parameter MoE, minimum ~244GB combined RAM+VRAM even at the most
      aggressive quantization) -- do not re-investigate Kimi without a
      hardware change.

## Open items carried forward, not resolved this session

- Items 1-4 (subagent/worker-contract/skills/parallel-workflow experiments)
  -- explicitly sequenced before item 6 in the agenda's own priority order,
  but item 6 got worked on anyway this session (verification, not the fix).
  Items 1-4 remain genuinely untouched.
- Item 5 (Agent Client investigation) -- untouched, investigation only.
- Item 6 (caller-chain escalation architectural fix) -- verified but NOT
  fixed. Needs Ray's go-ahead on the architectural change before anyone
  implements it.
- Item 7 (semantic evaluation next step: worked examples + deterministic
  check + re-run) -- decided, not started.
- Item 8 (A2A recorder production wiring) -- still just a decision point,
  handoff exists (`handoff-chatmap-a2a-recorder-production-wiring-2026-09-10.md`,
  in the archive now -- check it wasn't archived by mistake given it's still
  relevant).
- Item 9 (SessionStart hook) -- on hold pending a known Claude Code crash
  bug, not yet re-checked.
- Item 10 (lowercase all filenames) -- not started. Flagged this session as
  probably more involved than it sounds (repo-wide renames risk breaking
  hardcoded references, same class of problem the handoff-pruning archive
  script had to be tested against first).
- Item 11 (real-time web capture investigation) -- not started.
- **OpenWorker follow-up run** -- Ray's decision, not yet made as of this
  session's end.
- **Ollama CPU-only diagnosis** -- in progress with Claude Code as this
  session ends; check its outcome first thing in the new chat.
- **Two known doc-sync gaps**, both patches drafted this session but never
  confirmed applied: (a) the OpenWorker Active Agenda item (would have been
  item 12) is NOT in the current `working-context.md` -- verify before
  assuming it's tracked; (b) the portability Deferred-list note still
  references the old `real-md-files` path instead of `dotmdfiles/real`.

## Suggested first move in the new chat

1. Fetch the repository, confirm current `master` commit and clean working
   tree.
2. Check what Claude Code found on the Ollama CPU-only question.
3. Check whether `working-context.md` actually has the OpenWorker item and
   the corrected portability-note path, or whether those patches still need
   applying.
4. Ask Ray whether he wants to proceed with the OpenWorker capable-model
   run (possibly using `deepseek-r1:14b` locally instead of a cloud key, if
   the GPU issue gets resolved first), or move to one of items 1-4.
