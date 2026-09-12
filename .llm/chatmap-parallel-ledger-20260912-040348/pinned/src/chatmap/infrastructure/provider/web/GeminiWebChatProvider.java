package chatmap.infrastructure.provider.web;


import chatmap.infrastructure.provider.ProviderIdentity;
import chatmap.domain.Source;

/**
 * A {@link chatmap.application.port.provider.ChatProvider} that reads the latest gemini.google.com conversation
 * in-process over CDP. Sibling of {@link ClaudeWebChatProvider} for Gemini.
 * The underlying {@link GeminiWebAdapter} was verified against the live page.
 */
public final class GeminiWebChatProvider extends CdpWebChatProvider {

    static final String FALLBACK_TITLE = "Gemini (web) live chat";

    public GeminiWebChatProvider() {
        this(CdpWebChatProvider.DEFAULT_CDP_URL);
    }

    public GeminiWebChatProvider(String cdpUrl) {
        this(new ChromeCdpLauncher(), new GeminiWebAdapter(cdpUrl), cdpUrl);
    }

    public GeminiWebChatProvider(ChromeCdpLauncher launcher, GeminiWebAdapter adapter, String cdpUrl) {
        super("Gemini (web)", FALLBACK_TITLE, Source.geminiWeb, launcher, adapter, cdpUrl,
                "Gemini web inventory is limited to all discoverable sidebar entries. "
                + "Entries without a durable /app/<id> URL use a session-local sidebar reference.");
    }

    @Override
    protected String extractIdentity(String url) {
        return ProviderIdentity.geminiWebId(url);
    }
}
