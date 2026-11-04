package com.uxplima.uxmessentials.guard.adapter;

import org.bukkit.Bukkit;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.projectiles.ProjectileSource;

import org.jspecify.annotations.Nullable;

/**
 * Finds the player behind an indirect action: the shooter of an arrow, trident, wind charge or thrown potion,
 * whoever lit a TNT, the owner of a lingering cloud, fangs or a pet, the rider of a mount. Nature when nobody.
 */
public final class Causes {

    private Causes() {}

    /** The responsible player, or null when nature did it. */
    public static @Nullable Player player(@Nullable Entity entity) {
        return player(entity, 0);
    }

    private static @Nullable Player player(@Nullable Entity entity, int depth) {
        if (entity == null || depth > 4) {
            return null;
        }
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile) {
            return source(projectile.getShooter(), depth);
        }
        if (entity instanceof TNTPrimed tnt) {
            return player(tnt.getSource(), depth + 1);
        }
        if (entity instanceof AreaEffectCloud cloud) {
            return source(cloud.getSource(), depth);
        }
        if (entity instanceof EvokerFangs fangs) {
            return player(fangs.getOwner(), depth + 1);
        }
        if (entity instanceof Tameable pet && pet.isTamed() && pet.getOwnerUniqueId() != null) {
            return Bukkit.getPlayer(pet.getOwnerUniqueId());
        }
        for (Entity passenger : entity.getPassengers()) {
            if (passenger instanceof Player rider) {
                return rider;
            }
        }
        return null;
    }

    private static @Nullable Player source(@Nullable ProjectileSource source, int depth) {
        return source instanceof Entity shooter ? player(shooter, depth + 1) : null;
    }
}
