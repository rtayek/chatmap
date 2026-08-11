# Codex Handoff: Build a Read-Only Chat Keyword Inventory

## Immediate Goal

Generate a private, deterministic keyword-and-phrase inventory from the existing ChatMap database. Use it to discover likely projects, recurring topics, and relationships before designing automatic organization.

This is an exploratory analysis pass—not a production feature yet.

## Safety Boundaries

Do not modify the ChatMap database.

Do not modify the repository, commit, push, create a pull request, or rewrite history.

Do not place generated reports, temporary programs, or extracted chat material inside the repository.

Prefer Java and SQL. Do not use Python.

Keep all report files private and local. They may contain sensitive topic names even though they must not contain complete messages.

## Database

Database path:

`C:\Users\ray\.chatmap\chatmap.db`

Preserve the existing pre-import backup:

`C:\Users\ray\.chatmap\chatmap.db.backup-chatgpt-archive-20260805-232103`

Expected current aggregate state:

- 309 chats total
- 301 imported ChatGPT chats
- 8 pre-existing chats
- 8,311 messages total
- 8,282 messages belonging to the imported ChatGPT chats

Open SQLite in genuinely read-only mode and enable `PRAGMA query_only=ON`. Do not invoke application startup or migration code merely to perform this analysis.

If current counts differ, report the observed counts and continue only when the difference is explainable.

## Input Corpus

For this first report, analyze:

- chat titles
- messages whose role is exactly `user`

Do not include assistant, system, tool, or other message roles in the keyword corpus. Assistant responses are long, repeat user terminology, and would distort the initial picture of Ray's actual topics.

Treat each chat as one document for document-frequency and TF-IDF calculations.

Give title terms greater weight than message terms—approximately three times normal message weight is reasonable—but always show unweighted document frequency separately.

## Tokenization and Normalization

Use a deterministic, documented tokenizer.

Requirements:

- case-insensitive matching using `Locale.ROOT`
- Unicode-aware words
- preserve technically meaningful forms when practical, including `C++`, `C#`, `.NET`, `JavaFX`, `ChatGPT`, repository names, and hyphenated identifiers
- retain one representative display spelling while using a normalized form for counting
- ignore empty tokens and one-character noise except meaningful technical tokens
- exclude numbers-only tokens, timestamps, UUIDs, URL components, and obvious generated file IDs
- exclude English stop words and high-frequency conversational filler
- separate natural-language terms from path/repository/identifier candidates rather than discarding all identifiers

Report the final stop-word list or its source and any project-specific exclusions. Do not quietly tune exclusions to manufacture attractive clusters.

## Required Measurements

### 1. Global Keywords

Produce the top 100 meaningful terms with:

- normalized term
- representative display form
- number of distinct chats containing it (`chatCount`)
- total occurrences in the selected corpus (`termCount`)
- percentage of analyzed chats containing it

Rank primarily by distinct-chat count. Very long chats must not dominate merely through repetition.

### 2. Phrases

Produce the top 100 meaningful bigrams and trigrams with:

- phrase
- distinct-chat count
- total occurrence count
- phrase length

Break phrases at discarded noise, URLs, and message boundaries. Do not form phrases across separate messages.

### 3. Per-Chat Keywords

Produce up to ten TF-IDF-style keywords or phrases for each chat.

Include only:

- internal chat ID
- source
- title
- top terms/phrases and their scores

Do not include message excerpts.

### 4. Keyword Relationships

Produce high-value keyword pairs that co-occur in the same chat.

For each pair, include:

- both terms
- number of chats containing both
- support as a percentage of chats
- a normalized association measure such as Jaccard similarity or positive PMI

Apply a sensible minimum joint-chat count so one-off coincidences do not dominate. Report the threshold.

### 5. Possible Project Clusters

Suggest exploratory project/topic clusters from titles, identifiers, and keyword relationships.

For each candidate cluster, report:

- proposed neutral label
- strongest associated terms
- number of candidate chats
- internal chat IDs and titles
- confidence or a short reason

Do not write projects or tags to the database. Do not force every chat into a cluster. An `unclassified` remainder is expected.

Do not use an LLM, embeddings, or external services during this first deterministic pass.

## Output Files

Write reports outside the repository under a timestamped directory such as:

`C:\Users\ray\.chatmap\reports\keyword-inventory-YYYYMMDD-HHMMSS\`

Produce:

1. `keyword-summary.md`
   - corpus statistics
   - top global keywords
   - top phrases
   - top co-occurring pairs
   - proposed project clusters
   - method and limitations

2. `chat-keywords.csv`
   - one row per chat/keyword combination
   - chat ID, source, title, term, score, and rank

3. `keyword-counts.csv`
   - complete aggregate term and phrase counts used by the summary

Use UTF-8 and deterministic ordering for tied results.

Do not include message bodies or excerpts in any output.

## Validation

Verify and report:

- database opened read-only
- database file timestamp and size unchanged by the analysis
- analyzed chat count
- analyzed user-message count
- assistant-message count included in corpus: exactly zero
- number of unique normalized terms
- number of retained bigrams and trigrams
- number of unclassified chats
- repeated runs against unchanged data produce byte-identical CSV output, except for timestamped paths or explicitly documented timestamps
- no repository files changed or added

Manually inspect aggregate output for obvious failures such as ordinary stop words dominating, URLs becoming keywords, or code punctuation producing meaningless phrases. Do not inspect or report raw chat text.

## Completion Report

Report:

- exact report directory and filenames
- database counts observed
- tokenizer and ranking method
- top 25 keywords
- top 25 phrases
- top 20 keyword relationships
- proposed project clusters and chat counts
- analysis limitations
- `git status --short --branch`, confirming the repository remained unchanged

Do not modify the database, repository, or Git history. Stop after reporting so Ray can review the vocabulary before any tagging or clustering feature is designed.

