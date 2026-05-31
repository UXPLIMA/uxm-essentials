package com.uxplima.uxmessentials.playerstate.application;

import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerEffects;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /nightvision} (alias {@code /nv}): toggle a permanent night-vision effect on yourself. Self-only —
 * a client-side convenience, not a staff buff with a target form. The {@link PlayerEffects} port applies or
 * removes the effect on the player's owning region thread and reports the resulting on/off state, which this
 * use case turns into the matching confirmation.
 */
public final class ToggleNightVision {

    private final PlayerEffects effects;
    private final PlayerStateNotifier notifier;

    public ToggleNightVision(PlayerEffects effects, PlayerStateNotifier notifier) {
        this.effects = Objects.requireNonNull(effects, "effects");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Toggle night vision on {@code who}; returns the resulting on/off state. */
    public boolean toggle(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        boolean enabled = effects.toggleNightVision(who);
        notifier.send(who, enabled ? PlayerstateMessageKey.NIGHTVISION_ON : PlayerstateMessageKey.NIGHTVISION_OFF);
        return enabled;
    }
}
