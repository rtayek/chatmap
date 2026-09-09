# ChatMap Controller Builder — Implementation Handoff

## Recipient

Give this handoff to **Claude or Codex working in `rtayek/chatmap`** —
small, self-contained, low-risk refactor. No architectural decisions
left open; this is mechanical.

## Purpose

`ChatMapController` has grown six telescoping constructors, each one
adding exactly one more dependency on top of the previous:

```java
ChatMapController(import, export, search, project, tag, summary, live)
ChatMapController(..., archiveImportService)
ChatMapController(..., conversationInventoryService)
ChatMapController(..., promptService)
ChatMapController(..., promptRouterService)   // current last link
```

Every time a new service gets added to the controller, this chain
grows by one more overload. Replace it with a builder before a
seventh dependency makes it worse.

## Confirmed Before Starting

Checked all call sites (`grep -rn "new ChatMapController(" src tst`).
Production code **only** uses `ChatMapController(ServiceGraph
services)` — the telescoping constructors are used exclusively by
tests, in exactly two files:

- `tst/chatmap/presentation/ui/ChatMapControllerTest.java` (5 call
  sites: lines 94, 194, 236, 469, 550 as of this writing — recheck line
  numbers before editing, they will have shifted)
- `tst/chatmap/presentation/ui/ChatMapMvpWorkflowTest.java` (1 call
  site, line 59 as of this writing)

This makes the refactor low-risk: no production wiring changes, and
the blast radius is fully known and small.

## Design

Mirror the existing `Chat.Builder` pattern in this same codebase
(`src/chatmap/domain/Chat.java`) — same shape: a static nested
`Builder` class, one fluent setter per field returning `this`, a
`build()` method at the end. Do not invent a different builder style;
reuse the pattern already established and familiar in this project.

```java
public final class ChatMapController {
    // ... existing fields unchanged ...

    private ChatMapController(Builder builder) {
        this.importService = builder.importService;
        this.exportService = builder.exportService;
        this.searchService = builder.searchService;
        this.projectService = builder.projectService;
        this.tagService = builder.tagService;
        this.summaryService = builder.summaryService;
        this.liveChatFetchService = builder.liveChatFetchService;
        this.archiveImportService = builder.archiveImportService;
        this.conversationInventoryService = builder.conversationInventoryService;
        this.promptService = builder.promptService;
        this.promptRouterService = builder.promptRouterService;
        this.listState = new ChatListState();
    }

    /** Wires the controller from the shared {@link ServiceGraph} — the production path. Unchanged. */
    public ChatMapController(ServiceGraph services) {
        this(builder()
                .importService(services.importService())
                .exportService(services.exportService())
                .searchService(services.searchService())
                .projectService(services.projectService())
                .tagService(services.tagService())
                .summaryService(services.summaryService())
                .liveChatFetchService(services.liveChatFetchService())
                .archiveImportService(services.archiveImportService())
                .conversationInventoryService(services.conversationInventoryService())
                .promptService(services.promptService())
                .promptRouterService(services.promptRouterService()));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ImportService importService;
        private ExportService exportService;
        private SearchService searchService;
        private ProjectService projectService;
        private TagService tagService;
        private SummaryService summaryService;
        private LiveChatFetchService liveChatFetchService;
        private ChatGptArchiveImportService archiveImportService;
        private ConversationInventoryService conversationInventoryService;
        private PromptService promptService;
        private PromptRouterService promptRouterService;

        private Builder() {
        }

        public Builder importService(ImportService v) { this.importService = v; return this; }
        public Builder exportService(ExportService v) { this.exportService = v; return this; }
        public Builder searchService(SearchService v) { this.searchService = v; return this; }
        public Builder projectService(ProjectService v) { this.projectService = v; return this; }
        public Builder tagService(TagService v) { this.tagService = v; return this; }
        public Builder summaryService(SummaryService v) { this.summaryService = v; return this; }
        public Builder liveChatFetchService(LiveChatFetchService v) { this.liveChatFetchService = v; return this; }
        public Builder archiveImportService(ChatGptArchiveImportService v) { this.archiveImportService = v; return this; }
        public Builder conversationInventoryService(ConversationInventoryService v) { this.conversationInventoryService = v; return this; }
        public Builder promptService(PromptService v) { this.promptService = v; return this; }
        public Builder promptRouterService(PromptRouterService v) { this.promptRouterService = v; return this; }

        public ChatMapController build() {
            Objects.requireNonNull(importService, "importService");
            Objects.requireNonNull(exportService, "exportService");
            Objects.requireNonNull(searchService, "searchService");
            Objects.requireNonNull(projectService, "projectService");
            Objects.requireNonNull(tagService, "tagService");
            Objects.requireNonNull(summaryService, "summaryService");
            Objects.requireNonNull(liveChatFetchService, "liveChatFetchService");
            // archiveImportService, conversationInventoryService, promptService,
            // promptRouterService stay nullable — existing methods already null-check
            // them at call time (see executePrompt/routePrompt/importChatGptArchive/
            // conversationInventory) and this behavior must not change.
            return new ChatMapController(this);
        }
    }
}
```

Required-vs-nullable split above reflects the original six
constructors: the first seven parameters (`importService` through
`liveChatFetchService`) appear in every existing constructor and are
therefore effectively required; `archiveImportService`,
`conversationInventoryService`, `promptService`, and
`promptRouterService` only appear in the later constructors and are
already handled as nullable elsewhere in the class. Preserve that
distinction — do not make the four optional ones required, and do not
silently make the seven required ones optional.

## Migration Steps

1. Add the `Builder` class and the private `Builder`-accepting
   constructor as shown above.
2. Update the `ChatMapController(ServiceGraph services)` constructor
   to delegate through the builder (shown above). Its public signature
   and behavior must not change — production code depends on it as-is.
3. Delete the five now-redundant telescoping constructors (the ones
   taking 7, 8, 9, 10, and 11 positional parameters).
4. Update the six test call sites (see "Confirmed Before Starting"
   above) to use `ChatMapController.builder()...build()` instead of
   positional constructor calls. Each test call site currently uses a
   different subset of the parameters — set only what each test
   actually needs via the builder's named methods, do not feel
   obligated to set every field just because the old constructor
   required it positionally.
5. Run the full test suite and confirm nothing else references a
   removed constructor signature.

## Non-Goals

- Do not add a builder to `ServiceGraph` itself — this handoff is
  scoped to `ChatMapController` only. `ServiceGraph`'s own
  construction is a separate concern.
- Do not change what's required vs. optional beyond preserving the
  existing behavior (see above) — this is a mechanical refactor, not
  an opportunity to reconsider which dependencies should be mandatory.
- Do not touch production wiring (`ChatMapControllerFactory`,
  `ChatMapRuntime`) — both already go through the
  `ServiceGraph`-accepting constructor and require no changes.

## Completion Criteria

`ChatMapController` has exactly two public constructors: the
`ServiceGraph`-accepting production constructor (unchanged behavior)
and none of the telescoping ones — all six test call sites use the
builder instead, and `./gradlew test` passes.

Run:

```bash
./gradlew test
```
