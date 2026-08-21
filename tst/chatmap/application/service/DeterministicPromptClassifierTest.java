package chatmap.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import chatmap.domain.PromptClassificationLevel;
import chatmap.domain.PromptClassificationReason;

class DeterministicPromptClassifierTest {

    private final DeterministicPromptClassifier classifier = new DeterministicPromptClassifier();

    @Test
    void classifiesLocalUnambiguousPromptsAsLightweight() {
        PromptClassification result = classifier.classify("Explain this compile error in Widget.java.");

        assertEquals(PromptClassificationLevel.LIGHTWEIGHT, result.level());
        assertTrue(result.reasons().contains(PromptClassificationReason.explanation));
        assertTrue(result.reasons().contains(PromptClassificationReason.localScope));
    }

    @Test
    void classifiesMultiFileAndArchitecturalPromptsAsMonster() {
        PromptClassification result = classifier.classify("Review architecture across multiple modules.");

        assertEquals(PromptClassificationLevel.MONSTER, result.level());
        assertTrue(result.reasons().contains(PromptClassificationReason.multiFileScope));
        assertTrue(result.reasons().contains(PromptClassificationReason.architectureImpact));
    }

    @Test
    void classifiesGradleBuildAndDependencyImpactAsMonster() {
        PromptClassification result = classifier.classify("Fix the Gradle build and dependency configuration.");

        assertEquals(PromptClassificationLevel.MONSTER, result.level());
        assertTrue(result.reasons().contains(PromptClassificationReason.buildImpact));
    }

    @Test
    void defaultsAmbiguousPromptsToMonster() {
        PromptClassification result = classifier.classify("Make it better.");

        assertEquals(PromptClassificationLevel.MONSTER, result.level());
        assertEquals(PromptClassificationReason.ambiguous, result.reasons().getFirst());
    }

    @Test
    void matchingIsCaseInsensitiveAndToleratesHarmlessPunctuation() {
        PromptClassification result = classifier.classify("EXPLAIN: this compiler error!");

        assertEquals(PromptClassificationLevel.LIGHTWEIGHT, result.level());
        assertTrue(result.reasons().contains(PromptClassificationReason.explanation));
    }
}
