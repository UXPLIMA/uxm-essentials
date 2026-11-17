package com.uxplima.uxmessentials.moderation.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Tells the {@link ExemptionWatch} when LuckPerms recalculates a player's permissions, which is how a grant or a
 * removal made while they are online shows. Bound only once LuckPerms is known to be there.
 */
@NullMarked
public final class LuckPermsExemptionChanges {

    private LuckPermsExemptionChanges() {}

    /** Subscribe {@code watch} to LuckPerms' recalculations for as long as {@code plugin} is enabled. */
    public static void watch(Plugin plugin, Server server, Scheduler scheduler, ExemptionWatch watch) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(watch, "watch");
        var unused = LuckPermsProvider.get().getEventBus().subscribe(plugin, UserDataRecalculateEvent.class, event -> {
            PlayerRef who = new PlayerRef(
                    event.getUser().getUniqueId(),
                    Objects.requireNonNullElse(event.getUser().getUsername(), "?"));
            // LuckPerms calls on its own thread; the node is read on the player's. Nothing runs for a
            // player who is not online here.
            scheduler.onEntity(who, () -> {
                Player player = server.getPlayer(who.uuid());
                if (player != null && player.isOnline()) {
                    watch.changed(player);
                }
            });
        });
    }
}
