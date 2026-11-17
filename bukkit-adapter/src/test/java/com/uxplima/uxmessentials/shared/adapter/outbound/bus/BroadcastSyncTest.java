package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.network.BroadcastFrame;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.NetworkMessageCodec;
import org.junit.jupiter.api.Test;

/** A {@code /broadcast} sent on one backend is shown on another once, even when the bus delivers it twice. */
class BroadcastSyncTest {

    private final List<String> shownOnSurvival = new ArrayList<>();
    private final RemoteSyncRegistry survival = new RemoteSyncRegistry();
    private final List<NetworkMessage> wire = new ArrayList<>();

    @Test
    void aBroadcastCrossesTheWireAndIsShownOnceWhenItArrivesTwice() {
        BroadcastSync.register(survival, shownOnSurvival::add);
        Consumer<String> lobby = BroadcastSync.publisher(new Wire("lobby-1"));

        lobby.accept("<gold>Restart in five minutes</gold>");
        deliver();
        deliver();

        assertThat(shownOnSurvival).containsExactly("<gold>Restart in five minutes</gold>");
    }

    @Test
    void twoBroadcastsWithTheSameTextAreBothShown() {
        BroadcastSync.register(survival, shownOnSurvival::add);
        Consumer<String> lobby = BroadcastSync.publisher(new Wire("lobby-1"));

        lobby.accept("Vote now");
        lobby.accept("Vote now");
        deliver();

        assertThat(shownOnSurvival).containsExactly("Vote now", "Vote now");
    }

    @Test
    void theFrameKeepsItsTagAndText() {
        BroadcastFrame sent = new BroadcastFrame("lobby-1", java.util.UUID.randomUUID(), "Hello <b>all</b>");

        NetworkMessage read = NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent));

        assertThat(read).isEqualTo(sent);
        assertThat(read.type().wireTag()).isEqualTo((byte) 43);
    }

    /** Hands every frame on the wire to the survival backend, encoded and decoded as the transport would. */
    private void deliver() {
        for (NetworkMessage message : wire) {
            survival.dispatch(NetworkMessageCodec.decode(NetworkMessageCodec.encode(message)), (listener, failure) -> {
                throw new AssertionError(failure);
            });
        }
    }

    private final class Wire implements BusPublisher {

        private final String serverId;

        Wire(String serverId) {
            this.serverId = serverId;
        }

        @Override
        public void publish(NetworkMessage message) {
            wire.add(message);
        }

        @Override
        public String serverId() {
            return serverId;
        }
    }
}
