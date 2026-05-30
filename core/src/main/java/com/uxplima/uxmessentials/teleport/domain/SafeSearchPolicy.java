package com.uxplima.uxmessentials.teleport.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.shared.domain.Result;

/**
 * The pure decision logic of the random-teleport safe-location search: given a world's {@link
 * SafeSearchArea}, its excluded biomes, and the claim-awareness flag, decide whether one {@link
 * SafeCandidate} the adapter validated off-thread is acceptable. This is the queue's <em>refill
 * primitive</em>'s verdict step — the random-point generation, async chunk load, biome read, and
 * safe-Y resolution all happen in the adapter; the policy only judges the resulting facts.
 *
 * <p>The policy is deliberately side-effect-free and clock-injected ({@link #accept} takes the
 * validation {@code Instant}) so it is trivially unit-testable and never touches Bukkit. The ordering
 * of the checks is cheapest-first: a coordinate out of bounds is rejected before the biome set is even
 * consulted.
 *
 * @param excludedBiomes biomes a candidate may not land in (lower-cased {@link BiomeName})
 * @param claimAware whether candidates inside protected claims are rejected
 */
public record SafeSearchPolicy(Set<BiomeName> excludedBiomes, boolean claimAware) {

    public SafeSearchPolicy {
        Objects.requireNonNull(excludedBiomes, "excludedBiomes");
        excludedBiomes = Set.copyOf(excludedBiomes);
    }

    /** A policy that excludes no biomes and ignores claims — the bare default. */
    public static SafeSearchPolicy permissive() {
        return new SafeSearchPolicy(Set.of(), false);
    }

    /**
     * Judge a validated candidate against {@code area}. On success the candidate becomes an {@link
     * RtpSafeLocation} stamped with {@code validatedAt}; on failure the {@link SafeRejection} explains
     * why, for refill diagnostics.
     */
    public Result<RtpSafeLocation, SafeRejection> accept(
            SafeSearchArea area, SafeCandidate candidate, Instant validatedAt) {
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(validatedAt, "validatedAt");
        Optional<SafeRejection> rejection = firstFailure(area, candidate);
        if (rejection.isPresent()) {
            return Result.err(rejection.get());
        }
        double radius = area.radiusOf(candidate.x(), candidate.z());
        return Result.ok(new RtpSafeLocation(candidate.position(), radius, validatedAt));
    }

    private Optional<SafeRejection> firstFailure(SafeSearchArea area, SafeCandidate candidate) {
        if (!area.contains(candidate.x(), candidate.z())) {
            return Optional.of(SafeRejection.OUT_OF_BOUNDS);
        }
        if (excludedBiomes.contains(candidate.biome())) {
            return Optional.of(SafeRejection.EXCLUDED_BIOME);
        }
        if (!candidate.standingSafe()) {
            return Optional.of(SafeRejection.UNSAFE_GROUND);
        }
        if (claimAware && candidate.insideClaim()) {
            return Optional.of(SafeRejection.INSIDE_CLAIM);
        }
        return Optional.empty();
    }

    /** True when {@code biome} is excluded — exposed for the cheap on-serve revalidation. */
    public boolean excludes(BiomeName biome) {
        return excludedBiomes.contains(Objects.requireNonNull(biome, "biome"));
    }
}
