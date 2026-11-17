package com.uxplima.uxmessentials.shared.adapter.inbound.networkpresence;

import java.util.Objects;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.networkpresence.TrackNetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Reads a player's presence traits again when LuckPerms recalculates their permissions, which is how a staff node
 * granted or taken while they are online shows, so the other backends list them as staff, or no longer, at once.
 * Without LuckPerms the presence heartbeat reads every trait again. Bound only once LuckPerms is known to be there.
 */
@NullMarked
public final class LuckPermsPresenceTraits {

    private LuckPermsPresenceTraits() {}

    /** Subscribe {@code track} to LuckPerms' recalculations for as long as {@code plugin} is enabled. */
    public static void watch(Plugin plugin, Scheduler scheduler, TrackNetworkPresence track) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(track, "track");
        var unused = LuckPermsProvider.get().getEventBus().subscribe(plugin, UserDataRecalculateEvent.class, event -> {
            PlayerRef who = new PlayerRef(
                    event.getUser().getUniqueId(),
                    Objects.requireNonNullElse(event.getUser().getUsername(), "?"));
            // LuckPerms calls on its own thread; the node is read on the player's. Nothing runs for a player who is
            // not on this backend.
            scheduler.onEntity(who, () -> track.retrait(who.uuid()));
        });
    }
}
