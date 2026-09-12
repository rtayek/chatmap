package chatmap.application.service;

/**
 * Fence-safe Markdown section writing. Backtick fences must be strictly longer than
 * any backtick run inside the content they wrap, or the content can prematurely close
 * the fence and corrupt the rest of the document.
 */
final class MarkdownFences {

    private MarkdownFences() {
    }

    static String fenceFor(String content) {
        int longest = 0;
        int current = 0;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '`') {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 0;
            }
        }
        return "`".repeat(Math.max(3, longest + 1));
    }

    static void appendFencedSection(StringBuilder md, String heading, String content) {
        md.append("\n## ").append(heading).append("\n\n");
        String fence = fenceFor(content);
        md.append(fence).append("\n").append(content);
        if (!content.endsWith("\n")) {
            md.append("\n");
        }
        md.append(fence).append("\n");
    }
}
