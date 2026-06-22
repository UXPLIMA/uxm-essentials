package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.network.HomeChanged;
import com.uxplima.uxmessentials.shared.network.ServerPing;
import org.junit.jupiter.api.Test;

/**
 * Window logic of {@link ClusterPeers}: a peer counts as live only while its last heartbeat is within the
 * liveness window, the roster keys on {@code server-id} so a restarting peer refreshes rather than duplicates,
 * and the listener records exactly the {@link ServerPing} frames and ignores everything else. Time is injected
 * as the {@code now} argument so the window boundaries are asserted deterministically with no real clock.
 */
class ClusterPeersTest {

    private static final Duration WINDOW = Duration.ofSeconds(90);
    private static final long T0 = 1_000_000L;

    @Test
    void aPeerWithinTheWindowCountsAsLive() {
        ClusterPeers peers = new ClusterPeers(WINDOW);
        peers.record("lobby-2", T0);

        assertThat(peers.livePeerCount(T0)).isEqualTo(1);
        assertThat(peers.livePeerCount(T0 + WINDOW.toMillis()))
                .as("exactly at the window edge is still live")
                .isEqualTo(1);
    }

    @Test
    void aPeerPastTheWindowAgesOut() {
        ClusterPeers peers = new ClusterPeers(WINDOW);
        peers.record("lobby-2", T0);

        assertThat(peers.livePeerCount(T0 + WINDOW.toMillis() + 1)).isZero();
    }

    @Test
    void countsEachDistinctPeerOnce() {
        ClusterPeers peers = new ClusterPeers(WINDOW);
        peers.record("lobby-2", T0);
        peers.record("survival-3", T0);

        assertThat(peers.livePeerCount(T0)).isEqualTo(2);
    }

    @Test
    void aRestartingPeerRefreshesItsEntryRatherThanDuplicating() {
        ClusterPeers peers = new ClusterPeers(WINDOW);
        peers.record("lobby-2", T0);
        peers.record("lobby-2", T0 + 5_000);

        assertThat(peers.livePeerCount(T0 + 5_000)).isEqualTo(1);
        assertThat(peers.lastActivity()).contains(T0 + 5_000);
    }

    @Test
    void anOlderLatePingDoesNotOverwriteAFresherLastSeen() {
        ClusterPeers peers = new ClusterPeers(WINDOW);
        peers.record("lobby-2", T0 + 5_000);
        // A stale ping arrives out of order; the fresher last-seen must win.
        peers.record("lobby-2", T0);

        assertThat(peers.lastActivity()).contains(T0 + 5_000);
    }

    @Test
    void lastActivityIsEmptyUntilAPeerIsHeardFrom() {
        assertThat(new ClusterPeers(WINDOW).lastActivity()).isEmpty();
    }

    @Test
    void lastActivityIsTheMostRecentAcrossAllPeers() {
        ClusterPeers peers = new ClusterPeers(WINDOW);
        peers.record("lobby-2", T0);
        peers.record("survival-3", T0 + 12_000);

        assertThat(peers.lastActivity()).contains(T0 + 12_000);
    }

    @Test
    void theListenerRecordsTheOriginAndTimestampOfAServerPing() {
        ClusterPeers peers = new ClusterPeers(WINDOW);

        peers.listener().onRemoteChange(new ServerPing("lobby-2", T0));

        assertThat(peers.livePeerCount(T0)).isEqualTo(1);
        assertThat(peers.lastActivity()).contains(T0);
    }

    @Test
    void theListenerIgnoresFramesThatAreNotServerPings() {
        ClusterPeers peers = new ClusterPeers(WINDOW);

        peers.listener()
                .onRemoteChange(new HomeChanged("lobby-2", UUID.fromString("00000000-0000-0000-0000-0000000000aa")));

        assertThat(peers.livePeerCount(T0)).isZero();
        assertThat(peers.lastActivity()).isEmpty();
    }
}
