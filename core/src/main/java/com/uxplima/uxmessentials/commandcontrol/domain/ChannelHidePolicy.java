package com.uxplima.uxmessentials.commandcontrol.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * The pure decision behind the plugin-channel hider: given the list of plugin-messaging channel names the server is
 * about to advertise to a client (the {@code minecraft:register} / {@code minecraft:unregister} channel list), keep only
 * the allowed channels and drop the rest. A client-side mod fingerprints installed plugins by the channels the server
 * registers, so stripping every channel not on the allow-list denies it that signal.
 *
 * <p>Built once from config, it holds the enable gate and the normalised allow-list; {@link #filter(List)} is a plain
 * function over a channel-name list, so the strip logic unit-tests exactly with no packet or connection in sight. The
 * adapter owns reading the channel names out of the outbound packet and writing the filtered list back - this type
 * never sees a live packet, which keeps the domain pure and lets the fragile packet plumbing be swapped without
 * touching the decision.
 *
 * <p>Channel names are compared case-insensitively and trimmed. A namespaced channel like {@code minecraft:brand} is
 * matched as a whole; the allow-list holds full channel identifiers, not bare labels.
 */
public final class ChannelHidePolicy {

    private final boolean enabled;
    private final Set<String> allowedChannels;

    private ChannelHidePolicy(boolean enabled, Set<String> allowedChannels) {
        this.enabled = enabled;
        this.allowedChannels = allowedChannels;
    }

    /**
     * Build a channel-hide policy, normalising every allowed channel to a lowercase, trimmed identifier.
     *
     * @param enabled whether the plugin-channel hider is switched on
     * @param allowedChannels the channel identifiers that may still be advertised to the client
     */
    public static ChannelHidePolicy of(boolean enabled, List<String> allowedChannels) {
        Objects.requireNonNull(allowedChannels, "allowedChannels");
        Set<String> allowed = new LinkedHashSet<>();
        for (String channel : allowedChannels) {
            if (channel != null && !channel.isBlank()) {
                allowed.add(normalize(channel));
            }
        }
        return new ChannelHidePolicy(enabled, Set.copyOf(allowed));
    }

    /**
     * Keep only the allowed channels of {@code channels}, in their original order. When the hider is off the list is
     * returned unchanged; otherwise every channel not on the allow-list is dropped (so the result may be empty, which
     * the adapter reads as "advertise nothing").
     */
    public List<String> filter(List<String> channels) {
        Objects.requireNonNull(channels, "channels");
        if (!enabled) {
            return List.copyOf(channels);
        }
        List<String> kept = new ArrayList<>(channels.size());
        for (String channel : channels) {
            if (channel != null && allowedChannels.contains(normalize(channel))) {
                kept.add(channel);
            }
        }
        return List.copyOf(kept);
    }

    /** True when {@code channel} is on the allow-list and so may still be advertised to the client. */
    public boolean allows(String channel) {
        Objects.requireNonNull(channel, "channel");
        return allowedChannels.contains(normalize(channel));
    }

    /** True when the hider is switched on - the adapter injects its packet interceptor only then. */
    public boolean isEnabled() {
        return enabled;
    }

    /** Lowercase and trim a channel identifier so the allow-list compares uniformly. */
    private static String normalize(String channel) {
        return channel.trim().toLowerCase(Locale.ROOT);
    }
}
