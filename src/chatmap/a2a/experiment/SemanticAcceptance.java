package chatmap.a2a.experiment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Deterministic acceptance contract for one bounded semantic probe. */
final class SemanticAcceptance {
    private static final List<String> EXPECTED_LINES = List.of(
            "state=FAILED",
            "reportedReason=worker timeout",
            "retryPolicy=PROHIBITED",
            "causalConclusion=NOT_ESTABLISHED");

    private SemanticAcceptance() {
    }

    static String prompt() {
        return """
                Return exactly four lines in key=value format and no other text.
                Use the field names and order shown below.
                Facts:
                - The recorded task state is FAILED.
                - The recorded reason is worker timeout.
                - The recorded retry policy is PROHIBITED.
                - The ledger records observations and establishes no causal conclusion.
                Required fields:
                state
                reportedReason
                retryPolicy
                causalConclusion
                For causalConclusion, use the value NOT_ESTABLISHED.
                """.strip();
    }

    static Evaluation evaluate(String output) {
        Objects.requireNonNull(output, "output");
        List<String> actualLines = output.strip().lines().toList();
        List<String> problems = new ArrayList<>();

        if (actualLines.size() != EXPECTED_LINES.size()) {
            problems.add("Expected " + EXPECTED_LINES.size()
                    + " lines but received " + actualLines.size());
        }

        int comparableLines = Math.min(actualLines.size(), EXPECTED_LINES.size());
        for (int index = 0; index < comparableLines; index++) {
            String expected = EXPECTED_LINES.get(index);
            String actual = actualLines.get(index);
            if (!expected.equals(actual)) {
                problems.add("Line " + (index + 1) + " expected <"
                        + expected + "> but received <" + actual + ">");
            }
        }

        return new Evaluation(problems.isEmpty(), List.copyOf(problems));
    }

    record Evaluation(boolean accepted, List<String> problems) {
        Evaluation {
            Objects.requireNonNull(problems, "problems");
            problems = List.copyOf(problems);
        }
    }
}
