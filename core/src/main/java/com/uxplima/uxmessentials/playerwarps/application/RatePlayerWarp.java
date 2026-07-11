package com.uxplima.uxmessentials.playerwarps.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpRatingStore;
import com.uxplima.uxmessentials.playerwarps.domain.BayesianRating;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RatingSummary;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /pwarp rate <name> <1-5>}: any viewer who can see a warp awards it a star rating, driving the Bayesian
 * {@code rating_score} the "top rated" browse sorts on. A star outside 1..5 is rejected
 * ({@link PlayerWarpError#RATING_INVALID}); a missing warp is {@link PlayerWarpError#NOT_FOUND}; the owner may not
 * rate their own warp ({@link PlayerWarpError#CANNOT_RATE_OWN}) — self-rating from your own account is the cheapest
 * score-boost, so blocking the owner closes the obvious hole and the Bayesian smoothing blunts the rest.
 *
 * <p>A valid vote upserts the rater's star, then recomputes the denormalised rollup from the store's tally and global
 * mean through {@link BayesianRating} and writes it back in one guarded UPDATE, so the sort column never drifts from
 * the vote rows.
 */
public final class RatePlayerWarp {

    private final PlayerWarpRepository repository;
    private final WarpRatingStore ratings;
    private final PlayerWarpNotifier notifier;
    private final BayesianRating scoring;
    private final Clock clock;

    public RatePlayerWarp(
            PlayerWarpRepository repository,
            WarpRatingStore ratings,
            PlayerWarpNotifier notifier,
            BayesianRating scoring,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.ratings = Objects.requireNonNull(ratings, "ratings");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scoring = Objects.requireNonNull(scoring, "scoring");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Record {@code actor}'s {@code stars} on warp {@code name}, or reject an invalid star, missing warp, or self-rate. */
    public Result<Unit, PlayerWarpError> rate(PlayerRef actor, PlayerWarpName name, int stars) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        if (stars < 1 || stars > 5) {
            notifier.send(actor, PlayerWarpError.RATING_INVALID.messageKey(), Map.of("warp", name.value()));
            return Result.err(PlayerWarpError.RATING_INVALID);
        }
        Optional<PlayerWarp> found = repository.findByName(name);
        if (found.isEmpty()) {
            notifier.send(actor, PlayerWarpError.NOT_FOUND.messageKey(), Map.of("warp", name.value()));
            return Result.err(PlayerWarpError.NOT_FOUND);
        }
        PlayerWarp warp = found.get();
        if (warp.owner().uuid().equals(actor.uuid())) {
            notifier.send(actor, PlayerWarpError.CANNOT_RATE_OWN.messageKey(), Map.of("warp", name.value()));
            return Result.err(PlayerWarpError.CANNOT_RATE_OWN);
        }
        PlayerWarpId id = warp.id().orElseThrow();
        ratings.put(id, actor.uuid(), stars, clock.instant());
        RatingSummary rollup = scoring.summarise(ratings.tally(id), ratings.globalMean());
        repository.updateRating(id, rollup);
        notifier.send(
                actor,
                PlayerwarpsMessageKey.PWARP_RATED,
                Map.of("warp", name.value(), "rating", Integer.toString(stars)));
        return Result.ok();
    }
}
