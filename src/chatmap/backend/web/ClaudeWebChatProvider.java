package chatmap.backend.web;

import chatmap.backend.providers.ProviderIdentity;
import chatmap.domain.Source;

/**
 * A {@link chatmap.backend.providers.ChatProvider} that reads the latest claude.ai conversation directly,
 * in-process, over CDP — no external server, no HTTP boundary, no environment
 * variables. A drop-in replacement for the old HttpChatProvider at the
 * {@link chatmap.backend.providers.ChatProvider} interface.
 */
public final class ClaudeWebChatProvider extends CdpWebChatProvider {

    static final String FALLBACK_TITLE = "Claude (web) live chat";

    public ClaudeWebChatProvider() {
        this(DEFAULT_CDP_URL);
    }

    public ClaudeWebChatProvider(String cdpUrl) {
        this(new ChromeCdpLauncher(), new ClaudeWebAdapter(cdpUrl), cdpUrl);
    }

    public ClaudeWebChatProvider(ChromeCdpLauncher launcher, ClaudeWebAdapter adapter, String cdpUrl) {
        super("Claude (web)", FALLBACK_TITLE, Source.claudeWeb, launcher, adapter, cdpUrl,
                "Claude web inventory is limited to all discoverable sidebar links; "
                + "lazy-loaded or archived conversations may be absent.");
    }

    @Override
    protected String extractIdentity(String url) {
        return ProviderIdentity.claudeWebId(url);
    }
}
