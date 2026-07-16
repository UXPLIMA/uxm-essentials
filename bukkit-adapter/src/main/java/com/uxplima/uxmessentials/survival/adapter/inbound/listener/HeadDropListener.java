package com.uxplima.uxmessentials.survival.adapter.inbound.listener;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.survival.application.port.RandomSource;
import com.uxplima.uxmessentials.survival.domain.DropChance;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Head-drop: a slain entity may drop its head. A player killed by another player drops their own player head at full
 * odds when {@code player-head-on-pvp} is set; a slain mob drops its vanilla head at the configured {@code mob-chance},
 * or at a per-mob override. It listens on {@link EntityDeathEvent} and adds the head to the event's drop list, so it
 * rides the entity's normal loot (and any loot-cancelling plugin ahead of it, hence {@code ignoreCancelled}).
 *
 * <p>The roll is the pure {@link DropChance}: the listener draws one bounded value through the seedable
 * {@link RandomSource} and asks the chance whether that draw {@link DropChance#drops(int) drops}, which keeps the
 * decision deterministic under test. A mob with no vanilla head item drops nothing regardless of the chance.
 *
 * <h2>Folia</h2>
 * The death event fires on the dying entity's region and the handler only reads that entity and appends to the drop
 * list the event already owns, so no scheduler hop is needed.
 */
@NullMarked
public final class HeadDropListener implements Listener {

    private final boolean playerHeadOnPvp;
    private final DropChance defaultMobChance;
    private final Map<EntityType, DropChance> mobChances;
    private final RandomSource random;

    public HeadDropListener(
            boolean playerHeadOnPvp,
            DropChance defaultMobChance,
            Map<EntityType, DropChance> mobChances,
            RandomSource random) {
        this.playerHeadOnPvp = playerHeadOnPvp;
        this.defaultMobChance = Objects.requireNonNull(defaultMobChance, "defaultMobChance");
        this.mobChances = Map.copyOf(Objects.requireNonNull(mobChances, "mobChances"));
        this.random = Objects.requireNonNull(random, "random");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead instanceof Player victim ? victim.getKiller() : null;
        headFor(dead, killer).ifPresent(head -> event.getDrops().add(head));
    }

    /** The head to drop for {@code dead}, if any: the victim's player head on a PvP kill, else the mob's head by chance. */
    Optional<ItemStack> headFor(LivingEntity dead, @Nullable Player killer) {
        if (dead instanceof Player victim) {
            return playerHead(victim, killer);
        }
        return mobHead(dead.getType());
    }

    private Optional<ItemStack> playerHead(Player victim, @Nullable Player killer) {
        if (!playerHeadOnPvp || killer == null) {
            return Optional.empty();
        }
        return Optional.of(SurvivalHeads.playerHead(victim));
    }

    private Optional<ItemStack> mobHead(EntityType type) {
        DropChance chance = mobChances.getOrDefault(type, defaultMobChance);
        if (chance.isNever()) {
            return Optional.empty();
        }
        Optional<Material> material = SurvivalHeads.mobHeadMaterial(type);
        if (material.isEmpty() || !chance.drops(random.nextBounded(DropChance.RESOLUTION))) {
            return Optional.empty();
        }
        return Optional.of(SurvivalHeads.mobHead(material.get()));
    }
}
