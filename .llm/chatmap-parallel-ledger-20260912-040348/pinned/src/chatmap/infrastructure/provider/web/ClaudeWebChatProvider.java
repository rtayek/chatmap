package chatmap.infrastructure.provider.web;


import chatmap.infrastructure.provider.ProviderIdentity;
import chatmap.domain.Source;

/**
 * A {@link chatmap.application.port.provider.ChatProvider} that reads the latest claude.ai conversation directly,
 * in-process, over CDP — no external server, no HTTP boundary, no environment
 * variables. A drop-in replacement for the old HttpChatProvider at the
 * {@link chatmap.application.port.provider.ChatProvider} interface.
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
