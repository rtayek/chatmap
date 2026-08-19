package chatmap.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MarkdownFencesTest {

    @Test
    void fenceIsLongerThanTheLongestBacktickRunInContent() {
        assertEquals("````", MarkdownFences.fenceFor("some ```code``` here"));
        assertEquals("`````", MarkdownFences.fenceFor("nested ```` fence"));
    }

    @Test
    void minimumFenceIsThreeBackticksWhenContentHasNone() {
        assertEquals("```", MarkdownFences.fenceFor("plain text, no backticks"));
    }

    @Test
    void appendedSectionRoundTripsTheOriginalContentBetweenTheFences() {
        StringBuilder md = new StringBuilder();
        String content = "line one\nline two with ```a fence``` inside\nline three";

        MarkdownFences.appendFencedSection(md, "Output", content);

        String rendered = md.toString();
        String fence = MarkdownFences.fenceFor(content);
        int openStart = rendered.indexOf(fence);
        int openEnd = openStart + fence.length();
        int closeStart = rendered.indexOf(fence, openEnd);

        assertTrue(openStart >= 0, "opening fence must be present");
        assertTrue(closeStart > openEnd, "closing fence must be present after the content");
        String extracted = rendered.substring(openEnd + 1, closeStart - 1);
        assertEquals(content, extracted);
    }

    @Test
    void appendedSectionHandlesContentAlreadyEndingInNewline() {
        StringBuilder md = new StringBuilder();

        MarkdownFences.appendFencedSection(md, "Output", "already terminated\n");

        String rendered = md.toString();
        assertEquals(
                "\n## Output\n\n```\nalready terminated\n```\n",
                rendered);
    }
}
