package com.uxplima.uxmessentials.playerstate.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerEffects;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /ext} (alias {@code /extinguish}) {@code [player]}: put out a burning player. A live-only effect
 * through the {@link PlayerEffects} port (no persisted flag, no domain event), then a confirmation to the
 * actor and, for a staff target, to the subject.
 */
public final class Extinguish {

    private final PlayerEffects effects;
    private final PlayerStateNotifier notifier;

    public Extinguish(PlayerEffects effects, PlayerStateNotifier notifier) {
        this.effects = Objects.requireNonNull(effects, "effects");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Extinguish {@code who} themselves. */
    public void extinguish(PlayerRef who) {
        extinguishFor(who, who);
    }

    /** Extinguish {@code subject} on behalf of {@code actor}. */
    public void extinguishFor(PlayerRef actor, PlayerRef subject) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(subject, "subject");
        effects.extinguish(subject);
        if (actor.equals(subject)) {
            notifier.send(actor, PlayerstateMessageKey.EXTINGUISHED);
            return;
        }
        notifier.send(actor, PlayerstateMessageKey.EXTINGUISHED_OTHER, Map.of("player", subject.name()));
        notifier.send(subject, PlayerstateMessageKey.EXTINGUISHED);
    }
}
