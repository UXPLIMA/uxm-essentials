package com.uxplima.uxmessentials.vote.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.VotePartyCounter;

/**
 * Serves the {@code /voteparty} display: the current accumulated count against the configured threshold,
 * and the number of votes still needed. Reads the durable counter through the repository and frames it
 * with the {@code VOTEPARTY_STATUS} key; the threshold is the module's configured party threshold.
 */
public final class VotePartyStatus {

    private final VoteRepository repository;
    private final VoteNotifier notifier;
    private final int threshold;

    public VotePartyStatus(VoteRepository repository, VoteNotifier notifier, int threshold) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        if (threshold < 1) {
            throw new IllegalArgumentException("threshold must be at least one: " + threshold);
        }
        this.threshold = threshold;
    }

    /** Show the current vote-party progress (count, threshold, remaining) to {@code viewer}. */
    public void show(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        VotePartyCounter counter = new VotePartyCounter(Math.min(repository.partyCount(), threshold), threshold);
        int remaining = Math.max(0, threshold - counter.count());
        notifier.send(
                viewer,
                VoteMessageKey.VOTEPARTY_STATUS,
                Map.of(
                        "count", Integer.toString(counter.count()),
                        "threshold", Integer.toString(threshold),
                        "remaining", Integer.toString(remaining)));
    }
}
