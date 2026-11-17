package com.uxplima.uxmessentials.moderation.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * An account was given a copy of the mute of another spelling of its name on an offline-mode server. It is not a new
 * mute: no staff member gave it, so nothing announces it; it only tells the in-memory mute checks what the account now
 * holds.
 *
 * @param target the account that holds the copy
 * @param from the account whose mute it copies
 * @param mute the copy
 * @param at when it was copied
 */
public record MuteInherited(PlayerRef target, PlayerRef from, MuteState mute, Instant at) implements ModerationEvent {

    public MuteInherited {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(mute, "mute");
        Objects.requireNonNull(at, "at");
    }
}
