package com.uxplima.uxmessentials.shared.network;

/**
 * The plugin-messaging channel the cross-server bus rides on. A single pure constant so the backend bus client (the
 * bukkit adapter, which registers it as an outgoing/incoming channel) and the proxy broker (the velocity adapter, which
 * registers it on the proxy and relays frames between backends) name the exact same channel, changing it on one side
 * without the other silences the bridge. The default is {@code uxmessentials:bus_v1}; an operator may override the
 * backend's side from {@code network.bus-channel} in {@code config.conf}, but the proxy registers this canonical
 * default.
 */
public final class BusChannel {

    /** The namespace segment of the channel key. */
    public static final String NAMESPACE = "uxmessentials";

    /** The name segment of the channel key; the {@code _v1} suffix is the wire-format generation. */
    public static final String NAME = "bus_v1";

    /** The full channel key {@code uxmessentials:bus_v1} as registered on both sides. */
    public static final String FULL = NAMESPACE + ":" + NAME;

    /**
     * The largest frame the plugin-messaging path may carry. The proxy relays a frame to the other backends as a
     * serverbound custom payload, and a backend disconnects the carrying player for one larger than this.
     */
    public static final int MAX_FRAME_BYTES = 32_767;

    private BusChannel() {}
}
