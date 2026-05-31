package com.uxplima.uxmessentials.itemworld.adapter.outbound;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;

import com.uxplima.uxmessentials.itemworld.domain.PurgeSelection;
import org.jspecify.annotations.NullMarked;

/**
 * Sweeps entities for the purge family ({@code /butcher}, {@code /killall}, {@code /remove}) according to a
 * validated {@link PurgeSelection}, and returns the count removed. The domain owns <em>what</em> may be swept
 * (the scope, the type filter, the never-players category and the radius clamp); this adapter performs the live
 * world scan and removal, the one side-effecting step.
 *
 * <p>Invariants the sweep enforces regardless of selection: a {@link Player} is never removed, and a
 * {@link Tameable} that is tamed is left alone (a player's pet survives a {@code /killall}). A radius scope
 * scans the actor's nearby entities; a world scope scans the whole world. The scan runs on the relevant region
 * thread — the caller schedules it through the {@code Scheduler} port.
 */
@NullMarked
public final class BukkitEntityPurger {

    private BukkitEntityPurger() {}

    /** Remove the entities {@code selection} targets around {@code actor}, returning how many were removed. */
    public static int purge(Player actor, PurgeSelection selection) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(selection, "selection");
        World world = actor.getWorld();
        Iterable<? extends Entity> candidates = selection.scope() == PurgeSelection.Scope.RADIUS
                ? nearby(actor, selection.radius())
                : world.getEntities();
        int removed = 0;
        for (Entity entity : candidates) {
            if (matches(entity, selection)) {
                entity.remove();
                removed++;
            }
        }
        return removed;
    }

    private static Iterable<Entity> nearby(Player actor, int radius) {
        return actor.getNearbyEntities(radius, radius, radius);
    }

    private static boolean matches(Entity entity, PurgeSelection selection) {
        if (entity instanceof Player) {
            return false; // never sweep players
        }
        if (entity instanceof Tameable tameable && tameable.isTamed()) {
            return false; // a tamed pet is protected
        }
        return switch (selection.category()) {
            case MONSTERS -> entity instanceof Monster;
            case NAMED_TYPE -> selection
                    .typeId()
                    .map(id -> typeMatches(entity.getType(), id))
                    .orElse(false);
            case ALL_ENTITIES -> true;
        };
    }

    private static boolean typeMatches(EntityType type, String id) {
        Optional<EntityType> resolved = BukkitEntityResolver.spawnable("minecraft:" + id);
        if (resolved.isPresent()) {
            return type == resolved.get();
        }
        return type.name().toLowerCase(Locale.ROOT).equals(id);
    }
}
