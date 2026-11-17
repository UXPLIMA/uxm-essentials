package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.Objects;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.network.BusTransport;
import org.jspecify.annotations.NullMarked;

/**
 * The proxy plugin's word on a backend whose bus runs over Redis alone. The proxy plugin tells a backend how a player
 * reached it, whether they are a Bedrock player, and their answer to a form, over that player's own connection, on the
 * plugin-messaging channel, whatever carries the bus between backends. This listens on that channel for those frames
 * only: they are checked against the network secret and
 * receipted with their carrier exactly as on the Velocity carrier, and nothing is ever sent on it.
 */
@NullMarked
final class ProxyWordTransport implements BusTransport {

    private final PluginMessagingTransport channel;

    ProxyWordTransport(PluginMessagingTransport channel) {
        this.channel = Objects.requireNonNull(channel, "channel");
    }

    @Override
    public void start(Consumer<byte[]> onFrame) {
        Objects.requireNonNull(onFrame, "onFrame");
        channel.start(frame -> {
            if (PluginMessagingTransport.fromProxyPlugin(frame)) {
                onFrame.accept(frame);
            }
        });
    }

    @Override
    public void stop() {
        channel.stop();
    }

    /** Backend frames travel over Redis; this channel only listens. */
    @Override
    public void send(byte[] frame) {
        Objects.requireNonNull(frame, "frame");
    }

    /** It reaches no peer, so it never vouches for the bus. */
    @Override
    public boolean healthy() {
        return false;
    }
}
