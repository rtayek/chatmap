# MyClaw to ChatMap Consolidation Plan

> [!IMPORTANT]
> **Decision**: Merge **MyClaw** (`rtayek/myclaw`) completely into **ChatMap** (`cjatmanager` / `rtayek/chatmap`).
> This creates a single, unified codebase in `cjatmanager` with a single Gradle build pipeline (`build.gradle.kts`), single quality suite (Checkstyle, PMD, SpotBugs, JaCoCo), and unified JavaFX UI.

---

## 1. Unified Project Structure in ChatMap (`cjatmanager`)

```
cjatmanager/
├── build.gradle.kts                 # Unified Gradle Kotlin DSL build configuration
├── config/                          # Unified quality pipeline rules (Checkstyle, PMD, SpotBugs)
└── src/
    └── chatmap/
        ├── backend/                 # Execution Backends (Claude CLI, Codex CLI, Agy CLI, Ollama CLI, CDP Web Adapters)
        │   ├── AiBackend.java       # (From MyClaw) Generic prompt execution contract
        │   ├── CommandRunner.java   # (From MyClaw) Process execution engine
        │   ├── ChromeCdpLauncher.java # Standardized CDP launcher
        │   └── PlaywrightWebAdapter.java # Web browser chat connector
        ├── domain/                  # Unified Chat, Message, PromptResult, Source, Tag models
        ├── repository/              # SQLite Database Repositories (Relational & Event-sourced)
        ├── service/                 # Ingestion, Summarization, Consolidation, Prompt Execution Services
        ├── ui/                      # Unified JavaFX 21 User Interface (ChatMapApp)
        └── cli/                     # CLI Entry Points (Consolidator, Importer, Summarizer, Prompt Harness)
```

---

## 2. Key Components Being Merged from MyClaw

| Component | Origin | Target Location in `cjatmanager` | Purpose |
| :--- | :--- | :--- | :--- |
| **`CommandRunner.java`** | `myclaw.execution` | `chatmap.backend.CommandRunner` | ProcessBuilder execution engine with timeouts and output capture |
| **`AiBackend.java` & Implementations** | `myclaw.backend` | `chatmap.backend` | Generic prompt execution interfaces for Claude, Codex, Agy, Ollama CLI backends |
| **`PlaywrightWebAdapter.java`** | `myclaw.web` | `chatmap.backend.PlaywrightWebAdapter` | CDP Web browser chat submission to `chatgpt.com` & `claude.ai` |
| **Prompt Harness Services** | `myclaw.application` | `chatmap.service.PromptService` | Submitting prompts to backends and auto-recording results into ChatMap database |

---

## 3. Step-by-Step Consolidation Roadmap

### Step 1: Port Execution Engine & Web Adapters
1. Copy `CommandRunner.java`, `CommandRequest.java`, `CommandResult.java` from `myclaw` to `chatmap.backend`.
2. Port `PlaywrightWebAdapter.java` into `chatmap.backend`.
3. Add `com.microsoft.playwright:playwright` dependency to [`build.gradle.kts`](file:///C:/Users/ray/eclipse-workspace/cjatmanager/build.gradle.kts).

### Step 2: Port AI Backend Contracts & Implementations
1. Port `AiBackend.java`, `AiRequest.java`, `AiResponse.java`, `BackendId.java` to `chatmap.backend`.
2. Port CLI backends (`ClaudeCliBackend`, `CodexCliBackend`, `AgyCliBackend`, `OllamaCliBackend`) into `chatmap.backend`.

### Step 3: Wire Prompt Execution to ChatMap Database
1. Update `PromptService.java` to write incoming prompt requests and backend responses directly into ChatMap's `SqliteChatRepository` and `SqliteMessageRepository`.

### Step 4: Integrate JavaFX UI Controls
1. Add a **Prompt Execution Panel / Console** to `ChatMapApp.java` (JavaFX) allowing users to select an AI backend (Claude, Codex, Agy, Ollama, Web), submit a prompt, view the response in real-time, and auto-save to the ChatMap graph/database.

### Step 5: Verification & Cleanup
1. Run `.\gradlew.bat check test` in `cjatmanager` to ensure all tests, Checkstyle, PMD, SpotBugs, and JaCoCo coverage pass 100%.
2. Commit and push unified changes to `rtayek/chatmap.git`.
