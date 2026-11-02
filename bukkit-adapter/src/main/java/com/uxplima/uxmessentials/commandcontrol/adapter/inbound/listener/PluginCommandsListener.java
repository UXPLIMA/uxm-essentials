package com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerLoadEvent;

import com.uxplima.uxmessentials.commandcontrol.adapter.CommandControlState;
import org.jspecify.annotations.NullMarked;

/**
 * Opens the {@code plugin:<name>} list entries again whenever the set of commands can have changed: once every plugin
 * has enabled, and each time a plugin is enabled or disabled while the server runs. uxmEssentials enables before many
 * of the plugins an entry names, so the entries opened at its own start miss their commands until then.
 */
@NullMarked
public final class PluginCommandsListener implements Listener {

    private final CommandControlState state;

    public PluginCommandsListener(CommandControlState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        state.refresh();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        state.refresh();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        state.refresh();
    }
}
