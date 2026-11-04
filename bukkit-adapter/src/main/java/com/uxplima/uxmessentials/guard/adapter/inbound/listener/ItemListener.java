package com.uxplima.uxmessentials.guard.adapter.inbound.listener;

import com.uxplima.uxmessentials.guard.adapter.ActionKinds;
import com.uxplima.uxmessentials.guard.adapter.Causes;
import com.uxplima.uxmessentials.guard.adapter.GuardRuntime;
import com.uxplima.uxmessentials.guard.domain.BuiltInActions;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrowableProjectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;

/**
 * Dropping, picking up, throwing and eating items. A thrown item is checked where it is thrown from; a denied
 * ender pearl, wind charge or potion stays in the hand.
 */
public final class ItemListener implements Listener {

    private final GuardRuntime guard;

    public ItemListener(GuardRuntime guard) {
        this.guard = Objects.requireNonNull(guard, "guard");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!guard.allow(event.getPlayer(), BuiltInActions.ITEM_DROP, event.getPlayer().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && !guard.allow(player, BuiltInActions.ITEM_PICKUP, player.getWorld(), (int) Math.floor(player.getX()),
                        (int) Math.floor(player.getY()), (int) Math.floor(player.getZ()), false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        Player shooter = Causes.player(projectile);
        if (shooter == null) {
            return;
        }
        Material item = material(projectile);
        if (item == null) {
            return;
        }
        if (!guard.allowItem(shooter, ActionKinds.item(item), shooter.getLocation(), item.name())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Material item = event.getItem().getType();
        if (item == Material.CHORUS_FRUIT
                && !guard.allowItem(event.getPlayer(), BuiltInActions.ITEM_USE_CHORUS_FRUIT,
                        event.getPlayer().getLocation(), item.name())) {
            event.setCancelled(true);
        }
    }

    private static @org.jspecify.annotations.Nullable Material material(Projectile projectile) {
        if (projectile instanceof ThrownPotion potion) {
            return potion.getItem().getType();
        }
        if (projectile instanceof ThrowableProjectile throwable) {
            return throwable.getItem().getType();
        }
        return switch (projectile.getType()) {
            case WIND_CHARGE -> Material.WIND_CHARGE;
            case FIREWORK_ROCKET -> Material.FIREWORK_ROCKET;
            default -> null;
        };
    }
}
