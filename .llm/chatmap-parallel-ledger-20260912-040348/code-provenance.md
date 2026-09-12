# Recorder code provenance
Review pin: 1bb642d390059e955a548148a4bb3b546f1c3d2f
Initial worktree was clean at that revision.
Initial javac compilation into classes/ exited 0 and compiled the recorder plus required production sources from src/.
During worker review live HEAD advanced to f006df5dafbdedebacf2183693739f3bc33aaa76 (Test parallel ledger recording via CLI emulation).
Workers observed a modified ParallelLedgerExperimentTest.java and untracked ParallelLedgerRecordHarness.java. This experiment did not write either.
Coordinator command git diff --stat 1bb642d390059e955a548148a4bb3b546f1c3d2f -- src returned no differences.
A git archive of pinned src/ was extracted into pinned/. A fresh javac compilation into pinned-classes/ exited 0.
All 25 original class files were compared by SHA-256 to the fresh pinned-source compilation and were byte-identical.
Comparison completed before 2026-09-12 04:08:24 UTC.
Subsequent recording uses pinned-classes and pinned/src on its runtime classpath.
Existing lib jars supplied dependencies; no dependency was installed or changed.
Runtime prints existing multiple-SLF4J-provider warnings; commands succeeded. No logging configuration was changed.
No repository code was changed by this experiment, so the handoff's conditional full Gradle gate was not triggered. This is a direct compiled runtime exercise, not a full-project quality claim.

