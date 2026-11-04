package com.uxplima.uxmessentials.guard.adapter.inbound.listener;

import com.uxplima.uxmessentials.guard.adapter.ActionKinds;
import com.uxplima.uxmessentials.guard.adapter.Causes;
import com.uxplima.uxmessentials.guard.adapter.GuardRuntime;
import com.uxplima.uxmessentials.guard.domain.ActionId;
import com.uxplima.uxmessentials.guard.domain.BuiltInActions;
import java.util.Objects;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;

/**
 * Harm done by players, directly or through what they shot, lit, threw or own. A victim is classified by
 * {@link ActionKinds#damage(Entity)}; the check runs at the victim's position, so a player outside a no-pvp zone
 * cannot shoot into it. Harm with nobody behind it (a zombie biting) is left to the game.
 */
public final class DamageListener implements Listener {

    private final GuardRuntime guard;

    public DamageListener(GuardRuntime guard) {
        this.guard = Objects.requireNonNull(guard, "guard");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Player attacker = Causes.player(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        ActionId action = ActionKinds.damage(victim);
        if (!guard.allow(attacker, action, victim.getLocation())) {
            event.setCancelled(true);
            if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile
                    && !(projectile instanceof org.bukkit.entity.Trident)) {
                projectile.setFireTicks(0);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCombust(EntityCombustByEntityEvent event) {
        Player attacker = Causes.player(event.getCombuster());
        if (attacker != null && !attacker.equals(event.getEntity())
                && !guard.allow(attacker, ActionKinds.damage(event.getEntity()), event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPotion(PotionSplashEvent event) {
        Player thrower = Causes.player(event.getPotion());
        if (thrower == null) {
            return;
        }
        for (LivingEntity affected : event.getAffectedEntities()) {
            if (affected.equals(thrower)) {
                continue;
            }
            if (!guard.allow(thrower, ActionKinds.damage(affected), affected.getLocation())) {
                event.setIntensity(affected, 0);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLingering(LingeringPotionSplashEvent event) {
        Player thrower = Causes.player(event.getEntity());
        if (thrower != null
                && !guard.allow(thrower, BuiltInActions.ITEM_USE_POTION, event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Player remover = Causes.player(event.getRemover());
        if (remover != null && !guard.allow(remover, BuiltInActions.HANGING_BREAK, event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        Player attacker = Causes.player(event.getAttacker());
        if (attacker != null && !guard.allow(attacker, BuiltInActions.DAMAGE_VEHICLE, event.getVehicle().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        Player attacker = Causes.player(event.getAttacker());
        if (attacker != null
                && !guard.allow(attacker, BuiltInActions.VEHICLE_DESTROY, event.getVehicle().getLocation())) {
            event.setCancelled(true);
        }
    }
}
