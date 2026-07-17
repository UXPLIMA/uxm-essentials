package com.uxplima.uxmessentials.servertweaks.adapter.outbound;

import org.bukkit.entity.Player;

/**
 * The seam through which the F3-brand join listener delivers the configured server brand to one player. The real
 * implementation ({@link PluginMessageBrandSender}) sends a {@code minecraft:brand} plugin message; a test binds a
 * recording fake so the listener's behaviour can be verified without the plugin-message plumbing.
 */
@FunctionalInterface
public interface ServerBrandSender {

    /** Send the configured server brand to {@code player} so it shows on the client's F3 debug screen. */
    void send(Player player);
}
