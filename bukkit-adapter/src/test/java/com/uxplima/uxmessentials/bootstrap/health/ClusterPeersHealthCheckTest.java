package com.uxplima.uxmessentials.bootstrap.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import com.uxplima.uxmessentials.shared.adapter.outbound.bus.ClusterPeers;
import com.uxplima.uxmessentials.shared.application.health.HealthResult;
import com.uxplima.uxmessentials.shared.application.health.HealthStatus;
import org.junit.jupiter.api.Test;

/**
 * Pins the cluster-peer line {@code /uxmess doctor} renders from the {@link ClusterPeers} roster. The two
 * states an operator needs to tell apart are covered: no peers seen (a standalone backend) and N live peers
 * with a "last activity Ns ago" tail. The clock is injected so the "ago" figure is deterministic.
 */
class ClusterPeersHealthCheckTest {

    private static final Duration WINDOW = Duration.ofSeconds(90);
    private static final long NOW = 1_000_000L;

    @Test
    void withNoPeersReportsStandalone() {
        ClusterPeersHealthCheck check = new ClusterPeersHealthCheck(new ClusterPeers(WINDOW), () -> NOW);

        HealthResult result = check.check();

        assertThat(result.status()).isEqualTo(HealthStatus.OK);
        assertThat(result.message()).contains("standalone").contains("no peers");
    }

    @Test
    void withLivePeersReportsTheCountAndLastActivity() {
        ClusterPeers peers = new ClusterPeers(WINDOW);
        peers.record("lobby-2", NOW - 5_000);
        peers.record("survival-3", NOW - 3_000);
        ClusterPeersHealthCheck check = new ClusterPeersHealthCheck(peers, () -> NOW);

        HealthResult result = check.check();

        assertThat(result.status()).isEqualTo(HealthStatus.OK);
        // Two live peers, and the most recent activity was three seconds ago.
        assertThat(result.message()).contains("2 peer(s)").contains("3s ago");
    }

    @Test
    void peersThatHaveAgedOutOfTheWindowDoNotCount() {
        ClusterPeers peers = new ClusterPeers(WINDOW);
        peers.record("lobby-2", NOW - WINDOW.toMillis() - 1);
        ClusterPeersHealthCheck check = new ClusterPeersHealthCheck(peers, () -> NOW);

        HealthResult result = check.check();

        assertThat(result.message()).contains("standalone");
    }

    @Test
    void namesTheClusterSubsystem() {
        assertThat(new ClusterPeersHealthCheck(new ClusterPeers(WINDOW)).name()).isEqualTo("cluster-peers");
    }
}
