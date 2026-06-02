package com.uxplima.uxmessentials.playerstate.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerEffects;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /clearinventory} (aliases {@code /ci}, {@code /clear}) {@code [player]}: empty a player's inventory. A
 * live-only effect through the {@link PlayerEffects} port (no persisted flag, no domain event), then a
 * confirmation to the actor and, for a staff target, to the subject.
 */
public final class ClearInventory {

    private final PlayerEffects effects;
    private final PlayerStateNotifier notifier;

    public ClearInventory(PlayerEffects effects, PlayerStateNotifier notifier) {
        this.effects = Objects.requireNonNull(effects, "effects");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Clear {@code who}'s own inventory. */
    public void clear(PlayerRef who) {
        clearFor(who, who);
    }

    /** Clear {@code subject}'s inventory on behalf of {@code actor}. */
    public void clearFor(PlayerRef actor, PlayerRef subject) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(subject, "subject");
        effects.clearInventory(subject);
        if (actor.equals(subject)) {
            notifier.send(actor, PlayerstateMessageKey.INVENTORY_CLEARED);
            return;
        }
        notifier.send(actor, PlayerstateMessageKey.INVENTORY_CLEARED_OTHER, Map.of("player", subject.name()));
        notifier.send(subject, PlayerstateMessageKey.INVENTORY_CLEARED);
    }
}
