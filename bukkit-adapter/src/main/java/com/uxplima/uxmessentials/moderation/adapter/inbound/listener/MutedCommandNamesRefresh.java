package com.uxplima.uxmessentials.moderation.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerLoadEvent;

import com.uxplima.uxmessentials.moderation.application.MutedCommandPolicy;
import org.jspecify.annotations.NullMarked;

/**
 * Makes the muted-command policy read the names of its listed commands again whenever the server's command list can
 * have changed: once every plugin has loaded, and whenever a plugin is enabled or disabled while the server runs.
 */
@NullMarked
public final class MutedCommandNamesRefresh implements Listener {

    private final MutedCommandPolicy policy;

    public MutedCommandNamesRefresh(MutedCommandPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerLoad(ServerLoadEvent event) {
        policy.forgetCommandNames();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        policy.forgetCommandNames();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        policy.forgetCommandNames();
    }
}
