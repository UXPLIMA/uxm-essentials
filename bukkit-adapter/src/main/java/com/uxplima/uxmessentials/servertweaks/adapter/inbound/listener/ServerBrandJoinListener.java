package com.uxplima.uxmessentials.servertweaks.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.servertweaks.adapter.outbound.ServerBrandSender;
import org.jspecify.annotations.NullMarked;

/**
 * Re-sends the configured server brand to each player as they join, so the F3 debug screen shows the custom brand
 * rather than the one the server announced during the login/configuration phase. The tweak is gated by the
 * {@code enabled} flag the wiring resolves from {@code f3-brand.enabled}: when the tweak is off the listener is a
 * no-op, sending nothing.
 *
 * <p>The join event already fires on the joining player's own thread, so the send happens where it is safe; the
 * actual delivery is delegated to a {@link ServerBrandSender} seam, keeping the plugin-message mechanics out of the
 * listener and letting a test verify the send with a recording fake.
 */
@NullMarked
public final class ServerBrandJoinListener implements Listener {

    private final boolean enabled;
    private final ServerBrandSender sender;

    public ServerBrandJoinListener(boolean enabled, ServerBrandSender sender) {
        this.enabled = enabled;
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (enabled) {
            sender.send(event.getPlayer());
        }
    }
}
