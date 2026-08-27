package com.uxplima.uxmessentials.worlds.adapter.inbound.listener;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.worlds.application.ResolveVoidRescue;
import com.uxplima.uxmessentials.worlds.application.port.WorldTeleporter;
import com.uxplima.uxmessentials.worlds.domain.WorldTeleportCause;
import org.jspecify.annotations.NullMarked;

/**
 * Catches players who fall out of a world that configures a void rescue, and sends them where that world's
 * chain says instead of letting the drop kill them.
 *
 * <p>Two triggers, because a lobby and an event world want different things. The void damage is the backstop
 * every armed world gets: the damage is cancelled and the player is moved, which works on any world floor
 * without configuring a height. A world that also sets {@code void-rescue-y} is caught earlier, on the move
 * itself, so the player never sees the fall bottom out.
 *
 * <p>Spectators fall through both triggers untouched (flying below the map is the point of the mode), as do
 * holders of the exempt node, which is how staff inspect a void without being yanked back.
 */
@NullMarked
public final class VoidRescueListener implements Listener {

    /** Holders keep falling: staff inspecting a world floor should not be teleported out of it. */
    public static final String EXEMPT_NODE = "uxmessentials.world.voidrescue.exempt";

    private final ResolveVoidRescue rescue;
    private final WorldTeleporter teleporter;
    private final Permissions permissions;

    public VoidRescueListener(ResolveVoidRescue rescue, WorldTeleporter teleporter, Permissions permissions) {
        this.rescue = Objects.requireNonNull(rescue, "rescue");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onVoidDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID || !(event.getEntity() instanceof Player player)) {
            return;
        }
        if (tryRescue(player, BukkitRefs.toRef(player.getWorld()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        // Hot path: only a descent that crosses a block boundary can cross the trigger line, and only then is
        // the per-world setting worth reading. Everything else leaves the event untouched.
        if (to.getBlockY() >= event.getFrom().getBlockY()) {
            return;
        }
        WorldRef world = BukkitRefs.toRef(to.getWorld());
        OptionalInt trigger = rescue.triggerY(world);
        if (trigger.isEmpty() || to.getBlockY() >= trigger.getAsInt()) {
            return;
        }
        tryRescue(event.getPlayer(), world);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        rescue.forget(BukkitRefs.toRef(event.getPlayer()));
    }

    private boolean tryRescue(Player player, WorldRef world) {
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        PlayerRef who = BukkitRefs.toRef(player);
        if (permissions.has(who, EXEMPT_NODE)) {
            return false;
        }
        Optional<Position> destination = rescue.rescue(who, world);
        return destination.isPresent() && teleporter.teleport(who, destination.get(), WorldTeleportCause.VOID_RESCUE);
    }
}
