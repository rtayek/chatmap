---
project: chatmap
agent: claude
branch: feature-markdown-fence-safety
---

# Task: Fence agent output safely when writing it into Markdown result/failure files

## The bug

`HandoffInboxManager.writeResultFile` (in
`src/chatmap/application/service/HandoffInboxManager.java`) builds the `.result.md`
that syncs to the phone. It ends with:

```java
+ "## Output\n\n"
+ agentResult.standardOutput();
```

The agent's stdout is dropped in raw, UNFENCED, directly under a heading. Two
problems:
1. With stream-json, stdout is NDJSON/JSON — rendered as raw Markdown it's noise,
   and any line starting with `#`, `-`, `>` etc. is misinterpreted as Markdown
   structure.
2. Even if wrapped in a naive ``` fence, agent responses routinely CONTAIN triple
   backticks (code blocks), which would prematurely close the fence and corrupt
   the rest of the document.

The failure-report path that includes agent stdout has the same exposure.

## The fix (port MyClaw's proven logic)

MyClaw already solved this in `TranscriptRenderer`. Port its two ideas — a
fence-safe section writer:

1. Add a small helper (a package-private static utility class, e.g.
   `MarkdownFences`, in the same package, OR private static methods on
   `HandoffInboxManager` — your call, prefer a tiny reusable class since the
   failure path needs it too) with:

   ```java
   // Returns a backtick fence strictly longer than the longest backtick run
   // in content, and never shorter than 3.
   static String fenceFor(String content) {
       int longest = 0, current = 0;
       for (int i = 0; i < content.length(); i++) {
           if (content.charAt(i) == '`') { current++; longest = Math.max(longest, current); }
           else { current = 0; }
       }
       return "`".repeat(Math.max(3, longest + 1));
   }

   // Appends a fenced "## heading" section whose fence can't be broken by content.
   static void appendFencedSection(StringBuilder md, String heading, String content) {
       md.append("\n## ").append(heading).append("\n\n");
       String fence = fenceFor(content);
       md.append(fence).append("\n").append(content);
       if (!content.endsWith("\n")) md.append("\n");
       md.append(fence).append("\n");
   }
   ```

2. In `writeResultFile`, replace the raw `"## Output\n\n" + standardOutput` tail
   with `appendFencedSection(sb, "Output", agentResult.standardOutput())`.
   Keep the header block (Project/Agent/Branch/Timestamp/Exit code/Full stdout/
   stderr paths) exactly as-is — only the output section changes.

3. Apply the same `appendFencedSection` to any place agent stdout/stderr is
   embedded in the failure report (find where the failure path writes agent
   output and fence it the same way).

## Constraints

- Behavior otherwise identical: same filename, same header fields, same location.
  Only the output section becomes fenced.
- Do not pull in MyClaw as a dependency — COPY the small logic (it's ~15 lines).
  ChatMap and MyClaw are separate repos.
- Application layer, no new external deps.
- Naming: lowerCamelCase, no underscores.
- `./gradlew check` passes.

## Tests (add to a MarkdownFences test or the HandoffInboxManager test)

- **Fence longer than content backticks:** content containing ```` ``` ````
  (a triple-backtick run) is wrapped in a fence of at least 4 backticks; content
  with a 4-run gets a 5-fence. Assert the opening fence length > longest inner run.
- **Minimum fence is 3:** content with no backticks gets exactly ```` ``` ````.
- **Round-trip integrity:** the written section, when the fence is stripped,
  equals the original content (no truncation, trailing newline handled).
- **writeResultFile output is fenced:** a result file written with stdout that
  contains a ``` fence does not have that fence break the document (the `## Output`
  content is enclosed by a longer fence).

## Validation

`./gradlew check` passes; result files with backtick-containing agent output are
valid Markdown (inner fences don't escape the block); new fence tests green.
