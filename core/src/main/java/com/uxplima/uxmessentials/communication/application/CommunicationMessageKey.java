package com.uxplima.uxmessentials.communication.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The communication context's user-visible message keys — the plugin's <em>own</em> strings only. Each constant
 * maps 1:1 to a kebab-case catalog key in {@code messages_<lang>.conf} ({@code BROADCAST_TOGGLE_ON} ↔
 * {@code communication.broadcast-toggle.on}); the constant is the compile-time handle, the catalog holds the
 * text. These resolve through both locale catalogs and are parity-checked.
 *
 * <p>Deliberately <em>not</em> here: the operator-authored join/quit/death templates, the announcer lines, and
 * the {@code /rules} / {@code /motd} body. That is operator content in {@code communication.conf}, rendered
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

    // /me — the third-person action broadcast line; the typed action is a placeholder, never re-parsed.
    ME("communication.me"),

    // /clearchat — the cleared notice the flushed players see, and the actor confirmation.
    CLEARCHAT_CLEARED("communication.clearchat.cleared"),
    CLEARCHAT_BY("communication.clearchat.by"),

    // /togglechat — the global chat lock toggle confirmations and the blocked notice a muted speaker sees.
    CHAT_LOCK_ON("communication.chat-lock.on"),
    CHAT_LOCK_OFF("communication.chat-lock.off"),
    CHAT_LOCKED("communication.chat-lock.locked"),

    // /uxmess reload communication — the announcer schedule was reloaded.
    ANNOUNCER_RELOADED("communication.announcer-reloaded");

    private final String key;

    CommunicationMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
