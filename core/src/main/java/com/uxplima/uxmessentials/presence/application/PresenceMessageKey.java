package com.uxplima.uxmessentials.presence.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The presence context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code AFK_NOW} ↔ {@code afk.now}); the constant is the compile-time handle,
 * the catalog holds the text. There are no inline player-facing literals anywhere in the context — every
 * message resolves through one of these.
 *
 * <p>Per the i18n contract, a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set.
 */
public enum PresenceMessageKey implements MessageKey {

    // /afk — manual and auto AFK transitions
    AFK_NOW("afk.now"),
    AFK_NOW_REASON("afk.now-reason"),
    AFK_RETURNED("afk.returned"),
    AFK_BROADCAST_AWAY("afk.broadcast-away"),
    AFK_BROADCAST_AWAY_REASON("afk.broadcast-away-reason"),
    AFK_BROADCAST_BACK("afk.broadcast-back"),

    // anti-AFK: shown once per AFK session when item pickups are blocked while away
    AFK_PICKUP_BLOCKED("afk.pickup-blocked"),

    // /vanish
    VANISH_ON("vanish.on"),
    VANISH_OFF("vanish.off");

    private final String key;

    PresenceMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
