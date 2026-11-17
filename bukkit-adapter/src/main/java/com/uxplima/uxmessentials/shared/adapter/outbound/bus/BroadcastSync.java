package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.application.RecentIds;
import com.uxplima.uxmessentials.shared.network.BroadcastFrame;
import org.jspecify.annotations.NullMarked;

/**
 * Carries an operator's {@code /broadcast} to the other backends of the network: {@link #publisher} sends the text as
 * a {@link BroadcastFrame}, and {@link #register} shows one that arrives to the players here, once even when the bus
 * delivers it twice. With the network off nothing is sent and nothing arrives.
 */
@NullMarked
public final class BroadcastSync {

    private BroadcastSync() {}

    /** Sends a broadcast's text to the other backends. */
    public static Consumer<String> publisher(BusPublisher bus) {
        Objects.requireNonNull(bus, "bus");
        return body -> bus.publish(new BroadcastFrame(bus.serverId(), UUID.randomUUID(), body));
    }

    /** Shows each broadcast another backend sent through {@code showHere}, the same path one sent here takes. */
    public static void register(RemoteSyncRegistry registry, Consumer<String> showHere) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(showHere, "showHere");
        RecentIds seen = new RecentIds();
        registry.register(message -> {
            if (message instanceof BroadcastFrame broadcast && seen.firstTime(broadcast.broadcastId())) {
                showHere.accept(broadcast.body());
            }
        });
    }
}
