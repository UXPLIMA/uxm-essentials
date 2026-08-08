package com.uxplima.uxmessentials.playerstate.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.application.port.PlaytimeRepository;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /playtime reset [player]}: wipe a player's tracked playtime, deleting every per-day row through the
 * {@link PlaytimeRepository}. The viewer resets their own ledger, or another player's once the adapter has gated
 * the target on the reset-others node. A confirmation is sent to the actor naming whose playtime was cleared.
 *
 * <p>{@code /playtime resetall}: the administrative companion that wipes every player's tracked ledger in one
 * call, gated by the same {@code uxmessentials.playtime.reset} node the per-player reset uses, confirming to the
 * actor that the whole ledger was cleared.
 *
 * <p>This clears only the DB-backed breakdown the sampler feeds; the vanilla play-one-minute statistic is owned by
 * the server and is intentionally left untouched, so {@code /playtime} still shows the lifetime continuity line.
 */
public final class ResetPlaytime {

    private final PlaytimeRepository repository;
    private final Notifier notifier;

    public ResetPlaytime(PlaytimeRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Reset {@code who}'s own tracked playtime. */
    public void reset(PlayerRef who) {
        resetFor(who, who);
    }

    /** Reset {@code subject}'s tracked playtime on behalf of {@code actor}, confirming to the actor. */
    public void resetFor(PlayerRef actor, PlayerRef subject) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(subject, "subject");
        repository.reset(subject.uuid());
        PlayerstateMessageKey key = actor.equals(subject)
                ? PlayerstateMessageKey.PLAYTIME_RESET
                : PlayerstateMessageKey.PLAYTIME_RESET_OTHER;
        notifier.send(actor, key, Map.of("player", subject.name()));
    }

    /** Wipe every player's tracked playtime, confirming the whole-ledger clear to {@code actor}. */
    public void resetAll(PlayerRef actor) {
        Objects.requireNonNull(actor, "actor");
        repository.resetAll();
        notifier.send(actor, PlayerstateMessageKey.PLAYTIME_RESET_ALL);
    }
}
