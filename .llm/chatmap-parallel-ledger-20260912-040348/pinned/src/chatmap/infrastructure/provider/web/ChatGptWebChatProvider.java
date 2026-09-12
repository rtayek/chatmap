package chatmap.infrastructure.provider.web;


import chatmap.infrastructure.provider.ProviderIdentity;
import chatmap.domain.Source;

/**
 * A {@link chatmap.application.port.provider.ChatProvider} that reads the latest chatgpt.com conversation in-process
 * over CDP, from the already-logged-in Chrome (no server, no API key). Sibling of
 * {@link ClaudeWebChatProvider} for ChatGPT.
 */
public final class ChatGptWebChatProvider extends CdpWebChatProvider {

    static final String FALLBACK_TITLE = "ChatGPT (web) live chat";

    public ChatGptWebChatProvider() {
        this(CdpWebChatProvider.DEFAULT_CDP_URL);
    }

    public ChatGptWebChatProvider(String cdpUrl) {
        this(new ChromeCdpLauncher(), new ChatGptWebAdapter(cdpUrl), cdpUrl);
    }

    public ChatGptWebChatProvider(ChromeCdpLauncher launcher, ChatGptWebAdapter adapter, String cdpUrl) {
        super("ChatGPT (web)", FALLBACK_TITLE, Source.chatGptWeb, launcher, adapter, cdpUrl,
                "ChatGPT web inventory is limited to all discoverable sidebar links; "
                + "lazy-loaded, archived, or hidden conversations may be absent.");
    }

    @Override
    protected String extractIdentity(String url) {
        return ProviderIdentity.chatGptWebId(url);
    }
}
