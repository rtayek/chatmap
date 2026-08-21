package chatmap.domain;

/** Durable reason codes used to audit deterministic prompt-routing rules. */
public enum PromptClassificationReason {
    localScope("LOCAL_SCOPE"),
    smallChange("SMALL_CHANGE"),
    explanation("EXPLANATION"),
    boilerplate("BOILERPLATE"),
    conversion("CONVERSION"),
    multiFileScope("MULTI_FILE_SCOPE"),
    architectureImpact("ARCHITECTURE_IMPACT"),
    buildImpact("BUILD_IMPACT"),
    persistenceImpact("PERSISTENCE_IMPACT"),
    crossComponentDebugging("CROSS_COMPONENT_DEBUGGING"),
    broadReview("BROAD_REVIEW"),
    coordinatedVerification("COORDINATED_VERIFICATION"),
    ambiguous("AMBIGUOUS");

    private final String code;

    PromptClassificationReason(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
