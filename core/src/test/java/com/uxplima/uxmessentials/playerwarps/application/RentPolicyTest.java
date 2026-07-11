package com.uxplima.uxmessentials.playerwarps.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.playerwarps.domain.RentDecision;
import com.uxplima.uxmessentials.playerwarps.domain.RentState;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Test;

class RentPolicyTest {

    private static final Instant NOW = Instant.parse("2026-07-10T12:00:00Z");
    private final RentPolicy policy = new RentPolicy();

    private static RentConfig enabled() {
        return new RentConfig(
                true,
                new BigDecimal("100"),
                "default",
                Duration.ofDays(7),
                Duration.ofDays(3),
                List.of(Duration.ofHours(24)),
                Set.of(),
                Set.of(),
                Set.of());
    }

    private static RentState paidThrough(Instant until) {
        return new RentState(until, Optional.empty(), Optional.empty());
    }

    private static RentState suspended(Instant archiveAfter) {
        return new RentState(
                NOW.minus(Duration.ofDays(1)), Optional.of(NOW.minus(Duration.ofDays(1))), Optional.of(archiveAfter));
    }

    @Test
    void aDisabledSubGroupNeverActs() {
        RentConfig off = RentConfig.disabled();
        assertThat(policy.decide(WarpStatus.ACTIVE, paidThrough(NOW.minus(Duration.ofDays(1))), NOW, off))
                .isEqualTo(RentDecision.NONE);
    }

    @Test
    void anActiveWarpPastItsTermIsDue() {
        assertThat(policy.decide(WarpStatus.ACTIVE, paidThrough(NOW.minusSeconds(1)), NOW, enabled()))
                .isEqualTo(RentDecision.DUE);
    }

    @Test
    void anActiveWarpAtTheExactBoundaryIsDue() {
        assertThat(policy.decide(WarpStatus.ACTIVE, paidThrough(NOW), NOW, enabled()))
                .isEqualTo(RentDecision.DUE);
    }

    @Test
    void anActiveWarpStillPaidThroughIsLeftAlone() {
        assertThat(policy.decide(WarpStatus.ACTIVE, paidThrough(NOW.plusSeconds(1)), NOW, enabled()))
                .isEqualTo(RentDecision.NONE);
    }

    @Test
    void aSuspendedWarpInsideGraceIsRetried() {
        assertThat(policy.decide(WarpStatus.SUSPENDED, suspended(NOW.plus(Duration.ofDays(1))), NOW, enabled()))
                .isEqualTo(RentDecision.RETRY);
    }

    @Test
    void aSuspendedWarpPastGraceIsArchived() {
        assertThat(policy.decide(WarpStatus.SUSPENDED, suspended(NOW.minusSeconds(1)), NOW, enabled()))
                .isEqualTo(RentDecision.ARCHIVE);
    }

    @Test
    void aSuspendedWarpAtItsArchiveInstantIsArchived() {
        assertThat(policy.decide(WarpStatus.SUSPENDED, suspended(NOW), NOW, enabled()))
                .isEqualTo(RentDecision.ARCHIVE);
    }

    @Test
    void anArchivedWarpIsInert() {
        assertThat(policy.decide(WarpStatus.ARCHIVED, paidThrough(NOW.minus(Duration.ofDays(9))), NOW, enabled()))
                .isEqualTo(RentDecision.NONE);
    }

    @Property
    void activeWarpsAreDueExactlyWhenTheTermHasLapsed(
            @ForAll @LongRange(min = -100_000, max = 100_000) long offsetSeconds) {
        Instant paidUntil = NOW.plusSeconds(offsetSeconds);
        RentDecision decision = policy.decide(WarpStatus.ACTIVE, paidThrough(paidUntil), NOW, enabled());
        boolean lapsed = !NOW.isBefore(paidUntil);
        assertThat(decision).isEqualTo(lapsed ? RentDecision.DUE : RentDecision.NONE);
    }

    @Property
    void suspendedWarpsArchiveExactlyWhenGraceHasLapsed(
            @ForAll @LongRange(min = -100_000, max = 100_000) long offsetSeconds) {
        Instant archiveAfter = NOW.plusSeconds(offsetSeconds);
        RentDecision decision = policy.decide(WarpStatus.SUSPENDED, suspended(archiveAfter), NOW, enabled());
        boolean archiveDue = !NOW.isBefore(archiveAfter);
        assertThat(decision).isEqualTo(archiveDue ? RentDecision.ARCHIVE : RentDecision.RETRY);
    }

    @Property
    void aDisabledConfigIsAlwaysInertRegardlessOfState(
            @ForAll @LongRange(min = -100_000, max = 100_000) long offsetSeconds) {
        Instant paidUntil = NOW.plusSeconds(offsetSeconds);
        assertThat(policy.decide(WarpStatus.ACTIVE, paidThrough(paidUntil), NOW, RentConfig.disabled()))
                .isEqualTo(RentDecision.NONE);
    }
}
