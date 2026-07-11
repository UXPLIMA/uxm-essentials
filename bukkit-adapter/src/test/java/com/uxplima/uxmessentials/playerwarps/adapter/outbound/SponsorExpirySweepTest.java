package com.uxplima.uxmessentials.playerwarps.adapter.outbound;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.playerwarps.application.SponsorConfig;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.Sponsorship;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/**
 * The sponsor expiry sweep: a disabled sub-group schedules nothing, an enabled one arms the loop, and one pass frees
 * every lapsed slot while stamping the post-expiry cooldown computed from the term the warp was sponsored until.
 */
class SponsorExpirySweepTest {

    private static final Instant NOW = Instant.parse("2026-07-10T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration INTERVAL = Duration.ofMinutes(60);
    private static final WorldRef WORLD = new WorldRef(new UUID(9L, 9L), "world");
    private static final PlayerRef OWNER = new PlayerRef(new UUID(1L, 1L), "mara");

    private final PlayerWarpRepository repo = mock(PlayerWarpRepository.class);

    private static SponsorConfig config(boolean enabled, int cooldownDays) {
        return new SponsorConfig(enabled, 5, 7, new BigDecimal("1000"), "default", 1, Duration.ofDays(cooldownDays));
    }

    private SponsorExpirySweep sweep(SponsorConfig config, Scheduler scheduler) {
        return new SponsorExpirySweep(repo, config, scheduler, INTERVAL, CLOCK, mock(Logger.class));
    }

    private static PlayerWarp warp(long id, String name, Instant until, int slot) {
        return PlayerWarp.create(OWNER, "mara", PlayerWarpName.of(name), Position.of(WORLD, 0, 64, 0), NOW)
                .withId(PlayerWarpId.of(id))
                .withSponsorship(Optional.of(new Sponsorship(until, slot)), NOW);
    }

    @Test
    void aDisabledSubGroupSchedulesNothing() {
        Scheduler scheduler = mock(Scheduler.class);

        sweep(config(false, 3), scheduler).start();

        verify(scheduler, never()).asyncAfter(any(), any());
    }

    @Test
    void anEnabledSubGroupSchedulesTheLoop() {
        Scheduler scheduler = mock(Scheduler.class);

        sweep(config(true, 3), scheduler).start();

        verify(scheduler).asyncAfter(eq(INTERVAL), any());
    }

    @Test
    void oneSweepFreesEachLapsedSlotAndStampsTheCooldown() {
        Instant lapsedAt = NOW.minusSeconds(60);
        when(repo.expiredSponsorships(any(), anyInt())).thenReturn(List.of(warp(1L, "citadel", lapsedAt, 2)));

        sweep(config(true, 3), mock(Scheduler.class)).sweepOnce();

        // The cooldown runs from the term the warp was sponsored until, not from the sweep's wall clock.
        verify(repo).expireSponsorship(PlayerWarpId.of(1L), lapsedAt.plus(Duration.ofDays(3)));
    }
}
