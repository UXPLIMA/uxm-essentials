package com.uxplima.uxmessentials.communication.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The communication context's user-visible message keys — the plugin's <em>own</em> strings only. Each constant
 * maps 1:1 to a kebab-case catalog key in {@code messages_<lang>.conf} ({@code BROADCAST_TOGGLE_ON} ↔
 * {@code communication.broadcast-toggle.on}); the constant is the compile-time handle, the catalog holds the
 * text. These resolve through both locale catalogs and are parity-checked.
 *
 * <p>Deliberately <em>not</em> here: the operator-authored join/quit/death templates, the announcer lines, and
 * the {@code /rules} / {@code /motd} body. That is operator content in the module's content siblings, rendered
 * through MiniMessage, and never a {@code MessageKey} — so the locale-parity guard never sees it. Only the
 * plugin's own confirmations and errors live here.
 *
 * <p>Per the i18n contract, a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set.
 */
public enum CommunicationMessageKey implements MessageKey {

    // /broadcasttoggle — the per-player announcer subscription toggle confirmations.
    BROADCAST_TOGGLE_ON("communication.broadcast-toggle.on"),
    BROADCAST_TOGGLE_OFF("communication.broadcast-toggle.off"),

    // /rules /motd /info — a requested info page that is not configured.
    INFO_PAGE_MISSING("communication.info-page-missing"),

    // /rules /motd /info [page] — the paginated header/footer chrome drawn around a multi-page info body. The page
    // body itself is operator content, never a MessageKey; only this framing is the plugin's own string.
    INFO_PAGE_HEADER("communication.info-page.header"),
    INFO_PAGE_FOOTER("communication.info-page.footer"),

    // /me — the third-person action broadcast line; the typed action is a placeholder, never re-parsed.
    ME("communication.me"),

    // /clearchat — the cleared notice the flushed players see, and the actor confirmation.
    CLEARCHAT_CLEARED("communication.clearchat.cleared"),
    CLEARCHAT_BY("communication.clearchat.by"),

    // /togglechat — the global chat lock toggle confirmations and the blocked notice a muted speaker sees.
    CHAT_LOCK_ON("communication.chat-lock.on"),
    CHAT_LOCK_OFF("communication.chat-lock.off"),
    CHAT_LOCKED("communication.chat-lock.locked"),

    // /uxmess reload communication, /announce reload — the announcer config was reloaded.
    ANNOUNCER_RELOADED("communication.announcer-reloaded"),

    // /announce list — the header above the announcement list, one entry per announcement, and the empty notice.
    ANNOUNCE_LIST_HEADER("communication.announce.list-header"),
    ANNOUNCE_LIST_ENTRY("communication.announce.list-entry"),
    ANNOUNCE_LIST_EMPTY("communication.announce.list-empty"),

    // /announce preview <id> — the requested announcement id is not configured.
    ANNOUNCE_PREVIEW_UNKNOWN("communication.announce.preview-unknown");

    private final String key;

    CommunicationMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
