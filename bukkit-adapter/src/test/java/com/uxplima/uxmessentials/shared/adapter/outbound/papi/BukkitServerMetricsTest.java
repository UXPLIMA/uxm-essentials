package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.OptionalInt;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Covers {@link BukkitServerMetrics}: the seam reads the live online roster, slot count, version and per-world
 * population off MockBukkit, measures uptime against the captured enable instant with a fixed clock, and the heap
 * figures are non-negative megabyte reads off the JVM.
 */
class BukkitServerMetricsTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void readsTheLiveOnlineRosterMaxAndVersion() {
        server.addSimpleWorld("world");
        server.addPlayer();
        server.addPlayer();
        BukkitServerMetrics metrics = new BukkitServerMetrics(server, Instant.now());

        assertThat(metrics.onlinePlayers()).isEqualTo(2);
        assertThat(metrics.maxPlayers()).isEqualTo(server.getMaxPlayers());
        assertThat(metrics.minecraftVersion()).isEqualTo(server.getMinecraftVersion());
    }

    @Test
    void uptimeIsMeasuredAgainstTheEnableInstant() {
        Instant enabledAt = Instant.parse("2026-01-01T00:00:00Z");
        Clock now = Clock.fixed(enabledAt.plus(Duration.ofMinutes(90)), ZoneOffset.UTC);
        BukkitServerMetrics metrics = new BukkitServerMetrics(server, enabledAt, now);

        assertThat(metrics.uptime()).isEqualTo(Duration.ofMinutes(90));
    }

    @Test
    void uptimeNeverGoesNegativeWhenTheClockTrailsEnable() {
        Instant enabledAt = Instant.parse("2026-01-01T00:00:00Z");
        Clock before = Clock.fixed(enabledAt.minus(Duration.ofSeconds(5)), ZoneOffset.UTC);
        BukkitServerMetrics metrics = new BukkitServerMetrics(server, enabledAt, before);

        assertThat(metrics.uptime()).isEqualTo(Duration.ZERO);
    }

    @Test
    void heapReadsAreNonNegativeMegabytes() {
        BukkitServerMetrics metrics = new BukkitServerMetrics(server, Instant.now());

        assertThat(metrics.ramUsedMb()).isGreaterThanOrEqualTo(0);
        assertThat(metrics.ramMaxMb()).isGreaterThan(0);
        assertThat(metrics.ramFreeMb()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void worldPlayersCountsTheNamedWorldAndEmptiesAnUnknownOne() {
        server.addSimpleWorld("world");
        server.addPlayer();
        BukkitServerMetrics metrics = new BukkitServerMetrics(server, Instant.now());

        assertThat(metrics.worldPlayers("world")).isEqualTo(OptionalInt.of(1));
        assertThat(metrics.worldPlayers("does_not_exist")).isEqualTo(OptionalInt.empty());
    }
}
