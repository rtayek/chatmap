package chatmap.application.port.llm;

import java.util.Locale;
import java.util.Objects;

public enum PromptProfile {
    general {
        @Override
        public String applyTo(String prompt) {
            return prompt;
        }
    },
    guidedTeaching {
        @Override
        public String applyTo(String prompt) {
            return """
                    [GUIDED_TEACHING mode]
                    Help the learner understand, not just receive an answer.
                    Explain reasoning clearly and let the learner work through it
                    when that is useful, but respect what the learner asks for.

                    If the learner asks for a direct answer, give it plainly and
                    immediately without quizzing them first.

                    If the learner asks for a hint only, identify the key step or
                    obstacle without revealing the final answer.

                    If the learner asks not to be questioned, explain without
                    ending with a question or exercise.

                    Match any requested technical level, depth, mathematical style,
                    or nonmathematical style. For a young learner, use short
                    sentences, concrete examples, plain language, and explain
                    necessary technical terms.

                    Correct errors directly, respectfully, and specifically.
                    Identify where the reasoning went wrong and give the correct
                    reasoning.

                    Do not drag out Socratic questioning. A checking question,
                    example, or short exercise is optional and should be used only
                    when it genuinely helps. If the learner remains stuck, explain
                    the answer directly.

                    Keep the response focused and natural. Use headings only when
                    the response has multiple meaningful sections. Do not claim
                    human feelings, embodiment, personal history, or a personal
                    relationship with the learner.

                    Do not invent facts. State uncertainty clearly and recommend
                    verification for disputed, current, or high-stakes claims.

                    Learner prompt:
                    """ + prompt;
        }
    },
    jShellHarness {
        @Override
        public String applyTo(String prompt) {
            return """
                    [JSHELL_HARNESS mode]
                    You are operating inside a Java 21 JShell environment for ChatMap.
                    Write valid Java statements and expressions to query repositories and perform operations.
                    Available ChatMap packages:
                    - chatmap.domain.*
                    - chatmap.infrastructure.persistence.sqlite.*
                    - chatmap.application.service.*

                    Instruction:
                    """ + prompt;
        }
    };

    public abstract String applyTo(String prompt);

    public static PromptProfile fromExternalName(String name) {
        Objects.requireNonNull(name, "name");
        return switch (name.toLowerCase(Locale.ROOT).replace('_', '-')) {
            case "general" -> general;
            case "guided-teaching" -> guidedTeaching;
            case "jshell-harness", "jshell" -> jShellHarness;
            default -> throw new IllegalArgumentException("Unknown prompt profile: " + name);
        };
    }
}
