package chatmap.application.service;

/** Classifies developer prompts without spending an LLM request. */
public interface PromptClassifier {
    PromptClassification classify(String prompt);
}
