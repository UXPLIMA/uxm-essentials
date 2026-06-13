package com.uxplima.uxmessentials.vote.application;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.vote.domain.PartyResetSchedule;
import com.uxplima.uxmessentials.vote.domain.reward.RewardSpec;

/**
 * The full configuration of a vote party: what to give (a {@link RewardSpec}), how many votes
 * trigger it ({@code baseThreshold}), whether only voters who voted during the party window are
 * eligible ({@code onlyVoters}), how much the threshold grows after each party ({@code escalateBy}),
 * when the counter resets on a calendar boundary ({@code resetSchedule}), and at which intermediate
 * counts to broadcast an approach announcement ({@code announceAt}).
 *
 * <p>After each party fires the effective threshold rises by {@code escalateBy} votes (stored as a
 * threshold override in the repository); an operator can clear the override via admin commands to
 * return to the base threshold.
 *
 * @param reward         the reward spec applied to each eligible player when the party fires
 * @param baseThreshold  votes needed to trigger a party (must be at least 1); superseded by any
 *                       positive threshold override stored in the repository
 * @param onlyVoters     when {@code true} only players who voted during the current party window
 *                       receive rewards; when {@code false} every online player receives them
 * @param escalateBy     how many votes to add to the threshold after each party fires (0 = no
 *                       escalation)
 * @param resetSchedule  when the party counter resets on a calendar boundary
 * @param announceAt     intermediate counts at which a broadcast announces remaining votes needed;
 *                       empty = no mid-run announcements
 */
public record PartyConfig(
        RewardSpec reward,
        int baseThreshold,
        boolean onlyVoters,
        int escalateBy,
        PartyResetSchedule resetSchedule,
        List<Integer> announceAt) {

    public PartyConfig {
        Objects.requireNonNull(reward, "reward");
        Objects.requireNonNull(resetSchedule, "resetSchedule");
        Objects.requireNonNull(announceAt, "announceAt");
        if (baseThreshold < 1) {
            throw new IllegalArgumentException("baseThreshold must be at least one: " + baseThreshold);
        }
        if (escalateBy < 0) {
            throw new IllegalArgumentException("escalateBy must not be negative: " + escalateBy);
        }
        announceAt = List.copyOf(announceAt);
    }
}
