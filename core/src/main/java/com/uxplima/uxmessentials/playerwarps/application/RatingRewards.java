package com.uxplima.uxmessentials.playerwarps.application;

import java.util.Objects;

import com.uxplima.uxmessentials.playerwarps.application.port.RatingRewardGranter;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpRatingRewardStore;

/**
 * The three collaborators the rate use case needs to grant a rating reward, bundled so {@link RatePlayerWarp} takes
 * a single {@code Optional<RatingRewards>} rather than three parallel optionals that must all be present or all
 * absent together. The wiring builds this bundle only when the {@code ratings.rewards} sub-group is enabled and
 * hands the use case {@code Optional.empty()} otherwise, so a disabled sub-group instantiates no store and no
 * granter, grants nothing, and writes no reward row.
 *
 * @param store the dedup ledger the use case checks and records grants through
 * @param granter the port that actually credits money / dispatches the command
 * @param config the sub-group tunables holding the rater and owner reward specs
 */
public record RatingRewards(WarpRatingRewardStore store, RatingRewardGranter granter, RatingRewardConfig config) {

    public RatingRewards {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(granter, "granter");
        Objects.requireNonNull(config, "config");
    }
}
