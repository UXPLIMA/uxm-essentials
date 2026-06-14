package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.BanChanged;
import com.uxplima.uxmessentials.shared.network.HomeChanged;
import com.uxplima.uxmessentials.shared.network.MuteChanged;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage of the moderation context's cross-server seam, both halves in isolation against fakes (no
 * Bukkit): the outbound publisher (a successful ban/mute becomes the matching origin-stamped frame) and the
 * inbound listener (a remote {@code BanChanged} drives the re-enforcement action; a {@code MuteChanged} and any
 * unrelated frame are no-ops). The kick logic itself is covered by {@code EnforceRemoteBanTest} in :core; here
 * the listener's job is only to route the target UUID to that action.
 */
final class ModerationSyncTest {

    private static final String SERVER_ID = "survival-1";
    private static final PlayerRef TARGET = new PlayerRef(UUID.randomUUID(), "griefer");

    @Test
    void banChangedPublishesAnOriginStampedBanFrame() {
        RecordingPublisher publisher = new RecordingPublisher();

        ModerationSync.publisher(publisher).banChanged(TARGET);

        assertThat(publisher.published).containsExactly(new BanChanged(SERVER_ID, TARGET.uuid()));
    }

    @Test
    void muteChangedPublishesAnOriginStampedMuteFrame() {
        RecordingPublisher publisher = new RecordingPublisher();

        ModerationSync.publisher(publisher).muteChanged(TARGET);

        assertThat(publisher.published).containsExactly(new MuteChanged(SERVER_ID, TARGET.uuid()));
    }

    @Test
    void listenerDrivesTheReEnforcementActionForARemoteBan() {
        List<UUID> reEnforced = new ArrayList<>();
        RemoteSyncListener listener = ModerationSync.listener(reEnforced::add);

        listener.onRemoteChange(new BanChanged("lobby-2", TARGET.uuid()));

        assertThat(reEnforced).containsExactly(TARGET.uuid());
    }

    @Test
    void listenerIgnoresAMuteFrame() {
        List<UUID> reEnforced = new ArrayList<>();
        RemoteSyncListener listener = ModerationSync.listener(reEnforced::add);

        listener.onRemoteChange(new MuteChanged("lobby-2", TARGET.uuid()));

        assertThat(reEnforced).isEmpty();
    }

    @Test
    void listenerIgnoresAnUnrelatedFrame() {
        List<UUID> reEnforced = new ArrayList<>();
        RemoteSyncListener listener = ModerationSync.listener(reEnforced::add);

        listener.onRemoteChange(new HomeChanged("lobby-2", UUID.randomUUID()));

        assertThat(reEnforced).isEmpty();
    }

    /** A {@link BusPublisher} that records every published frame and stamps a fixed origin. */
    private static final class RecordingPublisher implements BusPublisher {
        final List<NetworkMessage> published = new ArrayList<>();

        @Override
        public void publish(NetworkMessage message) {
            published.add(message);
        }

        @Override
        public String serverId() {
            return SERVER_ID;
        }
    }
}
