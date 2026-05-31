package com.uxplima.uxmessentials.playerstate.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerEffects;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /rest [player]}: reset a player's time-since-rest statistic so the accumulated phantom-spawn pressure
 * clears and phantoms stop targeting them. A live-only effect through the {@link PlayerEffects} port
 * ({@code Statistic.TIME_SINCE_REST}). Config-gated: when the feature is disabled the actor is told and nothing
 * is changed. The actor is confirmed and, for a staff target, the subject is told too.
 */
public final class ResetRest {

    private final PlayerEffects effects;
    private final PlayerStateNotifier notifier;
    private final boolean enabled;

    public ResetRest(PlayerEffects effects, PlayerStateNotifier notifier, boolean enabled) {
        this.effects = Objects.requireNonNull(effects, "effects");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.enabled = enabled;
    }

    /** Reset {@code who}'s own time-since-rest statistic. */
    public void reset(PlayerRef who) {
        resetFor(who, who);
    }

    /** Reset {@code subject}'s time-since-rest statistic on behalf of {@code actor}. */
    public void resetFor(PlayerRef actor, PlayerRef subject) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(subject, "subject");
        if (!enabled) {
            notifier.send(actor, PlayerstateMessageKey.REST_DISABLED);
            return;
        }
        effects.resetRest(subject);
        if (actor.equals(subject)) {
            notifier.send(actor, PlayerstateMessageKey.REST_DONE);
            return;
        }
        notifier.send(actor, PlayerstateMessageKey.REST_DONE_OTHER, Map.of("player", subject.name()));
        notifier.send(subject, PlayerstateMessageKey.REST_DONE);
    }
}
