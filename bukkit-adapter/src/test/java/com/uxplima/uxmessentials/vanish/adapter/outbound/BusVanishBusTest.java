package com.uxplima.uxmessentials.vanish.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.adapter.outbound.bus.BusPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.NetworkMessageCodec;
import com.uxplima.uxmessentials.vanish.application.VanishSync;
import com.uxplima.uxmessentials.vanish.application.port.VanishBus;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import org.junit.jupiter.api.Test;

/** Every vanish frame a backend sends is numbered within its run, and a peer hears the number with the change. */
class BusVanishBusTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000a1e"), "Alex");
    private static final long RUN = 0x5eed_cafeL;

    private final List<NetworkMessage> published = new ArrayList<>();
    private final BusVanishBus survival = new BusVanishBus(
            new BusPublisher() {
                @Override
                public void publish(NetworkMessage message) {
                    published.add(message);
                }

                @Override
                public String serverId() {
                    return "survival";
                }
            },
            RUN,
            true);
    private final BusVanishBus lobby = new BusVanishBus(
            new BusPublisher() {
                @Override
                public void publish(NetworkMessage message) {}

                @Override
                public String serverId() {
                    return "lobby";
                }
            },
            1L,
            true);
    private final List<String> heard = new ArrayList<>();

    @Test
    void changesAndDeparturesShareOneGrowingSequence() {
        lobby.subscribe(new VanishBus.Listener() {
            @Override
            public void changed(String origin, long run, long sequence, VanishSync change) {
                heard.add(origin + " " + run + " #" + sequence + (change.vanished() ? " vanished" : " revealed"));
            }

            @Override
            public void departed(String origin, long run, long sequence, UUID player) {
                heard.add(origin + " " + run + " #" + sequence + " departed");
            }

            @Override
            public void pinged(String origin, long run, VanishBus.PeerRun kind) {}
        });

        survival.publish(VanishSync.vanished(ALEX, VanishLevel.DEFAULT));
        survival.publish(VanishSync.revealed(ALEX));
        survival.publishDeparture(ALEX, VanishLevel.DEFAULT);
        published.forEach(frame -> lobby.onFrame(NetworkMessageCodec.decode(NetworkMessageCodec.encode(frame))));

        assertThat(heard)
                .containsExactly(
                        "survival " + RUN + " #1 vanished",
                        "survival " + RUN + " #2 revealed",
                        "survival " + RUN + " #3 departed");
    }
}
