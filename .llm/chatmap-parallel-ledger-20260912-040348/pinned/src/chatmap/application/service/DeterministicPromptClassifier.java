package chatmap.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import chatmap.domain.PromptClassificationLevel;
import chatmap.domain.PromptClassificationReason;

/** Keyword and phrase based binary classifier. Uncertain prompts escalate. */
public final class DeterministicPromptClassifier implements PromptClassifier {

    private static final Pattern multiFilePattern = Pattern.compile(
            "\\b(multiple|many|all|every|across|repository|repo-wide|system-wide|project-wide)\\b"
                    + "|\\b(files|packages|modules|components)\\b");
    private static final Pattern architecturePattern = Pattern.compile(
            "\\b(architecture|architectural|redesign|migration|migrate|refactor|audit|design)\\b");
    private static final Pattern buildPattern = Pattern.compile(
            "\\b(gradle|build|dependency|dependencies|classpath|schema|api|apis|persistence|database|sqlite)\\b");
    private static final Pattern debuggingPattern = Pattern.compile(
            "\\b(root cause|trace|cross-component|integration|intermittent|race|deadlock|concurrency)\\b");
    private static final Pattern broadReviewPattern = Pattern.compile(
            "\\b(review everything|find all|all problems|code review|full review)\\b");
    private static final Pattern verificationPattern = Pattern.compile(
            "\\b(coordinated|repeated|end-to-end|e2e|integration test|quality gate)\\b");

    private static final Pattern lightweightPattern = Pattern.compile(
            "\\b(explain|what does|why does|syntax|compile error|compiler error|small|isolated|local)\\b"
                    + "|\\b(method|class|script|boilerplate|format|formatting|convert|conversion)\\b");

    @Override
    public PromptClassification classify(String prompt) {
        String normalized = normalize(prompt);
        List<PromptClassificationReason> monsterReasons = monsterReasons(normalized);
        if (!monsterReasons.isEmpty()) {
            return new PromptClassification(PromptClassificationLevel.MONSTER,
                    confidence(monsterReasons, 0.74), monsterReasons);
        }

        List<PromptClassificationReason> lightReasons = lightReasons(normalized);
        if (!lightReasons.isEmpty()) {
            return new PromptClassification(PromptClassificationLevel.LIGHTWEIGHT,
                    confidence(lightReasons, 0.70), lightReasons);
        }

        return new PromptClassification(PromptClassificationLevel.MONSTER,
                0.55, List.of(PromptClassificationReason.ambiguous));
    }

    private static List<PromptClassificationReason> monsterReasons(String prompt) {
        List<PromptClassificationReason> reasons = new ArrayList<>();
        addIf(reasons, multiFilePattern.matcher(prompt).find(), PromptClassificationReason.multiFileScope);
        addIf(reasons, architecturePattern.matcher(prompt).find(), PromptClassificationReason.architectureImpact);
        addIf(reasons, buildPattern.matcher(prompt).find(), PromptClassificationReason.buildImpact);
        addIf(reasons, prompt.contains("schema") || prompt.contains("persistence") || prompt.contains("database"),
                PromptClassificationReason.persistenceImpact);
        addIf(reasons, debuggingPattern.matcher(prompt).find(), PromptClassificationReason.crossComponentDebugging);
        addIf(reasons, broadReviewPattern.matcher(prompt).find(), PromptClassificationReason.broadReview);
        addIf(reasons, verificationPattern.matcher(prompt).find(), PromptClassificationReason.coordinatedVerification);
        return reasons;
    }

    private static List<PromptClassificationReason> lightReasons(String prompt) {
        if (!lightweightPattern.matcher(prompt).find()) {
            return List.of();
        }
        List<PromptClassificationReason> reasons = new ArrayList<>();
        addIf(reasons, prompt.contains("explain") || prompt.contains("what does") || prompt.contains("why does"),
                PromptClassificationReason.explanation);
        addIf(reasons, prompt.contains("syntax") || prompt.contains("compile error")
                || prompt.contains("compiler error") || prompt.contains("method") || prompt.contains("class"),
                PromptClassificationReason.localScope);
        addIf(reasons, prompt.contains("small") || prompt.contains("isolated") || prompt.contains("local"),
                PromptClassificationReason.smallChange);
        addIf(reasons, prompt.contains("script") || prompt.contains("boilerplate"),
                PromptClassificationReason.boilerplate);
        addIf(reasons, prompt.contains("format") || prompt.contains("convert"),
                PromptClassificationReason.conversion);
        if (reasons.isEmpty()) {
            reasons.add(PromptClassificationReason.localScope);
        }
        return reasons;
    }

    private static void addIf(List<PromptClassificationReason> reasons, boolean condition,
            PromptClassificationReason reason) {
        if (condition) {
            reasons.add(reason);
        }
    }

    private static double confidence(List<PromptClassificationReason> reasons, double base) {
        return Math.min(0.95, base + (reasons.size() - 1) * 0.06);
    }

    private static String normalize(String prompt) {
        if (prompt == null) {
            return "";
        }
        return prompt.toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}&&[^-]]+", " ").replaceAll("\\s+", " ").trim();
    }
}
