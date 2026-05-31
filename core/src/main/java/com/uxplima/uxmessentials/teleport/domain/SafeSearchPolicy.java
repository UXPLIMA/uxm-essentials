package com.uxplima.uxmessentials.teleport.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.shared.domain.Result;

/**
 * The pure decision logic of the random-teleport safe-location search: given a world's {@link
 * SafeSearchArea}, its excluded biomes, avoided landing blocks, the permitted Y band, and the
 * claim-awareness flag, decide whether one {@link SafeCandidate} the adapter validated off-thread is
 * acceptable. This is the queue's <em>refill primitive</em>'s verdict step — the random-point generation,
 * async chunk load, biome read, material read, and safe-Y resolution all happen in the adapter; the policy
 * only judges the resulting facts.
 *
 * <p>The policy is deliberately side-effect-free and clock-injected ({@link #accept} takes the
 * validation {@code Instant}) so it is trivially unit-testable and never touches Bukkit. The ordering
 * of the checks is cheapest-first: a coordinate out of bounds is rejected before the biome set is even
 * consulted.
 *
 * @param excludedBiomes biomes a candidate may not land in (lower-cased {@link BiomeName})
 * @param avoidBlocks materials a candidate may not land on (lower-cased {@link BlockTypeName})
 * @param yBand the vertical band a candidate's landing Y must fall within
 * @param claimAware whether candidates inside protected claims are rejected
 */
public record SafeSearchPolicy(
        Set<BiomeName> excludedBiomes, Set<BlockTypeName> avoidBlocks, YBand yBand, boolean claimAware) {

    public SafeSearchPolicy {
        Objects.requireNonNull(excludedBiomes, "excludedBiomes");
        Objects.requireNonNull(avoidBlocks, "avoidBlocks");
        Objects.requireNonNull(yBand, "yBand");
        excludedBiomes = Set.copyOf(excludedBiomes);
        avoidBlocks = Set.copyOf(avoidBlocks);
    }

    /** A policy with only excluded biomes and claim-awareness — no avoid-blocks, no Y clamp. */
    public SafeSearchPolicy(Set<BiomeName> excludedBiomes, boolean claimAware) {
        this(excludedBiomes, Set.of(), YBand.unbounded(), claimAware);
    }

    /** A policy that excludes no biomes, avoids no blocks, clamps no Y, and ignores claims. */
    public static SafeSearchPolicy permissive() {
        return new SafeSearchPolicy(Set.of(), Set.of(), YBand.unbounded(), false);
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
        if (!yBand.contains(candidate.y())) {
            return Optional.of(SafeRejection.OUT_OF_Y_BAND);
        }
        if (excludedBiomes.contains(candidate.biome())) {
            return Optional.of(SafeRejection.EXCLUDED_BIOME);
        }
        if (!candidate.standingSafe()) {
            return Optional.of(SafeRejection.UNSAFE_GROUND);
        }
        if (landsOnAvoidedBlock(candidate)) {
            return Optional.of(SafeRejection.AVOIDED_BLOCK);
        }
        if (claimAware && candidate.insideClaim()) {
            return Optional.of(SafeRejection.INSIDE_CLAIM);
        }
        return Optional.empty();
    }

    private boolean landsOnAvoidedBlock(SafeCandidate candidate) {
        return candidate.landing().map(avoidBlocks::contains).orElse(false);
    }

    /** True when {@code biome} is excluded — exposed for the cheap on-serve revalidation. */
    public boolean excludes(BiomeName biome) {
        return excludedBiomes.contains(Objects.requireNonNull(biome, "biome"));
    }
}
