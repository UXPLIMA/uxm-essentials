package com.uxplima.uxmessentials.vanish.adapter.outbound;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.adapter.outbound.bus.BusPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.ServerPing;
import com.uxplima.uxmessentials.shared.network.VanishStateChanged;
import com.uxplima.uxmessentials.vanish.application.VanishSync;
import com.uxplima.uxmessentials.vanish.application.port.VanishBus;
import com.uxplima.uxmessentials.vanish.application.port.VanishBus.PeerRun;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Bridges the vanish context's {@link VanishBus} port to the shared cross-server bus. A local change or departure is
 * published as a {@link VanishStateChanged} frame through the {@link BusPublisher}; every inbound frame the bus
 * dispatches to {@link #onFrame} is translated back and handed to the subscribed listener on the bus's off-tick
 * dispatch thread, which does its own region hop for any Bukkit touch.
 *
 * <p>The heartbeats are read too and passed on with what they say about the sender's run, from its boot nonce: the
 * first heard, the same run, or a new run after a restart. Only the nonce is compared, never the sender's clock.
 *
 * <p>The publisher stamps this backend's {@code server-id} as the frame origin, so the shared loop sentinel drops the
 * echo on the origin backend. With the network bus disabled the injected publisher is the no-op one, so publishing is
 * silently discarded and {@link #healthy()} reports false.
 */
@NullMarked
public final class BusVanishBus implements VanishBus {

    private final BusPublisher publisher;
    private final boolean enabled;
    private final Map<String, Long> bootByPeer = new ConcurrentHashMap<>();
    private volatile @Nullable Listener listener;

    public BusVanishBus(BusPublisher publisher, boolean enabled) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.enabled = enabled;
    }

    @Override
    public void publish(VanishSync change) {
        Objects.requireNonNull(change, "change");
        publisher.publish(new VanishStateChanged(
                publisher.serverId(),
                change.player(),
                change.playerName(),
                change.vanished(),
                change.level().level()));
    }

    @Override
    public void publishDeparture(PlayerRef who, VanishLevel level) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(level, "level");
        publisher.publish(
                new VanishStateChanged(publisher.serverId(), who.uuid(), who.name(), true, level.level(), true));
    }

    @Override
    public void subscribe(Listener listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    @Override
    public boolean healthy() {
        return enabled;
    }

    /** Register this with the bus registry: translate a vanish frame or a heartbeat for the subscribed listener. */
    public void onFrame(NetworkMessage message) {
        Listener target = listener;
        if (target == null) {
            return;
        }
        switch (message) {
            case VanishStateChanged frame
            when frame.departed() -> target.departed(frame.originServer(), frame.player());
            case VanishStateChanged frame ->
                target.changed(
                        frame.originServer(),
                        new VanishSync(
                                frame.player(), frame.playerName(), frame.vanished(), VanishLevel.of(frame.level())));
            case ServerPing ping -> {
                Long previous = bootByPeer.put(ping.originServer(), ping.bootNonce());
                PeerRun run;
                if (previous == null) {
                    run = PeerRun.FIRST_HEARD;
                } else if (previous == ping.bootNonce()) {
                    run = PeerRun.SAME_RUN;
                } else {
                    run = PeerRun.NEW_RUN;
                }
                target.pinged(ping.originServer(), run);
            }
            default -> {
                // Every other frame belongs to another context.
            }
        }
    }
}
