package com.uxplima.uxmessentials.vote.domain.reward;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * A milestone reward keyed off a player's all-time vote count. Exactly one of {@code at} (a one-shot
 * threshold paid the single time the count equals it) or {@code every} (a recurring threshold paid each
 * time the count is a positive multiple of it) is present; both being present or both absent is a config
 * error the constructor rejects. The present value must be strictly positive.
 *
 * @param at the one-shot all-time count to pay at, or empty when this is a recurring milestone
 * @param every the recurring step to pay on multiples of, or empty when this is a one-shot milestone
 * @param reward the spec to pay when the milestone matches
 */
public record MilestoneReward(OptionalLong at, OptionalLong every, RewardSpec reward) {

    public MilestoneReward {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(every, "every");
        Objects.requireNonNull(reward, "reward");
        if (at.isPresent() == every.isPresent()) {
            throw new IllegalArgumentException("exactly one of at/every must be present");
        }
        if (at.isPresent() && at.getAsLong() <= 0) {
            throw new IllegalArgumentException("at must be positive: " + at.getAsLong());
        }
        if (every.isPresent() && every.getAsLong() <= 0) {
            throw new IllegalArgumentException("every must be positive: " + every.getAsLong());
        }
    }

    /** True when this milestone fires at the given all-time count: equality for {@code at}, a positive multiple for {@code every}. */
    public boolean matches(long alltime) {
        return at.isPresent() ? alltime == at.getAsLong() : alltime > 0 && alltime % every.getAsLong() == 0;
    }
}
