package com.uxplima.uxmessentials.worlds.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import org.jspecify.annotations.NullMarked;

/**
 * Enforces the per-world {@code spawn-animals} setting. Paper 26.2 dropped the animal half of the old
 * server-side spawn flags, so the only place left to hold the setting is the spawn itself: a natural animal
 * spawn in a world that has animals switched off is cancelled here. Monsters still have their own toggle and
 * are applied with the rest of the world settings.
 *
 * <p>The world's setting is read from the Caffeine-warmed {@code WorldRepository} snapshot, which is what makes
 * this cheap enough to sit on a per-spawn event.
 */
@NullMarked
public final class AnimalSpawnListener implements Listener {

    private final WorldRepository repository;

    public AnimalSpawnListener(WorldRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL || !isAnimal(event.getEntity())) {
            return;
        }
        WorldName name;
        try {
            name = WorldName.of(event.getEntity().getWorld().getName());
        } catch (IllegalArgumentException unusableName) {
            return; // a world the module does not manage
        }
        boolean allowed = repository
                .find(name)
                .map(world -> world.settings().get(WorldProperties.SPAWN_ANIMALS))
                .orElse(Boolean.TRUE);
        if (!allowed) {
            event.setCancelled(true);
        }
    }

    /** What the old animals flag covered: land animals, water creatures, and the ambient mobs (bats). */
    private static boolean isAnimal(Entity entity) {
        return entity instanceof Animals || entity instanceof WaterMob || entity instanceof Ambient;
    }
}
