package com.uxplima.uxmessentials.vote.domain.event;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The accumulated vote count reached the configured threshold and a vote party fired, every eligible online
 * player received the party rewards and the counter was reset to zero.
 *
 * <p>{@code onlyVoters} and {@code absentVoters} tell other backends whom they may reward: everyone they have
 * online, or only the voters of this party window. The voters online here were already rewarded, so only the
 * absent ones are listed; the shared participant set is cleared as the party fires and cannot be read later.
 *
 * @param threshold the count at which the party fired
 * @param onlyVoters whether only the voters of the party window were eligible
 * @param absentVoters the eligible voters who were not online here; empty when {@code onlyVoters} is false
 */
public record VotePartyTriggered(int threshold, boolean onlyVoters, Set<UUID> absentVoters) implements VoteEvent {

    public VotePartyTriggered {
        Objects.requireNonNull(absentVoters, "absentVoters");
        if (threshold < 1) {
            throw new IllegalArgumentException("threshold must be at least one: " + threshold);
        }
        if (!onlyVoters && !absentVoters.isEmpty()) {
            throw new IllegalArgumentException("absent voters are listed only for a voters-only party");
        }
        absentVoters = Set.copyOf(absentVoters);
    }
}
