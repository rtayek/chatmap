ChatMap project handoff — 2026-08-13

ChatMap (rtayek/chatmap) is a Java 25 desktop app for importing/searching/exporting AI chat histories across six sources, checked out at /c/Users/ray/eclipse-workspace/cjatmanager. Development flow: I (Claude) review the repo, write handoff .md files, you drop them in handoffs/ (via sync-handoffs.sh in ~/bin) and push, then Claude Code implements them. open-reports.sh (also in ~/bin) opens ./gradlew check reports in the browser.

Architecture: just went through a full hexagonal restructuring — domain / application (port/service/model/support) / infrastructure (ai/command/exporter/importer/persistence/provider) / presentation (cli/ui) / app.bootstrap. Boundaries are enforced by a real test (ArchitectureBoundaryTest) that scans source for forbidden cross-layer imports, not just documented convention.

All six sources (Claude Code, Codex, Gemini CLI, Claude/ChatGPT/Gemini web) are confirmed working and exhaustively enumerated — web providers now use real authenticated APIs (Claude, ChatGPT) or a hardened scroll-to-terminal algorithm (Gemini), verified live: 663 imported, 0 failed, all six report complete.

Recently closed: domain test coverage, provider round-trip + discovery-dedup tests, CommandRunner/CliBootstrap test coverage (including a real subprocess-kill test), no-content-vs-genuine-failure reporting distinction, SLF4J/Logback logging with correct home-resolution ordering.

Still open, lowest to highest priority as last discussed:

Whether handoff-first-three.md (and similar stale .md handoffs) will ever get picked up again — you were going to check
Two small findings from an outside code review, not yet turned into a handoff: a silent catch (Exception ignored) in PromptService.listSessions(), and a possibly-redundant string-switch for CLI binary names
Remaining untested CLI entry points (ConversationInventoryCli, LiveSourceExchanges, SummarizeChatCli, ImportAllChatsCli)
JSON output for importAllChats — deprioritized, but you flagged wanting it "soon"
JShellBackend's fate (convert to subprocess vs. delete) — deliberately shelved
An unexplained but currently-inactive burst of Gemini CLI session noise from July 22nd (a2a-server) — root cause never found, nothing running now, ChatMap-side handling already fixed regardless