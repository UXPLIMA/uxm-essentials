package com.uxplima.uxmessentials.vote.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;

/**
 * Admin use case: set the global vote-party counter to a specific value. Does not fire a party even
 * if the new count exceeds the threshold — use {@link AddPartyCount} when you want threshold-checking.
 */
public final class SetPartyCount {

    private final VoteRepository repository;
    private final VoteNotifier notifier;

    public SetPartyCount(VoteRepository repository, VoteNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /**
     * Set the party counter to {@code count} and notify {@code actor}.
     *
     * @param actor the admin running the command (receives confirmation)
     * @param count the new counter value (must not be negative)
     */
    public void set(PlayerRef actor, int count) {
        Objects.requireNonNull(actor, "actor");
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative: " + count);
        }
        repository.setPartyCount(count);
        notifier.send(actor, VoteMessageKey.VOTEPARTY_COUNT_SET, Map.of("count", Integer.toString(count)));
    }
}
