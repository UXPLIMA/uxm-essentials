package com.uxplima.uxmessentials.playerstate.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerEffects;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /nightvision [player]} (alias {@code /nv}): toggle a permanent night-vision effect. The
 * {@link PlayerEffects} port applies or removes the effect on the subject's owning region thread and reports the
 * resulting on/off state, which this use case turns into the matching confirmation. The target form follows the other
 * playerstate verbs: the actor is confirmed and, for a staff target, the subject is told too.
 */
public final class ToggleNightVision {

    private final PlayerEffects effects;
    private final Notifier notifier;

    public ToggleNightVision(PlayerEffects effects, Notifier notifier) {
        this.effects = Objects.requireNonNull(effects, "effects");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Toggle night vision on {@code who}; returns the resulting on/off state. */
    public boolean toggle(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return toggleFor(who, who);
    }

    /** Toggle {@code subject}'s night vision on behalf of {@code actor}; returns the resulting on/off state. */
    public boolean toggleFor(PlayerRef actor, PlayerRef subject) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(subject, "subject");
        boolean enabled = effects.toggleNightVision(subject);
        if (actor.equals(subject)) {
            notifier.send(
                    actor, enabled ? PlayerstateMessageKey.NIGHTVISION_ON : PlayerstateMessageKey.NIGHTVISION_OFF);
            return enabled;
        }
        notifier.send(
                actor,
                enabled ? PlayerstateMessageKey.NIGHTVISION_ON_OTHER : PlayerstateMessageKey.NIGHTVISION_OFF_OTHER,
                Map.of("player", subject.name()));
        notifier.send(subject, enabled ? PlayerstateMessageKey.NIGHTVISION_ON : PlayerstateMessageKey.NIGHTVISION_OFF);
        return enabled;
    }
}
