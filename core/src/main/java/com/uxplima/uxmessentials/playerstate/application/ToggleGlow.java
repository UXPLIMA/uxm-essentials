package com.uxplima.uxmessentials.playerstate.application;

import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerEffects;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /glow}: toggle a permanent glowing outline on yourself. Self-only — a client-side convenience, not a
 * staff buff with a target form. The {@link PlayerEffects} port applies or removes the outline on the player's
 * owning region thread and reports the resulting on/off state, which this use case turns into the matching
 * confirmation.
 */
public final class ToggleGlow {

    private final PlayerEffects effects;
    private final Notifier notifier;

    public ToggleGlow(PlayerEffects effects, Notifier notifier) {
        this.effects = Objects.requireNonNull(effects, "effects");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Toggle the glowing outline on {@code who}; returns the resulting on/off state. */
    public boolean toggle(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        boolean enabled = effects.toggleGlow(who);
        notifier.send(who, enabled ? PlayerstateMessageKey.GLOW_ON : PlayerstateMessageKey.GLOW_OFF);
        return enabled;
    }
}
