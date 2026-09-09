# Handoff: Implementing the First Three Action Items

## Overview
This handoff provides the precise instructions and code adjustments required to tackle the first three high-priority action items identified in our code review for `cjatmanager`:
1. **Normalize Transcript Output Paths** (switching from `~/.myclaw/transcripts` to `.chatmap-local/transcripts`).
2. **Resolve Build Version Drift** (aligning documentation/README references to Java 21).
3. **Wire `PromptService` into the UI / CLI Controller**.

---

## Task 1: Normalize Transcript Output Paths
In `PromptService.java`, ensure the default or configured transcript directory points cleanly to the project-local `.chatmap-local/transcripts` folder instead of legacy MyClaw paths.

### Implementation adjustment in `ChatMapPaths.java` or service bootstrap:
```java
public static Path defaultTranscriptDirectory(Path projectRoot) {
    return projectRoot.resolve(".chatmap-local").resolve("transcripts");
}
```

---

## Task 2: Resolve Build Version Drift
Update documentation files (`README.md`, `VISION.md`, `ARCHITECTURE.md`, `HOWTO.md`, `ROADMAP.md`) where Java version mentions appear. 

### Change:
- Change references from `Java 25` to `Java 21` to match `build.gradle.kts` (`java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }`).

---

## Task 3: Wire `PromptService` into `ChatMapController`
To expose prompt execution and AI agent backend interaction through the application, add `PromptService` as a managed dependency in `ChatMapController`.

### Excerpt for `ChatMapController.java`:
```java
public final class ChatMapController {
    private final PromptService promptService;

    public ChatMapController(
            ImportService importService,
            ExportService exportService,
            SearchService searchService,
            ProjectService projectService,
            TagService tagService,
            SummaryService summaryService,
            LiveChatFetchService liveChatFetchService,
            ChatGptArchiveImportService archiveImportService,
            ConversationInventoryService inventoryService,
            PromptService promptService) {
        // ... assign fields ...
        this.promptService = Objects.requireNonNull(promptService, "promptService");
    }

    public PromptResult executePrompt(String backendName, String prompt, PromptProfile profile) throws SQLException {
        return promptService.submit(backendName, prompt, profile);
    }
}
```
