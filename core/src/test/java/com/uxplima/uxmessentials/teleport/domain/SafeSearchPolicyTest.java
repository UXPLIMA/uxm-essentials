package com.uxplima.uxmessentials.teleport.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/**
 * The pure safe-search verdict: a candidate must clear the radius annulus, the Y band, the excluded
 * biomes, the standing-safety read, the avoid-blocks list, and (when claim-aware) protected claims, in
 * that cheapest-first order.
 */
class SafeSearchPolicyTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final SafeSearchArea AREA = new SafeSearchArea(WORLD, 0.0, 0.0, 0.0, 1000.0, 1000.0);
    private static final Instant NOW = Instant.parse("2026-05-31T00:00:00Z");

    private static SafeCandidate candidate(double y, String biome, String landingBlock) {
        Position position = new Position(WORLD, 100.5, y, 100.5, 0f, 0f);
        return new SafeCandidate(position, BiomeName.of(biome), true, false, BlockTypeName.of(landingBlock));
    }

    @Test
    void aPlainGrassCandidateInBandIsAccepted() {
        SafeSearchPolicy policy = SafeSearchPolicy.permissive();

        Result<RtpSafeLocation, SafeRejection> result =
                policy.accept(AREA, candidate(70, "plains", "grass_block"), NOW);

        assertThat(result.isOk()).isTrue();
    }

    @Test
    void landingOnAnAvoidedBlockIsRejected() {
        SafeSearchPolicy policy =
                new SafeSearchPolicy(Set.of(), Set.of(BlockTypeName.of("lava")), YBand.unbounded(), false);

        Result<RtpSafeLocation, SafeRejection> result = policy.accept(AREA, candidate(70, "plains", "lava"), NOW);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(SafeRejection.AVOIDED_BLOCK);
    }

    @Test
    void aCandidateBelowTheYBandIsRejected() {
        SafeSearchPolicy policy = new SafeSearchPolicy(Set.of(), Set.of(), new YBand(60, 120), false);

        Result<RtpSafeLocation, SafeRejection> result =
                policy.accept(AREA, candidate(30, "plains", "grass_block"), NOW);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(SafeRejection.OUT_OF_Y_BAND);
    }

    @Test
    void aCandidateAboveTheYBandIsRejected() {
        SafeSearchPolicy policy = new SafeSearchPolicy(Set.of(), Set.of(), new YBand(60, 120), false);

        Result<RtpSafeLocation, SafeRejection> result =
                policy.accept(AREA, candidate(200, "plains", "grass_block"), NOW);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(SafeRejection.OUT_OF_Y_BAND);
    }

    @Test
    void aCandidateWithNoReadLandingBlockPassesTheAvoidCheck() {
        SafeSearchPolicy policy =
                new SafeSearchPolicy(Set.of(), Set.of(BlockTypeName.of("lava")), YBand.unbounded(), false);
        SafeCandidate noLanding =
                new SafeCandidate(new Position(WORLD, 100.5, 70.0, 100.5, 0f, 0f), BiomeName.of("plains"), true, false);

        assertThat(policy.accept(AREA, noLanding, NOW).isOk()).isTrue();
    }
}
