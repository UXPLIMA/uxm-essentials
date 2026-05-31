package com.uxplima.uxmessentials.teleport.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The teleport context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key
 * in {@code messages_<lang>.conf} ({@code TPA_SENT} ↔ {@code teleport.tpa.sent}); the constant is the
 * compile-time handle, the catalog holds the text. There are no inline player-facing literals anywhere
 * in the context — every message resolves through one of these.
 *
 * <p>Per the i18n contract, a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set.
 */
public enum TeleportMessageKey implements MessageKey {

    // tpa lifecycle
    TPA_SENT("teleport.tpa.sent"),
    TPA_RECEIVED("teleport.tpa.received"),
    TPA_HERE_RECEIVED("teleport.tpa.here-received"),
    TPA_ACCEPTED("teleport.tpa.accepted"),
    TPA_DENIED("teleport.tpa.denied"),
    TPA_CANCELLED("teleport.tpa.cancelled"),
    TPA_EXPIRED("teleport.tpa.expired"),
    TPA_SELF("teleport.tpa.self"),
    TPA_TARGET_OFFLINE("teleport.tpa.target-offline"),
    TPA_TOGGLED_OFF("teleport.tpa.toggled-off"),
    TPA_BLOCKED("teleport.tpa.blocked"),
    TPA_NONE_PENDING("teleport.tpa.none-pending"),
    TPA_TOGGLE_ON("teleport.tpa.toggle-on"),
    TPA_TOGGLE_OFF("teleport.tpa.toggle-off"),

    // back
    BACK_RETURNED("teleport.back.returned"),
    BACK_NONE("teleport.back.none"),
    BACK_DEATH_DENIED("teleport.back.death-denied"),

    // rtp
    RTP_SEARCHING("teleport.rtp.searching"),
    RTP_DISALLOWED("teleport.rtp.disallowed"),
    RTP_NO_LOCATION("teleport.rtp.no-location"),
    RTP_EXHAUSTED("teleport.rtp.exhausted"),

    // spawn
    SPAWN_TELEPORTED("teleport.spawn.teleported"),
    SPAWN_SET("teleport.spawn.set"),
    SPAWN_UNRESOLVED("teleport.spawn.unresolved"),

    // admin / positional
    TP_DONE("teleport.tp.done"),
    TP_NO_TARGET_BLOCK("teleport.tp.no-target-block"),

    // a jailed player may not self-teleport — the moderation context's JailGate denies it here
    JAILED("teleport.jailed"),

    // shared cooldown / warmup feedback owned by the teleport tiers
    COOLDOWN_ACTIVE("teleport.cooldown.active"),
    WARMUP_STARTED("teleport.warmup.started"),
    WARMUP_CANCELLED("teleport.warmup.cancelled");

    private final String key;

    TeleportMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
