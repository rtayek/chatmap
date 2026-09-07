package chatmap.a2a.experiment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SemanticAcceptanceTest {
    @Test
    void acceptsExactStructuredFacts() {
        SemanticAcceptance.Evaluation evaluation = SemanticAcceptance.evaluate("""
                state=FAILED
                reportedReason=worker timeout
                retryPolicy=PROHIBITED
                causalConclusion=NOT_ESTABLISHED
                """);

        assertTrue(evaluation.accepted());
        assertTrue(evaluation.problems().isEmpty());
    }

    @Test
    void rejectsIncorrectFact() {
        SemanticAcceptance.Evaluation evaluation = SemanticAcceptance.evaluate("""
                state=FAILED
                reportedReason=worker timeout
                retryPolicy=UNKNOWN
                causalConclusion=NOT_ESTABLISHED
                """);

        assertFalse(evaluation.accepted());
        assertTrue(evaluation.problems().stream()
                .anyMatch(problem -> problem.contains("retryPolicy=PROHIBITED")));
    }

    @Test
    void rejectsProseAndMissingFields() {
        SemanticAcceptance.Evaluation evaluation =
                SemanticAcceptance.evaluate("The task failed after a timeout.");

        assertFalse(evaluation.accepted());
        assertTrue(evaluation.problems().stream()
                .anyMatch(problem -> problem.contains("Expected 4 lines")));
    }

    @Test
    void rejectsExtraOutput() {
        SemanticAcceptance.Evaluation evaluation = SemanticAcceptance.evaluate("""
                state=FAILED
                reportedReason=worker timeout
                retryPolicy=PROHIBITED
                causalConclusion=NOT_ESTABLISHED
                explanation=The timeout caused the failure.
                """);

        assertFalse(evaluation.accepted());
        assertTrue(evaluation.problems().stream()
                .anyMatch(problem -> problem.contains("Expected 4 lines")));
    }
}
