package chatmap.infrastructure.provider.web;


import java.util.List;

/**
 * Structured outcome of one exhaustive discovery attempt: every conversation
 * summary accumulated (deduplicated, order preserved) plus whether that list
 * is actually the complete set and, when it is not, why.
 */
record WebDiscoveryResult(
        String provider,
        List<CdpTranscriptAdapter.ChatWebSummary> conversations,
        DiscoveryStatus status,
        String reason) {

    WebDiscoveryResult {
        conversations = List.copyOf(conversations);
    }

    int discoveredCount() {
        return conversations.size();
    }

    static WebDiscoveryResult unavailable(String provider, String reason) {
        return new WebDiscoveryResult(provider, List.of(), DiscoveryStatus.unavailable, reason);
    }

    static WebDiscoveryResult failed(String provider, String reason) {
        return new WebDiscoveryResult(provider, List.of(), DiscoveryStatus.failed, reason);
    }
}
