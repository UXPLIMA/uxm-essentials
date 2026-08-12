package com.uxplima.uxmessentials.playerstate.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerEffects;
import com.uxplima.uxmessentials.playerstate.domain.GlowColor;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /glow [colour] [player]}: the glowing outline, its colour, and who wears it. Bare {@code /glow} toggles the
 * outline; naming a colour turns it on and draws it in that colour, which is idempotent so a second call only
 * re-colours. The {@link PlayerEffects} port applies the change on the subject's owning region thread and reports the
 * resulting on/off state, which this use case turns into the matching confirmation. As with the other target verbs the
 * actor is confirmed and, for a staff target, the subject is told too.
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
        return toggleFor(who, who);
    }

    /** Toggle {@code subject}'s outline on behalf of {@code actor}; returns the resulting on/off state. */
    public boolean toggleFor(PlayerRef actor, PlayerRef subject) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(subject, "subject");
        boolean enabled = effects.toggleGlow(subject);
        if (actor.equals(subject)) {
            notifier.send(actor, enabled ? PlayerstateMessageKey.GLOW_ON : PlayerstateMessageKey.GLOW_OFF);
            return enabled;
        }
        notifier.send(
                actor,
                enabled ? PlayerstateMessageKey.GLOW_ON_OTHER : PlayerstateMessageKey.GLOW_OFF_OTHER,
                Map.of("player", subject.name()));
        notifier.send(subject, enabled ? PlayerstateMessageKey.GLOW_ON : PlayerstateMessageKey.GLOW_OFF);
        return enabled;
    }

    /** Turn {@code subject}'s outline on in {@code colour} on behalf of {@code actor}. */
    public void colourFor(PlayerRef actor, PlayerRef subject, GlowColor colour) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(colour, "colour");
        effects.setGlow(subject, colour);
        String name = colour.id();
        if (actor.equals(subject)) {
            notifier.send(actor, PlayerstateMessageKey.GLOW_COLOR, Map.of("color", name));
            return;
        }
        notifier.send(actor, PlayerstateMessageKey.GLOW_COLOR_OTHER, Map.of("color", name, "player", subject.name()));
        notifier.send(subject, PlayerstateMessageKey.GLOW_COLOR, Map.of("color", name));
    }
}
